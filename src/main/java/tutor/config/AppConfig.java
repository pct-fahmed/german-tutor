package tutor.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    public static final List<String> LEVELS = List.of("A1", "A2", "B1", "B2", "C1");

    public static AppConfig load() {
        Properties p = readProperties();
        return new AppConfig(
                p.getProperty("backend", "claude-code"),
                p.getProperty("claude.binary", "claude"),
                p.getProperty("model", "claude-opus-5"),
                p.getProperty("level", "B1"),
                path(p, "whisper.binary", "bin/whisper-cli"),
                path(p, "whisper.model", "models/ggml-small.bin"),
                path(p, "piper.binary", "bin/piper/piper"),
                path(p, "piper.voice", "models/de_DE-thorsten-medium.onnx"),
                Double.parseDouble(p.getProperty("piper.lengthScale", "1.15")));
    }

    public static void saveLevel(String level) {
        Properties p = readProperties();
        p.setProperty("level", level);
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
                p.store(w, "Deutsch Tutor");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + CONFIG_FILE, e);
        }
    }

    private static Properties readProperties() {
        Properties p = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
                p.load(r);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + CONFIG_FILE, e);
            }
        }
        return p;
    }

    private static Path path(Properties p, String key, String defaultRelative) {
        String value = p.getProperty(key);
        return value != null ? Path.of(value) : CONFIG_DIR.resolve(defaultRelative);
    }
}
