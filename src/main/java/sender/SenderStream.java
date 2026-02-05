package sender;


import common.Utils;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Component;
import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;

import java.io.*;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static common.Utils.*;

public class SenderStream {

    private final List<QuicClientConnection> connections = new ArrayList<>();
    private final Path file;
    private SenderConfig config;
    private static final byte FILE_METADATA = 1;
    private static final byte CHUNK = 2;
    private static final byte CTRL = 3;
    private static final byte CTRL_DONE = 4;
    private static final byte CTRL_OK = 5;
    private ArrayBlockingQueue<byte[]> bufferPool;
    private static int HDR = 12;
    private static final int CHUNK_FRAME_HDR = 1 + 4 + 8 + 4;
    private static final int BATCH_BYTES = 1024 * 1024;

    public SenderStream(
            List<Component> components,
            SenderConfig senderConfig
    ) throws SocketException, UnknownHostException, IllegalStateException, IllegalArgumentException {

        if (SenderStateHolder.getManifest() == null ||
                SenderStateHolder.getManifest().getPaths().isEmpty()) {
            throw new IllegalStateException("No transfer is active");
        }

        if (components.isEmpty()) {
            throw new IllegalArgumentException("0 connections formed");
        }

        for (Component component : components) {
            component.getSocket().setSendBufferSize(senderConfig.udpWriteBufferBytes);
            component.getSocket().setReceiveBufferSize(senderConfig.udpReadBufferBytes);

            TransportAddress remoteAddress = component.getSelectedPair().getRemoteCandidate().getTransportAddress();

            QuicClientConnection connection = QuicClientConnection.newBuilder()
                    .host(remoteAddress.getHostAddress())
                    .port(remoteAddress.getPort())
                    .applicationProtocol("THRUFLUX")
                    .socketFactory(addr -> component.getSocket())
                    .noServerCertificateCheck()
                    .connectTimeout(Duration.ofSeconds(30))
                    .maxOpenPeerInitiatedBidirectionalStreams(senderConfig.quicMaxIncomingStreams)
                    .defaultStreamReceiveBufferSize(senderConfig.quicStreamWindowBytes)
                    .maxIdleTimeout(Duration.ofSeconds(30))
                    .build();

            connections.add(connection);
        }

        file = Paths.get(SenderStateHolder.getManifest().getPaths().get(0));
        config = senderConfig;
    }

