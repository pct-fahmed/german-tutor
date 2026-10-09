package tutor;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import tutor.config.AppConfig;
import tutor.ui.MainView;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load();
        MainView view = new MainView(config);

        Scene scene = new Scene(view, 960, 640);
        scene.getStylesheets().add(App.class.getResource("/tutor/app.css").toExternalForm());

        stage.setTitle("Deutsch Tutor (" + config.level() + ")");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
