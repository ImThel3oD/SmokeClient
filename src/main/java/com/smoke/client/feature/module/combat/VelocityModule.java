package com.smoke.client.feature.module.combat;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.PacketInboundEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.EnumSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class VelocityModule extends Module {
    private final EnumSetting<Mode> mode = addSetting(new EnumSetting<>("mode", "Mode", "Velocity behavior.", Mode.class, Mode.JUMP));
    private final NumberSetting horizontal = addSetting(new NumberSetting("horizontal", "Horizontal", "Horizontal knockback kept percentage.", 80.0D, 0.0D, 100.0D, 1.0D));
    private final NumberSetting vertical = addSetting(new NumberSetting("vertical", "Vertical", "Vertical knockback kept percentage.", 100.0D, 0.0D, 100.0D, 1.0D));

    public VelocityModule(ModuleContext context) {
        super(context, "velocity", "Velocity", "Uses vanilla movement actions to soften knockback response.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
        horizontal.visibleWhen(() -> mode.value() == Mode.REDUCE);
        vertical.visibleWhen(() -> mode.value() == Mode.REDUCE);
    }

    @Override
    public String displaySuffix() {
        return mode.displayValue();
    }

    @Subscribe
    private void onPacket(PacketInboundEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (mode.value() == Mode.REDUCE) {
            Packet<?> reduced = reduce(event.packet(), client.player.getId());
            if (reduced != null) event.replace(reduced);
            return;
        }
        if (!isKnockback(event.packet(), client.player.getId())) return;
        switch (mode.value()) {
            case JUMP -> client.execute(() -> client.execute(() -> {
                if (client.player != null && client.player.isOnGround()) client.player.jump();
            }));
            case REVERSE -> context().packets().send(new PlayerMoveC2SPacket.Full(
                    client.player.getPos(), client.player.getYaw(), client.player.getPitch(), client.player.isOnGround(), client.player.horizontalCollision));
            case SPRINT_RESET -> {
                context().packets().send(new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                context().packets().send(new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }
            case REDUCE -> { }
        }
    }

    private boolean isKnockback(Packet<?> packet, int playerId) {
        return packet instanceof EntityVelocityUpdateS2CPacket velocity && velocity.getEntityId() == playerId
                || packet instanceof ExplosionS2CPacket explosion && explosion.playerKnockback().isPresent();
    }

    private Packet<?> reduce(Packet<?> packet, int playerId) {
        double h = horizontal.value() / 100.0D, v = vertical.value() / 100.0D;
        if (packet instanceof EntityVelocityUpdateS2CPacket velocity && velocity.getEntityId() == playerId) {
            return new EntityVelocityUpdateS2CPacket(playerId, new Vec3d(velocity.getVelocityX() * h, velocity.getVelocityY() * v, velocity.getVelocityZ() * h));
        }
        if (packet instanceof ExplosionS2CPacket explosion && explosion.playerKnockback().isPresent()) {
            return new ExplosionS2CPacket(
                    explosion.center(),
                    explosion.playerKnockback().map(knockback -> new Vec3d(knockback.x * h, knockback.y * v, knockback.z * h)),
                    explosion.explosionParticle(),
                    explosion.explosionSound()
            );
        }
        return null;
    }

    private enum Mode {
        JUMP,
        REVERSE,
        SPRINT_RESET,
        REDUCE
    }
}
