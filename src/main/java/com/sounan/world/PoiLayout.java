package com.sounan.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact positions of every interactive / scripted thing of the mountain, derived once from pois.json and the terrain.
 * {@link PoiBuilder} builds from these, the game logic and the AI read them.
 */
public final class PoiLayout {
    public enum HouseKind { PLAIN, RADIO, BROKEN, OMIKI, COLLAPSED }

    public record House(int x1, int z1, int x2, int z2, int floorY, Direction door, HouseKind kind) {
        public int cx() {
            return (x1 + x2) / 2;
        }

        public int cz() {
            return (z1 + z2) / 2;
        }

        public boolean inside(double x, double y, double z) {
            return x >= x1 && x < x2 + 1 && z >= z1 && z < z2 + 1 && y >= floorY && y < floorY + 5;
        }
    }

    private static volatile PoiLayout instance;

    public final BlockPos start;
    public final Direction startFacing;
    public final BlockPos bag;
    public final BlockPos ropeAnchor;
    public final Direction ropeFacing;
    /** Column of the climbing rope below the anchor and its top / bottom Y. */
    public final int ropeX, ropeZ, ropeTopY, ropeBottomY;
    public final List<House> houses;
    public final BlockPos omiki;
    public final BlockPos villageSleeper;
    public final Direction villageSleeperFacing;
    public final BlockPos busStop;
    public final BlockPos publicPhone;
    public final BlockPos noticeBoard;
    public final BlockPos watchBell;
    public final BlockPos hokora;
    public final Direction hokoraFacing;
    public final int shrineY;
    public final int bridgeX, bridgeZ0, bridgeZ1, bridgeY;
    public final House hut;
    public final BlockPos hutDoor;
    public final BlockPos hutKeyHook;
    public final BlockPos hutLogbook;
    public final BlockPos hutSleeper;
    public final Direction hutSleeperFacing;
    public final BlockPos hutCampfire;
    public final BlockPos pylon;
    public final int tunnelX, tunnelZ0, tunnelZ1, tunnelRoadN, tunnelRoadS;
    public final BlockPos gate;
    public final List<BlockPos> tunnelLamps;
    public final BlockPos tunnelMemo;
    public final BlockPos carDoor;
    public final BlockPos memorial;
    public final BlockPos streetLamp;
    public final Map<Long, Integer> memoIds;
    public final List<BlockPos> jizo;

