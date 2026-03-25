package com.smoke.client.mixin.screen;

import com.smoke.client.alt.AltScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private ButtonWidget smoke$altManagerButton;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void smoke$addAltManagerButton(CallbackInfo ci) {
        if (this.client == null) {
            return;
        }

        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = this.width / 2 - 100;
        int y = this.height / 4 + 48 + 72 + 48;

        smoke$altManagerButton = this.addDrawableChild(
                ButtonWidget.builder(
                                Text.literal("Alt Manager"),
                                button -> this.client.setScreen(new AltScreen(this))
                        )
                        .dimensions(x, y, buttonWidth, buttonHeight)
                        .build()
        );
    }
}

