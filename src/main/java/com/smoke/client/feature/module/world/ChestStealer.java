package com.smoke.client.feature.module.world;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class ChestStealer extends Module {
    private static final long MIN_DELAY_MS = 50L;
    private static final long MAX_DELAY_MS = 500L;
    private static final long DEFAULT_DELAY_MS = 75L;

    private static final int PLAYER_INV_SLOT_COUNT = PlayerInventory.MAIN_SIZE; // 36 (main + hotbar)

    private static final int OPEN_WARMUP_TICKS = 1;
    private static final long MAX_TICK_DELTA_MS = 250L;
    private static final long MAX_ACCUMULATED_MS = 1000L;

    private final NumberSetting delayMs = addSetting(new NumberSetting(
            "delay_ms",
            "Delay (ms)",
            "Delay between each shift-click while looting a container.",
            (double) DEFAULT_DELAY_MS,
            (double) MIN_DELAY_MS,
            (double) MAX_DELAY_MS,
            1.0
    ));

    private final BoolSetting autoClose = addSetting(new BoolSetting(
            "auto_close",
            "Auto Close",
            "Close the container screen when it is empty.",
            false
    ));

    private int activeSyncId = -1;
    private int warmupTicks;
    private int scanCursor;

    private long lastTickMs;
    private long accumulatedMs;
    private long currentDelayMs;

    public ChestStealer(ModuleContext context) {
        super(
                context,
                "auto_loot",
                "ChestStealer",
                "Automatically shift-clicks items from open containers into your inventory.",
                ModuleCategory.WORLD,
                GLFW.GLFW_KEY_UNKNOWN
        );
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.POST) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            reset();
            return;
        }

        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        if (player == null || interactionManager == null) {
            reset();
            return;
        }

        ScreenHandler handler = player.currentScreenHandler;
        if (!isLootableHandler(handler)) {
            reset();
            return;
        }

        int syncId = handler.syncId;
        if (syncId != activeSyncId) {
            beginSession(syncId);
            return;
        }

        tickTime();

        if (warmupTicks > 0) {
            warmupTicks--;
            return;
        }

        if (!handler.getCursorStack().isEmpty()) {
            accumulatedMs = 0L;
            return;
        }

        int containerSlots = containerSlotCount(handler);
        if (containerSlots <= 0) {
            reset();
            return;
        }

        if (accumulatedMs < currentDelayMs) {
            return;
        }

        int slotToClick = findNextLootableContainerSlot(handler, containerSlots, scanCursor, player, player.getInventory());
        if (slotToClick == -1) {
            if (isContainerEmpty(handler, containerSlots)) {
                if (autoClose.value()) {
                    player.closeHandledScreen();
                    reset();
                }
                return;
            }

            // Container has items, but none can be moved (inventory full). Pause without spamming clicks.
            accumulatedMs = 0L;
            return;
        }

        Slot slot = handler.getSlot(slotToClick);
        ItemStack before = slot.getStack().copy();
        interactionManager.clickSlot(handler.syncId, slotToClick, 0, SlotActionType.QUICK_MOVE, player);
        scanCursor = (slotToClick + 1) % containerSlots;

        ItemStack after = slot.getStack();
        accumulatedMs = 0L;
        currentDelayMs = jitteredDelayMs();

        if (areEqualIncludingCount(before, after)) {
            return;
        }
    }

    private static boolean isLootableHandler(ScreenHandler handler) {
        return handler instanceof GenericContainerScreenHandler
                || handler instanceof ShulkerBoxScreenHandler
                || handler instanceof HopperScreenHandler
                || handler instanceof Generic3x3ContainerScreenHandler;
    }

    private static int containerSlotCount(ScreenHandler handler) {
        int total = handler.slots.size();
        int container = total - PLAYER_INV_SLOT_COUNT;
        return Math.max(container, 0);
    }

    private static int findNextLootableContainerSlot(
            ScreenHandler handler,
            int containerSlots,
            int startIndex,
            PlayerEntity player,
            PlayerInventory inventory
    ) {
        for (int offset = 0; offset < containerSlots; offset++) {
            int slotId = (startIndex + offset) % containerSlots;
            Slot slot = handler.getSlot(slotId);
            if (!slot.canTakeItems(player)) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (!hasRoomFor(inventory, stack)) {
                continue;
            }
            return slotId;
        }
        return -1;
    }

    private static boolean hasRoomFor(PlayerInventory inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        for (int i = 0; i < PLAYER_INV_SLOT_COUNT; i++) {
            ItemStack invStack = inventory.getStack(i);
            if (invStack.isEmpty()) {
                return true;
            }
            if (ItemStack.areItemsAndComponentsEqual(invStack, stack) && invStack.getCount() < invStack.getMaxCount()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContainerEmpty(ScreenHandler handler, int containerSlots) {
        for (int i = 0; i < containerSlots; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private long jitteredDelayMs() {
        long base = Math.round(delayMs.value());
        base = Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, base));
        double factor = ThreadLocalRandom.current().nextDouble(0.80, 1.20);
        long jittered = Math.round(base * factor);
        return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, jittered));
    }

    private static boolean areEqualIncludingCount(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && ItemStack.areEqual(a, b);
    }

    private void beginSession(int syncId) {
        activeSyncId = syncId;
        warmupTicks = OPEN_WARMUP_TICKS;
        scanCursor = 0;
        lastTickMs = Util.getMeasuringTimeMs();
        accumulatedMs = 0L;
        currentDelayMs = jitteredDelayMs();
    }

    private void tickTime() {
        long now = Util.getMeasuringTimeMs();
        long delta = now - lastTickMs;
        lastTickMs = now;

        if (delta <= 0L) {
            return;
        }
        if (delta > MAX_TICK_DELTA_MS) {
            delta = MAX_TICK_DELTA_MS;
        }
        accumulatedMs = Math.min(MAX_ACCUMULATED_MS, accumulatedMs + delta);
    }

    private void reset() {
        activeSyncId = -1;
        warmupTicks = 0;
        scanCursor = 0;
        lastTickMs = 0L;
        accumulatedMs = 0L;
        currentDelayMs = jitteredDelayMs();
    }
}
