package com.huntcraft.gear.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.huntcraft.core.Element;
import com.huntcraft.core.HunterDamage;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.IHunterWeapon;
import com.huntcraft.core.IUpgradeable;
import com.huntcraft.core.Sharpness;
import com.huntcraft.core.SkillType;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.data.HunterData;
import com.huntcraft.gear.weapon.action.ActionDef;
import com.huntcraft.item.MaterialItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class of the 8 weapon types: stats from the {@link WeaponLine} and upgrade level, sharpness, combo motion values,
 * attribute modifiers, upgrade costs, tooltip and the shared right-click (charge / guard) plumbing.
 */
public abstract class HunterWeaponItem extends Item implements IHunterWeapon, IUpgradeable {
    public static final int MAX_LEVEL = 8;
    public static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    protected static final UUID REACH_UUID = UUID.fromString("7d3b9c1e-4a52-4f0e-9b8a-2c6d1e5f9a31");
    public static final int USE_TICKS = 72000;

    public final WeaponLine line;
    private final WeaponClass weaponClass;

    protected HunterWeaponItem(WeaponClass weaponClass, WeaponLine line, Properties props) {
        super(props.stacksTo(1));
        this.weaponClass = weaponClass;
        this.line = line;
    }

    @Override
    public WeaponClass weaponClass() {
        return weaponClass;
    }

    // ================================================================== stats
    @Override
    public float getAttack(ItemStack stack) {
        int lv = getLevel(stack);
        return (line.attackBase + line.attackGrowth * (lv - 1)) * WeaponTuning.attackMult(weaponClass);
    }

    @Override
    public Element getElement(ItemStack stack) {
        return line.elementFor(weaponClass);
    }

    @Override
    public float getElementValue(ItemStack stack) {
        return line.elementValue(weaponClass, getLevel(stack));
    }

    @Override
    public int getAffinity(ItemStack stack) {
        return line.affinity + (getLevel(stack) >= 6 ? 5 : 0);
    }

    public int getHcRarity(ItemStack stack) {
        int lv = getLevel(stack);
        return Math.min(8, line.rarity + (lv >= 6 ? 3 : lv >= 4 ? 1 : 0));
    }

    // ================================================================== sharpness
    /** Effective sharpness segments (with upgrade level bonus and the player's Handicraft, if given). */
    public int[] sharpnessSegments(ItemStack stack, @Nullable Player player) {
        int extra = Math.max(0, getLevel(stack) - 5) * 10;
        if (player != null) extra += 10 * HunterSkills.level(player, SkillType.HANDICRAFT);
        return SharpnessProfile.effective(line.sharpBase, line.sharpMax, extra);
    }

    public int maxSharpness(ItemStack stack, @Nullable Player player) {
        return SharpnessProfile.total(sharpnessSegments(stack, player));
    }

