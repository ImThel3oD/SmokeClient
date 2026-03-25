package com.smoke.client.feature.module.player;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class InvCleanerModule extends Module {
    private final NumberSetting delayMin = addSetting(new NumberSetting("delay_min", "Delay Min", "Minimum ms between drops.", 100.0, 50.0, 1000.0, 10.0));
    private final NumberSetting delayMax = addSetting(new NumberSetting("delay_max", "Delay Max", "Maximum ms between drops.", 200.0, 50.0, 1000.0, 10.0));
    private final BoolSetting rottenFlesh = addSetting(new BoolSetting("rotten_flesh", "Rotten Flesh", "Drop rotten flesh.", true));
    private final BoolSetting seeds = addSetting(new BoolSetting("seeds", "Seeds", "Drop all seed types.", true));
    private final BoolSetting poisonousPotato = addSetting(new BoolSetting("poisonous_potato", "Poisonous Potato", "Drop poisonous potatoes.", true));
    private final BoolSetting string = addSetting(new BoolSetting("string", "String", "Drop string.", false));
    private final BoolSetting spiderEyes = addSetting(new BoolSetting("spider_eyes", "Spider Eyes", "Drop spider and fermented spider eyes.", true));
    private final BoolSetting bones = addSetting(new BoolSetting("bones", "Bones", "Drop bones.", true));
    private final BoolSetting arrows = addSetting(new BoolSetting("arrows", "Arrows", "Drop all arrow types.", false));
    private final BoolSetting eggs = addSetting(new BoolSetting("eggs", "Eggs", "Drop eggs.", false));
    private final BoolSetting leather = addSetting(new BoolSetting("leather", "Leather", "Drop leather.", false));
    private final BoolSetting feathers = addSetting(new BoolSetting("feathers", "Feathers", "Drop feathers.", false));
    private final BoolSetting gunpowder = addSetting(new BoolSetting("gunpowder", "Gunpowder", "Drop gunpowder.", false));
    private final BoolSetting wool = addSetting(new BoolSetting("wool", "Wool", "Drop all wool colors.", false));
    private final BoolSetting dirt = addSetting(new BoolSetting("dirt", "Dirt", "Drop dirt, coarse dirt, rooted dirt.", false));
    private final BoolSetting gravel = addSetting(new BoolSetting("gravel", "Gravel", "Drop gravel.", false));
    private final BoolSetting cobblestone = addSetting(new BoolSetting("cobblestone", "Cobblestone", "Drop cobblestone.", false));
    private final BoolSetting woodenTools = addSetting(new BoolSetting("wooden_tools", "Wooden Tools", "Drop wooden tools.", true));
    private final BoolSetting stoneTools = addSetting(new BoolSetting("stone_tools", "Stone Tools", "Drop stone tools.", false));
    private final BoolSetting goldenTools = addSetting(new BoolSetting("golden_tools", "Golden Tools", "Drop golden tools.", false));
    private final BoolSetting leatherArmor = addSetting(new BoolSetting("leather_armor", "Leather Armor", "Drop leather armor pieces.", true));
    private final BoolSetting goldenArmor = addSetting(new BoolSetting("golden_armor", "Golden Armor", "Drop golden armor pieces.", false));
    private final BoolSetting chainmailArmor = addSetting(new BoolSetting("chainmail_armor", "Chainmail Armor", "Drop chainmail armor pieces.", false));

    private long lastDropMs;
    private long nextDelayMs;

    public InvCleanerModule(ModuleContext context) {
        super(context, "inv_cleaner", "InvCleaner", "Automatically drops unwanted items from inventory.", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override protected void onEnable() { lastDropMs = Util.getMeasuringTimeMs(); nextDelayMs = randomDelay(); }
    @Override protected void onDisable() { lastDropMs = 0L; }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.POST) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null || !player.isAlive()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        long now = Util.getMeasuringTimeMs();
        if (now - lastDropMs < nextDelayMs) return;
        Set<Item> trash = buildTrashSet();
        if (trash.isEmpty()) return;
        for (int slot = 9; slot <= 35; slot++) {
            if (trash.contains(player.playerScreenHandler.getSlot(slot).getStack().getItem())) {
                mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot, 1, SlotActionType.THROW, player);
                lastDropMs = now;
                nextDelayMs = randomDelay();
                return;
            }
        }
    }

    private Set<Item> buildTrashSet() {
        Set<Item> s = new HashSet<>();
        if (rottenFlesh.value()) s.add(Items.ROTTEN_FLESH);
        if (seeds.value()) Collections.addAll(s, Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD);
        if (poisonousPotato.value()) s.add(Items.POISONOUS_POTATO);
        if (string.value()) s.add(Items.STRING);
        if (spiderEyes.value()) Collections.addAll(s, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE);
        if (bones.value()) s.add(Items.BONE);
        if (arrows.value()) Collections.addAll(s, Items.ARROW, Items.TIPPED_ARROW, Items.SPECTRAL_ARROW);
        if (eggs.value()) s.add(Items.EGG);
        if (leather.value()) s.add(Items.LEATHER);
        if (feathers.value()) s.add(Items.FEATHER);
        if (gunpowder.value()) s.add(Items.GUNPOWDER);
        if (wool.value()) Collections.addAll(s, Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL, Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL, Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL);
        if (dirt.value()) Collections.addAll(s, Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT);
        if (gravel.value()) s.add(Items.GRAVEL);
        if (cobblestone.value()) s.add(Items.COBBLESTONE);
        if (woodenTools.value()) Collections.addAll(s, Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE);
        if (stoneTools.value()) Collections.addAll(s, Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.STONE_HOE);
        if (goldenTools.value()) Collections.addAll(s, Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE);
        if (leatherArmor.value()) Collections.addAll(s, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        if (goldenArmor.value()) Collections.addAll(s, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
        if (chainmailArmor.value()) Collections.addAll(s, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
        return s;
    }

    private long randomDelay() {
        long min = Math.round(delayMin.value()), max = Math.max(min, Math.round(delayMax.value()));
        return min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
