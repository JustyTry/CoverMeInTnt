package com.midrago.armor;

import java.util.Locale;

public enum ArmorPiece {
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
