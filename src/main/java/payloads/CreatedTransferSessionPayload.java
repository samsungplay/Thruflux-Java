package payloads;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CreatedTransferSessionPayload extends Payload {

    private final String joinCode;

    @JsonCreator
    public CreatedTransferSessionPayload(@JsonProperty("joinCode") String joinCode) {
        super("created_transfer_session");
        this.joinCode = joinCode;
    }

    public String getJoinCode() {
        return joinCode;
    }

}
