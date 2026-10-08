package io.github.davidefornari.blackjack.ui;

import io.github.davidefornari.blackjack.engine.Card;
import io.github.davidefornari.blackjack.engine.Dealer;
import io.github.davidefornari.blackjack.engine.Hand;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The felt's centre column: the dealer's hand with the deck beside it, the message line, and
 * the player's hands. Shows the engine's cards and deals each new one off the deck. Every card
 * keeps one {@link CardView} for the whole round, so a card in flight keeps flying across a
 * refresh.
 */
public final class TablePane extends VBox {

    private static final int DECK_CARDS = 5;
    private static final double DECK_STEP = 3;
    private static final int DEAL_STAGGER_MS = 150;

    private final HandPane dealerPane = new HandPane();
    private final Pane deckPane = new Pane();
    private final Label messageLabel = new Label();
    private final HBox playerHandsBox = new HBox(24);

    /**
     * Every card on the table this round and its view. By instance, not value: a 6-deck shoe
     * holds equal copies, and every rebuild deals fresh Card objects.
     */
    private final Map<Card, CardView> cardViews = new IdentityHashMap<>();
    /** The dealer's face-down card while it is down; null before the deal. */
    private CardView holeCardView;
    /** Views new to the table since the last {@link #dealNewCards()}. */
    private final List<Deal> pendingDeals = new ArrayList<>();

    /** A card arriving on the table: where it sits in its hand, and whether it comes off the deck. */
    private record Deal(CardView view, int position, boolean dealer, boolean fromDeck) {
    }

