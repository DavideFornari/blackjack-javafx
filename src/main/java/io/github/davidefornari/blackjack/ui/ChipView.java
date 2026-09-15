package io.github.davidefornari.blackjack.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single casino chip, rendered from real chip photos (cropped from
 * {@code docs/chip-reference.png}) rather than drawn in CSS — used for both the
 * clickable chip rail and every stacked bet pile on the table.
 *
 * <p>Denominations map onto the five available chip colors using the classic
 * real-world convention (ascending value = white, red, blue, green, black).
 */
public final class ChipView extends ImageView {

    public static final double DIAMETER = 36;

    private static final Map<Long, Image> IMAGES = loadImages();

    public ChipView(long denomination) {
        super(imageFor(denomination));
        setFitWidth(DIAMETER);
        setFitHeight(DIAMETER);
        setPreserveRatio(true);
        setSmooth(true);
        getStyleClass().add("chip");
    }

    public static Image imageFor(long denomination) {
        Image image = IMAGES.get(denomination);
        if (image == null) {
            throw new IllegalArgumentException("No chip image for denomination " + denomination);
        }
        return image;
    }

    /** Greedily breaks a wager down into the largest available chip denominations, for rendering a stack that represents an amount rather than a literal click history (e.g. after a split or double, where the wager is a number, not a list of clicks). */
    public static List<Long> breakdown(long amount) {
        List<Long> denominations = new ArrayList<>(IMAGES.keySet());
        denominations.sort((a, b) -> Long.compare(b, a));

        List<Long> chips = new ArrayList<>();
        long remaining = amount;
        for (long denomination : denominations) {
            while (remaining >= denomination) {
                chips.add(denomination);
                remaining -= denomination;
            }
        }
        return chips;
    }

    /** A ready-to-display stack of chips for {@code amount}: minimal chip count, biggest denomination at the bottom. */
    public static List<ChipView> stack(long amount) {
        List<Long> denominations = breakdown(amount);
        List<ChipView> chips = new ArrayList<>();
        for (int i = 0; i < denominations.size(); i++) {
            ChipView chip = new ChipView(denominations.get(i));
            chip.setTranslateY(-i * 5);
            chips.add(chip);
        }
        return chips;
    }

    private static Map<Long, Image> loadImages() {
        Map<Long, Image> map = new LinkedHashMap<>();
        map.put(5L, load("white"));
        map.put(10L, load("red"));
        map.put(25L, load("blue"));
        map.put(50L, load("green"));
        map.put(100L, load("black"));
        return map;
    }

    private static Image load(String colorName) {
        String path = "/io/github/davidefornari/blackjack/ui/chips/" + colorName + ".png";
        return new Image(ChipView.class.getResourceAsStream(path));
    }
}
