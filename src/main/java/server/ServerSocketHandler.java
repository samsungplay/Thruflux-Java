package server;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.Utils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import payloads.*;

import java.io.IOException;
import java.time.Duration;

@WebSocket
public class ServerSocketHandler {

    private ServerConfig serverConfig;

    private static final ObjectMapper mapper = new ObjectMapper();

    private Session session;

    private String id;


    private boolean isSender;

    private TransferSession currentTransfer;

    public ServerSocketHandler(ServerConfig serverConfig, boolean isSender) {
        this.serverConfig = serverConfig;
        this.isSender = isSender;
        this.id = NanoIdUtils.randomNanoId();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        ServerLogger.info("New " + (isSender ? "sender" : "receiver") +  " with id " + id + " has joined!");
        this.session = session;
        session.setIdleTimeout(Duration.ofSeconds(serverConfig.wsIdleTimeout));
        if(serverConfig.turnServer != null && serverConfig.turnStaticAuthSecret != null) {
            ServerLogger.info("Issuing a new TURN credentials for user " + id + "..");
            try {
                TurnCredentialsPayload turnCredentialsPayload = Utils.generateTurnCredentials(serverConfig.turnServer, serverConfig.turnStaticAuthSecret, id,
                        serverConfig.turnStaticCredTtl);
                session.getRemote().sendString(mapper.writeValueAsString(turnCredentialsPayload), WriteCallback.NOOP);
            } catch (Exception e) {
                ServerLogger.warn("Could not issue a new TURN static auth secret: " + e.getMessage());
            }
        }
        else {
            ServerLogger.info("No TURN configuration detected. Skipping issuing new TURN credentials for user " + id);
            try {
                session.getRemote().sendString(mapper.writeValueAsString(new TurnCredentialsPayload("none","none", "none")), WriteCallback.NOOP);
            } catch (JsonProcessingException e) {
                ServerLogger.error("Failed to acknowledge client's connection: " + e.getMessage());
            }
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        if(isSender) {
            ServerLogger.info("A sender with id " + id + " has left.");
        }
        else {
            ServerLogger.info("A receiver with id " + id + " has left.");
            if(currentTransfer != null) {
                try {
                    currentTransfer.getSenderSession().getRemote().sendString(mapper.writeValueAsString(new QuitTransferSessionPayload(id)), WriteCallback.NOOP);
                } catch (IOException e) {
                    ServerLogger.warn("Could not send leave message to the sender for receiver with id " + id + ": " + e.getMessage());
                }
            }
        }

        if(currentTransfer != null && session != null) {
            if(session.equals(currentTransfer.getSenderSession())) {
                currentTransfer.destroy();
            }
            else {
                currentTransfer.removeReceiver(session);
            }
        }
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) throws IOException {
        Payload payload = mapper.readValue(message, Payload.class);
        if(isSender && payload instanceof CreateTransferSessionPayload createTransferSessionPayload) {
            if(currentTransfer != null) {
                session.close(1008, "Duplicate session");
                return;
            }
            TransferSession transferSession = TransferSessionStore.INSTANCE.createSessionFrom(session, createTransferSessionPayload);
            currentTransfer = transferSession;
            session.getRemote().sendString(mapper.writeValueAsString(new CreatedTransferSessionPayload(transferSession.getJoinCode())), WriteCallback.NOOP);
        }
        else if(!isSender && payload instanceof JoinTransferSessionPayload joinTransferSessionPayload) {
            if(currentTransfer != null) {
                session.close(1008, "Duplicate session");
                return;
            }
            joinTransferSessionPayload.setReceiverId(id);
            String joinCode = joinTransferSessionPayload.getJoinCode();
            TransferSession transferSession = TransferSessionStore.INSTANCE.getSessionByJoinCode(joinCode);
            if(transferSession != null) {
                currentTransfer = transferSession;
                transferSession.addReceiver(id, session);
                transferSession.getSenderSession().getRemote().sendString(mapper.writeValueAsString(joinTransferSessionPayload),
                        WriteCallback.NOOP);
            }
            else {
                session.getRemote().sendString(mapper.writeValueAsString(new RejectTransferSessionPayload("No session exists for the join code")), WriteCallback.NOOP);
            }
        }

        else if(isSender && payload instanceof AcceptTransferSessionPayload acceptTransferSessionPayload) {
            if(currentTransfer == null) {
                session.close(1008, "Illegal payload when no transfer active");
                return;
            }
            Session receiverSession = currentTransfer.getReceiver(acceptTransferSessionPayload.getReceiverId());
            receiverSession.getRemote().sendString(mapper.writeValueAsString(acceptTransferSessionPayload));
        }

    }

}
