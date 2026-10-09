package tutor.speech;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.sound.sampled.AudioFormat;

// Records through the sound server (PipeWire/PulseAudio); Java Sound only reaches the raw ALSA device,
// which the sound server keeps busy
public final class Mic implements AutoCloseable {

    // Whisper expects 16 kHz mono PCM
    public static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

    private final Process process;

    private Mic(Process process) {
        this.process = process;
    }

    public static Mic open() throws IOException {
        Process process = new ProcessBuilder(List.of(
                "parecord", "--raw", "--format=s16le", "--rate=16000", "--channels=1", "--latency-msec=30"))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        return new Mic(process);
    }

    public InputStream stream() {
        return process.getInputStream();
    }

    @Override
    public void close() {
        process.destroy();
    }
}
