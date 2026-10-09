package tutor.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tutor.ai.TutorReply.Correction;
import tutor.config.AppConfig;

public class MistakeLog {

    public record Entry(String time, String said, String original, String corrected, String explanation) {
    }

    public record Summary(String original, String corrected, String explanation, int count, String lastSeen) {
    }

    private static final Path FILE = AppConfig.CONFIG_DIR.resolve("mistakes.jsonl");
    private final ObjectMapper json = new ObjectMapper();

    public synchronized void add(String said, List<Correction> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return;
        }
        StringBuilder lines = new StringBuilder();
        String now = LocalDateTime.now().withNano(0).toString();
        try {
            for (Correction c : corrections) {
                lines.append(json.writeValueAsString(
                        new Entry(now, said, c.original(), c.corrected(), c.explanation()))).append('\n');
            }
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Same mistake made several times is grouped, most frequent first
    public synchronized List<Summary> summary() {
        if (!Files.exists(FILE)) {
            return List.of();
        }
        Map<String, Summary> grouped = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Entry e = json.readValue(line, Entry.class);
                String key = normalize(e.original()) + "→" + normalize(e.corrected());
                Summary old = grouped.get(key);
                grouped.put(key, new Summary(e.original(), e.corrected(), e.explanation(),
                        old == null ? 1 : old.count() + 1, e.time()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Summary> list = new ArrayList<>(grouped.values());
        list.sort(Comparator.comparingInt(Summary::count).reversed()
                .thenComparing(Summary::lastSeen, Comparator.reverseOrder()));
        return list;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.GERMAN).replaceAll("[^\\p{L}\\s]", "").replaceAll("\\s+", " ").strip();
    }
}