    private PoiLayout(Pois p, TerrainData t) {
        Pois.Point s = p.point("start");
        start = s.pos();
        startFacing = s.facing();
        bag = p.point("bag").pos();

        // rope: from the stake step downstream until the ground drops away; the rope hangs on that cliff face
        Pois.Point ra = p.point("rope_anchor");
        ropeAnchor = ra.pos();
        Direction rf = Math.abs(p.cliffNx) > Math.abs(p.cliffNz) ? (p.cliffNx > 0 ? Direction.EAST : Direction.WEST)
                : (p.cliffNz > 0 ? Direction.SOUTH : Direction.NORTH);
        ropeFacing = rf;
        int rx = ra.x(), rz = ra.z();
        int topH = t.height(rx, rz);
        int bx = rx, bz = rz;
        for (int k = 1; k < 8; k++) {
            int cx = rx + rf.getStepX() * k, cz = rz + rf.getStepZ() * k;
            if (t.height(cx, cz) <= topH - 5) {
                bx = cx;
                bz = cz;
                break;
            }
        }
        ropeX = bx;
        ropeZ = bz;
        ropeTopY = topH;
        ropeBottomY = t.height(bx, bz) + 1;

        // village houses: (x1, z1, x2, z2, door, kind)
        List<House> hs = new ArrayList<>();
        hs.add(house(t, 8, -5, 14, 0, Direction.SOUTH, HouseKind.PLAIN));
        hs.add(house(t, 20, -6, 26, 0, Direction.SOUTH, HouseKind.RADIO));
        hs.add(house(t, 36, -6, 43, 0, Direction.SOUTH, HouseKind.BROKEN));
        hs.add(house(t, 18, 12, 25, 18, Direction.NORTH, HouseKind.OMIKI));
        hs.add(house(t, 40, 12, 47, 17, Direction.NORTH, HouseKind.COLLAPSED));
        houses = Collections.unmodifiableList(hs);
        House om = hs.get(3);
        omiki = new BlockPos(om.x1() + 1, om.floorY() + 2, om.z2() - 1);
        villageSleeper = new BlockPos(om.x2() - 2, om.floorY() + 1, om.z2() - 1);
        villageSleeperFacing = Direction.WEST;
        busStop = new BlockPos(4, t.height(4, 9) + 1, 9);
        publicPhone = new BlockPos(11, t.height(11, 9) + 1, 9);
        noticeBoard = new BlockPos(28, t.height(28, 9) + 1, 9);
        int wy = t.height(51, -1) + 1;
        watchBell = new BlockPos(51, wy + 11, -1);   // hangs under the roof at wy + 12, platform at wy + 10

        Pois.Point sh = p.point("shrine");
        shrineY = t.height(sh.x(), sh.z()) + 1;
        hokora = new BlockPos(sh.x(), shrineY, sh.z());
        hokoraFacing = sh.facing();

        Pois.Point bn = p.point("bridge_n"), bs = p.point("bridge_s");
        bridgeX = bn.x();
        bridgeZ0 = bn.z();
        bridgeZ1 = bs.z();
        bridgeY = bn.intValue("deck", bn.y());

        Pois.Point hp = p.point("hut");
        hut = house(t, hp.x() - 4, hp.z() - 3, hp.x() + 4, hp.z() + 3, Direction.EAST, HouseKind.PLAIN);
        hutDoor = new BlockPos(hut.x2(), hut.floorY() + 1, hut.cz());
        hutKeyHook = new BlockPos(hut.cx() + 1, hut.floorY() + 2, hut.z1() + 1);
        hutLogbook = new BlockPos(hut.x2() - 2, hut.floorY() + 2, hut.z2() - 1);
        hutSleeper = new BlockPos(hut.x1() + 1, hut.floorY() + 1, hut.z2() - 1);
        hutSleeperFacing = Direction.EAST;
        hutCampfire = new BlockPos(hut.x2() + 3, t.height(hut.x2() + 3, hut.cz()) + 1, hut.cz());

        Pois.Point py = p.point("pylon");
        pylon = new BlockPos(py.x(), t.height(py.x(), py.z()) + 1, py.z());

        tunnelX = (int) p.tunnelX;
        tunnelZ0 = (int) p.tunnelZ0;
        tunnelZ1 = (int) p.tunnelZ1;
        tunnelRoadN = p.point("tunnel_n").intValue("road", 114);
        tunnelRoadS = p.point("tunnel_s").intValue("road", 108);
        gate = new BlockPos(tunnelX, tunnelRoadN + 1, tunnelZ0 + 1);
        List<BlockPos> lamps = new ArrayList<>();
        int i = 0;
        for (int z = tunnelZ0 + 6; z < tunnelZ1 - 3; z += 8, i++) {
            boolean west = i % 2 == 0;
            lamps.add(new BlockPos(west ? tunnelX - 3 : tunnelX + 3, roadY(z) + 5, z));
        }
        tunnelLamps = Collections.unmodifiableList(lamps);
        int mz = (tunnelZ0 + tunnelZ1) / 2 + 9;
        tunnelMemo = new BlockPos(tunnelX - 2, roadY(mz) + 1, mz);

        Pois.Point car = p.point("car");
        carDoor = new BlockPos(car.x(), car.y() + 1, car.z() + 1);
        memorial = new BlockPos(155, t.height(155, 235) + 1, 235);
        streetLamp = new BlockPos(158, t.height(158, 245) + 1, 245);

        Map<Long, Integer> ids = new HashMap<>();
        for (Pois.Point m : p.memos) ids.put(new BlockPos(m.x(), m.y(), m.z()).asLong(), m.intValue("note", 1));
        House radio = hs.get(1), collapsed = hs.get(4);
        ids.put(new BlockPos(radio.x1() + 2, radio.floorY() + 1, radio.z1() + 2).asLong(), 7);
        ids.put(new BlockPos(collapsed.x1() + 2, collapsed.floorY() + 1, collapsed.z2() - 2).asLong(), 8);
        ids.put(tunnelMemo.asLong(), 12);
        memoIds = Collections.unmodifiableMap(ids);

        List<BlockPos> jz = new ArrayList<>();
        for (Pois.Point j : p.jizo) jz.add(j.pos());
        jizo = Collections.unmodifiableList(jz);
    }

    /** Road surface Y inside the tunnel (gentle slope from the north portal down to the south portal). */
    public int roadY(int z) {
        double f = (z - tunnelZ0) / (double) Math.max(1, tunnelZ1 - tunnelZ0);
        f = Math.max(0, Math.min(1, f));
        return (int) Math.round(tunnelRoadN + (tunnelRoadS - tunnelRoadN) * f);
    }

    public boolean inTunnel(double x, double y, double z) {
        return Math.abs(x - (tunnelX + 0.5)) <= 4.0 && z >= tunnelZ0 && z <= tunnelZ1 + 1 && y < roadY((int) Math.floor(z)) + 8;
    }

    private static House house(TerrainData t, int x1, int z1, int x2, int z2, Direction door, HouseKind kind) {
        int max = 0;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) max = Math.max(max, t.height(x, z));
        return new House(x1, z1, x2, z2, max + 1, door, kind);
    }

    public static PoiLayout get() {
        PoiLayout l = instance;
        if (l == null) {
            synchronized (PoiLayout.class) {
                l = instance;
                if (l == null) {
                    l = new PoiLayout(Pois.get(), TerrainData.get());
                    instance = l;
                }
            }
        }
        return l;
    }

    public int memoId(BlockPos pos) {
        Integer id = memoIds.get(pos.asLong());
        if (id != null) return id;
        // tolerate a block of drift (memo blocks are decals placed on the surface)
        for (Map.Entry<Long, Integer> e : memoIds.entrySet()) {
            BlockPos m = BlockPos.of(e.getKey());
            if (m.distManhattan(pos) <= 2) return e.getValue();
        }
        return 1;
    }
}
