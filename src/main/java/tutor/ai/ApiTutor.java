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

public class ApiTutor extends Tutor {

    private final AnthropicClient client = AnthropicOkHttpClient.fromEnv();
    private final List<BetaMessageParam> history = new ArrayList<>();

    public ApiTutor(AppConfig config) {
        super(config);
    }

    @Override
    protected void clearHistory() {
        history.clear();
    }

    @Override
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
