package tutor.speech;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tutor.config.AppConfig;

public class PiperSpeaker {

    private final AppConfig config;
    private Process current;

    public PiperSpeaker(AppConfig config) {
        this.config = config;
    }

    public boolean isInstalled() {
        return Files.isExecutable(config.piperBinary()) && Files.isRegularFile(config.piperVoice());
    }

    public void speak(String text) throws Exception {
        if (!isInstalled()) {
            throw new IllegalStateException("Piper not found. Run scripts/setup-piper.sh first.");
        }
        Path wav = Files.createTempFile("german-tutor-tts-", ".wav");
        try {
            synthesize(text, wav);
            play(wav);
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    public synchronized void stop() {
        if (current != null) {
            current.destroy();
        }
    }

    private void synthesize(String text, Path wav) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(List.of(
                config.piperBinary().toString(),
                "--model", config.piperVoice().toString(),
                "--length_scale", String.valueOf(config.speechSlowness()),
                "--output_file", wav.toString()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(text.replace('\n', ' ').getBytes(StandardCharsets.UTF_8));
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("piper failed with exit code " + exit);
        }
    }

    // paplay goes through the sound server, like Mic
    private void play(Path wav) throws Exception {
        Process process = new ProcessBuilder(List.of("paplay", wav.toString()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        synchronized (this) {
            stop();
            current = process;
        }
        process.waitFor();
    }
}
