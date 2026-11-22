package jp.archiquest.analysis;

/** 敷地（2つの角で指定した長方形） */
public record Site(int x1, int z1, int x2, int z2) {
    public int minX() {
        return Math.min(x1, x2);
    }

    public int maxX() {
        return Math.max(x1, x2);
    }

    public int minZ() {
        return Math.min(z1, z2);
    }

    public int maxZ() {
        return Math.max(z1, z2);
    }

    public int width() {
        return maxX() - minX() + 1;
    }

    public int depth() {
        return maxZ() - minZ() + 1;
    }

    public int area() {
        return width() * depth();
    }

    public boolean contains(int x, int z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }
}
