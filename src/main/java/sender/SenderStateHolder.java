package sender;

import payloads.CreateTransferSessionPayload;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SenderStateHolder {

    public static class ReceiverInfo {
        private final AtomicReference<String> receiverId = new AtomicReference<>();
        private final AtomicReference<String> status = new AtomicReference<>();
        private final AtomicReference<String> link = new AtomicReference<>();
        private final AtomicInteger files = new AtomicInteger();
        private final AtomicInteger resumed = new AtomicInteger();
        private final AtomicReference<Float> percent = new AtomicReference<>();
        private final AtomicLong ratePerSecond = new AtomicLong();
        private final AtomicInteger eta = new AtomicInteger();

        public ReceiverInfo(String receiverId) {
            this.receiverId.set(receiverId);
            this.status.set("CONNECTING");
            this.link.set("?");
        }

        public AtomicInteger getResumed() {
            return resumed;
        }

        public AtomicReference<String> getReceiverId() {
            return receiverId;
        }

        public AtomicReference<String> getStatus() {
            return status;
        }

        public AtomicInteger getFiles() {
            return files;
        }

        public AtomicLong getRatePerSecond() {
            return ratePerSecond;
        }

        public AtomicReference<Float> getPercent() {
            return percent;
        }

        public AtomicInteger getEta() {
            return eta;
        }

        public AtomicReference<String> getLink() {
            return link;
        }
    }



    private static final Map<String, ReceiverInfo> receivers = new ConcurrentHashMap<>();

    private static CreateTransferSessionPayload manifest;

    private static String joinCode = "";


    public static void addReceiver(String receiverId) {
        receivers.put(receiverId, new ReceiverInfo(receiverId));
    }

    public static void setManifest(CreateTransferSessionPayload manifest) {
        SenderStateHolder.manifest = manifest;
    }

    public static void setJoinCode(String joinCode) {
        SenderStateHolder.joinCode = joinCode;
    }

    public static CreateTransferSessionPayload getManifest() {
        return manifest;
    }

    public static String getJoinCode() {
        return joinCode;
    }

    public static ReceiverInfo getReceiver(String receiverId) {
        return receivers.get(receiverId);
    }

    public static Collection<ReceiverInfo> getReceivers() {
        return receivers.values();
    }


    public static void removeReceiver(String receiverId) {
        receivers.remove(receiverId);
    }
}
