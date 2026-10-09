package tutor.speech;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class AudioRecorder {

    private static final int MIN_BYTES = (int) (Mic.FORMAT.getFrameRate() * Mic.FORMAT.getFrameSize() * 0.4);

    private Mic mic;
    private ByteArrayOutputStream buffer;
    private Thread reader;

    public synchronized void start() throws IOException {
        mic = Mic.open();
        buffer = new ByteArrayOutputStream();
        InputStream in = mic.stream();
        ByteArrayOutputStream out = buffer;
        reader = Thread.ofVirtual().start(() -> {
            byte[] chunk = new byte[4096];
            int n;
            try {
                while ((n = in.read(chunk)) > 0) {
                    out.write(chunk, 0, n);
                }
            } catch (IOException ignored) {
                // the stream closes when the recording stops
            }
        });
    }

    // null when the recording was too short to transcribe
    public synchronized Path stop() throws IOException, InterruptedException {
        mic.close();
        reader.join();
        byte[] pcm = buffer.toByteArray();
        if (pcm.length < MIN_BYTES) {
            return null;
        }
        return writeWav(pcm);
    }

    public static Path writeWav(byte[] pcm) throws IOException {
        Path wav = Files.createTempFile("german-tutor-", ".wav");
        try (AudioInputStream in = new AudioInputStream(
                new ByteArrayInputStream(pcm), Mic.FORMAT, pcm.length / Mic.FORMAT.getFrameSize())) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, wav.toFile());
        }
        return wav;
    }
}
