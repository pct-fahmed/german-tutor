package tutor.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record AppConfig(
        String backend,
        String claudeBinary,
        String model,
        String level,
        Path whisperBinary,
        Path whisperModel,
        Path piperBinary,
        Path piperVoice,
        double speechSlowness) {

    public static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".german-tutor");
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    public static AppConfig load() {
        Properties p = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
                p.load(r);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + CONFIG_FILE, e);
            }
        }
        return new AppConfig(
                p.getProperty("backend", "claude-code"),
                p.getProperty("claude.binary", "claude"),
                p.getProperty("model", "claude-opus-5"),
                p.getProperty("level", "A2"),
                path(p, "whisper.binary", "bin/whisper-cli"),
                path(p, "whisper.model", "models/ggml-small.bin"),
                path(p, "piper.binary", "bin/piper/piper"),
                path(p, "piper.voice", "models/de_DE-thorsten-medium.onnx"),
                Double.parseDouble(p.getProperty("piper.lengthScale", "1.15")));
    }

    private static Path path(Properties p, String key, String defaultRelative) {
        String value = p.getProperty(key);
        return value != null ? Path.of(value) : CONFIG_DIR.resolve(defaultRelative);
    }
}
