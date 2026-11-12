package com.eternalrealm.client.entity;

import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.client.entity.model.gen.BeastEnemyAnimations;
import com.eternalrealm.client.entity.model.gen.BeastEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.ColossusBossAnimations;
import com.eternalrealm.client.entity.model.gen.ColossusBossGeometry;
import com.eternalrealm.client.entity.model.gen.DragonBossAnimations;
import com.eternalrealm.client.entity.model.gen.DragonBossGeometry;
import com.eternalrealm.client.entity.model.gen.FlyerEnemyAnimations;
import com.eternalrealm.client.entity.model.gen.FlyerEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.GolemEnemyAnimations;
import com.eternalrealm.client.entity.model.gen.GolemEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.HeroAnimations;
import com.eternalrealm.client.entity.model.gen.HeroGeometry;
import com.eternalrealm.client.entity.model.gen.HumanoidEnemyAnimations;
import com.eternalrealm.client.entity.model.gen.HumanoidEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.KingBossAnimations;
import com.eternalrealm.client.entity.model.gen.KingBossGeometry;
import com.eternalrealm.client.entity.model.gen.NpcAnimations;
import com.eternalrealm.client.entity.model.gen.NpcGeometry;
import com.eternalrealm.client.entity.model.gen.QueenBossAnimations;
import com.eternalrealm.client.entity.model.gen.QueenBossGeometry;
import com.eternalrealm.client.entity.model.gen.TreantBossAnimations;
import com.eternalrealm.client.entity.model.gen.TreantBossGeometry;
import com.eternalrealm.entity.BossRig;
import com.eternalrealm.entity.EnemyRig;

/** The generated tables of every rig. */
public final class ERRigs {
    public static final RigTables HUMANOID = new RigTables("humanoid", HumanoidEnemyGeometry.VARIANTS,
            HumanoidEnemyGeometry.TOGGLED_PARTS, HumanoidEnemyGeometry.VISIBLE, HumanoidEnemyGeometry.POSED_PARTS,
            HumanoidEnemyGeometry.POSES, HumanoidEnemyGeometry.SCALES, HumanoidEnemyGeometry.GLOWS,
            HumanoidEnemyAnimations::forVariant);
    public static final RigTables BEAST = new RigTables("beast", BeastEnemyGeometry.VARIANTS,
            BeastEnemyGeometry.TOGGLED_PARTS, BeastEnemyGeometry.VISIBLE, BeastEnemyGeometry.POSED_PARTS,
            BeastEnemyGeometry.POSES, BeastEnemyGeometry.SCALES, BeastEnemyGeometry.GLOWS,
            BeastEnemyAnimations::forVariant);
    public static final RigTables GOLEM = new RigTables("golem", GolemEnemyGeometry.VARIANTS,
            GolemEnemyGeometry.TOGGLED_PARTS, GolemEnemyGeometry.VISIBLE, GolemEnemyGeometry.POSED_PARTS,
            GolemEnemyGeometry.POSES, GolemEnemyGeometry.SCALES, GolemEnemyGeometry.GLOWS,
            GolemEnemyAnimations::forVariant);
    public static final RigTables FLYER = new RigTables("flyer", FlyerEnemyGeometry.VARIANTS,
            FlyerEnemyGeometry.TOGGLED_PARTS, FlyerEnemyGeometry.VISIBLE, FlyerEnemyGeometry.POSED_PARTS,
            FlyerEnemyGeometry.POSES, FlyerEnemyGeometry.SCALES, FlyerEnemyGeometry.GLOWS,
            FlyerEnemyAnimations::forVariant);
    public static final RigTables KING = new RigTables("king", KingBossGeometry.VARIANTS,
            KingBossGeometry.TOGGLED_PARTS, KingBossGeometry.VISIBLE, KingBossGeometry.POSED_PARTS,
            KingBossGeometry.POSES, KingBossGeometry.SCALES, KingBossGeometry.GLOWS, KingBossAnimations::forVariant);
    public static final RigTables TREANT = new RigTables("treant", TreantBossGeometry.VARIANTS,
            TreantBossGeometry.TOGGLED_PARTS, TreantBossGeometry.VISIBLE, TreantBossGeometry.POSED_PARTS,
            TreantBossGeometry.POSES, TreantBossGeometry.SCALES, TreantBossGeometry.GLOWS,
            TreantBossAnimations::forVariant);
    public static final RigTables COLOSSUS = new RigTables("colossus", ColossusBossGeometry.VARIANTS,
            ColossusBossGeometry.TOGGLED_PARTS, ColossusBossGeometry.VISIBLE, ColossusBossGeometry.POSED_PARTS,
            ColossusBossGeometry.POSES, ColossusBossGeometry.SCALES, ColossusBossGeometry.GLOWS,
            ColossusBossAnimations::forVariant);
    public static final RigTables QUEEN = new RigTables("queen", QueenBossGeometry.VARIANTS,
            QueenBossGeometry.TOGGLED_PARTS, QueenBossGeometry.VISIBLE, QueenBossGeometry.POSED_PARTS,
            QueenBossGeometry.POSES, QueenBossGeometry.SCALES, QueenBossGeometry.GLOWS,
            QueenBossAnimations::forVariant);
    public static final RigTables DRAGON = new RigTables("dragon", DragonBossGeometry.VARIANTS,
            DragonBossGeometry.TOGGLED_PARTS, DragonBossGeometry.VISIBLE, DragonBossGeometry.POSED_PARTS,
            DragonBossGeometry.POSES, DragonBossGeometry.SCALES, DragonBossGeometry.GLOWS,
            DragonBossAnimations::forVariant);
    /** The four legendary travellers; slots as in {@link com.eternalrealm.npc.HeroBodies}. */
    public static final RigTables HERO = new RigTables("hero", HeroGeometry.VARIANTS, HeroGeometry.TOGGLED_PARTS,
            HeroGeometry.VISIBLE, HeroGeometry.POSED_PARTS, HeroGeometry.POSES, HeroGeometry.SCALES, HeroGeometry.GLOWS,
            HeroAnimations::forVariant);
    /** The town NPCs; slots: idle, walk, work, talk. */
    public static final RigTables NPC = new RigTables("npc", NpcGeometry.VARIANTS, NpcGeometry.TOGGLED_PARTS,
            NpcGeometry.VISIBLE, NpcGeometry.POSED_PARTS, NpcGeometry.POSES, NpcGeometry.SCALES, NpcGeometry.GLOWS,
            NpcAnimations::forVariant);

    private ERRigs() {
    }

    public static RigTables of(EnemyRig rig) {
        return switch (rig) {
            case HUMANOID -> HUMANOID;
            case BEAST -> BEAST;
            case GOLEM -> GOLEM;
            case FLYER -> FLYER;
        };
    }

    public static RigTables of(BossRig rig) {
        return switch (rig) {
            case KING -> KING;
            case TREANT -> TREANT;
            case COLOSSUS -> COLOSSUS;
            case QUEEN -> QUEEN;
            case DRAGON -> DRAGON;
        };
    }
}