    public void sendTransfer() throws IOException {

        long fileSize = Files.size(file);
        byte[] fileNameBytes = file.getFileName().toString().getBytes(StandardCharsets.UTF_8);

        int chunkSize = config.chunkSize;
        int totalStreams = config.totalStreams;

        int queueCapacity = clamp(4*config.totalStreams, 512, 8192);
        int bufferPoolSize = queueCapacity + 2 * config.totalStreams;

        bufferPool = new ArrayBlockingQueue<>(bufferPoolSize);

        for (int i = 0; i < bufferPoolSize; i++) {
            bufferPool.add(new byte[chunkSize + HDR]);
        }

        ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(queueCapacity);
        AtomicLong sentBytes = new AtomicLong(0);
        AtomicBoolean diskDone = new AtomicBoolean(false);

        ScheduledExecutorService progressReporter =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setDaemon(true);
                    return thread;
                });

        progressReporter.scheduleAtFixedRate(() -> {
            SenderLogger.info("Sent: " + Utils.sizeToReadableFormat(sentBytes.get()) + "/" +
                    Utils.sizeToReadableFormat(fileSize));
        }, 250, 250, TimeUnit.MILLISECONDS);


        SenderLogger.info("Connecting to receiver...");

        for (QuicClientConnection connection : connections) {
            connection.connect();
        }

        ExecutorService ioHandler = Executors.newFixedThreadPool(totalStreams, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        Thread diskThread = new Thread(() -> {
            try (InputStream is = new BufferedInputStream(Files.newInputStream(file), 1024 * 1024)) {
                long offset = 0;
                int index = 0;
                while (offset < fileSize) {
                    int len = (int) Math.min(chunkSize, fileSize - offset);
                    byte[] buffer = bufferPool.take();

                    int read = 0;
                    while (read < len) {
                        int n = is.read(buffer, HDR + read, len - read);
                        if (n < 0) throw new EOFException("Error while reading file at offset = " + offset);
                        read += n;
                    }
                    putLongBE(buffer, 0, offset);
                    putIntBE(buffer, 8, len);
                    queue.put(buffer);
                    offset += len;
                    index++;
                }
            } catch (Exception e) {
                SenderLogger.error("Error while reading disk: " + e.getMessage());
            } finally {
                diskDone.set(true);
            }
        });
        diskThread.setDaemon(true);
        diskThread.start();

        CountDownLatch networkDone = new CountDownLatch(totalStreams);

        int totalConnections = connections.size();
        int base = totalStreams / totalConnections;
        int rem  = totalStreams % totalConnections;


        int streamsPerConnection = Math.max(1,totalStreams / connections.size());

        for (int i = 0; i < connections.size(); i++) {

            QuicClientConnection connection = connections.get(i);
            int streamsThisConn = base + (i < rem ? 1 : 0);

            for(int j=0; j<streamsThisConn; j++) {
                ioHandler.submit(() -> {
                    try {
                        QuicStream stream = connection.createStream(true);

                        BufferedOutputStream out = new BufferedOutputStream(stream.getOutputStream(), 256 * 1024);
                        DataOutputStream meta = new DataOutputStream(out);
                        writeMetadata(meta, fileSize, fileNameBytes, chunkSize);
                        meta.flush();

                        byte[] batch = new byte[BATCH_BYTES];
                        int pos = 0;

                        while (true) {
                            byte[] buf = queue.poll(2, TimeUnit.MILLISECONDS);

                            if (buf == null) {
                                if (diskDone.get() && queue.isEmpty()) break;

                                if (pos > 0) {
                                    out.write(batch, 0, pos);
                                    pos = 0;
                                }
                                continue;
                            }

                            long offset = getLongBE(buf, 0);
                            int len = getIntBE(buf, 8);

                            int need = CHUNK_FRAME_HDR + len;
                            if (need > batch.length) {
                                if (pos > 0) {
                                    out.write(batch, 0, pos);
                                    pos = 0;
                                }
                                batch[0] = CHUNK;
                                putIntBE(batch, 1, 0);
                                putLongBE(batch, 5, offset);
                                putIntBE(batch, 13, len);
                                out.write(batch, 0, CHUNK_FRAME_HDR);
                                out.write(buf, HDR, len);

                                sentBytes.addAndGet(len);
                                bufferPool.put(buf);
                                continue;
                            }

                            if (pos + need > batch.length) {
                                out.write(batch, 0, pos);
                                pos = 0;
                            }

                            batch[pos] = CHUNK;
                            putIntBE(batch, pos + 1, 0);
                            putLongBE(batch, pos + 5, offset);
                            putIntBE(batch, pos + 13, len);
                            System.arraycopy(buf, HDR, batch, pos + CHUNK_FRAME_HDR, len);
                            pos += need;

                            sentBytes.addAndGet(len);
                            bufferPool.put(buf);

                            if (pos >= (batch.length * 3) / 4) {
                                out.write(batch, 0, pos);
                                pos = 0;
                            }
                        }

                        if (pos > 0) out.write(batch, 0, pos);
                        out.flush();
                        out.close();

                    } catch (Exception e) {
                        SenderLogger.error("Error while sending data: " + e.getMessage());
                    } finally {
                        networkDone.countDown();
                    }
                });

            }

        }


        try {
            networkDone.await();
            SenderLogger.info("All streams finished. Sending DONE and waiting for OK...");
            waitAcknowledgement();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            progressReporter.shutdownNow();
            ioHandler.shutdown();
            for(QuicClientConnection connection : connections)
                connection.close();
        }


    }

    private void waitAcknowledgement() throws IOException {
        QuicStream controlStream = connections.get(0).createStream(true);

        try (OutputStream os = controlStream.getOutputStream();
             InputStream is = controlStream.getInputStream()) {
            os.write(CTRL);
            os.write(CTRL_DONE);
            os.flush();
            os.close();

            byte[] response = is.readNBytes(2);
            if (response.length != 2 || response[0] != CTRL || response[1] != CTRL_OK) {
                throw new IOException("Unexpected ACK: " + new String(response, StandardCharsets.UTF_8));
            }
        }

        SenderLogger.info("OK received from receiver.");
    }


    private void writeMetadata(DataOutputStream dos, long fileSize, byte[] fileNameBytes, int chunkSize) throws IOException {
        dos.writeByte(FILE_METADATA);
        dos.writeLong(fileSize);
        dos.writeInt(fileNameBytes.length);
        dos.write(fileNameBytes);
        dos.writeInt(chunkSize);
    }





}
