package server;

import common.Utils;
import gov.nist.javax.sip.stack.ServerLog;
import org.eclipse.jetty.websocket.api.Session;
import payloads.CreateTransferSessionPayload;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class TransferSession {


    private Session senderSession;
    private ConcurrentMap<String, Session> receiversMap = new ConcurrentHashMap<>();
    private String joinCode;
    private int maxReceivers;
    private long totalSize;
    private int filesCount;

    public TransferSession(Session senderSession, CreateTransferSessionPayload payload) {
        this.senderSession = senderSession;
        this.joinCode = Utils.generateJoinCode();
        this.maxReceivers = payload.getMaxReceivers();
        this.totalSize = payload.getTotalSize();
        this.filesCount = payload.getFilesCount();
    }

    public Session getSenderSession() {
        return senderSession;
    }

    public Collection<Session> getReceiverSessions() {
        return receiversMap.values();
    }

    public String getJoinCode() {
        return joinCode;
    }

    public void addReceiver(String receiverId, Session receiverSession) {
        receiversMap.put(receiverId, receiverSession);
    }

    public Session getReceiver(String receiverId) {
        return receiversMap.get(receiverId);
    }

    public void removeReceiver(Session receiverSession) {
        receiversMap.entrySet().removeIf(entry -> entry.getValue().equals(receiverSession));
    }

    public void destroy() {
        ServerLogger.info("A session with join code " + joinCode + " has been destroyed.");
        senderSession.close(1008, "Session destroyed");
        for(Session receiverSession : receiversMap.values()) {
            receiverSession.close(1008, "Session has been destroyed");
        }
    }

}
