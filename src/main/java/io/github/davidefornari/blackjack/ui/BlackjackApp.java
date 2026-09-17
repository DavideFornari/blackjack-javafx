package io.github.davidefornari.blackjack.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class BlackjackApp extends Application {

    @Override
    public void start(Stage stage) {
        GameController controller = new GameController();
        // No fixed width/height: the scene sizes itself to the root's actual preferred size,
        // so the window always fits the real content instead of a guessed constant that goes
        // stale (and crops controls) whenever the layout grows.
        Scene scene = new Scene(controller.getRoot());
        scene.getStylesheets().add(
                getClass().getResource("/io/github/davidefornari/blackjack/ui/blackjack.css").toExternalForm());

        stage.setTitle("Blackjack");
        stage.getIcons().add(AppIcon.load());
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(640);
        // Deliberately no sizeToScene() here: calling it before the window is realized computes
        // layout at the setup form's small natural size, then minWidth/minHeight clamp the actual
        // window bigger without a relayout — the content stays pinned top-left in a mostly-blank
        // window. show() alone already auto-sizes correctly (it accounts for the min constraints
        // as part of the same pass), so let it run first, then center on the size it settled on.
        stage.show();
        stage.centerOnScreen();

        // From here on, GameController grows (never shrinks) the window as dealt cards,
        // wager stacks, and extra split hands add content beyond this initial setup-screen size.
        controller.attachStage(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
