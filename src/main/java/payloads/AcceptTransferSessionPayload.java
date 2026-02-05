package payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AcceptTransferSessionPayload extends Payload {

    private final List<SerializedCandidate> localCandidates;
    private final String localUfrag;
    private final String localPassword;
    private final String receiverId;

    public AcceptTransferSessionPayload(@JsonProperty("localUfrag") String localUfrag, @JsonProperty("localPassword") String localPassword,
                                        @JsonProperty("localCandidates") List<SerializedCandidate> localCandidates,
                                        @JsonProperty("receiverId") String receiverId) {
        super("accept_transfer_session");
        this.localCandidates = localCandidates;
        this.localUfrag = localUfrag;
        this.localPassword = localPassword;
        this.receiverId = receiverId;
    }

    public List<SerializedCandidate> getLocalCandidates() {
        return localCandidates;
    }

    public String getLocalUfrag() {
        return localUfrag;
    }

    public String getLocalPassword() {
        return localPassword;
    }

    public String getReceiverId() {
        return receiverId;
    }
}
