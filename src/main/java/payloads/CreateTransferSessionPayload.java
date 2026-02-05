package payloads;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CreateTransferSessionPayload extends Payload {

    private final int maxReceivers;

    private final List<String> paths;

    private final long totalSize;

    private final int filesCount;

    @JsonCreator
    public CreateTransferSessionPayload(
            @JsonProperty("maxReceivers") int maxReceivers, @JsonProperty("paths") List<String> paths, @JsonProperty("totalSize") long totalSize,
            @JsonProperty("filesCount") int filesCount) {
        super("create_transfer_session");
        this.maxReceivers = maxReceivers;
        this.paths = paths;
        this.totalSize = totalSize;
        this.filesCount = filesCount;
    }

    public int getMaxReceivers() {
        return maxReceivers;
    }

    public List<String> getPaths() {
        return paths;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public int getFilesCount() {
        return filesCount;
    }

}
