package common;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public final class TracingDatagramSocket extends DatagramSocket {
    private final DatagramSocket delegate;
    public TracingDatagramSocket(DatagramSocket delegate) throws SocketException { this.delegate = delegate; }

    @Override
    public void receive(DatagramPacket p) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            sb.append(ste);
        }

        if(!sb.toString().contains("tech.kwik.core.receive")) {
            System.out.println("WARNING, PACKET SNITCHED: " + sb);
            throw new IOException("Ouch");
        }
        delegate.receive(p);
    }

    @Override public void send(DatagramPacket p) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            sb.append(ste);
        }

        if(!sb.toString().contains("tech.kwik.core.send")) {
            System.out.println("WARNING, PACKET SNITCHED: " + sb);
            throw new IOException("Ouch");
        }
        delegate.send(p);
    }
}