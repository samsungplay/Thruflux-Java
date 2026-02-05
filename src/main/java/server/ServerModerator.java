package server;

import java.util.concurrent.atomic.AtomicInteger;

public class ServerModerator {

    private static AtomicInteger numConnections = new AtomicInteger(0);
    private static AtomicInteger numSessions = new AtomicInteger(0);
    private static ServerConfig serverConfig;

    public static void setServerConfig(ServerConfig serverConfig) {
        ServerModerator.serverConfig = serverConfig;
    }


    public static boolean canHaveMoreSessions() {
        return numSessions.get() < serverConfig.maxSessions;
    }

    public static void registerSession() {
        numSessions.incrementAndGet();
    }

    public static void unregisterSession() {
        numSessions.decrementAndGet();
    }


}
