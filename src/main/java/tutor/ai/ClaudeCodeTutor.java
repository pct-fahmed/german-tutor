package tutor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import tutor.config.AppConfig;

// Uses the logged-in Claude Code CLI instead of an API key
public class ClaudeCodeTutor extends Tutor {

    private static final String REPLY_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "reply": {"type": "string", "description": "Your answer in German, continuing the conversation. Short and at the learner's level."},
                "replyEnglish": {"type": "string", "description": "English translation of the reply."},
                "corrections": {
                  "type": "array",
                  "description": "Mistakes in the learner's last message. Empty if there were none.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "original": {"type": "string", "description": "The wrong part, exactly as the learner wrote it."},
                      "corrected": {"type": "string", "description": "The corrected version."},
                      "explanation": {"type": "string", "description": "One short sentence in simple English explaining the rule."}
                    },
                    "required": ["original", "corrected", "explanation"]
                  }
                }
              },
              "required": ["reply", "replyEnglish", "corrections"]
            }
            """;

    private static final Path WORK_DIR = AppConfig.CONFIG_DIR.resolve("claude-sessions");
    private static final long TIMEOUT_SECONDS = 120;

    private final ObjectMapper json = new ObjectMapper();
    private String sessionId;

    public ClaudeCodeTutor(AppConfig config) {
        super(config);
    }

    @Override
    protected void clearHistory() {
        sessionId = null;
    }

    @Override
    public synchronized TutorReply send(String userText) {
        boolean firstTurn = sessionId == null;
        String id = firstTurn ? UUID.randomUUID().toString() : sessionId;

        List<String> command = new ArrayList<>(List.of(
                config.claudeBinary(), "-p",
                "--output-format", "json",
                "--json-schema", REPLY_SCHEMA,
                "--system-prompt", systemPrompt(),
                "--model", config.model(),
                "--effort", "low",
                "--tools", "",
                "--setting-sources", "",
                "--strict-mcp-config"));
        command.addAll(firstTurn ? List.of("--session-id", id) : List.of("--resume", id));

        JsonNode result = run(command, userText);
        if (result.path("is_error").asBoolean(false) || !result.hasNonNull("structured_output")) {
            throw new IllegalStateException("Claude Code: " + result.path("result").asText(
                    result.path("subtype").asText("no reply")));
        }
        sessionId = id;
        try {
            return json.treeToValue(result.get("structured_output"), TutorReply.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonNode run(List<String> command, String prompt) {
        try {
            Files.createDirectories(WORK_DIR);
            Process process = new ProcessBuilder(command).directory(WORK_DIR.toFile()).start();
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process, true));
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            }
            String stdout = readAll(process, false);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Claude Code did not answer within " + TIMEOUT_SECONDS + " s");
            }
            if (stdout.isBlank()) {
                throw new IllegalStateException("Claude Code failed (exit " + process.exitValue() + "): "
                        + stderr.join().strip());
            }
            return json.readTree(stdout);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot run '" + config.claudeBinary()
                    + "'. Is Claude Code installed and logged in? " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String readAll(Process process, boolean error) {
        try {
            byte[] bytes = (error ? process.getErrorStream() : process.getInputStream()).readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
