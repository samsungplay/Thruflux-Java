package sender;

import common.Utils;
import payloads.CreateTransferSessionPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class SenderFileHandler {

    public static CreateTransferSessionPayload generateCreateTransferSessionPayload(List<String> paths, int maxReceivers) throws IOException {
        AtomicLong size = new AtomicLong();
        AtomicInteger filesCount = new AtomicInteger();
        SenderRenderer.start();
        SenderRenderer.render("Scanning files... 0 file(s), " + Utils.sizeToReadableFormat(size.get()));

        for(String pathString : paths) {
            Path path = Paths.get(pathString);
            if(Files.isRegularFile(path)) {
                size.addAndGet(Files.size(path));
                filesCount.incrementAndGet();
                SenderRenderer.render("Scanning files... " + filesCount.get() + " file(s), " + Utils.sizeToReadableFormat(size.get()));
            }
            else {
                try(Stream<Path> pathStream = Files.walk(path)) {
                    pathStream.forEach(p -> {
                        try {
                            if(Files.isRegularFile(p)) {
                                filesCount.incrementAndGet();
                                size.addAndGet(Files.size(p));
                            }
                            SenderRenderer.render("Scanning files... " + filesCount.get() + " file(s), " + Utils.sizeToReadableFormat(size.get()));
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        }
        SenderRenderer.render("Scan Complete : " + filesCount.get() + " file(s), " + Utils.sizeToReadableFormat(size.get()));
        SenderRenderer.stop();
        return new CreateTransferSessionPayload(maxReceivers, paths, size.get(), filesCount.get());
    }
}
