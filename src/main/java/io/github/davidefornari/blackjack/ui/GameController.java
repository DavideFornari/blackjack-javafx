package io.github.davidefornari.blackjack.ui;

import io.github.davidefornari.blackjack.engine.BlackjackTable;
import io.github.davidefornari.blackjack.engine.Card;
import io.github.davidefornari.blackjack.engine.CountingSystem;
import io.github.davidefornari.blackjack.engine.Dealer;
import io.github.davidefornari.blackjack.engine.GameRules;
import io.github.davidefornari.blackjack.engine.Hand;
import io.github.davidefornari.blackjack.engine.InsuranceSettlement;
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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the whole game session (setup -> betting -> optional insurance decision ->
 * player turn -> settlement -> next round) and keeps the JavaFX scene graph in sync
 * with the {@link BlackjackTable} engine state. There is no FXML: for a UI this size,
 * wiring the scene graph directly in Java keeps every binding visible in one place.
 */
public final class GameController {

    /** GAME_OVER: the bankroll fell under {@link #MINIMUM_BET}. CASHED_OUT: the player chose to leave. */
    private enum Phase { SETUP, BETTING, AWAITING_INSURANCE, PLAYER_TURN, ROUND_OVER, GAME_OVER, CASHED_OUT }

    private static final long DEFAULT_BANKROLL = 1000;
    /**
     * The table minimum, and the real end-of-session threshold: bets are placed in chips, so a
     * bankroll below the smallest chip can't be wagered at all even though it isn't zero.
     * Checking for zero alone left 1-4 chips looking like a live game with every chip button
     * and Deal disabled and no way out of BETTING.
     */
    private static final long MINIMUM_BET = ChipView.DENOMINATIONS.get(0);

    private final StackPane root = new StackPane();
    private final VBox setupOverlay;
    private final ConfirmPane confirmOverlay = new ConfirmPane();
    private final GameSettingsPane gameSettingsOverlay;
    private final CountingGuidePane countingGuideOverlay = new CountingGuidePane();
    private final OverlayPane insuranceOverlay;
    private final BorderPane tableLayout;

    private final TablePane tablePane = new TablePane();
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
    private final Button leaveTableButton = new Button("Leave Table");
    private final Button newGameButton = new Button("New Game");

    private final TextField nameField = new TextField();
    private final TextField bankrollField = new TextField(String.valueOf(DEFAULT_BANKROLL));
    private final ComboBox<CountingSystem> countingCombo =
            new ComboBox<>(FXCollections.observableArrayList(CountingSystem.values()));
    private final CheckBox showCountCheckBox = new CheckBox("Show card count");
    private final Label setupErrorLabel = new Label();
    private final Label rulesSummaryLabel = new Label();

    private final Label insuranceMessageLabel = new Label();
    private final HandPane insuranceHandPreview = new HandPane();
    private final Button takeInsuranceButton = new Button("Take Insurance");
    private final Button declineInsuranceButton = new Button("No Thanks");
    private PauseTransition insuranceDelay;

    private Phase phase = Phase.SETUP;
    private BlackjackTable table;
    private Player player;
    private Map<Hand, Settlement> lastSettlements = Map.of();
    /** The chips behind the last dealt bet, re-placed for the next round when still affordable. */
    private List<Long> lastBetChips = List.of();
    private long startingBankroll;
    private boolean showCardCount;
    private CountingSystem countingSystem;
    private Stage stage;
    private GameRules pendingRules = GameRules.standard();

