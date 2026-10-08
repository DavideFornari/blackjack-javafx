package io.github.davidefornari.blackjack.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** The round's result as a ribbon across the table: pops in, holds, then fades out on its own. */
public final class WinLoseBannerPane extends VBox {

    private final Label titleLabel = new Label();
    private final Label amountLabel = new Label();
    private SequentialTransition animation;

    public WinLoseBannerPane() {
        super(4);
        getChildren().addAll(titleLabel, amountLabel);
        getStyleClass().add("win-lose-banner");
        titleLabel.getStyleClass().add("win-lose-banner-title");
        amountLabel.getStyleClass().add("win-lose-banner-amount");
        setAlignment(Pos.CENTER);
        // Spans the full table width like a ribbon, but must not stretch to the StackPane's
        // full height too (Region's default max height is Double.MAX_VALUE) — clamp it to its
        // own content height so it reads as a horizontal band, not a full-screen overlay.
        setMaxHeight(Region.USE_PREF_SIZE);
        setMouseTransparent(true);
        setVisible(false);
        setOpacity(0);
    }

    /** "WIN +N" / "LOST -N" / "PUSH +0" for the main hand(s) — always shown, so a push still gets a result. */
    public void showOutcome(long totalProfit) {
        String title = totalProfit > 0 ? "WIN" : totalProfit < 0 ? "LOST" : "PUSH";
        String styleClass = totalProfit > 0 ? "win-lose-banner-win"
                : totalProfit < 0 ? "win-lose-banner-lose" : "win-lose-banner-push";
        show(title, totalProfit, styleClass);
    }

    /** One combined banner for a won insurance bet, its net profit folding in the main hand's result. */
    public void showInsuranceWin(long netProfit) {
        show("INSURANCE WIN", netProfit, "win-lose-banner-win");
    }

    /** Scale+fade pop-in, hold, fade-out. */
    private void show(String title, long amount, String styleClass) {
        titleLabel.setText(title);
        amountLabel.setText((amount >= 0 ? "+" : "-") + Math.abs(amount));
        getStyleClass().removeAll("win-lose-banner-win", "win-lose-banner-lose", "win-lose-banner-push");
        getStyleClass().add(styleClass);

        if (animation != null) {
            animation.stop();
        }
        setOpacity(0);
        setScaleX(0.6);
        setScaleY(0.6);
        setVisible(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), this);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(220), this);
        scaleIn.setFromX(0.6);
        scaleIn.setFromY(0.6);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        ParallelTransition popIn = new ParallelTransition(fadeIn, scaleIn);

        PauseTransition hold = new PauseTransition(Duration.millis(1100));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(320), this);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> setVisible(false));

        animation = new SequentialTransition(popIn, hold, fadeOut);
        animation.play();
    }
}
