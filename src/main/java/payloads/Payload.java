package payloads;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreateTransferSessionPayload.class, name = "create_transfer_session"),
        @JsonSubTypes.Type(value = CreatedTransferSessionPayload.class, name = "created_transfer_session"),
        @JsonSubTypes.Type(value = JoinTransferSessionPayload.class, name = "join_transfer_session"),
        @JsonSubTypes.Type(value = AcceptTransferSessionPayload.class, name = "accept_transfer_session"),
        @JsonSubTypes.Type(value = TurnCredentialsPayload.class, name = "turn_credentials"),
        @JsonSubTypes.Type(value = RejectTransferSessionPayload.class, name= "reject_transfer_session"),
        @JsonSubTypes.Type(value = QuitTransferSessionPayload.class, name="quit_transfer_session")
})
public abstract class Payload {
    public String type;
    protected Payload(String type) {
        this.type = type;
    }
}
