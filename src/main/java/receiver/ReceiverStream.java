package receiver;


import common.Utils;
import org.ice4j.ice.Component;
import sender.SenderLogger;
import tech.kwik.core.QuicStream;
import tech.kwik.core.log.NullLogger;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static common.Utils.*;

public class ReceiverStream {

    private static final byte FILE_METADATA = 1;
    private static final byte CHUNK = 2;
    private static final byte CTRL = 3;
    private static final byte CTRL_DONE = 4;
    private static final byte CTRL_OK = 5;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile long fileSize;
    private volatile int chunkSize;
    private final AtomicLong receivedByts = new AtomicLong(0);
    private final AtomicBoolean init = new AtomicBoolean(false);
    private Path outDir;
    private volatile Path outFile;

    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(8192);

    private final ArrayBlockingQueue<byte[]> bufferPool = new ArrayBlockingQueue<>(8192);

    private ServerConnector serverConnector;

    private final CountDownLatch diskFlushed = new CountDownLatch(1);

    private volatile long startTime = 0;


    private static final int HDR = 12;

    private final AtomicLong lastReportBytes = new AtomicLong(0);
    private final AtomicLong lastReportNano = new AtomicLong(0);
    private final AtomicLong ewmaBpsBits = new AtomicLong(Double.doubleToRawLongBits(0.0));




    private void handleStream(QuicStream stream) {

        try (DataInputStream is = new DataInputStream(new BufferedInputStream(stream.getInputStream(), 256 * 1024)))  {
            int first = is.read();
            if (first == -1) return;

            byte type = (byte) first;

            //Handle ack
            if (type == CTRL) {
                int cmd = is.read();
                if (cmd != CTRL_DONE) {
                    throw new IOException("Unexpected CTRL cmd: " + cmd);
                }
                diskFlushed.await();
                try (OutputStream os = stream.getOutputStream()) {
                    os.write(CTRL);
                    os.write(CTRL_OK);
                    os.flush();
                }
                done.countDown();
                return;
            }

            if (type != FILE_METADATA) {
                throw new IOException("Expected file metadata but got: " + type);
            }

            long size = is.readLong();
            int fileNameLength = is.readInt();
            byte[] fileNameBytes = new byte[fileNameLength];
            is.readFully(fileNameBytes);

            String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
            int cs = is.readInt();

            if (init.compareAndSet(false, true)) {
                fileSize = size;
                chunkSize = cs;
                outFile = outDir.resolve(fileName);
                try (RandomAccessFile raf = new RandomAccessFile(outFile.toFile(), "rw")) {
                    raf.setLength(fileSize);
                }
                ReceiverLogger.info("Initialized receive: " + fileName + " size=" + fileSize + " chunkSize=" + chunkSize);
                startTime = System.currentTimeMillis();
                lastReportBytes.set(0);
                lastReportNano.set(System.nanoTime());
                ewmaBpsBits.set(Double.doubleToRawLongBits(0.0));
            }
            else {
                if (size != fileSize || cs != chunkSize) {
                    throw new IOException("Mismatched header across streams");
                }
            }

            while (true) {
                int t = is.read();
                if (t == -1) break;
                if ((byte) t != CHUNK) {
                    throw new IOException("Unexpected type: " + t);
                }

                int chunkIndex = is.readInt();   // you can ignore it
                long offset = is.readLong();
                int len = is.readInt();

                byte[] buf = bufferPool.take();
                if (len > buf.length - HDR) throw new IOException("buffer len too big: " + len);
                putLongBE(buf, 0, offset);
                putIntBE(buf, 8, len);
                is.readFully(buf, HDR, len);
                queue.put(buf);
                receivedByts.addAndGet(len);
            }

        }

        catch (Exception e) {
            ReceiverLogger.error("Error while handling stream: " + e.getMessage());
            stream.abortReading(-1);
            diskFlushed.countDown();
            done.countDown();
        }

    }



    public ReceiverStream(Component component, ReceiverConfig receiverConfig) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(Paths.get("thruflux.p12"))) {
            keyStore.load(is, "changeit".toCharArray());
        }
        SysOutLogger log = new SysOutLogger();
        log.logInfo(true);
        log.logRecovery(true);
        log.logCongestionControl(true);
        log.logStats(true);
        outDir =  Paths.get(receiverConfig.out);

        int poolSize = 2048;
        for (int i = 0; i < poolSize; i++) {
            bufferPool.add(new byte[receiverConfig.chunkSize + HDR]);
        }

        component.getSocket().setSendBufferSize(receiverConfig.udpWriteBufferBytes);
        component.getSocket().setReceiveBufferSize(receiverConfig.udpReadBufferBytes);

        serverConnector = ServerConnector.builder()
                .withLogger(new NullLogger())
