package com.smoke.client.feature.module.movement;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.ClipAtLedgeEvent;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.feature.module.world.scaffold.LegitScaffoldInput;
import com.smoke.client.mixin.accessor.InputAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.EnumSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;

public final class SafeWalkModule extends Module {
    private final EnumSetting<Mode> mode = addSetting(new EnumSetting<>("mode", "Mode", "How SafeWalk protects edges.", Mode.class, Mode.NORMAL));
    private final BoolSetting direction = addSetting(new BoolSetting("direction", "Direction", "Allow forward movement off edges.", true));
    private final LegitScaffoldInput input = new LegitScaffoldInput();

    private boolean clipTriggeredThisTick;
    private boolean keepLegitSneak;

    public SafeWalkModule(ModuleContext context) {
        super(context, "safe_walk", "SafeWalk", "Stops the player from walking off edges.", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public String displaySuffix() {
        return mode.displayValue();
    }

    @Override
    protected void onEnable() {
        clipTriggeredThisTick = false;
        keepLegitSneak = false;
        input.reset();
    }

    @Override
    protected void onDisable() {
        clipTriggeredThisTick = false;
        keepLegitSneak = false;
        input.reset();
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() == TickEvent.Phase.PRE) {
            clipTriggeredThisTick = false;
            syncLegitSneak();
            return;
        }

        keepLegitSneak = mode.value() == Mode.LEGIT && clipTriggeredThisTick;
        syncLegitSneak();
    }

    @Subscribe
    private void onClipAtLedge(ClipAtLedgeEvent event) {
        if (shouldForceClip(event.player())) {
            clipTriggeredThisTick = true;
            if (mode.value() == Mode.LEGIT) {
                keepLegitSneak = true;
                syncLegitSneak();
            }
            event.forceClip(true);
        }
    }

    public boolean shouldForceClip(ClientPlayerEntity player) {
        return enabled() && (!direction.value() || ((InputAccessor) player.input).smoke$getMovementVector().y <= 0.0F);
    }

    private void syncLegitSneak() {
        input.forceSneak(enabled() && mode.value() == Mode.LEGIT && keepLegitSneak);
    }

    private enum Mode {
        NORMAL,
        LEGIT
    }
}
