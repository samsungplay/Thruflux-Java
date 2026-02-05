package payloads;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.util.List;

public class JoinTransferSessionPayload extends Payload {

    private final List<SerializedCandidate> localCandidates;
    private final String localUfrag;
    private final String localPassword;
    private final String joinCode;
    private String receiverId;

    public JoinTransferSessionPayload(@JsonProperty("localUfrag") String localUfrag, @JsonProperty("localPassword") String localPassword,
                                      @JsonProperty("localCandidates") List<SerializedCandidate> localCandidates, @JsonProperty("joinCode") String joinCode,
                                      @JsonProperty("receiverId") String receiverId) {
        super("join_transfer_session");
        this.localCandidates = localCandidates;
        this.localUfrag = localUfrag;
        this.localPassword = localPassword;
        this.joinCode = joinCode;
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

    public String getJoinCode() {
        return joinCode;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }
}
