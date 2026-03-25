package com.smoke.client.feature.module.combat;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.PacketInboundEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class JumpResetModule extends Module {
    private final NumberSetting percentage = addSetting(new NumberSetting("percentage", "Percentage", "Vertical knockback kept percentage.", 85.0D, 0.0D, 100.0D, 1.0D));
    private final NumberSetting chance = addSetting(new NumberSetting("chance", "Chance", "Activation chance percent per hit.", 100.0D, 1.0D, 100.0D, 1.0D));

    public JumpResetModule(ModuleContext context) {
        super(context, "jump_reset", "JumpReset", "Reduces vertical knockback, then performs a vanilla jump.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public String displaySuffix() {
        return percentage.displayValue() + "%";
    }

    @Subscribe
    private void onPacket(PacketInboundEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(event.packet() instanceof EntityVelocityUpdateS2CPacket velocity) || client.player == null || velocity.getEntityId() != client.player.getId()) return;
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance.value()) return;
        event.replace(new EntityVelocityUpdateS2CPacket(client.player.getId(), new Vec3d(
                velocity.getVelocityX(),
                velocity.getVelocityY() * percentage.value() / 100.0D,
                velocity.getVelocityZ()
        )));
        client.execute(() -> client.execute(() -> {
            if (client.player != null && client.player.isOnGround()) client.player.jump();
        }));
    }
}
