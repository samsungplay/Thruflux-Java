package server;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import payloads.CreateTransferSessionPayload;

import java.time.Duration;

public class TransferSessionStore {

    public static TransferSessionStore INSTANCE;


    private Cache<String, TransferSession> sessionCache;

    public TransferSessionStore(ServerConfig serverConfig) {
        sessionCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(serverConfig.sessionTimeout))
                .maximumSize(serverConfig.maxSessions)
                .removalListener((String key, TransferSession transferSession, RemovalCause cause) -> {
                    if(cause == RemovalCause.EXPIRED && transferSession != null) {
                        ServerLogger.info("A session with join code " + transferSession.getJoinCode() + " has expired, destroying the session.");
                        transferSession.destroy();
                    }
                }).build();
    }


    public TransferSession getSessionByJoinCode(String joinCode) {
        return sessionCache.getIfPresent(joinCode);
    }

    public TransferSession createSessionFrom(Session senderSession, CreateTransferSessionPayload payload) {
        TransferSession transferSession = new TransferSession(senderSession, payload);
        sessionCache.put(transferSession.getJoinCode(),transferSession);
        ServerLogger.info("New session with join code " + transferSession.getJoinCode() + " has been created");
        return transferSession;
    }


}