//                .withPort(40022)
                .withSocket(component.getSocket())
                .withKeyStore(keyStore, "thruflux", "changeit".toCharArray())
                .withConfiguration(
                        ServerConnectionConfig.builder()
                                .maxOpenPeerInitiatedBidirectionalStreams(receiverConfig.quicMaxIncomingStreams)
                                .maxBidirectionalStreamBufferSize(receiverConfig.quicStreamWindowBytes)
                                .maxIdleTimeout(30_000)
                                .maxConnectionBufferSize(receiverConfig.quicConnWindowBytes)
                                .build()
                )
                .build();

        serverConnector.registerApplicationProtocol("THRUFLUX",
                (protocol, connection) -> new ApplicationProtocolConnection() {
                    @Override
                    public void acceptPeerInitiatedStream(QuicStream stream) {
                            ReceiverWorker.getIoWorker().submit(() -> handleStream(stream));
                    }
                });

        ScheduledExecutorService progressReporter = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        progressReporter.scheduleAtFixedRate(() -> {
            long size = fileSize;
            if (size <= 0) return;

            long nowNs = System.nanoTime();
            long nowBytes = receivedByts.get();

            long prevNs = lastReportNano.getAndSet(nowNs);
            long prevBytes = lastReportBytes.getAndSet(nowBytes);

            long dtNs = nowNs - prevNs;
            long dBytes = nowBytes - prevBytes;

            if (dtNs <= 0 || dBytes < 0) return;

            double dtSec = dtNs / 1_000_000_000.0;

            double instBps = dBytes / dtSec;

            final double halfLifeSec = 2.0;
            double alpha = 1.0 - Math.exp(-Math.log(2.0) * (dtSec / halfLifeSec));

            while (true) {
                long oldBits = ewmaBpsBits.get();
                double old = Double.longBitsToDouble(oldBits);
                double updated = old + alpha * (instBps - old);
                long newBits = Double.doubleToRawLongBits(updated);
                if (ewmaBpsBits.compareAndSet(oldBits, newBits)) {
                    break;
                }
            }

            double ewmaBps = Double.longBitsToDouble(ewmaBpsBits.get());
            double ewmaMBps = ewmaBps / 1_000_000.0;

            ReceiverLogger.info(
                    "Received: " + Utils.sizeToReadableFormat(nowBytes) + "/" + Utils.sizeToReadableFormat(size) +
                            String.format(" (%.2f MB/s ewma)", ewmaMBps)
            );

        }, 250, 250, TimeUnit.MILLISECONDS);

        Thread diskThread = new Thread(() -> {
            FileChannel channel = null;
            try {
                while (!init.get()) {
                    Thread.sleep(5);
                }
                channel = FileChannel.open(outFile, StandardOpenOption.WRITE);

                while (true) {
                    byte[] buf = queue.poll(50, TimeUnit.MILLISECONDS);
                    if (buf == null) {
                        if (fileSize > 0 && receivedByts.get() >= fileSize && queue.isEmpty()) {
                            break;
                        }
                        continue;
                    }


                    long offset = getLongBE(buf, 0);
                    int length = getIntBE(buf, 8);

                    ByteBuffer byteBuffer = ByteBuffer.wrap(buf, HDR, length);
                    while(byteBuffer.hasRemaining()) {
                        int w = channel.write(byteBuffer, offset);
                        offset += w;
                    }

                    bufferPool.offer(buf);
                }

                channel.force(true);
            } catch (Exception e) {
                ReceiverLogger.error("Error while writing disk: " + e.getMessage());
            } finally {
                diskFlushed.countDown();
                if (channel != null) {
                    try { channel.close(); } catch (IOException ignored) {}
                }
            }
        });

        diskThread.setDaemon(true);
        diskThread.start();

    }

    public void receiveTransfer() {
        serverConnector.start();

        try {
            done.await();
            SenderLogger.info("File received. Taken = " + (System.currentTimeMillis() - startTime) / 1000.0f + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            serverConnector.close();
            ReceiverWorker.getIoWorker().shutdown();
            System.exit(0);
        }
    }


}
