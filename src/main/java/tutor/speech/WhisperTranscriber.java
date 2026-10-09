package tutor.speech;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tutor.config.AppConfig;

public class WhisperTranscriber {

    // Whisper copies the style of its prompt; a prompt full of learner mistakes keeps it from silently fixing ours
    private static final String LEARNER_PROMPT =
            "Äh, gestern ich habe gegangen in die Stadt. Ich kaufen ein Brot und der Frau ist nett. "
                    + "Ich bin müde, weil ich habe viel gearbeitet.";

    private final AppConfig config;

    public WhisperTranscriber(AppConfig config) {
        this.config = config;
    }

    public void checkInstalled() {
        if (!Files.isExecutable(config.whisperBinary()) || !Files.isRegularFile(config.whisperModel())) {
            throw new IllegalStateException("Whisper not found. Run scripts/setup-whisper.sh first.");
        }
    }

    public String transcribe(Path wav) throws IOException, InterruptedException {
        checkInstalled();
        Process process = new ProcessBuilder(List.of(
                config.whisperBinary().toString(),
                "-m", config.whisperModel().toString(),
                "-f", wav.toString(),
                "-l", "de",
                "-t", String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() / 2)),
                "--prompt", LEARNER_PROMPT,
                "-nt",
                "-np"))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("whisper-cli failed with exit code " + exit);
        }
        return output
                .replaceAll("\\[[^]]*]|\\([^)]*\\)", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
