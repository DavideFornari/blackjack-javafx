package io.github.davidefornari.blackjack.ui;

import io.github.davidefornari.blackjack.engine.BlackjackTable;
import io.github.davidefornari.blackjack.engine.Card;
import io.github.davidefornari.blackjack.engine.CountingSystem;
import io.github.davidefornari.blackjack.engine.Dealer;
import io.github.davidefornari.blackjack.engine.GameRules;
import io.github.davidefornari.blackjack.engine.Hand;
import io.github.davidefornari.blackjack.engine.Player;
import io.github.davidefornari.blackjack.engine.RoundOutcome;
import io.github.davidefornari.blackjack.engine.Settlement;
import io.github.davidefornari.blackjack.engine.Shoe;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the whole game session (setup -> betting -> player turn -> settlement -> next
 * round) and keeps the JavaFX scene graph in sync with the {@link BlackjackTable}
 * engine state. There is no FXML: for a UI this size, wiring the scene graph directly
 * in Java keeps every binding visible in one place.
 */
public final class GameController {

    private enum Phase { SETUP, BETTING, PLAYER_TURN, ROUND_OVER, GAME_OVER }

    private static final long DEFAULT_BANKROLL = 1000;
    private static final long[] CHIP_DENOMINATIONS = {5, 10, 25, 50, 100};

    private final StackPane root = new StackPane();
    private final VBox setupOverlay;
    private final BorderPane tableLayout;

    private final HandPane dealerPane = new HandPane();
    private final HBox playerHandsBox = new HBox(24);
    private final Label messageLabel = new Label();
    private final Label bankrollLabel = new Label();
    private final Label shoeInfoLabel = new Label();

    private final Label bannerTitleLabel = new Label();
    private final Label bannerAmountLabel = new Label();
    private final VBox winLoseBanner = new VBox(4, bannerTitleLabel, bannerAmountLabel);
    private SequentialTransition bannerAnimation;

    private final List<Long> placedChips = new ArrayList<>();
    private final Map<Long, Button> chipButtonsByDenomination = new LinkedHashMap<>();
    private final StackPane betStackPane = new StackPane();
    private final Label betTotalLabel = new Label();
    private final Button clearBetButton = new Button("Clear Bet");
    private final Label betErrorLabel = new Label();
    private final Button dealButton = new Button("Deal");

    private final Button hitButton = new Button("Hit");
    private final Button standButton = new Button("Stand");
    private final Button doubleButton = new Button("Double");
    private final Button splitButton = new Button("Split");

    private final Button nextRoundButton = new Button("Next Round");
    private final Button newGameButton = new Button("New Game");

    private final TextField nameField = new TextField();
    private final TextField bankrollField = new TextField(String.valueOf(DEFAULT_BANKROLL));
    private final ComboBox<CountingSystem> countingCombo =
            new ComboBox<>(FXCollections.observableArrayList(CountingSystem.values()));
    private final CheckBox showCountCheckBox = new CheckBox("Show card count");
    private final Label setupErrorLabel = new Label();
    private final Label rulesSummaryLabel = new Label();

    private Phase phase = Phase.SETUP;
    private BlackjackTable table;
    private Player player;
    private Map<Hand, Settlement> lastSettlements = Map.of();
    private boolean showCardCount;
    private Stage stage;
    private GameRules pendingRules = GameRules.standard();

