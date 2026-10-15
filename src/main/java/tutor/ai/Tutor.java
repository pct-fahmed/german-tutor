package tutor.ai;

import java.util.List;
import java.util.Map;
import tutor.config.AppConfig;

public abstract class Tutor {

    private static final String SYSTEM_PROMPT = """
            You are a friendly German conversation tutor. The learner is at CEFR level %s \
            and practises speaking German with you.

            How to answer:
            - Reply in German at the learner's level: %s
            - Check the learner's last message for grammar, word choice, word order, articles and \
            case endings. List each real mistake as a correction; leave out stylistic nitpicks.
            - If the learner writes in English or asks what a word means, help briefly, then \
            continue in German.
            - The learner's text may come from speech recognition, so ignore missing punctuation \
            and capitalisation.
            - Text in square brackets is an instruction from the app, not from the learner; \
            never correct it.
            """;

    private static final Map<String, String> LEVEL_STYLE = Map.of(
            "A1", "very short, simple sentences (1-2), basic everyday words, only Präsens. "
                    + "End with a yes/no or very simple question.",
            "A2", "short sentences (1-3), everyday vocabulary, mostly Präsens and Perfekt, "
                    + "simple connectors like und, aber, weil. End with a simple question.",
            "B1", "2-4 sentences with subordinate clauses (weil, obwohl, dass, wenn), Präteritum "
                    + "of common verbs, Konjunktiv II for wishes and polite requests, and everyday "
                    + "idioms. Ask open questions about experiences, plans and opinions.",
            "B2", "natural, varied German with complex sentences, Passiv, Konjunktiv II and "
                    + "a broader vocabulary. Ask questions that make the learner argue, compare "
                    + "and explain their opinion.",
            "C1", "fully natural German as with a native speaker: idioms, nuanced vocabulary, "
                    + "Konjunktiv I in reported speech and complex structures. Discuss abstract "
                    + "topics and challenge the learner's arguments. Also point out phrasing that "
                    + "is correct but unidiomatic.");

    private static final String ROLE_PLAY = """

            Role-play: %s
            Stay in your role, keep the situation realistic and let the learner do most of the talking.
            """;

    private static final String START_ROLE_PLAY = "[Begin the role-play now with your first line.]";

    protected final AppConfig config;
    private Scenario scenario = Scenario.FREE;
    private String level;

    protected Tutor(AppConfig config) {
        this.config = config;
        this.level = config.level();
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

    public synchronized String level() {
        return level;
    }

    // Takes effect with the next reset
    public synchronized void setLevel(String level) {
        this.level = level;
    }

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
                + "Ask one question at a time.]");
    }

    protected synchronized String systemPrompt() {
        String prompt = SYSTEM_PROMPT.formatted(level,
                LEVEL_STYLE.getOrDefault(level, LEVEL_STYLE.get("B1")));
        return scenario.isRolePlay() ? prompt + ROLE_PLAY.formatted(scenario.rolePlay()) : prompt;
    }
}