    public GameController() {
        setupOverlay = buildSetupOverlay();
        gameSettingsOverlay = new GameSettingsPane(rules -> {
            pendingRules = rules;
            rulesSummaryLabel.setText(describeRules(rules));
        });
        insuranceOverlay = buildInsuranceOverlay();
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

        // gameSettingsOverlay and countingGuideOverlay are opened from buttons inside
        // setupOverlay, so they must come after it here to actually render on top of it.
        root.getChildren().addAll(
                tableLayout, shoeInfoLabel, winLoseBanner, confirmOverlay, insuranceOverlay,
                setupOverlay, gameSettingsOverlay, countingGuideOverlay);
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
     * Also installs the keyboard shortcuts (see {@link #onKeyPressed}).
     */
    public void attachStage(Stage stage) {
        this.stage = stage;
        stage.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
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
        setupErrorLabel.getStyleClass().add("error-label");
        showCountCheckBox.setSelected(false);

        rulesSummaryLabel.getStyleClass().add("rules-summary-label");
        rulesSummaryLabel.setWrapText(true);
        rulesSummaryLabel.setText(describeRules(pendingRules));

        Button gameSettingsButton = new Button("Game Settings");
        gameSettingsButton.setOnAction(e -> gameSettingsOverlay.open(pendingRules));

        Button countingGuideButton = new Button("?");
        countingGuideButton.getStyleClass().add("setup-help-button");
        countingGuideButton.setAccessibleText("Card-counting guide");
        countingGuideButton.setOnAction(e -> countingGuideOverlay.open());

        Button sitDownButton = new Button("Sit Down");
        sitDownButton.getStyleClass().add("primary-button");
        sitDownButton.setOnAction(e -> onSitDown());

        VBox form = new VBox(10,
                new Label("Player name"), nameField,
                new Label("Starting bankroll"), bankrollField,
                new Label("Card-counting system"), OverlayPane.buttonRow(countingCombo, countingGuideButton),
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

    /** Shows or hides {@code node}, taking it out of layout while hidden. */
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private String describeRules(GameRules rules) {
        return rules.deckCount() + " decks, "
                + (rules.dealerHitsSoftSeventeen() ? "H17" : "S17") + ", "
                + rules.blackjackPayout().displayName() + ", DAS "
                + (rules.doubleAfterSplitAllowed() ? "on" : "off") + ", "
                + rules.penetrationPercent() + "% penetration, max "
                + rules.maxSplitHands() + " splits, insurance "
                + (rules.insuranceAllowed() ? "on" : "off");
    }

    /**
     * Paused mid-deal, on an Ace up-card only, when {@link GameRules#insuranceAllowed()}
     * is on — an in-theme {@link OverlayPane} rather than a stock dialog. Stays here rather than
     * in its own class because its buttons drive the round's flow. The insurance
     * amount itself is fixed at the standard casino max (half the original wager) rather
     * than an adjustable field, to avoid a bespoke bet-amount input.
     */
    private OverlayPane buildInsuranceOverlay() {
        Label title = new Label("Insurance?");
        title.getStyleClass().add("setup-title");

        insuranceMessageLabel.setWrapText(true);
        insuranceMessageLabel.setTextAlignment(TextAlignment.CENTER);

        // Shrunk down (vs. the full-size cards on the table behind this overlay) so it reads
        // as a reference thumbnail, not a second copy of the hand competing for attention.
        insuranceHandPreview.setScaleX(0.7);
        insuranceHandPreview.setScaleY(0.7);

        takeInsuranceButton.getStyleClass().add("primary-button");
        takeInsuranceButton.setOnAction(e -> onTakeInsurance());

        declineInsuranceButton.setOnAction(e -> onDeclineInsurance());

        return new OverlayPane(340, title, insuranceHandPreview, insuranceMessageLabel,
                OverlayPane.buttonRow(declineInsuranceButton, takeInsuranceButton));
    }

    /** Shows the insurance popup after a short pause, so the dealt hand finishes animating onto the table first. */
    private void showInsuranceOverlayAfterDelay() {
        if (insuranceDelay != null) {
            insuranceDelay.stop();
        }
        insuranceDelay = new PauseTransition(Duration.millis(500));
        insuranceDelay.setOnFinished(e -> showInsuranceOverlay());
        insuranceDelay.play();
    }

    private void showInsuranceOverlay() {
        long cost = table.maxInsuranceBet();
        // A bet that took the whole bankroll leaves nothing to pay the side bet with, and
        // takeInsurance() throws on that — thrown inside an FX handler it only reaches stderr,
        // so the button would just look dead. Offer the decline as the only way on instead.
        boolean affordable = cost > 0 && cost <= player.bankroll();
        takeInsuranceButton.setDisable(!affordable);
        declineInsuranceButton.setText(affordable ? "No Thanks" : "Continue");
        insuranceMessageLabel.setText(affordable
                ? "The dealer is showing an Ace. Insure your hand for " + cost
                        + " chips against a dealer blackjack? It pays 2:1 if the dealer has one."
                : "The dealer is showing an Ace, but insuring this hand costs " + cost
                        + " chips and your bet left you only " + player.bankroll()
                        + ". You'll have to play it uninsured.");

        Hand hand = player.firstHand();
        List<CardView> views = new ArrayList<>();
        for (Card card : hand.cards()) {
            views.add(CardView.faceUp(card));
        }
        insuranceHandPreview.setCaption("Your Hand");
        insuranceHandPreview.setCards(views);
        insuranceHandPreview.setWager(0);
        insuranceHandPreview.setTotalText(handStatusText(hand));

        insuranceOverlay.open();
    }

    private void onTakeInsurance() {
        table.takeInsurance(table.maxInsuranceBet());
        insuranceOverlay.close();
        afterInsuranceDecision();
    }

    private void onDeclineInsurance() {
        table.declineInsurance();
        insuranceOverlay.close();
        afterInsuranceDecision();
    }

    /**
     * Winning insurance means the dealer had blackjack, so the round is already decided —
     * settle it and show one combined "INSURANCE WIN +netProfit" banner (main hand profit
     * folded in, e.g. bet 10 + insurance 5, dealer blackjack, plain losing hand: -10 main
     * hand + 10 insurance profit = "INSURANCE WIN +0") instead of a separate insurance
     * banner followed by the usual WIN/LOST/PUSH one. A loss or decline of insurance
     * doesn't get its own banner — just the round message text mentions it — and the round
     * proceeds exactly as it would have without insurance.
     */
    private void afterInsuranceDecision() {
        Optional<InsuranceSettlement> insurance = table.lastInsuranceSettlement();
        if (insurance.isPresent() && insurance.get().won()) {
            long mainHandProfit = settleRound();
            long insuranceProfit = insurance.get().payout() - insurance.get().amountWagered();
            showBanner("INSURANCE WIN", mainHandProfit + insuranceProfit, "win-lose-banner-win");
        } else if (table.isPlayerTurnComplete()) {
            showRoundOutcomeBanner(settleRound());
        } else {
            phase = Phase.PLAYER_TURN;
        }
        refresh();
    }

    private BorderPane buildTableLayout() {
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("table-layout");
        layout.setCenter(tablePane);
        layout.setBottom(buildControlsArea());
        return layout;
    }

    private HBox buildChipRow() {
        HBox row = new HBox(10);
        row.getStyleClass().add("chip-rail");
        row.setAlignment(Pos.CENTER);
        double size = ChipView.DIAMETER + 10;
        for (long denomination : ChipView.DENOMINATIONS) {
            Button chip = new Button();
            chip.setGraphic(new ChipView(denomination, size));
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
        leaveTableButton.setVisible(false);
        newGameButton.setVisible(false);
        HBox afterRoundRow = new HBox(10, nextRoundButton, leaveTableButton, newGameButton);
        afterRoundRow.setAlignment(Pos.CENTER);

        VBox controls = new VBox(14, statusBar, bettingBox, actionRow, afterRoundRow);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(14, 18, 22, 18));
        controls.getStyleClass().add("controls-area");
        return controls;
    }

    private void wireActions() {
        dealButton.setOnAction(e -> onDeal());
        hitButton.setOnAction(e -> { table.hit(); afterPlayerAction(); });
        standButton.setOnAction(e -> { table.stand(); afterPlayerAction(); });
        doubleButton.setOnAction(e -> { table.doubleDown(); afterPlayerAction(); });
        splitButton.setOnAction(e -> { table.split(); afterPlayerAction(); });
        nextRoundButton.setOnAction(e -> onNextRound());
        leaveTableButton.setOnAction(e -> onLeaveTable());
        newGameButton.setOnAction(e -> onNewGame());

        dealButton.setTooltip(new Tooltip("Enter"));
        hitButton.setTooltip(new Tooltip("H"));
        standButton.setTooltip(new Tooltip("S"));
        doubleButton.setTooltip(new Tooltip("D"));
        splitButton.setTooltip(new Tooltip("P"));
        nextRoundButton.setTooltip(new Tooltip("Enter"));
    }

    /**
     * Table shortcuts: H/S/D/P for hit, stand, double, split; Enter to deal or start the next
     * round. Each just fires its button, so the button's own enabled state is the only rule.
     * A stage-level filter, so it works whatever has focus — which is also why it stands down
     * while any overlay is up, where the setup and settings text fields need those keys. Esc
     * dismisses the topmost popup that has a Close/Cancel; setup and insurance need an answer.
     */
    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            for (OverlayPane popup : List.of(countingGuideOverlay, gameSettingsOverlay, confirmOverlay)) {
                if (popup.isOpen()) {
                    popup.close(); // exactly what each popup's Close/Cancel button does
                    event.consume();
                    return;
                }
            }
        }
        boolean overlayShowing = setupOverlay.isVisible() || gameSettingsOverlay.isOpen()
                || confirmOverlay.isOpen() || insuranceOverlay.isOpen() || countingGuideOverlay.isOpen();
        if (overlayShowing) {
            return;
        }
        Button target = switch (event.getCode()) {
            case H -> hitButton;
            case S -> standButton;
            case D -> doubleButton;
            case P -> splitButton;
            case ENTER -> phase == Phase.ROUND_OVER ? nextRoundButton : dealButton;
            default -> null;
        };
        if (target == null) {
            return;
        }
        event.consume();
        target.fire(); // no-op while disabled, and refresh() disables every hidden button
    }

    // ------------------------------------------------------------------
    // Phase transitions
    // ------------------------------------------------------------------

    private void onSitDown() {
        Long bankroll = parsePositiveLong(bankrollField.getText());
        // Anything under one chip can't be bet, so it would seat the player straight into the
        // dead end that MINIMUM_BET exists to prevent at the other end of the session.
        if (bankroll == null || bankroll < MINIMUM_BET) {
            setupErrorLabel.setText("Enter a starting bankroll of at least " + MINIMUM_BET + " chips.");
            return;
        }
        String name = nameField.getText() == null || nameField.getText().isBlank()
                ? "Player" : nameField.getText().trim();

        player = new Player(name, bankroll);
        startingBankroll = bankroll;
        countingSystem = countingCombo.getValue();
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
        lastBetChips = List.copyOf(placedChips);
        table.startRound(bet);
        if (table.isInsurancePending()) {
            phase = Phase.AWAITING_INSURANCE;
            refresh();
            showInsuranceOverlayAfterDelay();
            return;
        }
        if (table.isPlayerTurnComplete()) {
            // Decided at the deal — a natural, or a dealer natural peeked on a ten up-card.
            // The banner is this call site's job too, not just afterPlayerAction()'s.
            showRoundOutcomeBanner(settleRound());
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
        return sum(placedChips);
    }

    private static long sum(List<Long> chips) {
        return chips.stream().mapToLong(Long::longValue).sum();
    }

    private void afterPlayerAction() {
        if (table.isPlayerTurnComplete()) {
            showRoundOutcomeBanner(settleRound());
        }
        refresh();
    }

    /**
     * Resolves the dealer's turn and every player hand, advances the phase (ROUND_OVER, or
     * GAME_OVER once what's left can't cover {@link #MINIMUM_BET}), and returns the main
     * hand(s)' total profit. Doesn't show a banner itself — callers decide that, since a win against
     * a dealer blackjack the player insured needs its profit folded into a single combined
     * "INSURANCE WIN" banner instead of its own {@link #showRoundOutcomeBanner}.
     */
    private long settleRound() {
        table.playDealerTurn();
        List<Settlement> settlements = table.settle();
        Map<Hand, Settlement> byHand = new HashMap<>();
        long totalProfit = 0;
        for (Settlement s : settlements) {
            byHand.put(s.hand(), s);
            totalProfit += s.payout() - s.hand().wager();
        }
        lastSettlements = byHand;
        phase = player.bankroll() >= MINIMUM_BET ? Phase.ROUND_OVER : Phase.GAME_OVER;
        return totalProfit;
    }

    /** Pops up "WIN +N" / "LOST -N" / "PUSH +0" for the main hand(s) — always shown, so a push still gets a result. */
    private void showRoundOutcomeBanner(long totalProfit) {
        String title = totalProfit > 0 ? "WIN" : totalProfit < 0 ? "LOST" : "PUSH";
        String styleClass = totalProfit > 0 ? "win-lose-banner-win"
                : totalProfit < 0 ? "win-lose-banner-lose" : "win-lose-banner-push";
        showBanner(title, totalProfit, styleClass);
    }

    /** Scale+fade pop-in, hold, fade-out for {@code winLoseBanner}. */
    private void showBanner(String title, long amount, String styleClass) {
        bannerTitleLabel.setText(title);
        bannerAmountLabel.setText((amount >= 0 ? "+" : "-") + Math.abs(amount));
        winLoseBanner.getStyleClass().removeAll("win-lose-banner-win", "win-lose-banner-lose", "win-lose-banner-push");
        winLoseBanner.getStyleClass().add(styleClass);

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

    /** Re-places the last bet's chips, so repeating a bet is one click (or Enter); dropped if no longer affordable. */
    private void onNextRound() {
        lastSettlements = Map.of();
        placedChips.clear();
        if (sum(lastBetChips) <= player.bankroll()) {
            placedChips.addAll(lastBetChips);
        }
        phase = Phase.BETTING;
        refresh();
    }

    /** Confirms first — leaving ends the session, so a misclick next to "Next Round" shouldn't be able to. */
    private void onLeaveTable() {
        confirmOverlay.ask("Cash Out?",
                "Cash out with " + player.bankroll() + " chips and end this session?",
                "Yes, Cash Out",
                () -> {
                    phase = Phase.CASHED_OUT;
                    refresh();
                });
    }

    /** Only offered once the session is over (out of chips, or cashed out), so there's nothing left to confirm. */
    private void onNewGame() {
        phase = Phase.SETUP;
        table = null;
        player = null;
        lastSettlements = Map.of();
        lastBetChips = List.of();
        placedChips.clear();
        refresh();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void refresh() {
        boolean setupPhase = phase == Phase.SETUP;
        show(setupOverlay, setupPhase);
        // Hidden, not unmanaged: the setup form sits on bare felt (no leftovers from the last
        // session) while the table keeps its size, so the window doesn't resize around it.
        tableLayout.setVisible(!setupPhase);
        shoeInfoLabel.setVisible(!setupPhase);

        if (setupPhase) {
            return;
        }

        bankrollLabel.setText(player.name() + " — Bankroll: " + player.bankroll());
        String shoeInfo = "Shoe: " + table.cardsRemainingInShoe() + " cards left";
        if (showCardCount) {
            shoeInfo += "   |   " + countingSystem.displayName() + " count: "
                    + formatSigned(table.runningCount(countingSystem));
            if (countingSystem.isBalanced()) {
                // Locale.ROOT: an Italian locale would print "+1,5".
                shoeInfo += String.format(Locale.ROOT, " (true %+.1f)", table.trueCount(countingSystem));
            }
        }
        shoeInfoLabel.setText(shoeInfo);

        if (phase == Phase.BETTING) {
            tablePane.clear();
        } else {
            tablePane.show(table.dealer(), player.hands(),
                    phase == Phase.PLAYER_TURN ? table.activeHandIndex() : -1, this::handStatusText);
        }
        renderBetStack();
        renderMessage();

        boolean betting = phase == Phase.BETTING;
        boolean playerTurn = phase == Phase.PLAYER_TURN;
        boolean roundOver = phase == Phase.ROUND_OVER;
        boolean sessionOver = phase == Phase.GAME_OVER || phase == Phase.CASHED_OUT;

        long betTotal = currentBetTotal();
        for (long denomination : ChipView.DENOMINATIONS) {
            boolean wouldExceedBankroll = betTotal + denomination > player.bankroll();
            chipButtonsByDenomination.get(denomination).setDisable(!betting || wouldExceedBankroll);
        }
        clearBetButton.setDisable(!betting || placedChips.isEmpty());
        dealButton.setDisable(!betting || betTotal <= 0);
        betErrorLabel.setVisible(betting);
        // Once dealt, each hand shows its own chip stack right under its cards — the pre-deal
        // pile in the controls area would just be a confusing, stale duplicate of that.
        show(betStackPane, betting);
        show(betTotalLabel, betting);

        hitButton.setDisable(!playerTurn);
        standButton.setDisable(!playerTurn);
        doubleButton.setDisable(!(playerTurn && table.canDouble()));
        splitButton.setDisable(!(playerTurn && table.canSplit()));

        nextRoundButton.setDisable(!roundOver);
        nextRoundButton.setVisible(roundOver);
        leaveTableButton.setDisable(!roundOver);
        leaveTableButton.setVisible(roundOver);
        newGameButton.setVisible(sessionOver);
        newGameButton.setDisable(!sessionOver);

        growToFitContent();
        tablePane.dealNewCards(); // after growToFitContent(), so the deal is measured once the window has grown
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
        if (phase == Phase.CASHED_OUT) {
            long net = player.bankroll() - startingBankroll;
            String result = net > 0 ? "up " + net : net < 0 ? "down " + -net : "breaking even";
            tablePane.setMessage("Cashed out with " + player.bankroll() + " chips, " + result
                    + ". Thanks for playing, " + player.name() + ".");
        } else if (phase == Phase.GAME_OVER) {
            // GAME_OVER now also covers a non-zero bankroll too small to bet, so don't claim
            // "out of chips" when there are visibly a few left on the status line.
            tablePane.setMessage(player.bankroll() > 0
                    ? "Only " + player.bankroll() + " chips left — under the " + MINIMUM_BET
                            + "-chip minimum. Thanks for playing, " + player.name() + "."
                    : "Out of chips — thanks for playing, " + player.name() + ".");
        } else if (phase == Phase.ROUND_OVER) {
            tablePane.setMessage(summarizeRound());
        } else if (phase == Phase.PLAYER_TURN) {
            tablePane.setMessage("Your move" + insuranceNote());
        } else if (phase == Phase.AWAITING_INSURANCE) {
            tablePane.setMessage("Insurance?");
        } else if (!lastBetChips.isEmpty() && placedChips.equals(lastBetChips)) {
            tablePane.setMessage("Same bet as last round — Deal, or change it");
        } else {
            tablePane.setMessage("Place your bet");
        }
    }

    private String summarizeRound() {
        boolean anyWin = lastSettlements.values().stream()
                .anyMatch(s -> s.outcome() == RoundOutcome.WIN || s.outcome() == RoundOutcome.BLACKJACK_WIN);
        boolean anyLoss = lastSettlements.values().stream()
                .anyMatch(s -> s.outcome() == RoundOutcome.LOSS || s.outcome() == RoundOutcome.BUST);
        String summary;
        if (anyWin && !anyLoss) summary = "You win!";
        else if (anyLoss && !anyWin) summary = "Dealer wins";
        else if (!anyWin) summary = "Push";
        else summary = "Round over";

        return summary + insuranceNote();
    }

    /**
     * The side bet's result when insurance was taken this round, else "". The engine settles it
     * at the dealer peek, so by the player's turn it has already been lost.
     */
    private String insuranceNote() {
        return table.lastInsuranceSettlement()
                .filter(ins -> ins.amountWagered() > 0)
                .map(ins -> ins.won()
                        ? " — Insurance paid +" + (ins.payout() - ins.amountWagered())
                        : " — Insurance lost -" + ins.amountWagered())
                .orElse("");
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
