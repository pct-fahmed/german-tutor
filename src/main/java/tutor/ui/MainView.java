package tutor.ui;

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
import tutor.config.AppConfig;

public class MainView extends BorderPane {

    private final VBox chat = new VBox(8);
    private final ScrollPane chatScroll = new ScrollPane(chat);
    private final VBox corrections = new VBox(8);
    private final TextField input = new TextField();
    private final Button sendButton = new Button("Senden");
    private final Button talkButton = new Button("🎤 Sprechen");

    public MainView(AppConfig config) {
        chat.setPadding(new Insets(12));
        chatScroll.setFitToWidth(true);
        chatScroll.getStyleClass().add("chat");
        chat.heightProperty().addListener((obs, old, h) -> chatScroll.setVvalue(1.0));

        Label correctionsTitle = new Label("Korrekturen");
        correctionsTitle.getStyleClass().add("panel-title");
        VBox correctionsPanel = new VBox(8, correctionsTitle, corrections);
        correctionsPanel.setPadding(new Insets(12));
        correctionsPanel.setPrefWidth(300);
        correctionsPanel.getStyleClass().add("corrections");

        input.setPromptText("Schreib etwas auf Deutsch …");
        HBox.setHgrow(input, Priority.ALWAYS);
        talkButton.setDisable(true);
        HBox inputBar = new HBox(8, talkButton, input, sendButton);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(12));

        setCenter(chatScroll);
        setRight(correctionsPanel);
        setBottom(inputBar);

        sendButton.setOnAction(e -> submit());
        input.setOnAction(e -> submit());

        addTutorMessage("Hallo! Ich bin dein Deutschlehrer. Worüber möchtest du heute sprechen?");
    }

    private void submit() {
        String text = input.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        input.clear();
        addUserMessage(text);
    }

    public void addUserMessage(String text) {
        chat.getChildren().add(bubble(text, "bubble-user", Pos.CENTER_RIGHT));
    }

    public void addTutorMessage(String text) {
        chat.getChildren().add(bubble(text, "bubble-tutor", Pos.CENTER_LEFT));
    }

    private HBox bubble(String text, String styleClass, Pos alignment) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(480);
        label.getStyleClass().addAll("bubble", styleClass);
        HBox row = new HBox(label);
        row.setAlignment(alignment);
        return row;
    }
}
