package com.smoke.client.ui.font;

import com.smoke.client.SmokeClient;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class SmokeFonts {
    public static final Identifier VENOM = Identifier.of(SmokeClient.MOD_ID, "venom");
    public static final Identifier VENOM_SMALL = Identifier.of(SmokeClient.MOD_ID, "venom_small");
    public static final Identifier VENOM_THIN = Identifier.of(SmokeClient.MOD_ID, "venom_thin");
    public static final Identifier VENOM_TITLE = Identifier.of(SmokeClient.MOD_ID, "venom_title");

    public static final Style VENOM_STYLE = Style.EMPTY.withFont(VENOM);
    public static final Style VENOM_SMALL_STYLE = Style.EMPTY.withFont(VENOM_SMALL);
    public static final Style VENOM_THIN_STYLE = Style.EMPTY.withFont(VENOM_THIN);
    public static final Style VENOM_TITLE_STYLE = Style.EMPTY.withFont(VENOM_TITLE);

    private SmokeFonts() {
    }

    public static Text venom(String value) {
        return Text.literal(value).setStyle(VENOM_STYLE);
    }

    public static Text venomSmall(String value) {
        return Text.literal(value).setStyle(VENOM_SMALL_STYLE);
    }

    public static Text venomThin(String value) {
        return Text.literal(value).setStyle(VENOM_THIN_STYLE);
    }

    public static Text venomTitle(String value) {
        return Text.literal(value).setStyle(VENOM_TITLE_STYLE);
    }
}
