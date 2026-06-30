package com.aozainkmc.input.scoring;

public enum TalismanGrade {
    EXQUISITE("极品", 0.95),
    FINE("良品", 0.75),
    INFERIOR("劣质", 0.50),
    WASTE("废符", 0.00);

    private final String display;
    private final double threshold;

    TalismanGrade(String display, double threshold) {
        this.display = display;
        this.threshold = threshold;
    }

    public String display() {
        return display;
    }

    public static TalismanGrade of(double composite) {
        if (composite >= EXQUISITE.threshold) return EXQUISITE;
        if (composite >= FINE.threshold) return FINE;
        if (composite >= INFERIOR.threshold) return INFERIOR;
        return WASTE;
    }

    public static TalismanGrade byName(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
