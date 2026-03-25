package com.smoke.client.bootstrap;

import com.smoke.client.SmokeClient;
import com.smoke.client.command.CommandDispatcher;
import com.smoke.client.command.impl.BindCommand;
import com.smoke.client.command.impl.HelpCommand;
import com.smoke.client.command.impl.ModulesCommand;
import com.smoke.client.command.impl.TraceCommand;
import com.smoke.client.command.impl.ToggleCommand;
import com.smoke.client.config.ConfigService;
import com.smoke.client.event.EventBus;
import com.smoke.client.event.events.HudRenderEvent;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.event.events.WorldRenderEvent;
import com.smoke.client.feature.module.combat.CombatModules;
import com.smoke.client.feature.module.movement.MovementModules;
import com.smoke.client.feature.module.player.PlayerModules;
import com.smoke.client.feature.module.render.RenderModules;
import com.smoke.client.feature.module.world.WorldModules;
import com.smoke.client.input.BlocksModuleKeybindsScreen;
import com.smoke.client.input.InputService;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.module.ModuleManager;
import com.smoke.client.network.PacketService;
import com.smoke.client.rotation.RotationService;
import com.smoke.client.trace.ModuleTraceService;
import com.smoke.client.ui.click.ClickGuiScreen;
import com.smoke.client.ui.hud.HudService;
import com.smoke.client.ui.theme.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ClientRuntime {
    private static final Identifier HUD_LAYER_ID = Identifier.of(SmokeClient.MOD_ID, "hud");

    private final EventBus eventBus = new EventBus();
    private final PacketService packetService = new PacketService(eventBus);
    private final RotationService rotationService = new RotationService();
    private final ModuleManager moduleManager = new ModuleManager(eventBus);
    private final CommandDispatcher commandDispatcher = new CommandDispatcher();
    private final ConfigService configService = new ConfigService(FabricLoader.getInstance().getConfigDir());
    private final InputService inputService = new InputService();
    private final HudService hudService = new HudService();
    private final Theme theme = Theme.defaultTheme();
    private final ModuleTraceService moduleTraceService = new ModuleTraceService(this);
    private final ModuleContext moduleContext = new ModuleContext(this);

    private int autosaveTicks;

    public void start() {
        registerModules();
        registerCommands();
        eventBus.register(rotationService);
        eventBus.register(moduleTraceService);

        configService.load(moduleManager);
        hudService.registerDefaults(this);

        ClientTickEvents.START_CLIENT_TICK.register(this::onTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTickEnd);
        ClientSendMessageEvents.ALLOW_CHAT.register(this::onAllowChatMessage);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                moduleManager.getByType(com.smoke.client.feature.module.player.FakeLagModule.class).ifPresent(com.smoke.client.feature.module.player.FakeLagModule::onDisconnect));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) ->
                moduleManager.getByType(com.smoke.client.feature.module.player.FakeLagModule.class).ifPresent(com.smoke.client.feature.module.player.FakeLagModule::onWorldChange));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
        WorldRenderEvents.LAST.register(context -> eventBus.post(new WorldRenderEvent(context)));
        HudElementRegistry.addLast(HUD_LAYER_ID, (drawContext, tickCounter) -> {
            eventBus.post(new HudRenderEvent(drawContext, tickCounter));
            hudService.render(drawContext, tickCounter, this);
        });
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public PacketService packetService() {
        return packetService;
    }

    public RotationService rotationService() {
        return rotationService;
    }

    public ModuleManager moduleManager() {
        return moduleManager;
    }

    public CommandDispatcher commandDispatcher() {
        return commandDispatcher;
    }

    public ConfigService configService() {
        return configService;
    }

    public InputService inputService() {
        return inputService;
    }

    public HudService hudService() {
        return hudService;
    }

    public Theme theme() {
        return theme;
    }

    public ModuleTraceService moduleTraceService() {
        return moduleTraceService;
    }

    public ModuleContext moduleContext() {
        return moduleContext;
    }

    public void openClickGui() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new ClickGuiScreen(this));
        }
    }

    public void sendChatFeedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        Text text = Text.literal("[Smoke] " + message);
        if (client.player != null) {
            client.player.sendMessage(text, false);
            return;
        }

        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(text);
        }
    }

    private void registerModules() {
        CombatModules.register(moduleManager, moduleContext);
        MovementModules.register(moduleManager, moduleContext);
        RenderModules.register(moduleManager, moduleContext);
        PlayerModules.register(moduleManager, moduleContext);
        WorldModules.register(moduleManager, moduleContext);
    }

    private void registerCommands() {
        commandDispatcher.register(new HelpCommand());
        commandDispatcher.register(new ModulesCommand());
        commandDispatcher.register(new ToggleCommand());
        commandDispatcher.register(new BindCommand());
        commandDispatcher.register(new TraceCommand());
    }

    private void onTickStart(MinecraftClient client) {
        if (client.player == null) {
            rotationService.beginTick(null);
            return;
        }

        handleGlobalKeys(client);
        inputService.processModuleKeybinds(client, moduleManager);
        rotationService.beginTick(client.player);
        eventBus.post(TickEvent.pre());
        rotationService.refresh(client.player);
        rotationService.applyVisibleRotations(client.player);
    }

    private void onTickEnd(MinecraftClient client) {
        eventBus.post(TickEvent.post());

        // Apply rotations again at tick end so any render-side rotations (e.g., silent head/body yaw)
        // are not overwritten by vanilla entity tick updates. This prevents third-person "spinning"
        // when a rotation request opts into render rotation.
        if (client.player != null) {
            rotationService.applyVisibleRotations(client.player);
        }

        autosaveTicks++;
        if (autosaveTicks >= 200) {
            autosaveTicks = 0;
            configService.save(moduleManager);
        }
    }

    private boolean onAllowChatMessage(String message) {
        if (!commandDispatcher.isCommand(message)) {
            return true;
        }

        commandDispatcher.dispatch(this, message);
        return false;
    }

    private void handleGlobalKeys(MinecraftClient client) {
        if (client.getWindow() == null) {
            return;
        }

        boolean guiPressed = inputService.consumePress(GLFW.GLFW_KEY_RIGHT_SHIFT, client);
        if (client.currentScreen instanceof BlocksModuleKeybindsScreen captureScreen
                && !captureScreen.allowClientHotkey(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            return;
        }

        if (guiPressed) {
            if (client.currentScreen == null) {
                openClickGui();
            } else if (client.currentScreen instanceof ClickGuiScreen) {
                ((ClickGuiScreen) client.currentScreen).requestClose();
            }
        }
    }

    private void shutdown() {
        configService.save(moduleManager);
        moduleManager.shutdown();
        inputService.clear();
        eventBus.unregister(rotationService);
        eventBus.unregister(moduleTraceService);
        rotationService.clear();
        moduleTraceService.clearAll();
    }
}
