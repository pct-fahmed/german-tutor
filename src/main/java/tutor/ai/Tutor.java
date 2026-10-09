package tutor.ai;

import java.util.List;
import tutor.config.AppConfig;

public abstract class Tutor {

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

    protected final AppConfig config;
    private Scenario scenario = Scenario.FREE;

    protected Tutor(AppConfig config) {
        this.config = config;
    }

    public static Tutor create(AppConfig config) {
        return switch (config.backend()) {
            case "api" -> new ApiTutor(config);
            case "claude-code" -> new ClaudeCodeTutor(config);
            default -> throw new IllegalArgumentException(
                    "Unknown backend '" + config.backend() + "', use claude-code or api");
        };
    }

    public abstract TutorReply send(String userText);

    protected abstract void clearHistory();

    public synchronized void reset(Scenario scenario) {
        this.scenario = scenario;
        clearHistory();
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

    protected synchronized String systemPrompt() {
        String prompt = SYSTEM_PROMPT.formatted(config.level());
        return scenario.isRolePlay() ? prompt + ROLE_PLAY.formatted(scenario.rolePlay()) : prompt;
    }
}
