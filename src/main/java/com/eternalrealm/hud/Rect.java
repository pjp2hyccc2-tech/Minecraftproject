package com.eternalrealm.hud;

/** An axis-aligned rectangle in GUI pixels; empty rectangles take no space and never overlap anything. */
public record Rect(int x, int y, int width, int height) {
    public static final Rect NONE = new Rect(0, 0, 0, 0);

    public Rect {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("negative size: " + width + "x" + height);
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    public boolean intersects(Rect other) {
        return !isEmpty() && !other.isEmpty() && x < other.right() && other.x < right() && y < other.bottom()
                && other.y < bottom();
    }

    /** Whether the rectangle shares rows with {@code [top, bottom)}. */
    public boolean spansRows(int top, int bottom) {
        return !isEmpty() && y < bottom && top < bottom();
    }

    public boolean within(int screenWidth, int screenHeight) {
        return x >= 0 && y >= 0 && right() <= screenWidth && bottom() <= screenHeight;
    }
}
