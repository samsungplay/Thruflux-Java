package sender;

import picocli.CommandLine;

import java.util.List;

public class SenderConfig {

    @CommandLine.Parameters(arity="1..*", paramLabel = "PATHS", description = "The path(s) to share")
    List<String> paths;
    @CommandLine.Option(names="--server-url", defaultValue = "http://localhost:8080")
    String serverUrl;
    @CommandLine.Option(names="--max-receivers", defaultValue = "4")
    int maxReceivers;
    @CommandLine.Option(names="--stun-server", defaultValue = "stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478,stun:stun.bytepipe.app:3478")
    String stunServers;
    @CommandLine.Option(names="--turn-server")
    String turnServers;
    @CommandLine.Option(names="--test-turn")
    boolean testTurn;

    @CommandLine.Option(names="--quic-stream-window-bytes", defaultValue = "67108864")
    long quicStreamWindowBytes;
    @CommandLine.Option(names="--quic-max-incoming-streams", defaultValue = "256")
    int quicMaxIncomingStreams;

    @CommandLine.Option(names="--chunk-size", defaultValue = "16384")
    int chunkSize;

    @CommandLine.Option(names="--total-connections", defaultValue = "4")
    int totalConnections;

    @CommandLine.Option(names="--total-streams", defaultValue = "64")
    int totalStreams;
    @CommandLine.Option(names="--udp-read-buffer-bytes", defaultValue = "8388608")
    int udpReadBufferBytes;
    @CommandLine.Option(names="--udp-write-buffer-bytes", defaultValue = "8388608")
    int udpWriteBufferBytes;

    @CommandLine.Option(names="--benchmark")
    boolean benchmark;
    @CommandLine.Option(names="--verbose")
    boolean verbose;
    @CommandLine.Option(names="--version")
    boolean version;
    @CommandLine.Option(names="--help")
    boolean help;

}
