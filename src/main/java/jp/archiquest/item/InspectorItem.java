package jp.archiquest.item;

import jp.archiquest.analysis.BuildingAnalyzer;
import jp.archiquest.analysis.Site;
import jp.archiquest.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 建築診断スケール
 * <ul>
 *   <li>右クリック（ブロック）: 建物を診断</li>
 *   <li>スニーク＋右クリック（ブロック）: 敷地の角を指定（2回で敷地が決まる）</li>
 *   <li>右クリック（空中）: 使い方と敷地の状態を表示</li>
 *   <li>スニーク＋右クリック（空中）: 敷地をリセット</li>
 * </ul>
 */
public class InspectorItem extends Item {
    private static final String TAG_A = "SiteA";
    private static final String TAG_B = "SiteB";
    private static final String TAG_DIM = "SiteDim";

    public InspectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        if (sp.isShiftKeyDown()) {
            setCorner(sp, sl, stack, pos);
        } else {
            sp.getCooldowns().addCooldown(this, 20);
            BuildingAnalyzer.inspect(sp, sl, pos, getSite(stack, sl));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) {
                CompoundTag tag = stack.getTag();
                if (tag != null) {
                    tag.remove(TAG_A);
                    tag.remove(TAG_B);
                    tag.remove(TAG_DIM);
                }
                Msg.say(sp, "敷地の指定をリセットしたよ。");
            } else {
                Msg.say(sp, Component.literal("建築診断スケールの使い方").withStyle(ChatFormatting.YELLOW));
                Msg.send(sp, " ・建物のブロックを右クリック → 建物を診断", ChatFormatting.WHITE);
                Msg.send(sp, " ・スニーク＋ブロックを右クリック×2 → 敷地の角を指定（建ぺい率・容積率も診断）", ChatFormatting.WHITE);
                Msg.send(sp, " ・スニーク＋空中を右クリック → 敷地をリセット", ChatFormatting.WHITE);
                Site s = getSite(stack, level);
                Msg.send(sp, s == null ? " 敷地: 未設定" : " 敷地: " + s.width() + "m×" + s.depth() + "m = " + s.area() + "m²", ChatFormatting.GRAY);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void setCorner(ServerPlayer sp, ServerLevel level, ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        String dim = level.dimension().location().toString();
        boolean hasA = tag.contains(TAG_A) && dim.equals(tag.getString(TAG_DIM));
        boolean hasB = tag.contains(TAG_B);
        if (!hasA || hasB) {
            tag.putIntArray(TAG_A, new int[]{pos.getX(), pos.getZ()});
            tag.remove(TAG_B);
            tag.putString(TAG_DIM, dim);
            Msg.say(sp, "敷地の1つ目の角を (" + pos.getX() + ", " + pos.getZ() + ") に設定！ 次は対角の角をスニーク＋右クリックしてね。");
        } else {
            tag.putIntArray(TAG_B, new int[]{pos.getX(), pos.getZ()});
            Site s = getSite(stack, level);
            if (s != null) {
                Msg.say(sp, Component.literal("敷地を設定したよ！ " + s.width() + "m × " + s.depth() + "m = " + s.area() + "m²").withStyle(ChatFormatting.GREEN));
                Msg.send(sp, " 建ぺい率 ＝ 建築面積 ÷ 敷地面積、容積率 ＝ 延べ面積 ÷ 敷地面積。建物を診断すると計算するよ！", ChatFormatting.GRAY);
            }
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.0);
    }

    @Nullable
    public static Site getSite(ItemStack stack, @Nullable Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_A) || !tag.contains(TAG_B)) return null;
        if (level != null && !level.dimension().location().toString().equals(tag.getString(TAG_DIM))) return null;
        int[] a = tag.getIntArray(TAG_A);
        int[] b = tag.getIntArray(TAG_B);
        if (a.length < 2 || b.length < 2) return null;
        return new Site(a[0], a[1], b[0], b[1]);
    }

    /** プレイヤーの持ち物から、敷地が設定されたスケールを探す（/archi analyze 用） */
    @Nullable
    public static Site findSite(ServerPlayer p) {
        for (ItemStack s : List.of(p.getMainHandItem(), p.getOffhandItem())) {
            if (s.getItem() instanceof InspectorItem) {
                Site site = getSite(s, p.level());
                if (site != null) return site;
            }
        }
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof InspectorItem) {
                Site site = getSite(s, p.level());
                if (site != null) return site;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.archiquest.inspector.tooltip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.archiquest.inspector.tooltip2").withStyle(ChatFormatting.GRAY));
        Site s = getSite(stack, null);
        if (s != null) {
            tooltip.add(Component.literal(s.width() + "m × " + s.depth() + "m = " + s.area() + "m²").withStyle(ChatFormatting.GREEN));
        }
    }
}
