package io.github.davidefornari.blackjack.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class BlackjackApp extends Application {

    @Override
    public void start(Stage stage) {
        GameController controller = new GameController();
        Scene scene = new Scene(controller.getRoot(), 1040, 720);
        scene.getStylesheets().add(
                getClass().getResource("/io/github/davidefornari/blackjack/ui/blackjack.css").toExternalForm());

        stage.setTitle("Blackjack");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(640);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
