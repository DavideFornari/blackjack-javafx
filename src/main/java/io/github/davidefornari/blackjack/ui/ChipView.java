package io.github.davidefornari.blackjack.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /** Every chip value, smallest first. */
    public static final List<Long> DENOMINATIONS = List.copyOf(IMAGES.keySet());

    public ChipView(long denomination, double diameter) {
        super(Objects.requireNonNull(IMAGES.get(denomination), "No chip image for denomination " + denomination));
        setFitWidth(diameter);
        setFitHeight(diameter);
        setPreserveRatio(true);
        setSmooth(true);
    }

    /**
     * A ready-to-display stack of chips for {@code amount}: the fewest chips, biggest at the
     * bottom. It represents an amount, not the click history (after a split or double the
     * wager is just a number).
     */
    public static List<ChipView> stack(long amount) {
        List<ChipView> chips = new ArrayList<>();
        long remaining = amount;
        for (long denomination : DENOMINATIONS.reversed()) {
            for (; remaining >= denomination; remaining -= denomination) {
                ChipView chip = new ChipView(denomination, DIAMETER);
                chip.getStyleClass().add("chip"); // the drop shadow is a stack effect; the chip rail has none
                chip.setTranslateY(-chips.size() * 5);
                chips.add(chip);
            }
        }
        // ponytail: a remainder under the smallest chip isn't drawn. Every wager is a sum of
        // chips today; render it as a label if arbitrary amounts ever reach a stack.
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
        return new Image(Objects.requireNonNull(ChipView.class.getResourceAsStream(path), "Missing resource " + path));
    }
}
