package sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.IceHandler;
import common.Utils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceProcessingState;
import payloads.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.*;

@WebSocket
public class SenderSocketHandler {

    private SenderConfig senderConfig;
    private Session session;
    private CountDownLatch clientDone;
    private static final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeat;


    public SenderSocketHandler(SenderConfig senderConfig, CountDownLatch clientDone) {
        this.senderConfig = senderConfig;
        this.clientDone = clientDone;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) throws Exception {
        this.session = session;
        session.setIdleTimeout(Duration.ofSeconds(120));
        this.heartbeat = pinger.scheduleAtFixedRate(() -> {
            try {
                session.getRemote().sendPing(ByteBuffer.allocate(1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 0, 1, TimeUnit.MINUTES);

        SenderLogger.info("Succesfully connected to signaling server: " + senderConfig.serverUrl);

    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        SenderLogger.info("Websocket to signaling server closed with statusCode " + statusCode);
        SenderLogger.info("Reason=" + reason);
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
        pinger.shutdownNow();
        clientDone.countDown();
    }

    @OnWebSocketMessage
    public void onMessage(String message) throws Exception {
        Payload payload = mapper.readValue(message, Payload.class);
        if (payload instanceof CreatedTransferSessionPayload created) {
            SenderStateHolder.setJoinCode(created.getJoinCode());
//            SenderRenderer.startRenderLoop();
        } else if (payload instanceof TurnCredentialsPayload turnCredentialsPayload) {

            if (!turnCredentialsPayload.getUsername().equals("none") || !turnCredentialsPayload.getPassword().equals("none")) {
                IceHandler.addTurnServer(Utils.toTurnServer(turnCredentialsPayload.getTurnUrl(), turnCredentialsPayload.getUsername(), turnCredentialsPayload.getPassword()));
            }

            SenderWorker.getFileWorker().submit(() -> {
                try {
                    CreateTransferSessionPayload createTransferSessionPayload = SenderFileHandler.generateCreateTransferSessionPayload(senderConfig.paths, senderConfig.maxReceivers);
                    SenderStateHolder.setManifest(createTransferSessionPayload);
                    session.getRemote().sendString(mapper.writeValueAsString(createTransferSessionPayload), WriteCallback.NOOP);
                } catch (Exception e) {
                    SenderLogger.error("Failed to prepare transfer session: " + e.getMessage());
                    session.close(1011, "Failed to prepare transfer session");
                }
            });
        } else if (payload instanceof JoinTransferSessionPayload joinTransferSessionPayload) {
            String receiverId = joinTransferSessionPayload.getReceiverId();
            SenderStateHolder.addReceiver(receiverId);
            SenderStateHolder.ReceiverInfo receiverInfo = SenderStateHolder.getReceiver(receiverId);
            SenderWorker.submit(() -> {
                try {
                    IceHandler.CandidatesResult candidatesResult = IceHandler.gatherAllCandidates(true, senderConfig.totalConnections);
                    IceHandler.establishConnection(joinTransferSessionPayload.getLocalCandidates(), joinTransferSessionPayload.getLocalUfrag(), joinTransferSessionPayload.getLocalPassword(),
                            ( components) -> {
                                    receiverInfo.getStatus().set("CONNECTED");
                                    SenderLogger.info("ICE Complete.");
                                    for(Component component : components) {
                                        SenderLogger.info("component id=" + component.getComponentID() + ", pair=" + component.getSelectedPair().toShortString());
                                    }
                                    SenderWorker.submit(() -> {
                                        try {
                                            receiverInfo.getStatus().set("SENDING");
                                            SenderStream senderStream = new SenderStream(components, senderConfig);
                                            senderStream.sendTransfer();
                                        }
                                        catch(Exception e) {
                                            e.printStackTrace();
                                            SenderLogger.error("OOF: " + e.getMessage());
                                            receiverInfo.getStatus().set("FAILED");
                                        }
                                    });
                                if(components.isEmpty()) {
                                    receiverInfo.getStatus().set("UNREACHABLE");
                                }


                            });
                    AcceptTransferSessionPayload acceptTransferSessionPayload = new AcceptTransferSessionPayload(
                            candidatesResult.localUfrag(),
                            candidatesResult.localPassword(),
                            candidatesResult.candidates(),
                            receiverId
                    );

                    session.getRemote().sendString(mapper.writeValueAsString(acceptTransferSessionPayload), WriteCallback.NOOP);
                } catch (IOException ignored) {
                    receiverInfo.getStatus().set("UNREACHABLE");
                }
            });


        } else if (payload instanceof QuitTransferSessionPayload quitTransferSessionPayload) {
            SenderStateHolder.ReceiverInfo receiverInfo = SenderStateHolder.getReceiver(quitTransferSessionPayload.getReceiverId());
            if (receiverInfo != null) {
                receiverInfo.getStatus().set("DISCONNECTED");
            }
        }
    }


    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        SenderLogger.error("Websocket error while connected");
        SenderLogger.error(error.getMessage());
        clientDone.countDown();
    }


}