    public TablePane() {
        super(24);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(10, 20, 10, 20));
        messageLabel.getStyleClass().add("message-label");
        playerHandsBox.setAlignment(Pos.CENTER);
        dealerPane.setCaption("Dealer");
        getChildren().addAll(buildDealerRow(), messageLabel, playerHandsBox);
    }

    /**
     * The dealer's hand centred on the table, with the deck (a few face-down cards, each a step
     * up and right of the one below) on its right. An empty spacer as wide as the deck sits on
     * the left, so the dealer stays centred and a long dealer hand pushes the window wider
     * instead of sliding under the deck.
     */
    private HBox buildDealerRow() {
        for (int i = 0; i < DECK_CARDS; i++) {
            CardView back = CardView.faceDown();
            back.relocate(i * DECK_STEP, (DECK_CARDS - 1 - i) * DECK_STEP);
            deckPane.getChildren().add(back);
        }
        double deckWidth = CardView.WIDTH + (DECK_CARDS - 1) * DECK_STEP;
        double deckHeight = CardView.HEIGHT + (DECK_CARDS - 1) * DECK_STEP;
        deckPane.setMinSize(deckWidth, deckHeight);
        deckPane.setMaxSize(deckWidth, deckHeight);

        Region balance = new Region();
        balance.setMinWidth(deckWidth);
        Region leftGap = new Region();
        Region rightGap = new Region();
        HBox.setHgrow(leftGap, Priority.ALWAYS);
        HBox.setHgrow(rightGap, Priority.ALWAYS);

        // Drawn first (behind the dealer's hand), so dealt cards leave over the top of the deck.
        deckPane.setViewOrder(1);
        HBox row = new HBox(16, balance, leftGap, dealerPane, rightGap, deckPane);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    public void setMessage(String text) {
        messageLabel.setText(text);
    }

    /**
     * Empties the table between rounds, so every card dealt next is new. The engine still holds
     * last round's hands until the next deal, so they're hidden rather than shown.
     */
    public void clear() {
        cardViews.clear();
        holeCardView = null;
        dealerPane.setCards(List.of());
        dealerPane.setTotalText("");
        playerHandsBox.getChildren().clear();
    }

    /**
     * Shows the round in play. {@code activeHand} is the index of the hand being played, or -1
     * when none is; {@code statusText} gives each player hand's total or result line.
     */
    public void show(Dealer dealer, List<Hand> hands, int activeHand, Function<Hand, String> statusText) {
        List<Card> dealerCards = dealer.hand().cards();
        List<CardView> views = new ArrayList<>();
        for (int i = 0; i < dealerCards.size(); i++) {
            boolean hidden = i == 1 && !dealer.isHoleCardRevealed();
            if (hidden && holeCardView == null) {
                holeCardView = CardView.faceDown();
                pendingDeals.add(new Deal(holeCardView, i, true, true));
            }
            // A revealed hole card turns over in place; every other new card comes off the deck.
            views.add(hidden ? holeCardView : viewFor(dealerCards.get(i), i, true, i != 1 || holeCardView == null));
        }
        dealerPane.setCards(views);
        dealerPane.setTotalText(dealer.isHoleCardRevealed() ? "Total: " + dealer.hand().total() : "");

        playerHandsBox.getChildren().clear();
        for (int i = 0; i < hands.size(); i++) {
            Hand hand = hands.get(i);
            HandPane pane = new HandPane();
            pane.setCaption(hands.size() > 1 ? "Hand " + (i + 1) : "Your Hand");

            List<CardView> handViews = new ArrayList<>();
            List<Card> cards = hand.cards();
            for (int j = 0; j < cards.size(); j++) {
                handViews.add(viewFor(cards.get(j), j, false, true));
            }
            pane.setCards(handViews);
            pane.setWager(hand.wager());
            pane.setTotalText(statusText.apply(hand));
            pane.setActive(i == activeHand);
            playerHandsBox.getChildren().add(pane);
        }
    }

    /** The face-up view of {@code card}: its existing one if already on the table, else a new one queued to be dealt. */
    private CardView viewFor(Card card, int position, boolean dealer, boolean fromDeck) {
        CardView view = cardViews.get(card);
        if (view == null) {
            view = CardView.faceUp(card);
            cardViews.put(card, view);
            pendingDeals.add(new Deal(view, position, dealer, fromDeck));
        }
        return view;
    }

    /**
     * Flies the cards new since the last call off the deck into their slots, one after another.
     * Each card is already laid out in its slot; it starts offset (translate) onto the deck's
     * top card and eases back to 0, so it lands in its slot even if the layout moves mid-flight
     * (window growth, a split). Only a revealed hole card fades in place instead. Call it after
     * anything that resizes the window, so the deal is measured once the window has grown.
     */
    public void dealNewCards() {
        if (pendingDeals.isEmpty()) {
            return;
        }
        // The table deals player, dealer, player, dealer: by position in hand, player first.
        List<Deal> deals = new ArrayList<>(pendingDeals);
        pendingDeals.clear();
        deals.sort(Comparator.comparingInt(Deal::position).thenComparing(Deal::dealer));
        deals.forEach(deal -> deal.view().setOpacity(0));

        Platform.runLater(() -> { // positions exist only after the layout pass that follows a refresh
            Scene scene = getScene();
            if (scene != null) {
                scene.getRoot().applyCss();
                scene.getRoot().layout();
            }
            Point2D deckTop = deckPane.getChildren().get(DECK_CARDS - 1).localToScene(0, 0);
            for (int i = 0; i < deals.size(); i++) {
                CardView view = deals.get(i).view();
                FadeTransition fade = new FadeTransition(Duration.millis(120), view);
                fade.setToValue(1);
                ParallelTransition deal = new ParallelTransition(view, fade);
                if (deals.get(i).fromDeck() && view.getParent() != null) {
                    Point2D start = view.getParent().sceneToLocal(deckTop);
                    view.setTranslateX(start.getX() - view.getLayoutX());
                    view.setTranslateY(start.getY() - view.getLayoutY());
                    TranslateTransition fly = new TranslateTransition(Duration.millis(300), view);
                    fly.setToX(0);
                    fly.setToY(0);
                    deal.getChildren().add(fly);
                }
                deal.setDelay(Duration.millis(i * DEAL_STAGGER_MS));
                deal.play();
            }
        });
    }
}
