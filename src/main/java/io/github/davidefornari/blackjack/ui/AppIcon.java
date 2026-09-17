package io.github.davidefornari.blackjack.ui;

import javafx.scene.image.Image;

/**
 * Loads the window/taskbar icon: an Ace and a Jack of spades. Unlike the table's cards
 * (deliberately styled shapes, no image assets — see CLAUDE.md), this one is a real
 * image, cropped and made transparent from {@code docs/icon-reference.png} (kept in the
 * repo for provenance, watermark included) via a one-off Pillow script that wasn't
 * checked in — {@code app-icon.png} is the processed, transparent-background build
 * artifact actually loaded here, the same pattern {@code ChipView} uses for the chip
 * photos.
 */
final class AppIcon {

    private AppIcon() {
    }

    static Image load() {
        return new Image(AppIcon.class.getResourceAsStream(
                "/io/github/davidefornari/blackjack/ui/app-icon.png"));
    }
}