    public GameController() {
        setupOverlay = buildSetupOverlay();
        tableLayout = buildTableLayout();

        shoeInfoLabel.getStyleClass().add("count-badge");
        StackPane.setAlignment(shoeInfoLabel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(shoeInfoLabel, new Insets(0, 16, 16, 0));

        winLoseBanner.getStyleClass().add("win-lose-banner");
        bannerTitleLabel.getStyleClass().add("win-lose-banner-title");
        bannerAmountLabel.getStyleClass().add("win-lose-banner-amount");
        winLoseBanner.setAlignment(Pos.CENTER);
        // Spans the full table width like a ribbon, but must not stretch to the StackPane's
        // full height too (Region's default max height is Double.MAX_VALUE) — clamp it to its
        // own content height so it reads as a horizontal band, not a full-screen overlay.
        winLoseBanner.setMaxHeight(Region.USE_PREF_SIZE);
        winLoseBanner.setMouseTransparent(true);
        winLoseBanner.setVisible(false);
        winLoseBanner.setOpacity(0);

        root.getChildren().addAll(tableLayout, shoeInfoLabel, winLoseBanner, setupOverlay);
        wireActions();
        refresh();
    }

    public Parent getRoot() {
        return root;
    }

    /**
     * Lets the window grow to fit content added after launch (dealt cards, wager chip
     * stacks, extra hands from a split) instead of leaving them cropped behind a fixed
     * size that inevitably goes stale as the table UI grows. Deliberately grow-only —
     * see {@link #growToFitContent()} — so the window never jumps smaller mid-round.
     */
    public void attachStage(Stage stage) {
        this.stage = stage;
    }

    /** Grows (never shrinks) the window to fit the current layout, once it's actually measured. */
    private void growToFitContent() {
        if (stage == null) {
            return;
        }
        Platform.runLater(() -> {
            root.applyCss();
            root.layout();
            double neededWidth = root.prefWidth(-1);
            double neededHeight = root.prefHeight(-1);
            if (stage.getWidth() < neededWidth) {
                stage.setWidth(neededWidth);
            }
            if (stage.getHeight() < neededHeight) {
                stage.setHeight(neededHeight);
            }
        });
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private VBox buildSetupOverlay() {
        nameField.setPromptText("Your name");
        countingCombo.getSelectionModel().select(CountingSystem.HI_LO);
        countingCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(CountingSystem system) {
                return system == null ? "" : system.displayName();
            }

            @Override
            public CountingSystem fromString(String string) {
                return CountingSystem.HI_LO;
            }
        });
        setupErrorLabel.getStyleClass().add("error-label");
        showCountCheckBox.setSelected(false);

        rulesSummaryLabel.getStyleClass().add("rules-summary-label");
        rulesSummaryLabel.setWrapText(true);
        rulesSummaryLabel.setText(describeRules(pendingRules));

        Button gameSettingsButton = new Button("Game Settings");
        gameSettingsButton.setOnAction(e -> openGameSettingsDialog());

        Button sitDownButton = new Button("Sit Down");
        sitDownButton.getStyleClass().add("primary-button");
        sitDownButton.setOnAction(e -> onSitDown());

        VBox form = new VBox(10,
                new Label("Player name"), nameField,
                new Label("Starting bankroll"), bankrollField,
                new Label("Card-counting system"), countingCombo,
                showCountCheckBox,
                gameSettingsButton,
                rulesSummaryLabel,
                setupErrorLabel,
                sitDownButton);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(28));
        form.setMaxWidth(320);
        form.getStyleClass().add("setup-card");

