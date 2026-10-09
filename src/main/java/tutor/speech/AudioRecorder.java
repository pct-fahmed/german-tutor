package tutor.speech;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class AudioRecorder {

    // Whisper expects 16 kHz mono PCM
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final int MIN_BYTES = (int) (FORMAT.getFrameRate() * FORMAT.getFrameSize() * 0.4);

    private TargetDataLine line;
    private ByteArrayOutputStream buffer;
    private Thread reader;

    public synchronized void start() throws LineUnavailableException {
        line = AudioSystem.getTargetDataLine(FORMAT);
        line.open(FORMAT);
        line.start();
        buffer = new ByteArrayOutputStream();
        TargetDataLine current = line;
        ByteArrayOutputStream out = buffer;
        reader = Thread.ofVirtual().start(() -> {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = current.read(chunk, 0, chunk.length)) > 0) {
                out.write(chunk, 0, n);
            }
        });
    }

    // null when the recording was too short to transcribe
    public synchronized Path stop() throws IOException, InterruptedException {
        line.stop();
        line.close();
        reader.join();
        byte[] pcm = buffer.toByteArray();
        if (pcm.length < MIN_BYTES) {
            return null;
        }
        Path wav = Files.createTempFile("german-tutor-", ".wav");
        try (AudioInputStream in = new AudioInputStream(
                new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize())) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, wav.toFile());
        }
        return wav;
    }
}
