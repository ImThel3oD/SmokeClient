package com.smoke.client.feature.module.combat;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.WorldRenderEvent;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class AimAssistModule extends Module {
    private final NumberSetting reach = addSetting(new NumberSetting("reach", "Reach", "Maximum target distance.", 4.5D, 1.0D, 6.0D, 0.1D));
    private final NumberSetting fov = addSetting(new NumberSetting("fov", "FOV", "Aim assist cone in degrees.", 60.0D, 10.0D, 180.0D, 1.0D));
    private final NumberSetting speed = addSetting(new NumberSetting("speed", "Speed", "Fraction of the remaining angle applied per tick.", 0.3D, 0.05D, 1.0D, 0.05D));
    private final BoolSetting players = addSetting(new BoolSetting("players", "Players", "Track player targets.", true));
    private final BoolSetting mobs = addSetting(new BoolSetting("mobs", "Mobs", "Track hostile mobs.", false));
    private final BoolSetting animals = addSetting(new BoolSetting("animals", "Animals", "Track animal targets.", false));

    public AimAssistModule(ModuleContext context) {
        super(context, "aim_assist", "Aim Assist", "Gently nudges the real camera toward nearby targets already inside your FOV.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Subscribe
    private void onWorldRender(WorldRenderEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        float tickProgress = event.context().tickCounter().getTickProgress(true);
        float deltaTicks = event.context().tickCounter().getDynamicDeltaTicks();
        if (deltaTicks <= 0.0F) return;
        double reachSq = reach.value() * reach.value(), closestSq = reachSq;
        Vec3d eye = client.player.getCameraPosVec(tickProgress), look = client.player.getRotationVec(1.0F);
        Entity closest = null;
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || !entity.isAlive() || entity.isRemoved() || !matches(entity)) continue;
            double distSq = client.player.squaredDistanceTo(entity);
            if (distSq > closestSq) continue;
            Vec3d dir = entity.getCameraPosVec(tickProgress).subtract(eye);
            if (dir.lengthSquared() == 0.0D) continue;
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(dir.normalize()), -1.0D, 1.0D)));
            if (angle > fov.value() * 0.5D) continue;
            closest = entity;
            closestSq = distSq;
        }
        if (closest == null) return;
        Vec3d delta = closest.getCameraPosVec(tickProgress).subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        float step = 1.0F - (float) Math.pow(1.0D - speed.value(), deltaTicks);
        float yawDelta = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());
        float pitchDelta = targetPitch - client.player.getPitch();
        client.player.setYaw(client.player.getYaw() + yawDelta * step);
        client.player.setPitch(MathHelper.clamp(client.player.getPitch() + pitchDelta * step, -90.0F, 90.0F));
    }

    private boolean matches(Entity entity) {
        if (entity instanceof PlayerEntity) return players.value() && !AntiBot.isBot(entity);
        if (entity instanceof Monster) return mobs.value();
        return entity instanceof AnimalEntity && animals.value();
    }
}
