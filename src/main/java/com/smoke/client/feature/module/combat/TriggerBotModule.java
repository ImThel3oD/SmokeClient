package com.smoke.client.feature.module.combat;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class TriggerBotModule extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("range", "Range", "Maximum trigger distance.", 3.0D, 1.0D, 6.0D, 0.1D));
    private final NumberSetting minCps = addSetting(new NumberSetting("min_cps", "Min CPS", "Minimum clicks per second.", 9.0D, 7.0D, 20.0D, 1.0D));
    private final NumberSetting maxCps = addSetting(new NumberSetting("max_cps", "Max CPS", "Maximum clicks per second.", 12.0D, 7.0D, 20.0D, 1.0D));
    private final BoolSetting players = addSetting(new BoolSetting("players", "Players", "Trigger on players.", true));
    private final BoolSetting mobs = addSetting(new BoolSetting("mobs", "Mobs", "Trigger on hostile mobs.", false));
    private final BoolSetting animals = addSetting(new BoolSetting("animals", "Animals", "Trigger on passive animals.", false));
    private Entity target;
    private double delayTicks;
    private double elapsedTicks;

    public TriggerBotModule(ModuleContext context) {
        super(context, "trigger_bot", "Trigger Bot", "Automatically clicks when your real crosshair is already on a valid target.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.POST) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = client.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (client.player == null || client.world == null || client.interactionManager == null || !valid(client, entity)) { reset(); return; }
        if (entity != target) { target = entity; elapsedTicks = 0.0D; delayTicks = nextDelay(); return; }
        if (++elapsedTicks < delayTicks || client.player.getAttackCooldownProgress(0.0F) < 1.0F) return;
        client.interactionManager.attackEntity(client.player, entity);
        client.player.swingHand(Hand.MAIN_HAND);
        elapsedTicks = 0.0D;
        delayTicks = nextDelay();
    }

    @Override
    protected void onDisable() { reset(); }

    private boolean valid(MinecraftClient client, Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || client.player.squaredDistanceTo(entity) > range.value() * range.value()) return false;
        if (entity instanceof PlayerEntity) return players.value() && !AntiBot.isBot(entity);
        if (entity instanceof Monster) return mobs.value();
        return entity instanceof AnimalEntity && animals.value();
    }

    private double nextDelay() {
        int min = (int) Math.round(minCps.value()), max = Math.max(min, (int) Math.round(maxCps.value()));
        return 20.0D / ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void reset() {
        target = null;
        delayTicks = 0.0D;
        elapsedTicks = 0.0D;
    }
}
