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
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
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
    private static final long DEFAULT_BET = 50;

    private final StackPane root = new StackPane();
    private final VBox setupOverlay;
    private final BorderPane tableLayout;

    private final HandPane dealerPane = new HandPane();
    private final HBox playerHandsBox = new HBox(24);
    private final Label messageLabel = new Label();
    private final Label bankrollLabel = new Label();
    private final Label shoeInfoLabel = new Label();

    private final TextField betField = new TextField(String.valueOf(DEFAULT_BET));
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

    private Phase phase = Phase.SETUP;
    private BlackjackTable table;
    private Player player;
    private Map<Hand, Settlement> lastSettlements = Map.of();
    private boolean showCardCount;

    public GameController() {
        setupOverlay = buildSetupOverlay();
        tableLayout = buildTableLayout();

        shoeInfoLabel.getStyleClass().add("count-badge");
        StackPane.setAlignment(shoeInfoLabel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(shoeInfoLabel, new Insets(0, 16, 16, 0));

        root.getChildren().addAll(tableLayout, shoeInfoLabel, setupOverlay);
        wireActions();
        refresh();
    }

    public Parent getRoot() {
        return root;
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

        Button sitDownButton = new Button("Sit Down");
        sitDownButton.getStyleClass().add("primary-button");
        sitDownButton.setOnAction(e -> onSitDown());

        VBox form = new VBox(10,
                new Label("Player name"), nameField,
                new Label("Starting bankroll"), bankrollField,
                new Label("Card-counting system"), countingCombo,
                showCountCheckBox,
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

    private BorderPane buildTableLayout() {
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("table-layout");

        HBox topBar = new HBox(bankrollLabel);
        topBar.setPadding(new Insets(12, 18, 12, 18));
        topBar.getStyleClass().add("top-bar");
        layout.setTop(topBar);

        messageLabel.getStyleClass().add("message-label");
        playerHandsBox.setAlignment(Pos.CENTER);

        VBox center = new VBox(24, dealerPane, messageLabel, playerHandsBox);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(10, 20, 10, 20));
        layout.setCenter(center);

        layout.setBottom(buildControlsArea());
        return layout;
    }

    private VBox buildControlsArea() {
        betErrorLabel.getStyleClass().add("error-label");

        HBox chips = new HBox(8);
        for (long amount : new long[]{25, 50, 100, 250}) {
            Button chip = new Button(String.valueOf(amount));
            chip.getStyleClass().add("chip-button");
            chip.setOnAction(e -> betField.setText(String.valueOf(Math.min(amount, player.bankroll()))));
            chips.getChildren().add(chip);
        }
        Button allIn = new Button("All In");
        allIn.getStyleClass().add("chip-button");
        allIn.setOnAction(e -> betField.setText(String.valueOf(player.bankroll())));
        chips.getChildren().add(allIn);

        dealButton.getStyleClass().add("primary-button");
        HBox betRow = new HBox(10, new Label("Bet:"), betField, dealButton);
        betRow.setAlignment(Pos.CENTER);

        VBox bettingBox = new VBox(8, chips, betRow, betErrorLabel);
        bettingBox.setAlignment(Pos.CENTER);

        HBox actionRow = new HBox(10, hitButton, standButton, doubleButton, splitButton);
        actionRow.setAlignment(Pos.CENTER);

        nextRoundButton.getStyleClass().add("primary-button");
        newGameButton.getStyleClass().add("primary-button");
        HBox afterRoundRow = new HBox(10, nextRoundButton, newGameButton);
        afterRoundRow.setAlignment(Pos.CENTER);

        VBox controls = new VBox(14, bettingBox, actionRow, afterRoundRow);
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
        GameRules rules = GameRules.standard();
        Shoe shoe = new Shoe(rules.deckCount(), rules.penetrationPercent());
        table = new BlackjackTable(player, shoe, rules);

        setupErrorLabel.setText("");
        phase = Phase.BETTING;
        refresh();
    }

    private void onDeal() {
        Long bet = parsePositiveLong(betField.getText());
        if (bet == null) {
            betErrorLabel.setText("Enter a bet greater than zero.");
            return;
        }
        if (bet > player.bankroll()) {
            betErrorLabel.setText("You only have " + player.bankroll() + " to bet.");
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
        for (Settlement s : settlements) {
            byHand.put(s.hand(), s);
        }
        lastSettlements = byHand;
        phase = player.isBankrupt() ? Phase.GAME_OVER : Phase.ROUND_OVER;
    }

    private void onNextRound() {
        lastSettlements = Map.of();
        betField.setText(String.valueOf(Math.min(DEFAULT_BET, player.bankroll())));
        phase = Phase.BETTING;
        refresh();
    }

    private void onNewGame() {
        phase = Phase.SETUP;
        table = null;
        player = null;
        lastSettlements = Map.of();
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
        renderMessage();

        boolean betting = phase == Phase.BETTING;
        boolean playerTurn = phase == Phase.PLAYER_TURN;
        boolean roundOver = phase == Phase.ROUND_OVER;
        boolean gameOver = phase == Phase.GAME_OVER;

        betField.setDisable(!betting);
        dealButton.setDisable(!betting);
        betErrorLabel.setVisible(betting);

        hitButton.setDisable(!playerTurn);
        standButton.setDisable(!playerTurn);
        doubleButton.setDisable(!(playerTurn && table.canDouble()));
        splitButton.setDisable(!(playerTurn && table.canSplit()));

        nextRoundButton.setDisable(!roundOver);
        nextRoundButton.setVisible(roundOver);
        newGameButton.setVisible(roundOver || gameOver);
        newGameButton.setDisable(!(roundOver || gameOver));
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
            pane.setTotalText(handStatusText(hand));
            pane.setActive(phase == Phase.PLAYER_TURN && i == table.activeHandIndex());
            playerHandsBox.getChildren().add(pane);
            animateIn(views);
        }
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
