package com.smoke.client.feature.module.player;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.AttackBlockEvent;
import com.smoke.client.event.events.HandleInputEvent;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

public final class AutoToolModule extends Module {
    private static final float MIN_IMPROVEMENT = 1.0E-4F;

    private final BoolSetting preserveTools = addSetting(new BoolSetting(
            "preserve_tools",
            "Preserve Tools",
            "Avoid switching to tools with 1 durability remaining.",
            true
    ));

    private BlockPos miningPos;
    private int restoreSlot = -1;
    private int autoSlot = -1;

    public AutoToolModule(ModuleContext context) {
        super(
                context,
                "auto_tool",
                "AutoTool",
                "Automatically switches to the fastest hotbar tool while mining.",
                ModuleCategory.PLAYER,
                GLFW.GLFW_KEY_UNKNOWN
        );
    }

    @Override
    protected void onDisable() {
        restoreIfNeeded();
        clearState();
    }

    @Subscribe
    private void onAttackBlock(AttackBlockEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        if (client.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        if (restoreSlot != -1 && autoSlot != -1 && inventory.getSelectedSlot() != autoSlot) {
            clearState();
        }

        BlockPos pos = event.pos();
        if (miningPos != null && miningPos.equals(pos)) {
            return;
        }

        BlockState state = client.world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        int selectedSlot = inventory.getSelectedSlot();
        int bestSlot = findBestHotbarSlot(client.player, client.world, pos, state);
        miningPos = pos;

        if (bestSlot == selectedSlot) {
            if (restoreSlot != -1) {
                autoSlot = selectedSlot;
            } else {
                autoSlot = -1;
            }
            return;
        }

        if (restoreSlot == -1) {
            restoreSlot = selectedSlot;
        }

        inventory.setSelectedSlot(bestSlot);
        autoSlot = bestSlot;
    }

    @Subscribe
    private void onHandleInput(HandleInputEvent event) {
        if (event.phase() != HandleInputEvent.Phase.PRE) {
            return;
        }

        if (restoreSlot == -1) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            clearState();
            return;
        }

        int selected = client.player.getInventory().getSelectedSlot();
        if (autoSlot != -1 && selected != autoSlot) {
            clearState();
            return;
        }

        if (!isMining(client)) {
            restoreIfNeeded();
            clearState();
        }
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) {
            return;
        }

        if (restoreSlot == -1) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            clearState();
            return;
        }

        // If input handling won't run (GUI open) or attack isn't held, restore immediately.
        if (client.currentScreen != null || client.getOverlay() != null || client.options == null || !client.options.attackKey.isPressed()) {
            restoreIfNeeded();
            clearState();
        }
    }

    private static boolean isMining(MinecraftClient client) {
        if (client.currentScreen != null || client.getOverlay() != null || client.options == null || !client.options.attackKey.isPressed()) {
            return false;
        }
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (client.player == null || client.interactionManager == null) {
            return false;
        }
        if (client.player.isUsingItem()) {
            return false;
        }
        if (client.world != null && client.crosshairTarget instanceof BlockHitResult blockHitResult) {
            if (client.world.getBlockState(blockHitResult.getBlockPos()).isAir()) {
                return false;
            }
        }
        return client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR;
    }

    private int findBestHotbarSlot(ClientPlayerEntity player, BlockView world, BlockPos pos, BlockState state) {
        PlayerInventory inventory = player.getInventory();
        int originalSlot = inventory.getSelectedSlot();

        boolean toolRequired = state.isToolRequired();
        boolean hasSuitable = !toolRequired;
        if (toolRequired) {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (!stack.isEmpty() && stack.isSuitableFor(state)) {
                    hasSuitable = true;
                    break;
                }
            }
        }

        float bestDelta = calcDeltaWithSelectedSlot(player, world, pos, state, originalSlot);
        int bestSlot = originalSlot;

        if (hasSuitable && toolRequired && !inventory.getStack(originalSlot).isSuitableFor(state)) {
            // If the block requires a tool and we have at least one suitable tool available, always prefer a suitable tool over the current slot.
            bestDelta = Float.NEGATIVE_INFINITY;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (slot == originalSlot) {
                continue;
            }

            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (hasSuitable && toolRequired && !stack.isSuitableFor(state)) {
                continue;
            }
            if (preserveTools.value() && stack.willBreakNextUse()) {
                continue;
            }

            float delta = calcDeltaWithSelectedSlot(player, world, pos, state, slot);
            if (delta > bestDelta + MIN_IMPROVEMENT) {
                bestDelta = delta;
                bestSlot = slot;
            }
        }

        if (!preserveTools.value() || bestSlot != originalSlot) {
            return bestSlot;
        }

        // If we didn't find anything better, allow last-durability tools as a fallback.
        for (int slot = 0; slot < 9; slot++) {
            if (slot == originalSlot) {
                continue;
            }

            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !stack.willBreakNextUse()) {
                continue;
            }
            if (hasSuitable && toolRequired && !stack.isSuitableFor(state)) {
                continue;
            }

            float delta = calcDeltaWithSelectedSlot(player, world, pos, state, slot);
            if (delta > bestDelta + MIN_IMPROVEMENT) {
                bestDelta = delta;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private static float calcDeltaWithSelectedSlot(ClientPlayerEntity player, BlockView world, BlockPos pos, BlockState state, int slot) {
        PlayerInventory inventory = player.getInventory();
        int originalSlot = inventory.getSelectedSlot();
        if (originalSlot == slot) {
            return state.calcBlockBreakingDelta(player, world, pos);
        }

        inventory.setSelectedSlot(slot);
        try {
            return state.calcBlockBreakingDelta(player, world, pos);
        } finally {
            inventory.setSelectedSlot(originalSlot);
        }
    }

    private void restoreIfNeeded() {
        if (restoreSlot == -1) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        if (autoSlot != -1 && inventory.getSelectedSlot() != autoSlot) {
            return;
        }

        inventory.setSelectedSlot(restoreSlot);
    }

    private void clearState() {
        miningPos = null;
        restoreSlot = -1;
        autoSlot = -1;
    }
}
