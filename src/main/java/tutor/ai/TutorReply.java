package tutor.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record TutorReply(
        @JsonPropertyDescription("Your answer in German, continuing the conversation. Short and at the learner's level.")
        String reply,
        @JsonPropertyDescription("English translation of the reply.")
        String replyEnglish,
        @JsonPropertyDescription("Mistakes in the learner's last message. Empty if there were none.")
        List<Correction> corrections) {

    public record Correction(
            @JsonPropertyDescription("The wrong part, exactly as the learner wrote it.")
            String original,
            @JsonPropertyDescription("The corrected version.")
            String corrected,
            @JsonPropertyDescription("One short sentence in simple English explaining the rule.")
            String explanation) {
    }
}
