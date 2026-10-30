package com.mcbesiege.physics;

import java.util.ArrayList;
import java.util.List;

/** Contact data: one manifold per collider pair, each point with its own normal (normal points from A to B). */
public final class Contact {
    private Contact() {}

    public static final class Point {
        public V3 point;
        public V3 normal;
        /** Penetration depth; negative values are speculative gaps. */
        public double depth;
        V3 localA;
        V3 rA = V3.ZERO, rB = V3.ZERO, t1 = V3.ZERO, t2 = V3.ZERO;
        double normalMass, t1Mass, t2Mass;
        double normalImpulse, t1Impulse, t2Impulse;
        public double relVelN0;
        public boolean isNew = true;
        /** Sum of normal impulses applied over the current tick. */
        public double tickImpulse;

        public Point(V3 point, V3 normal, double depth) {
            this.point = point;
            this.normal = normal;
            this.depth = depth;
        }
    }

    public static final class Manifold {
        public RigidBody a, b;
        public Collider ca, cb;
        public StaticBox sb;
        public double friction, restitution;
        public final List<Point> pts = new ArrayList<>(4);
        long key;
        /** Set by the listener to make the contact non-solid (e.g. the world block was destroyed). */
        public boolean disabled;

        public void add(V3 point, V3 normal, double depth) {
            for (Point p : pts) {
                if (p.point.distSq(point) < 1e-4 && p.normal.dot(normal) > 0.95) {
                    if (depth > p.depth) {
                        p.depth = depth;
                        p.point = point;
                    }
                    return;
                }
            }
            pts.add(new Point(point, normal, depth));
        }
    }

    static long pairKey(int a, int b) {
        return ((long) a << 32) ^ (b & 0xffffffffL);
    }
}
