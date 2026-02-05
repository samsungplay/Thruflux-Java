package common;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.*;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;
import payloads.SerializedCandidate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class IceHandler {

    private static final List<StunServer> stunServers = new ArrayList<>();
    private static final List<TurnServer> turnServers = new ArrayList<>();
    private static final AtomicReference<Agent> currentAgent = new AtomicReference<>();


    public record StunServer(String host, int port) {
    }

    public record TurnServer(String host, int port, String username, String password) {
    }

    public record CandidatesResult(String localUfrag, String localPassword, List<SerializedCandidate> candidates) {

    }

    private static boolean shouldSendAddress(InetAddress addr) {
        if (addr == null) return false;
        if (addr.isAnyLocalAddress()) return false;
        if (addr.isLoopbackAddress()) return false;
        if (addr.isLinkLocalAddress()) return false;

        return true;
    }

    private static boolean shouldSendCandidate(Candidate<?> c) {
        if (c == null) return false;
        TransportAddress ta = c.getTransportAddress();
        if (ta == null) return false;
        if (ta.getTransport() != Transport.UDP) return false;
        return shouldSendAddress(ta.getAddress());
    }

    public static void addStunServer(StunServer stunServer) {
        stunServers.add(stunServer);
    }

    public static void addTurnServer(TurnServer turnServer) {
        turnServers.add(turnServer);
    }

    public static CandidatesResult gatherAllCandidates(boolean isSender, int n) throws IOException {
        Agent agent = new Agent();
        agent.setControlling(isSender);
        for (StunServer stunServer : stunServers) {
            TransportAddress stunAddr = new TransportAddress(
                    InetAddress.getByName(stunServer.host()), stunServer.port(), Transport.UDP
            );
            agent.addCandidateHarvester(new StunCandidateHarvester(stunAddr));
        }

        for (TurnServer turnServer : turnServers) {
            TransportAddress turnAddr = new TransportAddress(
                    InetAddress.getByName(turnServer.host()), turnServer.port(), Transport.UDP
            );
            LongTermCredential credential = new LongTermCredential(turnServer.username(), turnServer.password());
            agent.addCandidateHarvester(new TurnCandidateHarvester(turnAddr, credential));
        }


        IceMediaStream iceStream = agent.createMediaStream("data");

        List<Component> components = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            components.add(agent.createComponent(iceStream, 50000, 49152, 65535));
        }

        List<SerializedCandidate> serializedCandidates = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Component component = components.get(i);
            for (LocalCandidate localCandidate : component.getLocalCandidates()) {
                if (!shouldSendCandidate(localCandidate)) continue;
                SerializedCandidate serialized = Utils.serializeCandidate(localCandidate,component.getComponentID());
                serializedCandidates.add(serialized);
            }
        }

        currentAgent.set(agent);

        return new CandidatesResult(
                agent.getLocalUfrag(),
                agent.getLocalPassword(),
                serializedCandidates
        );
    }

    public static void establishConnection(List<SerializedCandidate> remoteCandidates, String remoteUfrag, String remotePassword,
                                           Consumer<List<Component>> connectionCallback) throws IllegalStateException, UnknownHostException {
        if (currentAgent.get() == null) {
            throw new IllegalStateException("Cannot start ICE connection because local candidates have not been gathered yet");
        }

        Agent agent = currentAgent.get();
        IceMediaStream iceStream = agent.getStream("data");
        iceStream.setRemoteUfrag(remoteUfrag);
        iceStream.setRemotePassword(remotePassword);


        for (SerializedCandidate serializedCandidate : remoteCandidates) {
            Component component = iceStream.getComponent(serializedCandidate.componentId());
            if(component == null) {
                throw new IllegalStateException("Total connections mismatch between sender and receiver");
            }
            RemoteCandidate remoteCandidate = Utils.deserializeCandidate(serializedCandidate, component);
            component.addRemoteCandidate(remoteCandidate);
        }

        agent.addStateChangeListener(evt -> {
            if(evt.getPropertyName().equals(Agent.PROPERTY_ICE_PROCESSING_STATE)) {

                IceProcessingState state = (IceProcessingState) evt.getNewValue();
                System.out.println("Hmm.." + state);
                if (state == IceProcessingState.TERMINATED) {
                   connectionCallback.accept(iceStream.getComponents().stream().filter(c -> c.getSelectedPair() != null).collect(Collectors.toCollection(ArrayList::new)));
                } else if (state == IceProcessingState.FAILED) {
                    connectionCallback.accept(List.of());
                }
            }
        });

        agent.startConnectivityEstablishment();

    }


}
