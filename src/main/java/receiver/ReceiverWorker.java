package receiver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReceiverWorker {
    private static final ExecutorService networkWorker = Executors.newVirtualThreadPerTaskExecutor();

    private static final ExecutorService ioWorker = Executors.newVirtualThreadPerTaskExecutor();


    public static ExecutorService getNetworkWorker() {
        return networkWorker;
    }

    public static ExecutorService getIoWorker() {
        return ioWorker;
    }

}
