package com.smoke.client.feature.module.combat;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class AntiBot extends Module {
    private static AntiBot instance;

    private final BoolSetting tabList = addSetting(new BoolSetting("tab_list", "Tab List", "Flag players missing from the actual tab list.", true));
    private final BoolSetting gamemode = addSetting(new BoolSetting("gamemode", "Gamemode", "Flag players without a valid tab-list gamemode.", true));
    private final BoolSetting ping = addSetting(new BoolSetting("ping", "Ping", "Flag players with zero tab-list latency.", true));
    private final BoolSetting ground = addSetting(new BoolSetting("ground", "Ground", "Flag players that never touch ground after 40 tracked ticks.", true));
    private final BoolSetting movement = addSetting(new BoolSetting("movement", "Movement", "Flag players that barely move after 60 tracked ticks.", true));
    private final BoolSetting invisibility = addSetting(new BoolSetting("invisibility", "Invisibility", "Flag players with the invisibility status effect.", false));
    private final BoolSetting entityId = addSetting(new BoolSetting("entity_id", "Entity ID", "Flag players with suspiciously negative or very large entity IDs.", false));

    private final Map<Integer, TrackingData> tracked = new HashMap<>();
    private int ticks;

    public AntiBot(ModuleContext context) {
        super(context, "anti_bot", "AntiBot", "Flags suspicious player entities so combat modules can skip bait NPCs.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
        instance = this;
    }

    public static boolean isBot(Entity entity) {
        AntiBot antiBot = instance;
        return antiBot != null && antiBot.enabled() && entity instanceof PlayerEntity player && antiBot.flagged(player);
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            reset();
            return;
        }
        ticks++;
        HashSet<Integer> seen = new HashSet<>();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || !player.isAlive() || player.isRemoved()) continue;
            seen.add(player.getId());
            Vec3d pos = player.getPos();
            TrackingData data = tracked.get(player.getId());
            if (data == null) tracked.put(player.getId(), new TrackingData(ticks, player.isOnGround(), 0.0D, pos));
            else {
                data.everOnGround |= player.isOnGround();
                data.totalDistanceMoved += data.lastPos.distanceTo(pos);
                data.lastPos = pos;
            }
        }
        tracked.entrySet().removeIf(entry -> !seen.contains(entry.getKey()));
    }

    private boolean flagged(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (client.player == null || handler == null || player == client.player || !player.isAlive() || player.isRemoved()) return false;
        PlayerListEntry entry = handler.getPlayerListEntry(player.getUuid());
        TrackingData data = tracked.get(player.getId());
        int age = data == null ? 0 : ticks - data.spawnTick;
        return tabList.value() && (entry == null || !handler.getListedPlayerListEntries().contains(entry))
                || gamemode.value() && (entry == null || entry.getGameMode() == GameMode.DEFAULT)
                || ping.value() && (entry == null || entry.getLatency() == 0)
                || ground.value() && data != null && age > 40 && !data.everOnGround
                || movement.value() && data != null && age > 60 && data.totalDistanceMoved < 0.1D
                || invisibility.value() && player.hasStatusEffect(StatusEffects.INVISIBILITY)
                || entityId.value() && suspiciousEntityId(player.getId());
    }

    private static boolean suspiciousEntityId(int id) {
        return id < 0 || id > 1_000_000;
    }

    private void reset() {
        tracked.clear();
        ticks = 0;
    }

    private static final class TrackingData {
        private final int spawnTick;
        private boolean everOnGround;
        private double totalDistanceMoved;
        private Vec3d lastPos;
        private TrackingData(int spawnTick, boolean everOnGround, double totalDistanceMoved, Vec3d lastPos) { this.spawnTick = spawnTick; this.everOnGround = everOnGround; this.totalDistanceMoved = totalDistanceMoved; this.lastPos = lastPos; }
    }
}
