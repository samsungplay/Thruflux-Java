package receiver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReceiverWorker {


    public static void submit(Runnable runnable) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(runnable);
        }
    }

}
