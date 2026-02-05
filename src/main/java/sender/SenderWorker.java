package sender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SenderWorker {
    private static final ExecutorService fileWorker = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService renderWorker = Executors.newSingleThreadScheduledExecutor();
    private static final ExecutorService networkWorker = Executors.newVirtualThreadPerTaskExecutor();
    private static final ExecutorService ioWorker = Executors.newCachedThreadPool();


    public static ExecutorService getFileWorker() {
        return fileWorker;
    }

    public static ScheduledExecutorService getRenderWorker() {
        return renderWorker;
    }

    public static ExecutorService getNetworkWorker() {
        return networkWorker;
    }

    public static ExecutorService getIoWorker() {
        return ioWorker;
    }
}
