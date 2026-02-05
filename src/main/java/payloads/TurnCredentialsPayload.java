package payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TurnCredentialsPayload extends Payload {

    private final String username;
    private final String password;
    private final String turnUrl;


    public TurnCredentialsPayload(@JsonProperty("username") String username, @JsonProperty("password") String password,
                                  @JsonProperty("turnUrl") String turnUrl) {
        super("turn_credentials");
        this.username = username;
        this.password = password;
        this.turnUrl = turnUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getTurnUrl() {
        return turnUrl;
    }
}
