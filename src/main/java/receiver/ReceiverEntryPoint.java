package receiver;

import common.IceHandler;
import common.Utils;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

@CommandLine.Command(
        name="join",
        mixinStandardHelpOptions = true,
        description="Thruflux Receiver"
)
public class ReceiverEntryPoint implements Runnable {

    @CommandLine.Mixin
    ReceiverConfig receiverConfig;

    private final CountDownLatch clientDone = new CountDownLatch(1);

    public static void main(String[] args) {
        System.exit(new CommandLine(new ReceiverEntryPoint()).execute(args));
    }

    @Override
    public void run() {

        Utils.disableExternalLogging();

        for(String stunUrl : receiverConfig.stunServers.split(",")) {
            IceHandler.addStunServer(Utils.toStunServer(stunUrl));
        }
        if(receiverConfig.turnServers != null) {
            for(String turnUrl : receiverConfig.turnServers.split(",")) {
                IceHandler.addTurnServer(Utils.toTurnServer(turnUrl));
            }
        }
        WebSocketClient client = new WebSocketClient();
        try {
            ReceiverLogger.info("Connecting to signaling server at " + receiverConfig.serverUrl + "...");
            client.start();
            ReceiverSocketHandler senderSocketHandler = new ReceiverSocketHandler(receiverConfig, clientDone);
            ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
            upgradeRequest.setHeader("X-Role", "receiver");
            client.connect(senderSocketHandler, Utils.toWebSocketURL(URI.create(receiverConfig.serverUrl)), upgradeRequest);
            clientDone.await();
        } catch (Exception e) {
            ReceiverLogger.error("Could not connect to signaling server at " + receiverConfig.serverUrl);
            ReceiverLogger.error(e.getMessage());
        }
        finally {
            try {
                ReceiverRenderer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
