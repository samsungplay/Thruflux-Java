package common;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.CandidateType;
import org.ice4j.ice.Component;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.RemoteCandidate;
import payloads.SerializedCandidate;
import payloads.TurnCredentialsPayload;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Utils {
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final char[] ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    public static String generateJoinCode() {
//        StringBuilder sb = new StringBuilder();
//        for(int i=0; i<16; i++) {
//            sb.append(ALPHANUMERIC[secureRandom.nextInt(ALPHANUMERIC.length)]);
//            if((i+1) % 4 == 0 && i != 15) {
//                sb.append("-");
//            }
//        }
//        return sb.toString();
        return "7RZQ-CLVP-OCP8-2VEK";
    }

    public static URI toWebSocketURL(URI httpUrl) {
        String wsScheme = httpUrl.getScheme().equalsIgnoreCase("http") ? "ws" : "wss";
        return URI.create(wsScheme + "://" + httpUrl.getHost() + (httpUrl.getPort() == -1 ? "" : ":" + httpUrl.getPort()) + "/ws");
    }

    public static String sizeToReadableFormat(long size) {
        if(size < 1024) return size + "B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp-1) + "B";
        return String.format("%.1f %s", size / Math.pow(1024,exp), unit);
    }


    public static IceHandler.StunServer toStunServer(String raw) {
        URI stunUrl = URI.create(raw);
        return new IceHandler.StunServer(stunUrl.getHost(), stunUrl.getPort() == -1 ? 3478 : stunUrl.getPort());
    }

    public static IceHandler.TurnServer toTurnServer(String raw) {
        URI turnUrl = URI.create(raw);
        int port = 3478;
        if (turnUrl.getScheme().equalsIgnoreCase("turns")) {
            port = 5349;
        }
        if(turnUrl.getPort() != -1) {
            port = turnUrl.getPort();
        }
        String username = "";
        String password = "";
        if(turnUrl.getUserInfo() != null) {
            String[] split = turnUrl.getUserInfo().split(":");
            username = split[0];
            if(split.length > 1) {
                password = split[1];
            }
        }
        return new IceHandler.TurnServer(turnUrl.getHost(), port, username, password);
    }

    public static IceHandler.TurnServer toTurnServer(String raw, String username, String password) {
        URI turnUrl = URI.create(raw);
        int port = 3478;
        if (turnUrl.getScheme().equalsIgnoreCase("turns")) {
            port = 5349;
        }
        if(turnUrl.getPort() != -1) {
            port = turnUrl.getPort();
        }
        return new IceHandler.TurnServer(turnUrl.getHost(), port, username, password);
    }

    public static TurnCredentialsPayload generateTurnCredentials(String turnUrl, String secret, String userId, long seconds) throws NoSuchAlgorithmException, InvalidKeyException {
        long expiry = System.currentTimeMillis() / 1000L + seconds;
        String username = expiry + ":" + userId;
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] code = mac.doFinal(username.getBytes(StandardCharsets.UTF_8));
        String password = Base64.getEncoder().encodeToString(code);
        return new TurnCredentialsPayload(username, password, turnUrl);
    }

    public static SerializedCandidate serializeCandidate(LocalCandidate localCandidate, int componentId) {
        TransportAddress transportAddress = localCandidate.getTransportAddress();
        String type = localCandidate.getType().toString();
        LocalCandidate relatedCandidate = localCandidate.getRelatedCandidate();

        SerializedCandidate relatedSerialized = null;
        if(relatedCandidate != null) {
            relatedSerialized = new SerializedCandidate(
                    componentId,
                    relatedCandidate.getType().toString(),
                    relatedCandidate.getFoundation(),
                    relatedCandidate.getPriority(),
                    relatedCandidate.getTransportAddress().getHostAddress(),
                    relatedCandidate.getTransportAddress().getPort(),
                    null
            );
        }

        return new SerializedCandidate(
                componentId,
                    type,
                localCandidate.getFoundation(),
                localCandidate.getPriority(),
                transportAddress.getHostAddress(),
                transportAddress.getPort(),
                relatedSerialized
        );
    }

    public static RemoteCandidate deserializeCandidate(SerializedCandidate candidate, Component component) throws UnknownHostException {
        CandidateType candidateType = CandidateType.parse(candidate.type());
        TransportAddress transportAddress = new TransportAddress(InetAddress.getByName(candidate.ip()),
                candidate.port(), Transport.UDP);

        return new RemoteCandidate(
                transportAddress,
                component,
                candidateType,
                candidate.foundation(),
                candidate.priority(),
                candidate.relatedCandidate() != null ?
                        new RemoteCandidate(
                                new TransportAddress(
                                        InetAddress.getByName(candidate.relatedCandidate().ip()),
                                        candidate.relatedCandidate().port(),
                                        Transport.UDP
                                ),
                                component,
                                CandidateType.parse(candidate.relatedCandidate().type()),
                                candidate.relatedCandidate().foundation(),
                                candidate.relatedCandidate().priority(),
                                null
                        ) : null
        );
    }


    public static void disableExternalLogging() {
        LogManager.getLogManager().reset();

        Logger root = Logger.getLogger("");
        root.setLevel(Level.OFF);

        for (Handler h : root.getHandlers()) {
            h.setLevel(Level.OFF);
            root.removeHandler(h);
        }

        Logger.getLogger("org.ice4j").setLevel(Level.OFF);
        Logger.getLogger("org.jitsi").setLevel(Level.OFF);
        Logger.getLogger("org.jitsi.utils.logging2").setLevel(Level.OFF);

    }

    public static void putLongBE(byte[] b, int off, long v) {
        b[off]     = (byte) (v >>> 56);
        b[off + 1] = (byte) (v >>> 48);
        b[off + 2] = (byte) (v >>> 40);
        b[off + 3] = (byte) (v >>> 32);
        b[off + 4] = (byte) (v >>> 24);
        b[off + 5] = (byte) (v >>> 16);
        b[off + 6] = (byte) (v >>> 8);
        b[off + 7] = (byte) (v);
    }

    public static long getLongBE(byte[] b, int off) {
        return ((long)(b[off] & 0xFF) << 56) |
                ((long)(b[off + 1] & 0xFF) << 48) |
                ((long)(b[off + 2] & 0xFF) << 40) |
                ((long)(b[off + 3] & 0xFF) << 32) |
                ((long)(b[off + 4] & 0xFF) << 24) |
                ((long)(b[off + 5] & 0xFF) << 16) |
                ((long)(b[off + 6] & 0xFF) << 8)  |
                ((long)(b[off + 7] & 0xFF));
    }

    public static void putIntBE(byte[] b, int off, int v) {
        b[off]     = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) (v);
    }

    public static int getIntBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) |
                ((b[off + 1] & 0xFF) << 16) |
                ((b[off + 2] & 0xFF) << 8) |
                (b[off + 3] & 0xFF);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
