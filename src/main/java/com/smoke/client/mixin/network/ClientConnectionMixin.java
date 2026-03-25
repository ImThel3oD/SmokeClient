package com.smoke.client.mixin.network;

import com.smoke.client.SmokeClient;
import com.smoke.client.feature.module.combat.KnockbackDelayModule;
import com.smoke.client.feature.module.player.FakeLagModule;
import com.smoke.client.network.PacketService;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin implements FakeLagModule.Sender {
    @Shadow
    public abstract NetworkSide getSide();

    @Invoker("sendImmediately")
    public abstract void smoke$sendImmediately(Packet<?> packet, ChannelFutureListener listener, boolean flush);

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void smoke$onInbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (SmokeClient.getRuntime() == null) {
            return;
        }

        PacketService.PacketDecision decision = SmokeClient.getRuntime().packetService().prepareInbound(packet);
        if (decision.cancelled()) {
            SmokeClient.getRuntime().packetService().clearPreparedInbound(packet);
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Packet<?> smoke$rewriteInbound(Packet<?> packet) {
        if (SmokeClient.getRuntime() == null) {
            return packet;
        }
        return SmokeClient.getRuntime().packetService().rewriteInbound(packet);
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("TAIL")
    )
    private void smoke$clearInbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (SmokeClient.getRuntime() != null) {
            SmokeClient.getRuntime().packetService().clearPreparedInbound(packet);
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void smoke$onOutbound(Packet<?> packet, ChannelFutureListener callback, boolean flush, CallbackInfo ci) {
        if (SmokeClient.getRuntime() == null) {
            return;
        }

        PacketService packetService = SmokeClient.getRuntime().packetService();
        PacketService.PacketDecision decision = packetService.prepareOutbound(packet);
        if (decision.cancelled()) {
            packetService.clearPreparedOutbound(packet);
            ci.cancel();
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/ClientConnection;sendImmediately(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"
            ),
            cancellable = true
    )
    private void smoke$delayOutbound(Packet<?> packet, ChannelFutureListener callback, boolean flush, CallbackInfo ci) {
        if (SmokeClient.getRuntime() == null) {
            return;
        }

        if (KnockbackDelayModule.intercept((ClientConnection) (Object) this, packet, callback, flush)
                || FakeLagModule.intercept((ClientConnection) (Object) this, packet, callback, flush)) {
            SmokeClient.getRuntime().packetService().clearPreparedOutbound();
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Packet<?> smoke$rewriteOutbound(Packet<?> packet) {
        if (SmokeClient.getRuntime() == null) {
            return packet;
        }
        return SmokeClient.getRuntime().packetService().rewriteOutbound(packet);
    }

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("TAIL")
    )
    private void smoke$clearOutbound(Packet<?> packet, ChannelFutureListener callback, boolean flush, CallbackInfo ci) {
        if (SmokeClient.getRuntime() != null) {
            SmokeClient.getRuntime().packetService().clearPreparedOutbound(packet);
        }
    }
}
