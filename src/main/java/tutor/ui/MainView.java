package tutor.ui;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tutor.ai.TutorReply;
import tutor.ai.TutorService;
import tutor.config.AppConfig;

public class MainView extends BorderPane {

    private final TutorService tutor;

    private final VBox chat = new VBox(8);
    private final ScrollPane chatScroll = new ScrollPane(chat);
    private final VBox corrections = new VBox(10);
    private final TextField input = new TextField();
    private final Button sendButton = new Button("Senden");
    private final Button talkButton = new Button("🎤 Sprechen");
    private final Label status = new Label();

    public MainView(AppConfig config) {
        this.tutor = new TutorService(config);

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

        input.setPromptText("Schreib etwas auf Deutsch …");
        HBox.setHgrow(input, Priority.ALWAYS);
        talkButton.setDisable(true);
        status.getStyleClass().add("status");
        HBox inputBar = new HBox(8, talkButton, input, sendButton);
        inputBar.setAlignment(Pos.CENTER);
        VBox bottom = new VBox(4, status, inputBar);
        bottom.setPadding(new Insets(8, 12, 12, 12));

        setCenter(chatScroll);
        setRight(correctionsPanel);
        setBottom(bottom);

        sendButton.setOnAction(e -> submit());
        input.setOnAction(e -> submit());

        addTutorMessage("Hallo! Ich bin dein Deutschlehrer. Worüber möchtest du heute sprechen?", null);
    }

    private void submit() {
        String text = input.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        input.clear();
        sendToTutor(text);
    }

    public void sendToTutor(String text) {
        addUserMessage(text);
        setBusy(true);

        Task<TutorReply> task = new Task<>() {
            @Override
            protected TutorReply call() {
                return tutor.send(text);
            }
        };
        task.setOnSucceeded(e -> {
            TutorReply reply = task.getValue();
            showCorrections(text, reply.corrections());
            addTutorMessage(reply.reply(), reply.replyEnglish());
            setBusy(false);
        });
        task.setOnFailed(e -> {
            status.setText("Fehler: " + task.getException().getMessage());
            setBusy(false, false);
        });
        Thread.ofVirtual().start(task);
    }

    private void setBusy(boolean busy) {
        setBusy(busy, true);
    }

    private void setBusy(boolean busy, boolean clearStatus) {
        input.setDisable(busy);
        sendButton.setDisable(busy);
        if (busy) {
            status.setText("Der Tutor denkt nach …");
        } else {
            if (clearStatus) {
                status.setText("");
            }
            input.requestFocus();
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
        chat.getChildren().add(bubble(text, null, "bubble-user", Pos.CENTER_RIGHT));
    }

    public void addTutorMessage(String text, String translation) {
        chat.getChildren().add(bubble(text, translation, "bubble-tutor", Pos.CENTER_LEFT));
    }

    private HBox bubble(String text, String translation, String styleClass, Pos alignment) {
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
        HBox row = new HBox(content);
        row.setAlignment(alignment);
        return row;
    }
}
