package tutor.progress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import tutor.ai.TutorReply.Correction;
import tutor.config.AppConfig;

public class SessionLog {

    public static final Path DIR = AppConfig.CONFIG_DIR.resolve("sessions");
    private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private Path file;
    private String title;

    // The file is only created once something is said, so empty sessions leave nothing behind
    public synchronized void start(String title) {
        this.title = title;
        this.file = null;
    }

    public synchronized void learner(String text, List<Correction> corrections) {
        StringBuilder md = new StringBuilder("**Ich:** ").append(text).append("\n\n");
        if (corrections != null) {
            for (Correction c : corrections) {
                md.append("- ~~").append(c.original()).append("~~ → **").append(c.corrected())
                        .append("**: ").append(c.explanation()).append('\n');
            }
            if (!corrections.isEmpty()) {
                md.append('\n');
            }
        }
        append(md.toString());
    }

    public synchronized void tutor(String text, String translation) {
        append("**Tutor:** " + text + "\n_" + translation + "_\n\n");
    }

    private void append(String text) {
        try {
            if (file == null) {
                Files.createDirectories(DIR);
                file = DIR.resolve(LocalDateTime.now().format(FILE_NAME) + ".md");
                Files.writeString(file, "# " + title + " (" + LocalDateTime.now().withNano(0) + ")\n\n",
                        StandardCharsets.UTF_8);
            }
            Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
