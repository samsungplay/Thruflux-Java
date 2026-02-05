package sender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SenderWorker {
    private static final ExecutorService fileWorker = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService renderWorker = Executors.newSingleThreadScheduledExecutor();


    public static ExecutorService getFileWorker() {
        return fileWorker;
    }

    public static ScheduledExecutorService getRenderWorker() {
        return renderWorker;
    }

    public static void submit(Runnable runnable) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(runnable);
        }
    }
}
