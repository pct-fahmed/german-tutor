package tutor.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import java.util.ArrayList;
import java.util.List;
import tutor.config.AppConfig;

public class TutorService {

    private static final String SYSTEM_PROMPT = """
            You are a friendly German conversation tutor. The learner is at CEFR level %s \
            and practises speaking German with you.

            How to answer:
            - Reply in German at the learner's level: short sentences (1-3), everyday vocabulary, \
            mostly Präsens and Perfekt. End with a simple question so the conversation keeps going.
            - Check the learner's last message for grammar, word choice, word order, articles and \
            case endings. List each real mistake as a correction; leave out stylistic nitpicks.
            - If the learner writes in English or asks what a word means, help briefly, then \
            continue in German.
            - The learner's text may come from speech recognition, so ignore missing punctuation \
            and capitalisation.
            - Text in square brackets is an instruction from the app, not from the learner; \
            never correct it.
            """;

    private static final String ROLE_PLAY = """

            Role-play: %s
            Stay in your role, keep the situation realistic and let the learner do most of the talking.
            """;

    private static final String START_ROLE_PLAY = "[Begin the role-play now with your first line.]";

    private final AnthropicClient client = AnthropicOkHttpClient.fromEnv();
    private final AppConfig config;
    private final List<BetaMessageParam> history = new ArrayList<>();
    private Scenario scenario = Scenario.FREE;

    public TutorService(AppConfig config) {
        this.config = config;
    }

    public synchronized void reset(Scenario scenario) {
        this.scenario = scenario;
        history.clear();
    }

    // Lets the tutor speak first in a role-play
    public TutorReply startRolePlay() {
        return send(START_ROLE_PLAY);
    }

    public TutorReply startPractice(List<String> mistakes) {
        return send("[The learner often makes these mistakes:\n- " + String.join("\n- ", mistakes)
                + "\nStart a short conversation that makes the learner use these forms again. "
                + "Ask one simple question at a time.]");
    }

    private String systemPrompt() {
        String prompt = SYSTEM_PROMPT.formatted(config.level());
        return scenario.isRolePlay() ? prompt + ROLE_PLAY.formatted(scenario.rolePlay()) : prompt;
    }

    public synchronized TutorReply send(String userText) {
        history.add(BetaMessageParam.builder()
                .role(BetaMessageParam.Role.USER)
                .content(userText)
                .build());

        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(4000L)
                .system(systemPrompt())
                .messages(history)
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));

        StructuredMessage<TutorReply> response;
        try {
            response = client.beta().messages()
                    .create(params.outputConfig(TutorReply.class, BetaOutputConfig.Effort.LOW).build());
        } catch (RuntimeException e) {
            history.removeLast();
            throw e;
        }

        BetaMessage raw = response.rawMessage();
        if (raw.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
            history.removeLast();
            throw new IllegalStateException("The model declined to answer this message.");
        }
        history.add(raw.toParam());

        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Empty reply (stop reason: "
                        + raw.stopReason().map(Object::toString).orElse("unknown") + ")"));
    }
}
