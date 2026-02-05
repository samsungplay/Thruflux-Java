package server;

import common.Renderer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import picocli.CommandLine;

import java.io.IOException;

@CommandLine.Command(
        name="thruserv",
        mixinStandardHelpOptions = true,
        description="Thruflux Server"
)
public class ServerEntryPoint implements Runnable {

    @CommandLine.Mixin
    ServerConfig serverConfig;

    public static void main(String[] args) {
        System.exit(new CommandLine(new ServerEntryPoint()).execute(args));
    }

    @Override
    public void run() {
        TransferSessionStore.INSTANCE = new TransferSessionStore(serverConfig);
        ServerModerator.setServerConfig(serverConfig);
        Server server = new Server(serverConfig.port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);
        JettyWebSocketServletContainerInitializer.configure(
                context, ((servletContext, container) -> {
                    container.addMapping("/ws", (req,res) -> {
                        String role = req.getHeader("X-Role");
                        return new ServerSocketHandler(serverConfig, role.equals("sender"));
                    });
                })
        );
        try {
            server.start();
            ServerLogger.info("Server started at port " + serverConfig.port);
            server.join();
        }
        catch(Exception e) {
            ServerLogger.error("Server failed to start at port " + serverConfig.port);
            ServerLogger.error(e.getMessage());
        }
    }
}
