package tutor.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tutor.ai.Scenario;
import tutor.ai.TutorReply;
import tutor.ai.Tutor;
import tutor.config.AppConfig;
import tutor.progress.MistakeLog;
import tutor.progress.SessionLog;
import tutor.speech.AudioRecorder;
import tutor.speech.PiperSpeaker;
import tutor.speech.WhisperTranscriber;

public class MainView extends BorderPane {

    private final Tutor tutor;
    private final AudioRecorder recorder = new AudioRecorder();
    private final WhisperTranscriber transcriber;
    private final PiperSpeaker speaker;
    private final MistakeLog mistakeLog = new MistakeLog();
    private final SessionLog sessionLog = new SessionLog();
    private boolean recording;

    private final VBox chat = new VBox(8);
    private final ScrollPane chatScroll = new ScrollPane(chat);
    private final VBox corrections = new VBox(10);
    private final Button talkButton = new Button("🎤 Sprechen");
    private final Label status = new Label();
    private final ComboBox<Scenario> scenarioBox = new ComboBox<>();
    private final Button restartButton = new Button("Neu starten");
    private final Button mistakesButton = new Button("📒 Meine Fehler");

    public MainView(AppConfig config) {
        this.tutor = Tutor.create(config);
        this.transcriber = new WhisperTranscriber(config);
        this.speaker = new PiperSpeaker(config);

        chat.setPadding(new Insets(12));
        chatScroll.setFitToWidth(true);
        chatScroll.getStyleClass().add("chat");
        chat.heightProperty().addListener((obs, old, h) -> chatScroll.setVvalue(1.0));

        Label correctionsTitle = new Label("Korrekturen");
        correctionsTitle.getStyleClass().add("panel-title");
        ScrollPane correctionsScroll = new ScrollPane(corrections);
        correctionsScroll.setFitToWidth(true);
        correctionsScroll.getStyleClass().add("chat");
        VBox.setVgrow(correctionsScroll, Priority.ALWAYS);
        VBox correctionsPanel = new VBox(8, correctionsTitle, correctionsScroll);
        correctionsPanel.setPadding(new Insets(12));
        correctionsPanel.setPrefWidth(320);
        correctionsPanel.getStyleClass().add("corrections");

        status.getStyleClass().add("status");
        talkButton.getStyleClass().add("talk");
        VBox bottom = new VBox(6, status, talkButton);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(8, 12, 12, 12));

