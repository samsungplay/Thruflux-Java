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

    public static void addStunServer(StunServer stunServer) {
        stunServers.add(stunServer);
    }

    public static void addTurnServer(TurnServer turnServer) {
        turnServers.add(turnServer);
    }

    public static CandidatesResult gatherAllCandidates(boolean isSender) throws IOException {
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
        Component component = agent.createComponent(iceStream, 50000, 49152, 65535);

        currentAgent.set(agent);


        return new CandidatesResult(
                agent.getLocalUfrag(),
                agent.getLocalPassword(),
                component.getLocalCandidates().stream().map(Utils::serializeCandidate)
                        .collect(Collectors.toCollection(ArrayList::new))
        );
    }

    public static void startConnection(List<SerializedCandidate> remoteCandidates, String remoteUfrag, String remotePassword,
                                       BiConsumer<IceProcessingState, Component> connectionCallback) throws IllegalStateException, UnknownHostException {
        if (currentAgent.get() == null) {
            throw new IllegalStateException("Cannot start ICE connection because local candidates have not been gathered yet");
        }

        Agent agent = currentAgent.get();
        IceMediaStream iceStream = agent.getStream("data");
        iceStream.setRemoteUfrag(remoteUfrag);
        iceStream.setRemotePassword(remotePassword);

        Component component = iceStream.getComponent(Component.RTP);

        for (SerializedCandidate remoteSerialized : remoteCandidates) {
            RemoteCandidate remoteCandidate = Utils.deserializeCandidate(remoteSerialized, component);
            component.addRemoteCandidate(remoteCandidate);
        }

        agent.addStateChangeListener(evt -> {
            if(evt.getPropertyName().equals(Agent.PROPERTY_ICE_PROCESSING_STATE)) {
                IceProcessingState state = (IceProcessingState) evt.getNewValue();
                connectionCallback.accept(state, component);
            }
        });

        agent.startConnectivityEstablishment();

    }


}