    public int sharpnessPoints(ItemStack stack, @Nullable Player player) {
        int max = maxSharpness(stack, player);
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(SharpnessProfile.TAG)) return max;
        return Math.max(0, Math.min(max, tag.getInt(SharpnessProfile.TAG)));
    }

    public void setSharpnessPoints(ItemStack stack, int points) {
        stack.getOrCreateTag().putInt(SharpnessProfile.TAG, Math.max(0, points));
    }

    public boolean needsSharpening(ItemStack stack, @Nullable Player player) {
        return usesSharpness() && sharpnessPoints(stack, player) < maxSharpness(stack, player);
    }

    @Override
    public Sharpness getSharpness(ItemStack stack, Player player) {
        int[] seg = sharpnessSegments(stack, player);
        return Sharpness.byIndex(SharpnessProfile.colorIndex(seg, sharpnessPoints(stack, player)));
    }

    @Override
    public void consumeSharpness(ItemStack stack, Player player, int amount) {
        if (!usesSharpness() || amount <= 0 || player.getAbilities().instabuild) return;
        setSharpnessPoints(stack, sharpnessPoints(stack, player) - amount);
    }

    // ================================================================== left click / combat actions
    /** Short id used by the action names of this class (gs_combo1, ls_spirit1, ...). */
    public String actionPrefix() {
        return switch (weaponClass) {
            case GREAT_SWORD -> "gs";
            case LONG_SWORD -> "ls";
            case SWORD_AND_SHIELD -> "sns";
            case DUAL_BLADES -> "db";
            case HAMMER -> "hm";
            case LANCE -> "ln";
            case HUNTING_HORN -> "hh";
            case BOW -> "bow";
        };
    }

    /** First action of the left click chain (the chain continues through ActionDef.next). */
    public String comboStart(ServerPlayer p, ItemStack stack, boolean sneak) {
        return actionPrefix() + "_combo1";
    }

    /** Action started by the attack key while guarding (null = nothing). */
    @Nullable
    public String guardAction(ServerPlayer p, ItemStack stack) {
        return null;
    }

    /** Server: an action of this weapon started (gauges, notes, sounds...). */
    public void onActionStart(ServerPlayer p, ItemStack stack, ActionDef def) {
    }

    /** Server: every tick of a running action (t = ticks since start). */
    public void onActionTick(ServerPlayer p, ItemStack stack, ActionDef def, int t) {
    }

    /** Server: the action ended (naturally or interrupted). */
    public void onActionEnd(ServerPlayer p, ItemStack stack, ActionDef def, boolean interrupted) {
    }

    /** Server: per target damage context tweaks of a hit window (motion value scaling, KO...). */
    public void configureActionHit(ServerPlayer p, ItemStack stack, ActionDef def, ActionDef.Hit hit, WeaponHits.Target target, HunterDamage.Context ctx) {
    }

    /** Server: a hit window was applied (hits = number of entities struck). */
    public void onActionHitDone(ServerPlayer p, ItemStack stack, ActionDef def, ActionDef.Hit hit, int hits) {
    }

    @Override
    public float getLeftClickMotionValue(Player player, ItemStack stack, LivingEntity target) {
        float[] mvs = WeaponTuning.combo(weaponClass);
        return mvs[0];
    }

    public double swingRange() {
        return 3.2 + WeaponTuning.reachBonus(weaponClass);
    }

    protected boolean heavy() {
        return weaponClass == WeaponClass.GREAT_SWORD || weaponClass == WeaponClass.HAMMER || weaponClass == WeaponClass.HUNTING_HORN;
    }

    public void swingSound(Player p, float volume) {
        playSound(p, heavy() ? WeaponSounds.SWING_HEAVY.get() : WeaponSounds.SWING_LIGHT.get(), volume, 0.9F + p.getRandom().nextFloat() * 0.2F);
    }

    // ================================================================== gauges / hooks
    @Override
    public void onHunterHit(Player player, ItemStack stack, LivingEntity target, float finalDamage, boolean weakSpot) {
        WeaponState s = WeaponState.get(player);
        s.lastHitTime = player.level().getGameTime();
        onWeaponHit(player, stack, s, target, finalDamage, weakSpot, s.special);
    }

    /** Gauge filling hook. `special` is true for special moves (spirit blade, blade dance...). */
    protected void onWeaponHit(Player p, ItemStack stack, WeaponState s, LivingEntity target, float damage, boolean weak, boolean special) {
    }

    /** Server tick while the weapon is held in the main hand. */
    public void serverTick(ServerPlayer p, ItemStack stack, WeaponState s) {
    }

    /** Called (server) when this weapon stops being the main hand item. */
    public void onUnequip(ServerPlayer p, WeaponState s) {
    }

    /** Runs a special move: gauge filling is suppressed while it executes. */
    public static void special(Player p, Runnable r) {
        WeaponState s = WeaponState.get(p);
        boolean old = s.special;
        s.special = true;
        try {
            r.run();
        } finally {
            s.special = old;
        }
    }

    // ================================================================== right click plumbing
    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** Guard strength (fraction of damage blocked) when guarding with this weapon, 0 = cannot guard. */
    public float guardPower() {
        return 0;
    }

    /** Charge thresholds in ticks for levels 1..3 (without Focus). */
    protected int[] chargeThresholds() {
        return new int[]{14, 28, 42};
    }

    public int chargeTicksFor(Player p, int level) {
        int[] th = chargeThresholds();
        float focus = 1.0F - 0.1F * HunterSkills.level(p, SkillType.FOCUS);
        return Math.max(1, Math.round(th[Math.max(0, Math.min(th.length - 1, level - 1))] * focus));
    }

    /** Charge level 0..3 after `ticksUsed` ticks of holding. */
    public int chargeLevel(Player p, int ticksUsed) {
        int lv = 0;
        for (int i = 1; i <= 3; i++) if (ticksUsed >= chargeTicksFor(p, i)) lv = i;
        return lv;
    }

    protected void beginUse(Player p, InteractionHand hand, int mode) {
        WeaponState s = WeaponState.get(p);
        s.useMode = mode;
        s.chargeReached = 0;
        p.startUsingItem(hand);
    }

    public boolean isGuarding(Player p) {
        return p.isUsingItem() && WeaponState.get(p).useMode == WeaponState.MODE_GUARD && p.getUseItem().getItem() == this;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof Player p)) return;
        WeaponState s = WeaponState.get(p);
        int used = getUseDuration(stack) - remaining;
        if (s.useMode == WeaponState.MODE_GUARD) {
            if (!level.isClientSide) {
                HunterData d = HunterData.get(p);
                d.guardPower = guardPower();
                d.guardTicksLeft = 3;
            }
        } else if (s.useMode == WeaponState.MODE_CHARGE) {
            int lv = chargeLevel(p, used);
            if (lv > s.chargeReached) {
                s.chargeReached = lv;
                if (!level.isClientSide) onChargeLevel(p, stack, lv);
            }
            if (!level.isClientSide) chargeTick(p, stack, used, lv);
        }
    }

    /** Server: a new charge level was reached (sound / particles). */
    protected void onChargeLevel(Player p, ItemStack stack, int level) {
        playSound(p, level >= 3 ? com.huntcraft.registry.HCSounds.CHARGE_MAX.get() : com.huntcraft.registry.HCSounds.CHARGE.get(), 1.0F, 0.8F + 0.2F * level);
        WeaponFx.chargeBurst(p, level);
    }

    /** Server: every tick while charging. */
    protected void chargeTick(Player p, ItemStack stack, int used, int level) {
        if (level > 0 && used % 6 == 0) WeaponFx.chargeAura(p, level);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player p)) return;
        WeaponState s = WeaponState.get(p);
        int mode = s.useMode;
        s.useMode = WeaponState.MODE_NONE;
        int used = getUseDuration(stack) - timeLeft;
        if (mode == WeaponState.MODE_CHARGE) {
            onChargeRelease(level, p, stack, used, chargeLevel(p, used));
        }
        s.chargeReached = -1;
    }

    /** Charge released (both sides; damage on the server). */
    protected void onChargeRelease(Level level, Player p, ItemStack stack, int used, int chargeLevel) {
    }

    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() == newStack.getItem();
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    // ================================================================== attributes
    public float displayDamage(ItemStack stack) {
        return Math.max(0.0F, getAttack(stack) * WeaponTuning.displayMV(weaponClass) - 1.0F);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", displayDamage(stack), AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", WeaponTuning.attackSpeed(weaponClass) - 4.0, AttributeModifier.Operation.ADDITION));
        double reach = WeaponTuning.reachBonus(weaponClass);
        if (reach > 0) {
            b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_UUID, "Weapon reach", reach, AttributeModifier.Operation.ADDITION));
        }
        return b.build();
    }

    // ================================================================== upgrades
    @Override
    public int getMaxLevel(ItemStack stack) {
        return MAX_LEVEL;
    }

    @Override
    public List<SmithingRegistry.Cost> getUpgradeCost(ItemStack stack, int nextLevel) {
        return WeaponRecipes.levelCost(weaponClass, line, nextLevel);
    }

    @Override
    public int getUpgradeZenny(ItemStack stack, int nextLevel) {
        return WeaponRecipes.levelZenny(line, nextLevel);
    }

    @Override
    public int getRequiredHR(ItemStack stack, int nextLevel) {
        return nextLevel > 5 ? 8 : line.forgeHR;
    }

    // ================================================================== helpers
    public static void playSound(Player p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void particles(Player p, ParticleOptions type, Vec3 pos, int count, double spread, double speed) {
        if (p.level() instanceof ServerLevel sl) sl.sendParticles(type, pos.x, pos.y, pos.z, count, spread, spread * 0.6, spread, speed);
    }

    /** Consumes stamina (Constitution applies). Returns false when the hunter is too tired. */
    public static boolean useStamina(Player p, float cost) {
        if (p.getAbilities().instabuild) return true;
        HunterData d = HunterData.get(p);
        float c = cost * (1.0F - 0.1F * HunterSkills.level(p, SkillType.CONSTITUTION));
        if (d.stamina < c * 0.5F || d.exhausted) return false;
        if (!p.level().isClientSide) d.consumeStamina(c);
        return true;
    }

    // ================================================================== name & tooltip
    @Override
    public Component getName(ItemStack stack) {
        MutableComponent c = Component.translatable(getDescriptionId(stack));
        int lv = getLevel(stack);
        if (lv > 1) c.append(" " + ROMAN[Math.min(ROMAN.length - 1, lv)]);
        return c.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MaterialItem.rarityColor(getHcRarity(stack)))));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int lv = getLevel(stack);
        tooltip.add(Component.translatable(weaponClass.key()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  "))
                .append(Component.translatable("tooltip.huntcraft.weapon.level", lv, MAX_LEVEL).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.attack", Math.round(getAttack(stack))).withStyle(ChatFormatting.WHITE));
        Element el = getElement(stack);
        if (el != Element.NONE) {
            tooltip.add(Component.translatable(el.isStatus() ? "tooltip.huntcraft.weapon.status" : "tooltip.huntcraft.weapon.element")
                    .withStyle(ChatFormatting.WHITE)
                    .append(el.displayName())
                    .append(Component.literal(" " + Math.round(getElementValue(stack))).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(el.color)))));
        }
        int aff = getAffinity(stack);
        if (aff != 0) {
            tooltip.add(Component.translatable("tooltip.huntcraft.weapon.affinity", (aff > 0 ? "+" : "") + aff)
                    .withStyle(aff > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (usesSharpness()) {
            tooltip.add(Component.translatable("tooltip.huntcraft.weapon.sharpness").withStyle(ChatFormatting.WHITE).append(sharpnessBar(stack)));
        }
        appendClassInfo(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.huntcraft.rarity", getHcRarity(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.help." + weaponClass.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** Class specific tooltip lines (guard, shot type, songs...). */
    protected void appendClassInfo(ItemStack stack, List<Component> tooltip) {
        if (guardPower() > 0) {
            tooltip.add(Component.translatable("tooltip.huntcraft.weapon.guard", Math.round(guardPower() * 100)).withStyle(ChatFormatting.GRAY));
        }
    }

    public MutableComponent sharpnessBar(ItemStack stack) {
        MutableComponent bar = Component.literal("");
        int[] seg = sharpnessSegments(stack, null);
        int[] max = line.sharpMax;
        int pts = sharpnessPoints(stack, null);
        int acc = 0;
        for (int i = 0; i < SharpnessProfile.COLORS; i++) {
            int len = seg[i];
            int potential = max[i] - len;
            if (len > 0) {
                int n = Math.max(1, Math.round(len / 10.0F));
                int filled = Math.max(0, Math.min(len, pts - acc));
                int nf = Math.round(n * filled / (float) len);
                if (nf > 0) bar.append(Component.literal("|".repeat(nf)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Sharpness.byIndex(i).color))));
                if (n - nf > 0) bar.append(Component.literal("|".repeat(n - nf)).withStyle(ChatFormatting.DARK_GRAY));
                acc += len;
            }
            if (potential > 0) {
                int n = Math.max(1, Math.round(potential / 10.0F));
                bar.append(Component.literal(".".repeat(n)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Sharpness.byIndex(i).color))));
            }
        }
        return bar;
    }

    // ================================================================== client
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.huntcraft.gear.weapon.client.WeaponItemExtensions.create(this));
    }
}
