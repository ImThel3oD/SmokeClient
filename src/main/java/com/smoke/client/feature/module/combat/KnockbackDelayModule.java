package com.smoke.client.feature.module.combat;

import com.smoke.client.event.EventPriority;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.AttackEntityPostEvent;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.network.ImmediatePacketSender;
import com.smoke.client.network.OutboundPacketInterceptor;
import com.smoke.client.setting.NumberSetting;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;

public final class KnockbackDelayModule extends Module implements OutboundPacketInterceptor {
    private final NumberSetting chanceMin = addSetting(new NumberSetting("chance_min", "Chance Min", "Minimum activation chance percent.", 80.0D, 10.0D, 100.0D, 1.0D));
    private final NumberSetting chanceMax = addSetting(new NumberSetting("chance_max", "Chance Max", "Maximum activation chance percent.", 100.0D, 10.0D, 100.0D, 1.0D));
    private final NumberSetting airMin = addSetting(new NumberSetting("air_delay_min", "Air Delay Min", "Minimum airborne hold in milliseconds.", 200.0D, 50.0D, 5000.0D, 1.0D));
    private final NumberSetting airMax = addSetting(new NumberSetting("air_delay_max", "Air Delay Max", "Maximum airborne hold in milliseconds.", 400.0D, 50.0D, 5000.0D, 1.0D));
    private final NumberSetting groundMin = addSetting(new NumberSetting("ground_delay_min", "Ground Delay Min", "Minimum grounded hold in milliseconds.", 150.0D, 50.0D, 5000.0D, 1.0D));
    private final NumberSetting groundMax = addSetting(new NumberSetting("ground_delay_max", "Ground Delay Max", "Maximum grounded hold in milliseconds.", 300.0D, 50.0D, 5000.0D, 1.0D));
    private final ConcurrentLinkedDeque<Entry> queue = new ConcurrentLinkedDeque<>();
    private long releaseAt;

    public KnockbackDelayModule(ModuleContext context) { super(context, "knockback_delay", "KnockbackDelay", "Delays outbound packets briefly after hits so knockback lands later.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN); }

    public void trigger(Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.isRemoved() || client.player == null) return;
        if (releaseAt == 0L && ThreadLocalRandom.current().nextDouble(100.0D) > roll(chanceMin, chanceMax)) return;
        releaseAt = System.currentTimeMillis() + Math.round(client.player.isOnGround() ? roll(groundMin, groundMax) : roll(airMin, airMax));
    }

    @Subscribe
    private void onTick(TickEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (event.phase() != TickEvent.Phase.PRE) return;
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) { queue.clear(); releaseAt = 0L; return; }
        if (releaseAt != 0L && System.currentTimeMillis() >= releaseAt) { flushAll(); releaseAt = 0L; }
    }

    @Override protected void onEnable() { queue.clear(); releaseAt = 0L; }
    @Override protected void onDisable() { releaseAt = 0L; flushAll(); }

    @Subscribe(priority = EventPriority.LOW)
    private void onAttackEntityPost(AttackEntityPostEvent event) {
        trigger(event.target());
    }

    @Override
    public boolean interceptOutbound(ClientConnection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
        if (!enabled() || releaseAt == 0L || connection == null || packet == null || !connection.isOpen() || packet instanceof KeepAliveC2SPacket) return false;
        queue.addLast(new Entry(connection, packet, listener, flush)); return true;
    }

    private void flushAll() { for (Entry entry; (entry = queue.pollFirst()) != null; ) if (entry.connection.isOpen()) ((ImmediatePacketSender) entry.connection).smoke$sendImmediately(entry.packet, entry.listener, entry.flush); }
    private static double roll(NumberSetting min, NumberSetting max) { double a = Math.min(min.value(), max.value()), b = Math.max(min.value(), max.value()); return a == b ? a : ThreadLocalRandom.current().nextDouble(a, b); }
    private record Entry(ClientConnection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {}
}
