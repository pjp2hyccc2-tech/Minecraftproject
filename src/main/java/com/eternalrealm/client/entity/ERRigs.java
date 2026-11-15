package com.eternalrealm.client.entity;

import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.client.entity.model.gen.BanditBossAnimations;
import com.eternalrealm.client.entity.model.gen.BanditBossGeometry;
import com.eternalrealm.client.entity.model.gen.BeastEnemyAnimations;
import com.eternalrealm.client.entity.model.gen.BeastEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.ColossusBossAnimations;
import com.eternalrealm.client.entity.model.gen.ColossusBossGeometry;
import com.eternalrealm.client.entity.model.gen.DragonBossAnimations;
import com.eternalrealm.client.entity.model.gen.DragonBossGeometry;
import com.eternalrealm.client.entity.model.gen.EndkingBossAnimations;
import com.eternalrealm.client.entity.model.gen.EndkingBossGeometry;
import com.eternalrealm.client.entity.model.gen.ExecutionerBossAnimations;
import com.eternalrealm.client.entity.model.gen.ExecutionerBossGeometry;
import com.eternalrealm.client.entity.model.gen.FiredragonBossAnimations;
import com.eternalrealm.client.entity.model.gen.FiredragonBossGeometry;
import com.eternalrealm.client.entity.model.gen.FlyerEnemyAnimations;
import com.eternalrealm.client.entity.model.gen.FlyerEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.GatekeeperBossAnimations;
import com.eternalrealm.client.entity.model.gen.GatekeeperBossGeometry;
import com.eternalrealm.client.entity.model.gen.GolemEnemyAnimations;
import com.eternalrealm.client.entity.model.gen.GolemEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.HeroAnimations;
import com.eternalrealm.client.entity.model.gen.HeroGeometry;
import com.eternalrealm.client.entity.model.gen.HumanoidEnemyAnimations;
import com.eternalrealm.client.entity.model.gen.HumanoidEnemyGeometry;
import com.eternalrealm.client.entity.model.gen.IcequeenBossAnimations;
import com.eternalrealm.client.entity.model.gen.IcequeenBossGeometry;
import com.eternalrealm.client.entity.model.gen.KingBossAnimations;
import com.eternalrealm.client.entity.model.gen.KingBossGeometry;
import com.eternalrealm.client.entity.model.gen.NpcAnimations;
import com.eternalrealm.client.entity.model.gen.NpcGeometry;
import com.eternalrealm.client.entity.model.gen.QueenBossAnimations;
import com.eternalrealm.client.entity.model.gen.QueenBossGeometry;
import com.eternalrealm.client.entity.model.gen.SandtitanBossAnimations;
import com.eternalrealm.client.entity.model.gen.SandtitanBossGeometry;
import com.eternalrealm.client.entity.model.gen.SkygodBossAnimations;
import com.eternalrealm.client.entity.model.gen.SkygodBossGeometry;
import com.eternalrealm.client.entity.model.gen.TreantBossAnimations;
import com.eternalrealm.client.entity.model.gen.TreantBossGeometry;
import com.eternalrealm.client.entity.model.gen.TreeguardBossAnimations;
import com.eternalrealm.client.entity.model.gen.TreeguardBossGeometry;
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
    public static final RigTables BANDIT = new RigTables("bandit", BanditBossGeometry.VARIANTS, BanditBossGeometry.TOGGLED_PARTS,
            BanditBossGeometry.VISIBLE, BanditBossGeometry.POSED_PARTS, BanditBossGeometry.POSES,
            BanditBossGeometry.SCALES, BanditBossGeometry.GLOWS, BanditBossAnimations::forVariant);
    public static final RigTables TREEGUARD = new RigTables("treeguard", TreeguardBossGeometry.VARIANTS,
            TreeguardBossGeometry.TOGGLED_PARTS, TreeguardBossGeometry.VISIBLE, TreeguardBossGeometry.POSED_PARTS,
            TreeguardBossGeometry.POSES, TreeguardBossGeometry.SCALES, TreeguardBossGeometry.GLOWS,
            TreeguardBossAnimations::forVariant);
    public static final RigTables SANDTITAN = new RigTables("sandtitan", SandtitanBossGeometry.VARIANTS,
            SandtitanBossGeometry.TOGGLED_PARTS, SandtitanBossGeometry.VISIBLE, SandtitanBossGeometry.POSED_PARTS,
            SandtitanBossGeometry.POSES, SandtitanBossGeometry.SCALES, SandtitanBossGeometry.GLOWS,
            SandtitanBossAnimations::forVariant);
    public static final RigTables EXECUTIONER = new RigTables("executioner", ExecutionerBossGeometry.VARIANTS,
            ExecutionerBossGeometry.TOGGLED_PARTS, ExecutionerBossGeometry.VISIBLE, ExecutionerBossGeometry.POSED_PARTS,
            ExecutionerBossGeometry.POSES, ExecutionerBossGeometry.SCALES, ExecutionerBossGeometry.GLOWS,
            ExecutionerBossAnimations::forVariant);
    public static final RigTables ICEQUEEN = new RigTables("icequeen", IcequeenBossGeometry.VARIANTS,
            IcequeenBossGeometry.TOGGLED_PARTS, IcequeenBossGeometry.VISIBLE, IcequeenBossGeometry.POSED_PARTS,
            IcequeenBossGeometry.POSES, IcequeenBossGeometry.SCALES, IcequeenBossGeometry.GLOWS,
            IcequeenBossAnimations::forVariant);
    public static final RigTables FIREDRAGON = new RigTables("firedragon", FiredragonBossGeometry.VARIANTS,
            FiredragonBossGeometry.TOGGLED_PARTS, FiredragonBossGeometry.VISIBLE, FiredragonBossGeometry.POSED_PARTS,
            FiredragonBossGeometry.POSES, FiredragonBossGeometry.SCALES, FiredragonBossGeometry.GLOWS,
            FiredragonBossAnimations::forVariant);
    public static final RigTables SKYGOD = new RigTables("skygod", SkygodBossGeometry.VARIANTS,
            SkygodBossGeometry.TOGGLED_PARTS, SkygodBossGeometry.VISIBLE, SkygodBossGeometry.POSED_PARTS,
            SkygodBossGeometry.POSES, SkygodBossGeometry.SCALES, SkygodBossGeometry.GLOWS,
            SkygodBossAnimations::forVariant);
    public static final RigTables GATEKEEPER = new RigTables("gatekeeper", GatekeeperBossGeometry.VARIANTS,
            GatekeeperBossGeometry.TOGGLED_PARTS, GatekeeperBossGeometry.VISIBLE, GatekeeperBossGeometry.POSED_PARTS,
            GatekeeperBossGeometry.POSES, GatekeeperBossGeometry.SCALES, GatekeeperBossGeometry.GLOWS,
            GatekeeperBossAnimations::forVariant);
    public static final RigTables ENDKING = new RigTables("endking", EndkingBossGeometry.VARIANTS,
            EndkingBossGeometry.TOGGLED_PARTS, EndkingBossGeometry.VISIBLE, EndkingBossGeometry.POSED_PARTS,
            EndkingBossGeometry.POSES, EndkingBossGeometry.SCALES, EndkingBossGeometry.GLOWS,
            EndkingBossAnimations::forVariant);
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
            case BANDIT -> BANDIT;
            case TREEGUARD -> TREEGUARD;
            case SANDTITAN -> SANDTITAN;
            case EXECUTIONER -> EXECUTIONER;
            case ICEQUEEN -> ICEQUEEN;
            case FIREDRAGON -> FIREDRAGON;
            case SKYGOD -> SKYGOD;
            case GATEKEEPER -> GATEKEEPER;
            case ENDKING -> ENDKING;
        };
    }
}
