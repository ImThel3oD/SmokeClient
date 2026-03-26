package com.smoke.client.mixin;

import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.smoke.client.SmokeClient;
import com.smoke.client.alt.AlteningService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;
import java.net.URL;
import java.util.UUID;

@Mixin(value = YggdrasilMinecraftSessionService.class, remap = false)
public abstract class MixinYggdrasilSessionService {
    private static final String ALTENING_SESSION = "http://sessionserver.thealtening.com/session/minecraft/";

    @Shadow
    @Final
    @Mutable
    private String baseUrl;

    @Shadow
    @Final
    @Mutable
    private URL joinUrl;

    @Shadow
    @Final
    @Mutable
    private URL checkUrl;

    @Unique
    private String smoke$originalBaseUrl;

    @Unique
    private URL smoke$originalJoinUrl;

    @Unique
    private URL smoke$originalCheckUrl;

    @Unique
    private boolean smoke$savedOriginalUrls;

    @Inject(method = "joinServer", at = @At("HEAD"))
    private void smoke$beforeJoinServer(UUID profileId, String authenticationToken, String serverId, CallbackInfo ci) {
        smoke$swapUrls();
    }

    @Inject(method = "fetchProfileUncached", at = @At("HEAD"))
    private void smoke$beforeFetchProfile(UUID profileId, boolean requireSecure, CallbackInfoReturnable<?> cir) {
        smoke$swapUrls();
    }

    @Unique
    private void smoke$swapUrls() {
        if (!smoke$savedOriginalUrls) {
            smoke$originalBaseUrl = this.baseUrl;
            smoke$originalJoinUrl = this.joinUrl;
            smoke$originalCheckUrl = this.checkUrl;
            smoke$savedOriginalUrls = true;
        }

        if (AlteningService.isServiceRedirected()) {
            if (!ALTENING_SESSION.equals(this.baseUrl)) {
                try {
                    this.baseUrl = ALTENING_SESSION;
                    this.joinUrl = URI.create(ALTENING_SESSION + "join").toURL();
                    this.checkUrl = URI.create(ALTENING_SESSION + "hasJoined").toURL();
                } catch (Exception exception) {
                    SmokeClient.LOGGER.warn("Failed to swap Altening session URLs: {}", exception.getMessage());
                }
            }
            return;
        }

        if (smoke$savedOriginalUrls && !this.baseUrl.equals(smoke$originalBaseUrl)) {
            this.baseUrl = smoke$originalBaseUrl;
            this.joinUrl = smoke$originalJoinUrl;
            this.checkUrl = smoke$originalCheckUrl;
        }
    }
}
