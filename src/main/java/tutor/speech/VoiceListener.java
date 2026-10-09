package tutor.speech;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.function.Consumer;

// Keeps the mic open and cuts the stream into sentences using loudness against the background noise
public class VoiceListener {

    private static final int FRAME_MS = 30;
    private static final int FRAME_BYTES = (int) (Mic.FORMAT.getFrameRate() * FRAME_MS / 1000)
            * Mic.FORMAT.getFrameSize();
    private static final int CALIBRATION_FRAMES = 500 / FRAME_MS;
    private static final int START_FRAMES = 150 / FRAME_MS;
    private static final int PRE_ROLL_FRAMES = 300 / FRAME_MS;
    private static final int MIN_SPEECH_FRAMES = 500 / FRAME_MS;
    private static final int MAX_FRAMES = 30_000 / FRAME_MS;
    private static final double MIN_RMS = 200;

    private final int silenceFrames;
    private final double factor;
    private final Consumer<Boolean> onHearing;
    private final Consumer<byte[]> onUtterance;

    private volatile boolean running;
    private volatile boolean paused;
    private Mic mic;

    // onHearing: true when speech starts, false when it turned out too short to use
    public VoiceListener(int silenceMs, double sensitivity, Consumer<Boolean> onHearing, Consumer<byte[]> onUtterance) {
        this.silenceFrames = Math.max(1, silenceMs / FRAME_MS);
        this.factor = 3.0 / sensitivity;
        this.onHearing = onHearing;
        this.onUtterance = onUtterance;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        mic = Mic.open();
        running = true;
        paused = false;
        InputStream in = mic.stream();
        Thread.ofVirtual().start(() -> {
            try {
                listen(in);
            } catch (IOException ignored) {
                // the stream closes when listening stops
            }
        });
    }

    public synchronized void stop() {
        running = false;
        if (mic != null) {
            mic.close();
            mic = null;
        }
    }

    // Pause while the tutor thinks or speaks so it never hears itself
    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public boolean isRunning() {
        return running;
    }

    private void listen(InputStream source) throws IOException {
        byte[] frame = new byte[FRAME_BYTES];
        ArrayDeque<byte[]> preRoll = new ArrayDeque<>();
        ByteArrayOutputStream utterance = null;
        double noise = 0;
        int calibrated = 0;
        int loudRun = 0;
        int quietRun = 0;
        int speechFrames = 0;
        int totalFrames = 0;
        boolean wasPaused = false;

        while (running) {
            if (source.readNBytes(frame, 0, frame.length) < frame.length) {
                return;
            }
            if (paused) {
                wasPaused = true;
                continue;
            }
            if (wasPaused) {
                // Start fresh so the tail of the tutor's voice is not part of the next sentence
                wasPaused = false;
                preRoll.clear();
                utterance = null;
                loudRun = 0;
            }

            double rms = rms(frame);
            if (calibrated < CALIBRATION_FRAMES) {
                noise += rms / CALIBRATION_FRAMES;
                calibrated++;
                continue;
            }
            boolean loud = rms > Math.max(MIN_RMS, noise * factor);

            if (utterance == null) {
                preRoll.addLast(frame.clone());
                if (preRoll.size() > PRE_ROLL_FRAMES) {
                    preRoll.removeFirst();
                }
                if (!loud) {
                    noise = noise * 0.98 + rms * 0.02;
                    loudRun = 0;
                    continue;
                }
                if (++loudRun < START_FRAMES) {
                    continue;
                }
                utterance = new ByteArrayOutputStream();
                for (byte[] f : preRoll) {
                    utterance.writeBytes(f);
                }
                preRoll.clear();
                speechFrames = loudRun;
                totalFrames = loudRun;
                quietRun = 0;
                onHearing.accept(true);
                continue;
            }

            utterance.writeBytes(frame);
            totalFrames++;
            if (loud) {
                speechFrames++;
                quietRun = 0;
            } else {
                quietRun++;
            }
            if (quietRun >= silenceFrames || totalFrames >= MAX_FRAMES) {
                byte[] pcm = utterance.toByteArray();
                utterance = null;
                loudRun = 0;
                if (speechFrames >= MIN_SPEECH_FRAMES) {
                    paused = true;
                    onUtterance.accept(pcm);
                } else {
                    onHearing.accept(false);
                }
            }
        }
    }

    private static double rms(byte[] pcm) {
        long sum = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int sample = (pcm[i + 1] << 8) | (pcm[i] & 0xff);
            sum += (long) sample * sample;
        }
        return Math.sqrt(sum / (pcm.length / 2.0));
    }
}
