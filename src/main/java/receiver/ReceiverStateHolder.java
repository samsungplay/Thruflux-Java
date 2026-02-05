package receiver;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ReceiverStateHolder {
    private static final AtomicReference<String> link = new AtomicReference<>();
    private static final AtomicInteger files = new AtomicInteger();
    private static final AtomicInteger resumed = new AtomicInteger();
    private static final AtomicReference<Float> percent = new AtomicReference<>();
    private static final AtomicLong ratePerSecond = new AtomicLong();
    private static final AtomicInteger eta = new AtomicInteger();

    public static AtomicReference<String> getLink() {
        return link;
    }

    public static AtomicInteger getFiles() {
        return files;
    }

    public static AtomicInteger getResumed() {
        return resumed;
    }

    public static AtomicReference<Float> getPercent() {
        return percent;
    }

    public static AtomicLong getRatePerSecond() {
        return ratePerSecond;
    }

    public static AtomicInteger getEta() {
        return eta;
    }
}