        scenarioBox.getItems().setAll(Scenario.values());
        scenarioBox.setValue(Scenario.FREE);
        Label scenarioLabel = new Label("Situation:");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(8, scenarioLabel, scenarioBox, restartButton, spacer, mistakesButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 12, 8, 12));
        topBar.getStyleClass().add("top-bar");

        setTop(topBar);
        setCenter(chatScroll);
        setRight(correctionsPanel);
        setBottom(bottom);

        talkButton.setOnAction(e -> toggleRecording());
        scenarioBox.setOnAction(e -> startConversation());
        restartButton.setOnAction(e -> startConversation());
        mistakesButton.setOnAction(e -> showMistakes());

        startConversation();
    }

    private void startConversation() {
        begin(scenarioBox.getValue(), null);
    }

    private void begin(Scenario scenario, List<String> practiceMistakes) {
        speaker.stop();
        chat.getChildren().clear();
        corrections.getChildren().clear();
        status.setText("");
        tutor.reset(scenario);
        sessionLog.start(practiceMistakes != null ? "Fehler üben" : scenario.toString());
        if (practiceMistakes != null) {
            askTutor(() -> tutor.startPractice(practiceMistakes), null);
        } else if (scenario.isRolePlay()) {
            askTutor(tutor::startRolePlay, null);
        } else {
            addTutorMessage("Hallo! Ich bin dein Deutschlehrer. Worüber möchtest du heute sprechen?", null);
        }
    }

    private void toggleRecording() {
        if (!recording) {
            speaker.stop();
            try {
                transcriber.checkInstalled();
                recorder.start();
            } catch (Exception ex) {
                status.setText("Fehler: " + ex.getMessage());
                return;
            }
            recording = true;
            talkButton.setText("⏹ Stopp");
            talkButton.getStyleClass().add("recording");
            status.setText("Ich höre zu … klick auf Stopp, wenn du fertig bist.");
            return;
        }

        recording = false;
        talkButton.setText("🎤 Sprechen");
        talkButton.getStyleClass().remove("recording");
        transcribeAndSend(recorder::stop);
    }

    private interface WavSource {
        Path get() throws Exception;
    }

    private void transcribeAndSend(WavSource source) {
        talkButton.setDisable(true);
        status.setText("Ich schreibe auf, was du gesagt hast …");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                Path wav = source.get();
                if (wav == null) {
                    return "";
                }
                try {
                    return transcriber.transcribe(wav);
                } finally {
                    Files.deleteIfExists(wav);
                }
            }
        };
        task.setOnSucceeded(e -> {
            String text = task.getValue();
            if (WhisperTranscriber.isJunk(text)) {
                status.setText("Ich habe nichts verstanden. Versuch es noch einmal.");
                setBusy(false, false);
            } else {
                sendToTutor(text);
            }
        });
        task.setOnFailed(e -> {
            status.setText("Fehler: " + task.getException().getMessage());
            setBusy(false, false);
        });
        Thread.ofVirtual().start(task);
    }

    public void sendToTutor(String text) {
        addUserMessage(text);
        askTutor(() -> tutor.send(text), text);
    }

    private void askTutor(Supplier<TutorReply> request, String userText) {
        setBusy(true);

        Task<TutorReply> task = new Task<>() {
            @Override
            protected TutorReply call() {
                return request.get();
            }
        };
        task.setOnSucceeded(e -> {
            TutorReply reply = task.getValue();
            if (userText != null) {
                showCorrections(userText, reply.corrections());
            }
            saveProgress(userText, reply);
            addTutorMessage(reply.reply(), reply.replyEnglish());
            setBusy(false);
            speak(reply.reply());
        });
        task.setOnFailed(e -> {
            status.setText("Fehler: " + task.getException().getMessage());
            setBusy(false, false);
        });
        Thread.ofVirtual().start(task);
    }

    private void saveProgress(String userText, TutorReply reply) {
        try {
            if (userText != null) {
                mistakeLog.add(userText, reply.corrections());
                sessionLog.learner(userText, reply.corrections());
            }
            sessionLog.tutor(reply.reply(), reply.replyEnglish());
        } catch (RuntimeException ex) {
            status.setText("Fehler beim Speichern: " + ex.getMessage());
        }
    }

    private void showMistakes() {
        List<MistakeLog.Summary> summary;
        try {
            summary = mistakeLog.summary();
        } catch (RuntimeException ex) {
            status.setText("Fehler beim Laden: " + ex.getMessage());
            return;
        }

        Stage dialog = new Stage();
        dialog.initOwner(getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Meine Fehler");

        VBox list = new VBox(10);
        list.setPadding(new Insets(12));
        if (summary.isEmpty()) {
            list.getChildren().add(new Label("Noch keine Fehler gespeichert. Sprich mit dem Tutor!"));
        }
        for (MistakeLog.Summary m : summary) {
            Label count = new Label(m.count() + "×");
            count.getStyleClass().add("mistake-count");
            Label wrong = new Label(m.original());
            wrong.getStyleClass().add("correction-wrong");
            Label right = new Label(m.corrected());
            right.getStyleClass().add("correction-right");
            HBox change = new HBox(6, count, wrong, new Label("→"), right);
            change.setAlignment(Pos.CENTER_LEFT);
            Label why = new Label(m.explanation());
            why.setWrapText(true);
            why.getStyleClass().add("correction-why");
            VBox entry = new VBox(4, change, why);
            entry.getStyleClass().add("correction-entry");
            list.getChildren().add(entry);
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button practise = new Button("Mit dem Tutor üben");
        practise.setDisable(summary.isEmpty());
        practise.setOnAction(e -> {
            dialog.close();
            List<String> top = summary.stream().limit(5)
                    .map(m -> m.original() + " → " + m.corrected() + " (" + m.explanation() + ")")
                    .toList();
            scenarioBox.setOnAction(null);
            scenarioBox.setValue(Scenario.FREE);
            scenarioBox.setOnAction(ev -> startConversation());
            begin(Scenario.FREE, top);
        });
        HBox actions = new HBox(practise);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(0, 12, 12, 12));

        Scene scene = new Scene(new VBox(scroll, actions), 520, 560);
        scene.getStylesheets().addAll(getScene().getStylesheets());
        dialog.setScene(scene);
        dialog.show();
    }

    private void speak(String text) {
        if (!speaker.isInstalled()) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                speaker.speak(text);
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Fehler beim Vorlesen: " + ex.getMessage()));
            }
        });
    }

    private void setBusy(boolean busy) {
        setBusy(busy, true);
    }

    private void setBusy(boolean busy, boolean clearStatus) {
        talkButton.setDisable(busy);
        scenarioBox.setDisable(busy);
        restartButton.setDisable(busy);
        mistakesButton.setDisable(busy);
        if (busy) {
            status.setText("Der Tutor denkt nach …");
        } else {
            if (clearStatus) {
                status.setText("");
            }
            talkButton.requestFocus();
        }
    }

    private void showCorrections(String userText, java.util.List<TutorReply.Correction> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        VBox entry = new VBox(6);
        entry.getStyleClass().add("correction-entry");
        Label said = new Label("„" + userText + "“");
        said.setWrapText(true);
        said.getStyleClass().add("correction-said");
        entry.getChildren().add(said);
        for (TutorReply.Correction c : list) {
            Label wrong = new Label(c.original());
            wrong.getStyleClass().add("correction-wrong");
            Label right = new Label(c.corrected());
            right.getStyleClass().add("correction-right");
            HBox change = new HBox(6, wrong, new Label("→"), right);
            change.setAlignment(Pos.CENTER_LEFT);
            Label why = new Label(c.explanation());
            why.setWrapText(true);
            why.getStyleClass().add("correction-why");
            entry.getChildren().addAll(change, why);
        }
        corrections.getChildren().addFirst(entry);
    }

    public void addUserMessage(String text) {
        chat.getChildren().add(bubble(text, null, "bubble-user", Pos.CENTER_RIGHT, false));
    }

    public void addTutorMessage(String text, String translation) {
        chat.getChildren().add(bubble(text, translation, "bubble-tutor", Pos.CENTER_LEFT, true));
    }

    private HBox bubble(String text, String translation, String styleClass, Pos alignment, boolean speakable) {
        Label label = new Label(text);
        label.setWrapText(true);
        VBox content = new VBox(4, label);
        if (translation != null && !translation.isBlank()) {
            Label english = new Label(translation);
            english.setWrapText(true);
            english.getStyleClass().add("translation");
            english.setVisible(false);
            english.setManaged(false);
            content.setOnMouseClicked(e -> {
                english.setVisible(!english.isVisible());
                english.setManaged(english.isVisible());
            });
            content.getChildren().add(english);
        }
        content.setMaxWidth(480);
        content.getStyleClass().addAll("bubble", styleClass);
        HBox row = new HBox(6, content);
        if (speakable) {
            Button replay = new Button("🔊");
            replay.getStyleClass().add("replay");
            replay.setOnAction(e -> speak(text));
            row.getChildren().add(replay);
        }
        row.setAlignment(alignment);
        return row;
    }
}
