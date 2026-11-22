package jp.archiquest.analysis;

import jp.archiquest.challenge.Challenges;
import jp.archiquest.config.ArchiConfig;
import jp.archiquest.knowledge.Subject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 建物の診断。
 * <ol>
 *   <li>クリックしたブロックから、つながっている建物ブロックを探索（建築面積・高さ・材料）</li>
 *   <li>建物の範囲内で「囲まれた空気」を探して部屋を見つける（床面積・天井高・窓・ドア・照明）</li>
 *   <li>建築基準法などの基準と照らし合わせて指摘する</li>
 * </ol>
 */
public final class BuildingAnalyzer {
    public static final int MAX_BLOCKS = 40_000;
    public static final int MAX_HORIZONTAL = 48;
    public static final int MAX_UP = 100;
    public static final int MAX_DOWN = 40;
    public static final long MAX_ROOM_VOLUME = 700_000L;

    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};
    private static final int DIR_UP = 2;
    private static final Direction[] DIRS = Direction.values();

    private static final Map<UUID, BuildingReport> LAST = new ConcurrentHashMap<>();

    private BuildingAnalyzer() {}

    public static void forget(UUID id) {
        LAST.remove(id);
    }

    @Nullable
    public static BuildingReport last(UUID id) {
        return LAST.get(id);
    }

    /** 診断してプレイヤーにレポートを送る */
    public static void inspect(ServerPlayer p, ServerLevel level, BlockPos start, @Nullable Site site) {
        long t0 = System.nanoTime();
        BuildingReport r = analyze(level, start, site);
        r.millis = (System.nanoTime() - t0) / 1_000_000L;
        r.send(p);
        if (r.error == null) {
            LAST.put(p.getUUID(), r);
            spawnRoomParticles(level, r);
            Challenges.onReport(p, r);
        }
    }

    public static BuildingReport analyze(ServerLevel level, BlockPos start, @Nullable Site site) {
        BuildingReport r = new BuildingReport();
        r.site = site;
        BlockState s0 = level.getBlockState(start);
        if (!Terrain.isStructural(s0)) {
            r.error = "そこは建物のブロックじゃないみたい。壁や床など、建物の一部をクリックしてね。";
            return r;
        }

        // ---- 1) 建物ブロックの探索
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<Long> seen = new HashSet<>();
        List<BlockPos> blocks = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        queue.add(start);
        seen.add(start.asLong());
        blocks.add(start);
        states.add(s0);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction d : DIRS) {
                BlockPos n = cur.relative(d);
                boolean outOfRange = Math.abs(n.getX() - start.getX()) > MAX_HORIZONTAL
                        || Math.abs(n.getZ() - start.getZ()) > MAX_HORIZONTAL
                        || n.getY() - start.getY() > MAX_UP || start.getY() - n.getY() > MAX_DOWN;
                if (!seen.add(n.asLong())) continue;
                if (!level.isLoaded(n)) {
                    r.truncated = true;
                    continue;
                }
                BlockState st = level.getBlockState(n);
                if (!Terrain.isStructural(st)) continue;
                if (outOfRange || blocks.size() >= MAX_BLOCKS) {
                    r.truncated = true;
                    continue;
                }
                blocks.add(n);
                states.add(st);
                queue.add(n);
            }
        }

        // ---- 2) 集計
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Map<Long, Integer> colBottom = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos b = blocks.get(i);
            BlockState st = states.get(i);
            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            minZ = Math.min(minZ, b.getZ());
            maxX = Math.max(maxX, b.getX());
            maxY = Math.max(maxY, b.getY());
            maxZ = Math.max(maxZ, b.getZ());
            r.materials.merge(MaterialKind.classify(st), 1, Integer::sum);
            if (Terrain.isGlass(st)) r.glass++;
            if (st.is(BlockTags.STAIRS)) r.stairs++;
            if (st.is(Blocks.LIGHTNING_ROD)) r.lightningRods++;
            if (Terrain.isOpening(st)) r.openings++;
            colBottom.merge(colKey(b.getX(), b.getZ()), b.getY(), Math::min);
        }
        r.blockCount = blocks.size();
        r.footprint = colBottom.size();
        r.minY = minY;
        r.maxY = maxY;

        // 地盤面：建物の柱（列）の一番下のブロックの直下が地面になっている高さの中央値
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        List<Integer> grounded = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : colBottom.entrySet()) {
            long key = e.getKey();
            mp.set(colX(key), e.getValue() - 1, colZ(key));
            if (level.isLoaded(mp) && Terrain.isGround(level.getBlockState(mp))) grounded.add(e.getValue());
        }
        if (grounded.isEmpty()) {
            r.groundY = minY;
        } else {
            Collections.sort(grounded);
            r.groundY = grounded.get(grounded.size() / 2);
        }
        r.height = Math.max(1, maxY + 1 - r.groundY);

        if (site != null) {
            int outside = 0;
            for (long key : colBottom.keySet()) if (!site.contains(colX(key), colZ(key))) outside++;
            r.outsideSite = outside;
        }

        MaterialKind main = MaterialKind.OTHER;
        int best = 0;
        for (MaterialKind k : new MaterialKind[]{MaterialKind.WOOD, MaterialKind.CONCRETE, MaterialKind.STEEL, MaterialKind.MASONRY}) {
            int c = r.materials.getOrDefault(k, 0);
            if (c > best) {
                best = c;
                main = k;
            }
        }
        r.main = main;

        // ---- 3) 部屋
        long vol = (long) (maxX - minX + 3) * (maxY - minY + 3) * (maxZ - minZ + 3);
        if (vol <= MAX_ROOM_VOLUME) {
            assignStories(r, findRooms(level, minX, minY, minZ, maxX, maxY, maxZ));
        } else {
            r.roomsSkipped = true;
        }

        // ---- 4) 指摘
        computeFindings(r);
        return r;
    }

    private static long colKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int colX(long key) {
        return (int) (key >> 32);
    }

    private static int colZ(long key) {
        return (int) key;
    }

    // ------------------------------------------------------------------ rooms

    private static List<RoomInfo> findRooms(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        final int ex0 = minX - 1, ey0 = minY - 1, ez0 = minZ - 1;
        final int sx = maxX - minX + 3, sy = maxY - minY + 3, sz = maxZ - minZ + 3;
        final int layer = sx * sz;
        final int vol = layer * sy;
        BitSet roomCell = new BitSet(vol);
        BitSet solid = new BitSet(vol);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        IntList queue = new IntList();
        List<RoomInfo> rooms = new ArrayList<>();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int idx = ((y - ey0) * sz + (z - ez0)) * sx + (x - ex0);
                    if (roomCell.get(idx) || solid.get(idx)) continue;
                    mp.set(x, y, z);
                    BlockState st = level.getBlockState(mp);
                    if (!Terrain.passable(level, mp, st)) {
                        solid.set(idx);
                        continue;
                    }

                    // 空気のかたまりを塗りつぶし
                    Region reg = new Region();
                    roomCell.set(idx);
                    if (st.getLightEmission() > 0) reg.lightCells.add(idx);
                    queue.clear();
                    queue.add(idx);
                    int head = 0;
                    while (head < queue.size()) {
                        int c = queue.get(head++);
                        int cx = c % sx + ex0;
                        int cz = (c / sx) % sz + ez0;
                        int cy = c / layer + ey0;
                        if (!reg.escaped) reg.cells.add(c);
                        for (int dir = 0; dir < 6; dir++) {
                            int nx = cx + DX[dir], ny = cy + DY[dir], nz = cz + DZ[dir];
                            boolean inner = nx >= minX && nx <= maxX && ny >= minY && ny <= maxY && nz >= minZ && nz <= maxZ;
                            if (!inner) {
                                // 建物の外側に抜けられる → 屋外
                                if (!reg.escaped) {
                                    mp.set(nx, ny, nz);
                                    if (!level.isLoaded(mp) || Terrain.passable(level, mp, level.getBlockState(mp))) reg.escaped = true;
                                }
                                continue;
                            }
                            int n = ((ny - ey0) * sz + (nz - ez0)) * sx + (nx - ex0);
                            if (roomCell.get(n)) continue;
                            if (reg.escaped && solid.get(n)) continue;
                            mp.set(nx, ny, nz);
                            BlockState ns = level.getBlockState(mp);
                            if (Terrain.passable(level, mp, ns)) {
                                roomCell.set(n);
                                queue.add(n);
                                if (!reg.escaped && ns.getLightEmission() > 0) reg.lightCells.add(n);
                            } else {
                                solid.set(n);
                                if (!reg.escaped) reg.boundary(n, c, ns, dir);
                            }
                        }
                    }
                    if (!reg.escaped && reg.cells.size() >= 2) {
                        rooms.addAll(reg.toRooms(ex0, ey0, ez0, sx, sz, layer));
                    }
                }
            }
        }
        return rooms;
    }

    /** 階の割り当て（床の高さが2ブロック以内なら同じ階とみなす） */
    private static void assignStories(BuildingReport r, List<RoomInfo> rooms) {
        if (rooms.isEmpty()) return;
        TreeSet<Integer> levels = new TreeSet<>();
        for (RoomInfo ri : rooms) levels.add(ri.levelY());
        List<Integer> clusters = new ArrayList<>();
        for (int lv : levels) {
            if (clusters.isEmpty() || lv - clusters.get(clusters.size() - 1) > 2) clusters.add(lv);
        }
        List<Integer> above = new ArrayList<>();
        List<Integer> below = new ArrayList<>();
        for (int c : clusters) {
            if (c >= r.groundY - 1) above.add(c);
            else below.add(c);
        }
        int total = 0;
        for (RoomInfo ri : rooms) {
            int rep = clusters.get(0);
            for (int c : clusters) if (c <= ri.levelY()) rep = c;
            int story;
            int ai = above.indexOf(rep);
            if (ai >= 0) story = ai + 1;
            else story = -(below.size() - below.indexOf(rep));
            r.rooms.add(ri.withStory(story));
            total += ri.floorArea();
        }
        r.totalFloorArea = total;
        r.storiesAbove = above.size();
        r.storiesBelow = below.size();
        r.rooms.sort(Comparator.comparingInt(RoomInfo::story).thenComparing(Comparator.comparingInt(RoomInfo::floorArea).reversed()));
    }

    // ------------------------------------------------------------------ findings

    private static void computeFindings(BuildingReport r) {
        int stories = r.effectiveStories();
        List<BuildingReport.Finding> f = r.findings;

        if (r.height > 60) {
            f.add(new BuildingReport.Finding("高さ60m超の超高層！ 時刻歴応答解析などで安全を確かめ、国土交通大臣の認定が必要（法20条）",
                    Subject.STRUCTURE, "str_seismic"));
        }
        if (r.height > 31) {
            f.add(new BuildingReport.Finding("高さ31m超：非常用の昇降機（非常用エレベーター）が必要（法34条2項）", Subject.LAW, "law_stairs"));
        }
        if (r.height > 20) {
            f.add(new BuildingReport.Finding(r.lightningRods > 0
                    ? "高さ20m超は避雷設備が必要（法33条）→ 避雷針を確認したよ！"
                    : "高さ20m超は避雷設備が必要（法33条）。避雷針ブロックを付けよう！", Subject.LAW, "law_stairs"));
        }

        if (r.main == MaterialKind.WOOD) {
            if (stories >= 2 || r.totalFloorArea > 200) {
                f.add(new BuildingReport.Finding("木造2階建て以上（または平屋で延べ200m²超）は、2025年4月から「新2号建築物」。構造・省エネの審査が省略されなくなったよ",
                        Subject.LAW, "law_basic"));
            } else {
                f.add(new BuildingReport.Finding("木造平屋で延べ200m²以下は「新3号建築物」（審査省略制度の対象）。2025年4月の改正で対象が小さくなったよ",
                        Subject.LAW, "law_basic"));
            }
            if (r.totalFloorArea > 300) {
                f.add(new BuildingReport.Finding("木造で延べ300m²超：2025年4月から構造計算が必要な規模（以前は500m²超）", Subject.STRUCTURE, "str_steel_wood"));
            }
            if (r.height > 16 || stories >= 4) {
                f.add(new BuildingReport.Finding("木造で高さ16m超または階数4以上：原則として主要構造部に高い耐火性能が求められるよ（法21条）",
                        Subject.LAW, "law_fire"));
            }
        }

        boolean needsIkkyu = r.height > 16 || stories >= 4 || (r.totalFloorArea > 1000 && stories >= 2)
                || (r.main != MaterialKind.WOOD && r.main != MaterialKind.OTHER && r.totalFloorArea > 300);
        if (needsIkkyu) {
            f.add(new BuildingReport.Finding("この規模は一級建築士でないと設計・工事監理できないよ（建築士法3条）。まさに君の出番！", Subject.LAW, "law_fire"));
        }

        if (r.stairs > 0) {
            f.add(new BuildingReport.Finding("マイクラの階段は1段が蹴上げ50cm・踏面50cm。住宅の基準は蹴上げ23cm以下・踏面15cm以上（令23条）だから、現実ならかなりの急階段！",
                    Subject.LAW, "law_stairs"));
        }

        int lowCeil = 0, dark = 0, relaxed = 0, stuffy = 0, noExit = 0;
        for (RoomInfo ri : r.rooms) {
            if (!ri.habitable()) continue;
            if (!ri.ceilingOk()) lowCeil++;
            if (!ri.lightOk()) {
                if (ri.lightRelaxedOk()) relaxed++;
                else dark++;
            }
            if (!ri.ventOk()) stuffy++;
            if (!ri.hasExit()) noExit++;
        }
        if (lowCeil > 0) {
            f.add(new BuildingReport.Finding("天井高2.1m未満の居室が" + lowCeil + "室。マイクラの2マス天井は2.0mなので、3マスにすれば基準クリア！（令21条）",
                    Subject.LAW, "law_lighting"));
        }
        if (dark > 0) {
            f.add(new BuildingReport.Finding("採光が足りない居室が" + dark + "室。窓（ガラス）を増やすか、3倍で計算される天窓を使おう（法28条）",
                    Subject.LAW, "law_lighting"));
        }
        if (relaxed > 0) {
            f.add(new BuildingReport.Finding("照明のおかげで採光1/10の緩和でセーフな居室が" + relaxed + "室（令和5年4月施行の緩和）",
                    Subject.LAW, "law_lighting"));
        }
        if (stuffy > 0) {
            f.add(new BuildingReport.Finding("換気用の開口が床面積の1/20未満の居室が" + stuffy + "室。マイクラのガラスは開かないので、ドアやトラップドアで換気を確保しよう",
                    Subject.LAW, "law_lighting"));
        }
        if (noExit > 0) {
            f.add(new BuildingReport.Finding("出入口のない密室が" + noExit + "室！ 避難できない部屋は危ないよ", Subject.PLANNING, "law_stairs"));
        }

        if (r.site != null) {
            if (r.outsideSite > 0) {
                f.add(new BuildingReport.Finding("建物が敷地から" + r.outsideSite + "m²はみ出しているよ！ 敷地の中に収めよう", Subject.LAW, "law_zoning"));
            }
            int covLimit = ArchiConfig.COVERAGE_LIMIT.get();
            int farLimit = ArchiConfig.FAR_LIMIT.get();
            if (r.coverage() * 100 > covLimit + 1e-9) {
                f.add(new BuildingReport.Finding("建ぺい率オーバー（" + Math.round(r.coverage() * 100) + "% > " + covLimit + "%）。建物を小さくするか、2階建てにして面積を稼ごう",
                        Subject.LAW, "law_zoning"));
            }
            if (r.floorAreaRatio() * 100 > farLimit + 1e-9) {
                f.add(new BuildingReport.Finding("容積率オーバー（" + Math.round(r.floorAreaRatio() * 100) + "% > " + farLimit + "%）。延べ面積を減らそう",
                        Subject.LAW, "law_zoning"));
            }
        }
    }

    private static void spawnRoomParticles(ServerLevel level, BuildingReport r) {
        for (RoomInfo ri : r.rooms) {
            if (!ri.habitable()) continue;
            boolean ok = ri.allOk();
            level.sendParticles(ok ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER,
                    ri.cx(), ri.levelY() + 1.2, ri.cz(), ok ? 14 : 4, 0.8, 0.4, 0.8, 0.0);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** 1つの「囲まれた空気のかたまり」 */
    private static final class Region {
        boolean escaped;
        final IntList cells = new IntList();
        final IntList lightCells = new IntList();
        final IntList bedOwners = new IntList();
        final Map<Integer, Integer> windowOwner = new HashMap<>();
        final Map<Integer, Integer> skylightOwner = new HashMap<>();
        final Map<Integer, Integer> openingOwner = new HashMap<>();
        final Map<Integer, Integer> lightOwner = new HashMap<>();

        void boundary(int n, int c, BlockState ns, int dir) {
            if (Terrain.isGlass(ns)) {
                if (dir == DIR_UP) skylightOwner.putIfAbsent(n, c);
                else if (DY[dir] == 0) windowOwner.putIfAbsent(n, c);
            } else if (Terrain.isOpening(ns)) {
                openingOwner.putIfAbsent(n, c);
            }
            if (ns.is(BlockTags.BEDS)) bedOwners.add(c);
            if (ns.getLightEmission() > 0) lightOwner.putIfAbsent(n, c);
        }

        /** 床の高さごとに部屋を分ける（吹き抜けや階段でつながった2階建ても階ごとに分かれる） */
        List<RoomInfo> toRooms(int ex0, int ey0, int ez0, int sx, int sz, int layer) {
            int[] cs = cells.toSortedArray();
            BitSet in = new BitSet();
            for (int c : cs) in.set(c);
            Map<Integer, Integer> floorY = new HashMap<>(cs.length * 2);
            TreeMap<Integer, Integer> floorCount = new TreeMap<>();
            for (int c : cs) {
                int y = c / layer + ey0;
                int below = c - layer;
                int fy;
                if (below >= 0 && in.get(below)) {
                    fy = floorY.get(below);
                } else {
                    fy = y;
                    floorCount.merge(y, 1, Integer::sum);
                }
                floorY.put(c, fy);
            }
            if (floorCount.isEmpty()) return List.of();

            // 主要な床の高さ（ある程度の広さがある床）
            int maxCount = 0;
            for (int v : floorCount.values()) maxCount = Math.max(maxCount, v);
            int threshold = Math.max(3, (int) Math.ceil(maxCount * 0.15));
            List<Integer> majors = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : floorCount.entrySet()) if (e.getValue() >= threshold) majors.add(e.getKey());
            if (majors.isEmpty()) majors.add(floorCount.firstKey());

            Map<Integer, Integer> cellLevel = new HashMap<>(cs.length * 2);
            TreeMap<Integer, Sub> subs = new TreeMap<>();
            for (int c : cs) {
                int fy = floorY.get(c);
                int lvl = majors.get(0);
                for (int m : majors) if (m <= fy) lvl = m;
                cellLevel.put(c, lvl);
                Sub s = subs.computeIfAbsent(lvl, k -> new Sub());
                s.cols.add(c % layer);
                s.volume++;
                s.sumX += c % sx + ex0 + 0.5;
                s.sumY += c / layer + ey0 + 0.5;
                s.sumZ += (c / sx) % sz + ez0 + 0.5;
            }
            for (int owner : windowOwner.values()) {
                Sub s = subOf(cellLevel, subs, owner);
                if (s != null) s.windows++;
            }
            for (int owner : skylightOwner.values()) {
                Sub s = subOf(cellLevel, subs, owner);
                if (s != null) s.skylights++;
            }
            for (int owner : openingOwner.values()) {
                Sub s = subOf(cellLevel, subs, owner);
                if (s != null) s.openings++;
            }
            for (int owner : lightOwner.values()) {
                Sub s = subOf(cellLevel, subs, owner);
                if (s != null) s.lights++;
            }
            for (int i = 0; i < lightCells.size(); i++) {
                Sub s = subOf(cellLevel, subs, lightCells.get(i));
                if (s != null) s.lights++;
            }
            for (int i = 0; i < bedOwners.size(); i++) {
                Sub s = subOf(cellLevel, subs, bedOwners.get(i));
                if (s != null) s.bed = true;
            }

            // 階段や吹き抜けで別の階とつながっていて、そちらにドアがあれば「出口あり」とみなす
            boolean regionHasDoor = !openingOwner.isEmpty();
            List<RoomInfo> out = new ArrayList<>();
            for (Map.Entry<Integer, Sub> e : subs.entrySet()) {
                Sub s = e.getValue();
                int area = s.cols.size();
                if (area < 3) continue;
                double avgCeil = (double) s.volume / area;
                if (avgCeil < 1.5) continue;
                boolean linkedExit = s.openings == 0 && regionHasDoor && subs.size() > 1;
                out.add(new RoomInfo(e.getKey(), 0, area, s.volume, avgCeil, s.windows, s.skylights, s.openings, s.lights, s.bed,
                        linkedExit, s.sumX / s.volume, s.sumY / s.volume, s.sumZ / s.volume));
            }
            return out;
        }

        @Nullable
        private static Sub subOf(Map<Integer, Integer> cellLevel, Map<Integer, Sub> subs, int cell) {
            Integer lvl = cellLevel.get(cell);
            return lvl == null ? null : subs.get(lvl);
        }
    }

    private static final class Sub {
        final HashSet<Integer> cols = new HashSet<>();
        int volume, windows, skylights, openings, lights;
        boolean bed;
        double sumX, sumY, sumZ;
    }

    /** 軽量な int リスト（キュー兼用） */
    static final class IntList {
        private int[] a = new int[64];
        private int n;

        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        int get(int i) {
            return a[i];
        }

        int size() {
            return n;
        }

        void clear() {
            n = 0;
        }

        int[] toSortedArray() {
            int[] c = Arrays.copyOf(a, n);
            Arrays.sort(c);
            return c;
        }
    }
}
