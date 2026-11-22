package jp.archiquest.registry;

import jp.archiquest.ArchiQuest;
import jp.archiquest.item.CertificateItem;
import jp.archiquest.item.InspectorItem;
import jp.archiquest.item.TextbookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArchiQuest.MODID);

    /** 一級建築士の教科書：右クリック（またはKキー）で教科書GUIを開く */
    public static final RegistryObject<Item> TEXTBOOK = ITEMS.register("textbook",
            () -> new TextbookItem(new Item.Properties().stacksTo(1)));

    /** 建築診断スケール：右クリックで建物を診断、スニーク右クリックで敷地の角を指定 */
    public static final RegistryObject<Item> INSPECTOR = ITEMS.register("inspector",
            () -> new InspectorItem(new Item.Properties().stacksTo(1)));

    /** 模試合格証書：模擬試験に合格するともらえる */
    public static final RegistryObject<Item> CERTIFICATE = ITEMS.register("certificate",
            () -> new CertificateItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {}
}
