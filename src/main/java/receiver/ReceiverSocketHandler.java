package receiver;

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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.*;

@WebSocket
public class ReceiverSocketHandler {

    private ReceiverConfig receiverConfig;
    private Session session;
    private CountDownLatch clientDone;
    private static final ObjectMapper mapper = new ObjectMapper();
    private CreateTransferSessionPayload createTransferSessionPayload;
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeat;

    public ReceiverSocketHandler(ReceiverConfig receiverConfig, CountDownLatch clientDone) {
        this.receiverConfig = receiverConfig;
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

        ReceiverLogger.info("Succesfully connected to signaling server: " + receiverConfig.serverUrl);


    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        ReceiverLogger.info("Websocket to signaling server closed with statusCode " + statusCode);
        ReceiverLogger.info("Reason=" + reason);
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
        pinger.shutdownNow();
        clientDone.countDown();
    }

    @OnWebSocketMessage
    public void onMessage(String message) throws Exception {
        Payload payload = mapper.readValue(message, Payload.class);

        if (payload instanceof TurnCredentialsPayload turnCredentialsPayload) {
            if (!turnCredentialsPayload.getUsername().equals("none") || !turnCredentialsPayload.getPassword().equals("none")) {
                IceHandler.addTurnServer(Utils.toTurnServer(turnCredentialsPayload.getTurnUrl(), turnCredentialsPayload.getUsername(), turnCredentialsPayload.getPassword()));
            }
            ReceiverLogger.info("Connecting to host...");
            ReceiverWorker.submit(() -> {
                try {
                    IceHandler.CandidatesResult candidatesResult = IceHandler.gatherAllCandidates(false, receiverConfig.totalConnections);
                    session.getRemote().sendString(mapper.writeValueAsString(new JoinTransferSessionPayload(
                            candidatesResult.localUfrag(),
                            candidatesResult.localPassword(),
                            candidatesResult.candidates(),
                            receiverConfig.joinCode,
                            "to_be_provided_by_server"
                    )), WriteCallback.NOOP);
                } catch (IOException e) {
                    ReceiverLogger.error("Failed to join a session: " + e.getMessage());
                    session.close(1011, "Failed to join a session");
                }
            });
        } else if (payload instanceof RejectTransferSessionPayload rejectTransferSessionPayload) {
            ReceiverLogger.error("Join session request rejected: " + rejectTransferSessionPayload.getReason());
            session.close(1011, "Join session request rejected");
        } else if (payload instanceof AcceptTransferSessionPayload acceptTransferSessionPayload) {
            ReceiverLogger.info("Accepted. Starting connection..");
            ReceiverWorker.submit(() -> {
                try {
                    IceHandler.establishConnection(acceptTransferSessionPayload.getLocalCandidates(),
                            acceptTransferSessionPayload.getLocalUfrag(),
                            acceptTransferSessionPayload.getLocalPassword(), ( components) -> {
                                    ReceiverLogger.info("ICE complete.");
                                    for(Component component : components) {
                                        ReceiverLogger.info("component id=" + component.getComponentID() + ", selected pair=" + component.getSelectedPair().toShortString());
                                    }
                                        ReceiverWorker.submit(() -> {
                                            try {
                                                ReceiverStream receiverStream = new ReceiverStream(components, receiverConfig);
                                                receiverStream.receiveTransfer();
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                ReceiverLogger.error("Error while receiving transfer : " + e.getMessage());
                                            }
                                        });



                            });

                } catch (UnknownHostException e) {
                    ReceiverLogger.error("Failed to connect to host: " + e.getMessage());
                }
            });
        }


    }


    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        ReceiverLogger.error("Websocket error while connected");
        ReceiverLogger.error(error.getMessage());
        clientDone.countDown();
    }


}