        VBox overlay = new VBox(new Label("Blackjack"), form);
        overlay.getChildren().get(0).getStyleClass().add("setup-title");
        overlay.setAlignment(Pos.CENTER);
        overlay.setSpacing(18);
        overlay.getStyleClass().add("setup-overlay");
        return overlay;
    }

    /**
     * Every field here is already a {@link GameRules} constructor parameter — this dialog
     * is purely UI, no engine changes. The dialog's own default background and field labels
     * are left alone; only the RadioButton/CheckBox caption text gets a small brightness bump
     * via {@code .game-settings-dialog} in {@code blackjack.css}, on request, for a bit more
     * contrast against that background.
     */
    private void openGameSettingsDialog() {
        Dialog<GameRules> dialog = new Dialog<>();
        dialog.setTitle("Game Settings");
        dialog.initOwner(stage);
        dialog.getDialogPane().getStyleClass().add("game-settings-dialog");
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/io/github/davidefornari/blackjack/ui/blackjack.css").toExternalForm());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<Integer> deckCombo = new ComboBox<>(FXCollections.observableArrayList(1, 2, 4, 6, 8));
        deckCombo.getSelectionModel().select(Integer.valueOf(pendingRules.deckCount()));

        ToggleGroup softSeventeenGroup = new ToggleGroup();
        RadioButton standRadio = new RadioButton("Stand (S17, friendlier)");
        RadioButton hitRadio = new RadioButton("Hit (H17, standard casino)");
        standRadio.setToggleGroup(softSeventeenGroup);
        hitRadio.setToggleGroup(softSeventeenGroup);
        (pendingRules.dealerHitsSoftSeventeen() ? hitRadio : standRadio).setSelected(true);

        CheckBox doubleAfterSplitCheck = new CheckBox("Allowed");
        doubleAfterSplitCheck.setSelected(pendingRules.doubleAfterSplitAllowed());

        ToggleGroup payoutGroup = new ToggleGroup();
        RadioButton payout32Radio = new RadioButton("3:2 (standard)");
        RadioButton payout65Radio = new RadioButton("6:5 (worse for the player)");
        payout32Radio.setToggleGroup(payoutGroup);
        payout65Radio.setToggleGroup(payoutGroup);
        (isStandardPayout(pendingRules.blackjackPayoutRatio()) ? payout32Radio : payout65Radio).setSelected(true);

        Slider penetrationSlider = new Slider(40, 80, pendingRules.penetrationPercent());
        penetrationSlider.setMajorTickUnit(10);
        penetrationSlider.setMinorTickCount(1);
        penetrationSlider.setSnapToTicks(true);
        penetrationSlider.setShowTickMarks(true);
        Label penetrationValueLabel = new Label(pendingRules.penetrationPercent() + "%");
        penetrationSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                penetrationValueLabel.setText(Math.round(newVal.doubleValue()) + "%"));

        ComboBox<Integer> maxSplitCombo = new ComboBox<>(FXCollections.observableArrayList(2, 3, 4));
        maxSplitCombo.getSelectionModel().select(Integer.valueOf(pendingRules.maxSplitHands()));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(18));
        int row = 0;
        grid.addRow(row++, new Label("Deck count"), deckCombo);
        grid.addRow(row++, new Label("Dealer soft 17"), new VBox(4, standRadio, hitRadio));
        grid.addRow(row++, new Label("Double after split"), doubleAfterSplitCheck);
        grid.addRow(row++, new Label("Blackjack payout"), new VBox(4, payout32Radio, payout65Radio));
        grid.addRow(row++, new Label("Shoe penetration"), new HBox(8, penetrationSlider, penetrationValueLabel));
        grid.addRow(row++, new Label("Max split hands"), maxSplitCombo);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != ButtonType.OK) {
                return null;
            }
            return new GameRules(
                    deckCombo.getValue(),
                    (int) Math.round(penetrationSlider.getValue()),
                    hitRadio.isSelected(),
                    payout32Radio.isSelected() ? 1.5 : 1.2,
                    doubleAfterSplitCheck.isSelected(),
                    maxSplitCombo.getValue());
        });

        dialog.showAndWait().ifPresent(rules -> {
            pendingRules = rules;
            rulesSummaryLabel.setText(describeRules(pendingRules));
        });
    }

    private boolean isStandardPayout(double ratio) {
        return Math.abs(ratio - 1.5) < 0.01;
    }

    private String describeRules(GameRules rules) {
        return rules.deckCount() + " decks, "
                + (rules.dealerHitsSoftSeventeen() ? "H17" : "S17") + ", "
                + (isStandardPayout(rules.blackjackPayoutRatio()) ? "3:2" : "6:5") + ", DAS "
                + (rules.doubleAfterSplitAllowed() ? "on" : "off") + ", "
                + rules.penetrationPercent() + "% penetration, max "
                + rules.maxSplitHands() + " splits";
    }

    private BorderPane buildTableLayout() {
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("table-layout");

        messageLabel.getStyleClass().add("message-label");
        playerHandsBox.setAlignment(Pos.CENTER);

        VBox center = new VBox(24, dealerPane, messageLabel, playerHandsBox);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(10, 20, 10, 20));
        layout.setCenter(center);

        layout.setBottom(buildControlsArea());
        return layout;
    }

    private HBox buildChipRow() {
        HBox row = new HBox(10);
        row.getStyleClass().add("chip-rail");
        row.setAlignment(Pos.CENTER);
        double size = ChipView.DIAMETER + 10;
        for (long denomination : CHIP_DENOMINATIONS) {
            ImageView icon = new ImageView(ChipView.imageFor(denomination));
            icon.setFitWidth(size);
            icon.setFitHeight(size);
            icon.setPreserveRatio(true);
            icon.setSmooth(true);

            Button chip = new Button();
            chip.setGraphic(icon);
            chip.getStyleClass().add("chip-button-round");
            chip.setPrefSize(size, size);
            chip.setMinSize(size, size);
            chip.setMaxSize(size, size);
            chip.setOnAction(e -> addChip(denomination));
            chipButtonsByDenomination.put(denomination, chip);
            row.getChildren().add(chip);
        }
        return row;
    }

    private VBox buildControlsArea() {
        HBox statusBar = new HBox(bankrollLabel);
        statusBar.setAlignment(Pos.CENTER);
        statusBar.getStyleClass().add("status-bar");

        betErrorLabel.getStyleClass().add("error-label");
        betTotalLabel.getStyleClass().add("bet-total-badge");
        clearBetButton.setOnAction(e -> onClearBet());

        betStackPane.setMinWidth(ChipView.DIAMETER + 20);

        dealButton.getStyleClass().add("primary-button");
        HBox betRow = new HBox(16, betStackPane, betTotalLabel, clearBetButton, dealButton);
        betRow.setAlignment(Pos.CENTER);

        VBox bettingBox = new VBox(10, buildChipRow(), betRow, betErrorLabel);
        bettingBox.setAlignment(Pos.CENTER);

        HBox actionRow = new HBox(10, hitButton, standButton, doubleButton, splitButton);
        actionRow.setAlignment(Pos.CENTER);

        nextRoundButton.getStyleClass().add("primary-button");
        newGameButton.getStyleClass().add("primary-button");
        // Buttons default to visible in JavaFX, and refresh() can't set their real state until
        // a table/player exist — without this they'd flash visible during SETUP, relying purely
        // on the overlay happening to cover them instead of actually being hidden.
        nextRoundButton.setVisible(false);
        newGameButton.setVisible(false);
        HBox afterRoundRow = new HBox(10, nextRoundButton, newGameButton);
        afterRoundRow.setAlignment(Pos.CENTER);

        VBox controls = new VBox(14, statusBar, bettingBox, actionRow, afterRoundRow);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(14, 18, 22, 18));
        controls.getStyleClass().add("controls-area");
        return controls;
    }

    private void wireActions() {
        dealButton.setOnAction(e -> onDeal());
        hitButton.setOnAction(e -> onHit());
        standButton.setOnAction(e -> onStand());
        doubleButton.setOnAction(e -> onDouble());
        splitButton.setOnAction(e -> onSplit());
        nextRoundButton.setOnAction(e -> onNextRound());
        newGameButton.setOnAction(e -> onNewGame());
    }

    // ------------------------------------------------------------------
    // Phase transitions
    // ------------------------------------------------------------------

    private void onSitDown() {
        Long bankroll = parsePositiveLong(bankrollField.getText());
        if (bankroll == null) {
            setupErrorLabel.setText("Enter a starting bankroll greater than zero.");
            return;
        }
        String name = nameField.getText() == null || nameField.getText().isBlank()
                ? "Player" : nameField.getText().trim();

        player = new Player(name, bankroll);
        player.setPreferredCountingSystem(countingCombo.getValue());
        showCardCount = showCountCheckBox.isSelected();
        GameRules rules = pendingRules;
        Shoe shoe = new Shoe(rules.deckCount(), rules.penetrationPercent());
        table = new BlackjackTable(player, shoe, rules);

        setupErrorLabel.setText("");
        phase = Phase.BETTING;
        refresh();
    }

    private void onDeal() {
        long bet = currentBetTotal();
        if (bet <= 0) {
            betErrorLabel.setText("Place a bet first — click a chip.");
            return;
        }
        betErrorLabel.setText("");
        table.startRound(bet);
        if (table.isPlayerTurnComplete()) {
            settleRound();
        } else {
            phase = Phase.PLAYER_TURN;
        }
        refresh();
    }

    /** Adds a chip to the current bet, ignored if it would exceed the bankroll (the button should already be disabled in that case). */
    private void addChip(long denomination) {
        if (currentBetTotal() + denomination > player.bankroll()) {
            return;
        }
        placedChips.add(denomination);
        refresh();
    }

    private void onClearBet() {
        placedChips.clear();
        refresh();
    }

    private long currentBetTotal() {
        long total = 0;
        for (long chip : placedChips) {
            total += chip;
        }
        return total;
    }

    private void onHit() {
        table.hit();
        afterPlayerAction();
    }

    private void onStand() {
        table.stand();
        afterPlayerAction();
    }

    private void onDouble() {
        table.doubleDown();
        afterPlayerAction();
    }

    private void onSplit() {
        table.split();
        afterPlayerAction();
    }

    private void afterPlayerAction() {
        if (table.isPlayerTurnComplete()) {
            settleRound();
        }
        refresh();
    }

    /** The single place that decides the post-settlement phase: ROUND_OVER, or GAME_OVER if the bet just made cleared the bankroll. */
    private void settleRound() {
        table.playDealerTurn();
        List<Settlement> settlements = table.settle();
        Map<Hand, Settlement> byHand = new HashMap<>();
        long totalProfit = 0;
        for (Settlement s : settlements) {
            byHand.put(s.hand(), s);
            totalProfit += s.payout() - s.hand().wager();
        }
        lastSettlements = byHand;
        phase = player.isBankrupt() ? Phase.GAME_OVER : Phase.ROUND_OVER;
        showRoundOutcomeBanner(totalProfit);
    }

    /** Pops up a brief scale+fade "WIN +N" / "LOST -N" banner over the table; a push (net zero across every hand) shows nothing since it's neither. */
    private void showRoundOutcomeBanner(long totalProfit) {
        if (totalProfit == 0) {
            return;
        }
        boolean win = totalProfit > 0;
        bannerTitleLabel.setText(win ? "WIN" : "LOST");
        bannerAmountLabel.setText((win ? "+" : "-") + Math.abs(totalProfit));
        winLoseBanner.getStyleClass().removeAll("win-lose-banner-win", "win-lose-banner-lose");
        winLoseBanner.getStyleClass().add(win ? "win-lose-banner-win" : "win-lose-banner-lose");

        if (bannerAnimation != null) {
            bannerAnimation.stop();
        }
        winLoseBanner.setOpacity(0);
        winLoseBanner.setScaleX(0.6);
        winLoseBanner.setScaleY(0.6);
        winLoseBanner.setVisible(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), winLoseBanner);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(220), winLoseBanner);
        scaleIn.setFromX(0.6);
        scaleIn.setFromY(0.6);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        ParallelTransition popIn = new ParallelTransition(fadeIn, scaleIn);

        PauseTransition hold = new PauseTransition(Duration.millis(1100));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(320), winLoseBanner);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> winLoseBanner.setVisible(false));

        bannerAnimation = new SequentialTransition(popIn, hold, fadeOut);
        bannerAnimation.play();
    }

    private void onNextRound() {
        lastSettlements = Map.of();
        placedChips.clear();
        phase = Phase.BETTING;
        refresh();
    }

    private void onNewGame() {
        phase = Phase.SETUP;
        table = null;
        player = null;
        lastSettlements = Map.of();
        placedChips.clear();
        refresh();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void refresh() {
        boolean setupPhase = phase == Phase.SETUP;
        setupOverlay.setVisible(setupPhase);
        setupOverlay.setManaged(setupPhase);

        if (setupPhase) {
            return;
        }

        bankrollLabel.setText(player.name() + " — Bankroll: " + player.bankroll());
        String shoeInfo = "Shoe: " + table.cardsRemainingInShoe() + " cards left";
        if (showCardCount) {
            CountingSystem system = player.preferredCountingSystem();
            shoeInfo += "   |   " + system.displayName() + " count: "
                    + formatSigned(table.runningCount(system));
        }
        shoeInfoLabel.setText(shoeInfo);

        renderDealer();
        renderPlayerHands();
        renderBetStack();
        renderMessage();

        boolean betting = phase == Phase.BETTING;
        boolean playerTurn = phase == Phase.PLAYER_TURN;
        boolean roundOver = phase == Phase.ROUND_OVER;
        boolean gameOver = phase == Phase.GAME_OVER;

        long betTotal = currentBetTotal();
        for (long denomination : CHIP_DENOMINATIONS) {
            boolean wouldExceedBankroll = betTotal + denomination > player.bankroll();
            chipButtonsByDenomination.get(denomination).setDisable(!betting || wouldExceedBankroll);
        }
        clearBetButton.setDisable(!betting || placedChips.isEmpty());
        dealButton.setDisable(!betting || betTotal <= 0);
        betErrorLabel.setVisible(betting);
        // Once dealt, each hand shows its own chip stack right under its cards — the pre-deal
        // pile in the controls area would just be a confusing, stale duplicate of that.
        betStackPane.setVisible(betting);
        betStackPane.setManaged(betting);
        betTotalLabel.setVisible(betting);
        betTotalLabel.setManaged(betting);

        hitButton.setDisable(!playerTurn);
        standButton.setDisable(!playerTurn);
        doubleButton.setDisable(!(playerTurn && table.canDouble()));
        splitButton.setDisable(!(playerTurn && table.canSplit()));

        nextRoundButton.setDisable(!roundOver);
        nextRoundButton.setVisible(roundOver);
        newGameButton.setVisible(roundOver || gameOver);
        newGameButton.setDisable(!(roundOver || gameOver));

        growToFitContent();
    }

    private void renderDealer() {
        Dealer dealer = table.dealer();
        // Between "Next Round" and the next "Deal", table.dealer() still holds last round's
        // finished hand (it only resets inside startRound()) — hide it so the table reads as empty.
        List<Card> cards = phase == Phase.BETTING ? List.of() : dealer.hand().cards();
        List<CardView> views = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            boolean hidden = i == 1 && !dealer.isHoleCardRevealed();
            views.add(hidden ? CardView.faceDown() : CardView.faceUp(cards.get(i)));
        }
        dealerPane.setCaption("Dealer");
        dealerPane.setCards(views);
        dealerPane.setTotalText(phase != Phase.BETTING && dealer.isHoleCardRevealed()
                ? "Total: " + dealer.hand().total() : "");
        animateIn(views);
    }

    private void renderPlayerHands() {
        playerHandsBox.getChildren().clear();
        if (phase == Phase.BETTING) {
            return; // same reasoning as renderDealer(): don't show last round's leftover hands
        }
        List<Hand> hands = player.hands();
        for (int i = 0; i < hands.size(); i++) {
            Hand hand = hands.get(i);
            HandPane pane = new HandPane();
            pane.setCaption(hands.size() > 1 ? "Hand " + (i + 1) : "Your Hand");

            List<CardView> views = new ArrayList<>();
            for (Card card : hand.cards()) {
                views.add(CardView.faceUp(card));
            }
            pane.setCards(views);
            pane.setWager(hand.wager());
            pane.setTotalText(handStatusText(hand));
            pane.setActive(phase == Phase.PLAYER_TURN && i == table.activeHandIndex());
            playerHandsBox.getChildren().add(pane);
            animateIn(views);
        }
    }

    private void renderBetStack() {
        betStackPane.getChildren().setAll(ChipView.stack(currentBetTotal()));
        betTotalLabel.setText("Bet: " + currentBetTotal());
    }

    private String handStatusText(Hand hand) {
        Settlement settlement = lastSettlements.get(hand);
        if (settlement != null) {
            long profit = settlement.payout() - hand.wager();
            return switch (settlement.outcome()) {
                case BLACKJACK_WIN -> "Blackjack! +" + profit;
                case WIN -> "Win +" + profit;
                case PUSH -> "Push (" + hand.total() + ")";
                case LOSS -> "Lose (" + hand.total() + ")";
                case BUST -> "Bust (" + hand.total() + ")";
            };
        }
        if (hand.status() == Hand.Status.BLACKJACK) {
            return "Blackjack!";
        }
        if (hand.isBust()) {
            return "Bust (" + hand.total() + ")";
        }
        return hand.total() + (hand.isSoft() ? " (soft)" : "");
    }

    private void renderMessage() {
        if (phase == Phase.GAME_OVER) {
            messageLabel.setText("Out of chips — thanks for playing, " + player.name() + ".");
        } else if (phase == Phase.ROUND_OVER) {
            messageLabel.setText(summarizeRound());
        } else if (phase == Phase.PLAYER_TURN) {
            messageLabel.setText("Your move");
        } else {
            messageLabel.setText("Place your bet");
        }
    }

    private String summarizeRound() {
        boolean anyWin = lastSettlements.values().stream()
                .anyMatch(s -> s.outcome() == RoundOutcome.WIN || s.outcome() == RoundOutcome.BLACKJACK_WIN);
        boolean anyLoss = lastSettlements.values().stream()
                .anyMatch(s -> s.outcome() == RoundOutcome.LOSS || s.outcome() == RoundOutcome.BUST);
        if (anyWin && !anyLoss) return "You win!";
        if (anyLoss && !anyWin) return "Dealer wins";
        if (!anyWin) return "Push";
        return "Round over";
    }

    private void animateIn(List<CardView> views) {
        SequentialTransition sequence = new SequentialTransition();
        long delay = 0;
        for (CardView view : views) {
            view.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(180), view);
            fade.setFromValue(0);
            fade.setToValue(1);
            PauseTransition pause = new PauseTransition(Duration.millis(delay));
            SequentialTransition perCard = new SequentialTransition(pause, fade);
            sequence.getChildren().add(perCard);
            delay += 90;
        }
        sequence.play();
    }

    private Long parsePositiveLong(String text) {
        if (text == null) {
            return null;
        }
        try {
            long value = Long.parseLong(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatSigned(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }
}
