package com.smoke.client.trace;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.event.EventPriority;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.ModuleToggleEvent;
import com.smoke.client.event.events.PacketInboundEvent;
import com.smoke.client.event.events.PacketOutboundEvent;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.rotation.RotationFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ModuleTraceService {
    public static final String ALL_ID = "all";
    public static final String SYSTEM_ID = "system";

    private static final int MAX_ENTRIES_PER_SCOPE = 160;
    private static final int HUD_ENTRY_LIMIT = 8;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    private static final List<String> FLAG_HINTS = List.of(
            "timer",
            "groundspoof",
            "ground spoof",
            "simulation",
            "nofall",
            "no fall",
            "badpackets",
            "flagged",
            "flag",
            "violat"
    );
    private static final List<String> FLAG_EXCLUDES = List.of(
            "/grim verbose",
            "toggle verbose messages",
            "including non flags"
    );

    private final ClientRuntime runtime;
    private final Map<String, Deque<TraceEntry>> entriesByScope = new HashMap<>();
    private final Map<String, String> lastSuffixByModule = new HashMap<>();

    private long currentTick;
    private long sequence;
    private boolean hudEnabled = true;
    private boolean autoFocus = true;
    private final LinkedHashSet<String> focusedScopeIds = new LinkedHashSet<>(List.of(SYSTEM_ID));
    private String lastTouchedModuleId;
    private String lastRotationOwnerId;
    private String lastRotationSummary;

    public ModuleTraceService(ClientRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public synchronized void trace(String moduleId, String channel, String message) {
        String scopeId = normalizeScope(moduleId);
        String normalizedChannel = normalizeChannel(channel);
        String normalizedMessage = normalizeMessage(message);
        if (normalizedMessage.isEmpty()) {
            return;
        }

        TraceEntry entry = new TraceEntry(
                ++sequence,
                currentTick,
                TIME_FORMAT.format(Instant.now()),
                scopeId,
                normalizedChannel,
                normalizedMessage
        );

        Deque<TraceEntry> queue = entriesByScope.computeIfAbsent(scopeId, ignored -> new ArrayDeque<>());
        queue.addLast(entry);
        while (queue.size() > MAX_ENTRIES_PER_SCOPE) {
            queue.removeFirst();
        }

        if (!SYSTEM_ID.equals(scopeId)) {
            lastTouchedModuleId = scopeId;
        }
    }

    public void trace(Module module, String channel, String message) {
        if (module != null) {
            trace(module.id(), channel, message);
        }
    }

    public void traceSystem(String channel, String message) {
        trace(SYSTEM_ID, channel, message);
    }

    public synchronized void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
    }

    public synchronized boolean hudEnabled() {
        return hudEnabled;
    }

    public synchronized void focusAuto() {
        autoFocus = true;
    }

    public synchronized void focusSystem() {
        focusModules(List.of(SYSTEM_ID));
    }

    public synchronized void focusModule(String moduleId) {
        focusModules(List.of(moduleId));
    }

    public synchronized void focusModules(Collection<String> moduleIds) {
        autoFocus = false;
        focusedScopeIds.clear();
        for (String moduleId : moduleIds) {
            String normalized = normalizeScope(moduleId);
            if (ALL_ID.equals(normalized)) {
                focusedScopeIds.clear();
                focusedScopeIds.add(ALL_ID);
                return;
            }
            focusedScopeIds.add(normalized);
        }

        if (focusedScopeIds.isEmpty()) {
            focusedScopeIds.add(SYSTEM_ID);
        }
    }

    public synchronized List<String> focusedScopeIds() {
        return List.copyOf(focusedScopeIds);
    }

    public synchronized boolean autoFocus() {
        return autoFocus;
    }

    public synchronized void clearAll() {
        entriesByScope.clear();
        lastSuffixByModule.clear();
        lastTouchedModuleId = null;
        lastRotationOwnerId = null;
        lastRotationSummary = null;
    }

    public synchronized void clearScope(String scopeId) {
        ScopeSelection selection = resolveRequestedScope(scopeId);
        if (selection.all()) {
            clearAll();
            return;
        }

        Set<String> scopesToClear = new LinkedHashSet<>(selection.explicitScopeIds());
        if (selection.includeSystem()) {
            scopesToClear.add(SYSTEM_ID);
        }
        for (String scope : scopesToClear) {
            clearSingleScope(scope);
        }
    }

    public synchronized TraceView view() {
        if (!hudEnabled) {
            return TraceView.hidden();
        }

        ScopeSelection selection = resolveScope();
        List<TraceEntry> entries = mergedEntries(selection, HUD_ENTRY_LIMIT);
        if (entries.isEmpty()) {
            return TraceView.hidden();
        }

        return new TraceView(
                true,
                buildHeader(selection, entries.size()),
                entries
        );
    }

    public synchronized boolean copyToClipboard(String scopeId) {
        String export = export(scopeId);
        if (export.isBlank()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.keyboard == null) {
            return false;
        }

        client.keyboard.setClipboard(export);
        return true;
    }

    public synchronized String export(String requestedScopeId) {
        ScopeSelection selection = resolveRequestedScope(requestedScopeId);
        List<TraceEntry> merged = mergedEntries(selection, Integer.MAX_VALUE);
        if (merged.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Smoke Module Trace").append('\n');
        builder.append("scope=").append(selectionKey(selection)).append('\n');
        builder.append("label=").append(scopeLabel(selection)).append('\n');
        builder.append("tick=").append(currentTick).append('\n');
        builder.append("entries=").append(merged.size()).append('\n');
        builder.append('\n');
        for (TraceEntry entry : merged) {
            builder.append(entry.exportLine()).append('\n');
        }
        return builder.toString();
    }

    public synchronized String resolvedScopeId() {
        return selectionKey(resolveScope());
    }

    public synchronized String describeScope(String requestedScopeId) {
        return scopeLabel(resolveRequestedScope(requestedScopeId));
    }

    public synchronized int entryCount(String requestedScopeId) {
        return mergedEntries(resolveRequestedScope(requestedScopeId), Integer.MAX_VALUE).size();
    }

    @Subscribe(priority = EventPriority.LOWEST)
    private void onTick(TickEvent event) {
        if (event.phase() == TickEvent.Phase.PRE) {
            synchronized (this) {
                currentTick++;
            }
            return;
        }

        observeSuffixChanges();
        observeRotation();
    }

    @Subscribe
    private void onModuleToggle(ModuleToggleEvent event) {
        trace(event.module(), "module", event.enabled() ? "enabled" : "disabled");
    }

    @Subscribe(priority = EventPriority.LOWEST)
    private void onPacketInbound(PacketInboundEvent event) {
        Object packet = event.packet();
        if (packet instanceof PlayerPositionLookS2CPacket correction) {
            Vec3d pos = correction.change().position();
            String message = String.format(
                    Locale.ROOT,
                    "position-look tp=%d pos=%.2f %.2f %.2f yaw=%.1f pitch=%.1f rel=%s",
                    correction.teleportId(),
                    pos.x, pos.y, pos.z,
                    cleanZero(correction.change().yaw()),
                    cleanZero(correction.change().pitch()),
                    correction.relatives()
            );
            traceSystem("server", message);
            traceActiveRotationOwner("server", message);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int playerId = client != null && client.player != null ? client.player.getId() : Integer.MIN_VALUE;
        if (packet instanceof EntityVelocityUpdateS2CPacket velocity && velocity.getEntityId() == playerId) {
            String message = String.format(
                    Locale.ROOT,
                    "velocity %.3f %.3f %.3f",
                    velocity.getVelocityX(),
                    velocity.getVelocityY(),
                    velocity.getVelocityZ()
            );
            traceSystem("server", message);
            return;
        }

        if (packet instanceof ExplosionS2CPacket explosion && explosion.playerKnockback().isPresent()) {
            Vec3d knockback = explosion.playerKnockback().orElse(Vec3d.ZERO);
            String message = String.format(
                    Locale.ROOT,
                    "explosion kb=%.3f %.3f %.3f",
                    knockback.x,
                    knockback.y,
                    knockback.z
            );
            traceSystem("server", message);
        }

        FlagMessage flagMessage = extractFlagMessage(packet);
        if (flagMessage != null) {
            String message = flagMessage.source() + " " + flagMessage.message();
            traceSystem("flag", message);
            traceLikelyModule("flag", message);
        }
    }

    @Subscribe(priority = EventPriority.LOWEST)
    private void onPacketOutbound(PacketOutboundEvent event) {
        RotationFrame frame = runtime.rotationService().currentFrame();
        if (!frame.active()) {
            return;
        }

        if (!(event.packet() instanceof PlayerMoveC2SPacket movePacket)) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        if (!movePacket.changesLook()) {
            return;
        }

        String kind = movePacket instanceof PlayerMoveC2SPacket.Full ? "full" : "look";
        String message = String.format(
                Locale.ROOT,
                "%s yaw=%.1f pitch=%.1f on=%s hc=%s",
                kind,
                displayYaw(movePacket.getYaw(player.getYaw())),
                cleanZero(movePacket.getPitch(player.getPitch())),
                movePacket.isOnGround(),
                movePacket.horizontalCollision()
        );
        trace(frame.ownerId(), "packet", message);
    }

    private void observeSuffixChanges() {
        synchronized (this) {
            for (Module module : runtime.moduleManager().all()) {
                if (!module.enabled()) {
                    lastSuffixByModule.remove(module.id());
                    continue;
                }

                String suffix = Objects.toString(module.displaySuffix(), "");
                String previous = lastSuffixByModule.put(module.id(), suffix);
                if (previous == null) {
                    if (!suffix.isBlank()) {
                        trace(module.id(), "state", "suffix=" + suffix);
                    }
                } else if (!previous.equals(suffix)) {
                    trace(module.id(), "state", suffix.isBlank() ? "suffix cleared" : "suffix=" + suffix);
                }
            }
        }
    }

    private void observeRotation() {
        RotationFrame frame = runtime.rotationService().currentFrame();
        synchronized (this) {
            if (!frame.active()) {
                if (lastRotationOwnerId != null) {
                    trace(lastRotationOwnerId, "rotation", "released");
                    lastRotationOwnerId = null;
                    lastRotationSummary = null;
                }
                return;
            }

            String summary = String.format(
                    Locale.ROOT,
                    "pkt=%.1f/%.1f tgt=%.1f/%.1f move=%s dig=%s",
                    displayYaw(frame.packetYaw()),
                    cleanZero(frame.packetPitch()),
                    displayYaw(frame.targetYaw()),
                    cleanZero(frame.targetPitch()),
                    frame.applyMovementCorrection(),
                    frame.applyDigitalInputCorrection()
            );

            if (!frame.ownerId().equals(lastRotationOwnerId)) {
                if (lastRotationOwnerId != null) {
                    trace(lastRotationOwnerId, "rotation", "released");
                }
                trace(frame.ownerId(), "rotation", "claimed " + summary);
                lastRotationOwnerId = frame.ownerId();
                lastRotationSummary = summary;
                return;
            }

            if (!summary.equals(lastRotationSummary)) {
                trace(frame.ownerId(), "rotation", summary);
                lastRotationSummary = summary;
            }
        }
    }

    private void traceActiveRotationOwner(String channel, String message) {
        RotationFrame frame = runtime.rotationService().currentFrame();
        if (frame.active()) {
            trace(frame.ownerId(), channel, message);
        }
    }

    private void traceLikelyModule(String channel, String message) {
        RotationFrame frame = runtime.rotationService().currentFrame();
        if (frame.active()) {
            trace(frame.ownerId(), channel, message);
            return;
        }
        if (lastTouchedModuleId != null) {
            trace(lastTouchedModuleId, channel, message);
        }
    }

    private void clearSingleScope(String scopeId) {
        String normalized = normalizeScope(scopeId);
        entriesByScope.remove(normalized);
        lastSuffixByModule.remove(normalized);
        if (normalized.equals(lastTouchedModuleId)) {
            lastTouchedModuleId = null;
        }
        if (normalized.equals(lastRotationOwnerId)) {
            lastRotationOwnerId = null;
            lastRotationSummary = null;
        }
    }

    private List<TraceEntry> mergedEntries(ScopeSelection selection, int limit) {
        List<TraceEntry> merged = new ArrayList<>();
        if (selection.all()) {
            for (Deque<TraceEntry> queue : entriesByScope.values()) {
                merged.addAll(queue);
            }
        } else {
            Set<String> scopeIds = new LinkedHashSet<>(selection.explicitScopeIds());
            if (selection.includeSystem()) {
                scopeIds.add(SYSTEM_ID);
            }
            for (String scopeId : scopeIds) {
                merged.addAll(snapshot(scopeId));
            }
        }
        merged.sort(Comparator.comparingLong(TraceEntry::sequence));
        if (merged.size() <= limit) {
            return merged;
        }
        return new ArrayList<>(merged.subList(merged.size() - limit, merged.size()));
    }

    private List<TraceEntry> snapshot(String scopeId) {
        Deque<TraceEntry> queue = entriesByScope.get(scopeId);
        return queue == null ? List.of() : new ArrayList<>(queue);
    }

    private ScopeSelection resolveScope() {
        if (!autoFocus) {
            return scopeSelection(focusedScopeIds);
        }

        RotationFrame frame = runtime.rotationService().currentFrame();
        if (frame.active()) {
            return scopeSelection(List.of(frame.ownerId()));
        }
        if (lastTouchedModuleId != null) {
            return scopeSelection(List.of(lastTouchedModuleId));
        }
        return scopeSelection(List.of(SYSTEM_ID));
    }

    private ScopeSelection resolveRequestedScope(String requestedScopeId) {
        if (requestedScopeId == null || requestedScopeId.isBlank()) {
            return resolveScope();
        }

        String normalized = requestedScopeId.trim().toLowerCase(Locale.ROOT);
        if ("current".equals(normalized) || "auto".equals(normalized)) {
            return resolveScope();
        }

        String[] parts = normalized.split(",");
        List<String> scopeIds = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                scopeIds.add(part);
            }
        }
        return scopeSelection(scopeIds);
    }

    private String buildHeader(ScopeSelection selection, int entryCount) {
        String label = scopeLabel(selection);
        String prefix = autoFocus && !selection.all() ? "Trace[auto:" + label + "]" : "Trace[" + label + "]";
        return prefix + " x" + entryCount;
    }

    private String scopeLabel(ScopeSelection selection) {
        if (selection.all()) {
            return ALL_ID;
        }

        List<String> scopeIds = selection.explicitScopeIds();
        if (scopeIds.size() == 1 && SYSTEM_ID.equals(scopeIds.get(0)) && !selection.includeSystem()) {
            return SYSTEM_ID;
        }

        List<String> labels = scopeIds.stream()
                .map(this::scopeLabel)
                .toList();
        if (labels.size() == 1) {
            return labels.get(0);
        }
        if (labels.size() == 2) {
            return labels.get(0) + " + " + labels.get(1);
        }
        return labels.get(0) + " + " + (labels.size() - 1) + " more";
    }

    private String scopeLabel(String scopeId) {
        if (ALL_ID.equals(scopeId) || SYSTEM_ID.equals(scopeId)) {
            return scopeId;
        }
        return runtime.moduleManager().getById(scopeId)
                .map(Module::name)
                .orElse(scopeId);
    }

    private String selectionKey(ScopeSelection selection) {
        if (selection.all()) {
            return ALL_ID;
        }
        return String.join(",", selection.explicitScopeIds());
    }

    private ScopeSelection scopeSelection(Collection<String> scopeIds) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        boolean includeSystem = false;
        for (String scopeId : scopeIds) {
            String normalizedScope = normalizeScope(scopeId);
            if (ALL_ID.equals(normalizedScope)) {
                return new ScopeSelection(List.of(ALL_ID), false, true);
            }
            if (SYSTEM_ID.equals(normalizedScope)) {
                includeSystem = true;
                continue;
            }
            normalized.add(normalizedScope);
        }

        if (normalized.isEmpty()) {
            return new ScopeSelection(List.of(SYSTEM_ID), false, false);
        }
        return new ScopeSelection(List.copyOf(normalized), true, false);
    }

    private static String normalizeScope(String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            return SYSTEM_ID;
        }
        return scopeId.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "trace";
        }
        return channel.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMessage(String message) {
        return message == null ? "" : message.trim();
    }

    private static float cleanZero(float value) {
        return value == 0.0F ? 0.0F : value;
    }

    private static float displayYaw(float value) {
        return cleanZero(MathHelper.wrapDegrees(value));
    }

    private static FlagMessage extractFlagMessage(Object packet) {
        String source;
        Text text;

        if (packet instanceof OverlayMessageS2CPacket overlay) {
            source = "overlay";
            text = overlay.text();
        } else if (packet instanceof GameMessageS2CPacket gameMessage) {
            source = gameMessage.overlay() ? "overlay" : "game";
            text = gameMessage.content();
        } else if (packet instanceof ProfilelessChatMessageS2CPacket profilelessChat) {
            source = "chat";
            text = profilelessChat.message();
        } else if (packet instanceof ChatMessageS2CPacket chatMessage) {
            source = "chat";
            Text unsigned = chatMessage.unsignedContent();
            text = unsigned != null ? unsigned : Text.literal(chatMessage.body().content());
        } else {
            return null;
        }

        String message = normalizeMessage(text == null ? "" : text.getString());
        if (message.isEmpty() || !looksLikeFlagMessage(message)) {
            return null;
        }
        return new FlagMessage(source, message);
    }

    private static boolean looksLikeFlagMessage(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        for (String exclude : FLAG_EXCLUDES) {
            if (normalized.contains(exclude)) {
                return false;
            }
        }
        for (String hint : FLAG_HINTS) {
            if (normalized.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    public record TraceView(boolean visible, String header, List<TraceEntry> entries) {
        public static TraceView hidden() {
            return new TraceView(false, "", List.of());
        }
    }

    public record TraceEntry(
            long sequence,
            long tick,
            String time,
            String scopeId,
            String channel,
            String message
    ) {
        public String hudLine() {
            return "t" + tick + " [" + channel + "] " + message;
        }

        public String exportLine() {
            return "[" + time + "][t" + tick + "][" + scopeId + "][" + channel + "] " + message;
        }
    }

    private record ScopeSelection(List<String> explicitScopeIds, boolean includeSystem, boolean all) {
    }

    private record FlagMessage(String source, String message) {
    }
}
