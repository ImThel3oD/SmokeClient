package com.smoke.client.feature.module.player;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.NumberSetting;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ConcurrentLinkedDeque;

public final class FakeLagModule extends Module {
    private static final int MAX_BUFFERED_PACKETS = 1024;
    private static volatile FakeLagModule instance;
    private final NumberSetting delay = addSetting(new NumberSetting("delay", "Delay", "Milliseconds added to outbound play packets.", 200.0D, 0.0D, 2000.0D, 1.0D));
    private final ConcurrentLinkedDeque<Entry> queue = new ConcurrentLinkedDeque<>();

    public FakeLagModule(ModuleContext context) {
        super(context, "fake_lag", "FakeLag", "Delays outbound play packets while preserving send order.", ModuleCategory.EXPLOIT, GLFW.GLFW_KEY_UNKNOWN);
        instance = this;
    }

    public static boolean intercept(ClientConnection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
        return instance != null && instance.queuePacket(connection, packet, listener, flush);
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) return;
        if (!isPlaySessionActive()) {
            disableOutsidePlaySession();
            return;
        }
        flushDuePackets();
    }

    @Override
    protected void onEnable() {
        queue.clear();
    }

    @Override
    protected void onDisable() {
        flushAll();
    }

    public void onDisconnect() {
        disableOutsidePlaySession();
    }

    public void onWorldChange() {
        disableOutsidePlaySession();
    }

    private boolean queuePacket(ClientConnection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
        if (!enabled() || connection == null || packet == null || !connection.isOpen() || !isPlaySession(connection)) return false;
        long now = System.currentTimeMillis();
        queue.addLast(new Entry(connection, packet, listener, flush, now + delay.value().longValue()));
        while (queue.size() > MAX_BUFFERED_PACKETS) sendQueued(queue.pollFirst());
        return true;
    }

    private void flushDuePackets() {
        for (Entry entry; (entry = queue.peekFirst()) != null && entry.releaseAt <= System.currentTimeMillis(); ) sendQueued(queue.pollFirst());
    }

    private void flushAll() {
        for (Entry entry; (entry = queue.pollFirst()) != null; ) sendQueued(entry);
    }

    private void sendQueued(@Nullable Entry entry) {
        if (entry == null || !entry.connection.isOpen() || !isPlaySession(entry.connection)) return;
        ((Sender) entry.connection).smoke$sendImmediately(entry.packet, entry.listener, entry.flush);
    }

    private boolean isPlaySessionActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.world != null && client.getNetworkHandler() != null && isPlaySession(client.getNetworkHandler().getConnection());
    }

    private static boolean isPlaySession(@Nullable ClientConnection connection) {
        return connection != null && connection.isOpen() && connection.getPacketListener() instanceof ClientPlayPacketListener;
    }

    private void disableOutsidePlaySession() {
        queue.clear();
        if (enabled()) context().modules().setEnabled(this, false);
    }

    private record Entry(ClientConnection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, long releaseAt) {
    }

    public interface Sender {
        void smoke$sendImmediately(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush);
    }
}
