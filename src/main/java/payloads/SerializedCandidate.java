package payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SerializedCandidate(
        int componentId,
        String type,
        String foundation,
        long priority,
        String ip,
        int port,
        @Nullable SerializedCandidate relatedCandidate
) {
}
