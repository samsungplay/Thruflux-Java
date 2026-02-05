package payloads;

import com.fasterxml.jackson.annotation.JsonProperty;


public class RejectTransferSessionPayload extends Payload {

    private final String reason;

    public RejectTransferSessionPayload(@JsonProperty("reason") String reason) {
        super("reject_transfer_session");
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
