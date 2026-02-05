package sender;

import common.IceHandler;
import common.Utils;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

@CommandLine.Command(
        name="host",
        mixinStandardHelpOptions = true,
        description="Thruflux Sender"
)
public class SenderEntryPoint implements Runnable {

    @CommandLine.Mixin
    SenderConfig senderConfig;

    private final CountDownLatch clientDone = new CountDownLatch(1);

    public static void main(String[] args) {
        System.exit(new CommandLine(new SenderEntryPoint()).execute(args));
    }

    @Override
    public void run() {

        Utils.disableExternalLogging();

        for(String stunUrl : senderConfig.stunServers.split(",")) {
            IceHandler.addStunServer(Utils.toStunServer(stunUrl));
        }
        if(senderConfig.turnServers != null) {
            for(String turnUrl : senderConfig.turnServers.split(",")) {
                IceHandler.addTurnServer(Utils.toTurnServer(turnUrl));
            }
        }
        WebSocketClient client = new WebSocketClient();
        try {
            SenderLogger.info("Connecting to signaling server at " + senderConfig.serverUrl + "...");
            client.start();
            SenderSocketHandler senderSocketHandler = new SenderSocketHandler(senderConfig, clientDone);
            ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
            upgradeRequest.setHeader("X-Role", "sender");
            client.connect(senderSocketHandler, Utils.toWebSocketURL(URI.create(senderConfig.serverUrl)), upgradeRequest);
            clientDone.await();
        } catch (Exception e) {
            SenderLogger.error("Could not connect to signaling server at " + senderConfig.serverUrl);
            SenderLogger.error(e.getMessage());
        }
        finally {
            try {
                SenderRenderer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            SenderRenderer.stopRenderLoop();
        }
    }
}
