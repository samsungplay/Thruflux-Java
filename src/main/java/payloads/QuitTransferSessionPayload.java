package payloads;


import com.fasterxml.jackson.annotation.JsonProperty;

public class QuitTransferSessionPayload extends Payload {

    String receiverId;

    public QuitTransferSessionPayload(@JsonProperty("receiverId") String receiverId) {
        super("quit_transfer_session");
        this.receiverId = receiverId;
    }

    public String getReceiverId() {
        return receiverId;
    }
}
