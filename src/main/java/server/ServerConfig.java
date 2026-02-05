package server;

import picocli.CommandLine;

public class ServerConfig {

    @CommandLine.Option(names="--port", defaultValue = "8080")
    int port;
    @CommandLine.Option(names="--max-sessions", defaultValue = "1000")
    int maxSessions;
    @CommandLine.Option(names="--max-receivers-per-sender", defaultValue = "10")
    int maxReceiversPerSender;
    @CommandLine.Option(names="--max-message-bytes", defaultValue = "65536")
    int maxMessageBytes;
    @CommandLine.Option(names="--ws-connects-per-min", defaultValue = "30")
    int wsConnectionsPerMin;
    @CommandLine.Option(names="--ws-connects-burst", defaultValue = "10")
    int wsConnectionsBurst;
    @CommandLine.Option(names="--ws-msgs-per-sec", defaultValue = "50")
    int wsMessagesPerSec;
    @CommandLine.Option(names="--ws-msgs-burst", defaultValue = "100")
    int wsMessagesBurst;
    @CommandLine.Option(names="--session-creates-per-min", defaultValue = "10")
    int sessionCreatesPerMin;
    @CommandLine.Option(names="--session-creates-burst", defaultValue = "5")
    int sessionCreatesBurst;
    @CommandLine.Option(names="--max-ws-connections", defaultValue = "2000")
    int maxWsConnections;
    @CommandLine.Option(names="--ws-idle-timeout", defaultValue = "600")
    int wsIdleTimeout;
    @CommandLine.Option(names="--session-timeout", defaultValue = "86400")
    int sessionTimeout;
    @CommandLine.Option(names="--turn-server")
    String turnServer;
    @CommandLine.Option(names="--turn-static-auth-secret")
    String turnStaticAuthSecret;
    @CommandLine.Option(names="--turn-cred-ttl", defaultValue = "600")
    long turnStaticCredTtl;
    @CommandLine.Option(names="--version")
    boolean version;
    @CommandLine.Option(names="--help")
    boolean help;

}
