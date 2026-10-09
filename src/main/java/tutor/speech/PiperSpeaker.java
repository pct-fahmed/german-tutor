package tutor.speech;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import tutor.config.AppConfig;

public class PiperSpeaker {

    private final AppConfig config;
    private Clip current;

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
            current.stop();
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

    private void play(Path wav) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile());
             Clip clip = AudioSystem.getClip()) {
            clip.addLineListener(e -> {
                if (e.getType() == LineEvent.Type.STOP) {
                    done.countDown();
                }
            });
            clip.open(in);
            synchronized (this) {
                stop();
                current = clip;
            }
            clip.start();
            done.await();
        }
    }
}
