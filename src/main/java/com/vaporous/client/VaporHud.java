package com.vaporous.client;

import com.vaporous.VaporConfig;
import com.vaporous.time.TimeDilation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Density gauge, mode readout, charge meters, and the cold haze while time is dilated. */
public final class VaporHud {
    private VaporHud() {}

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        TextRenderer font = mc.textRenderer;

        if (TimeDilation.CLIENT.active() && VaporConfig.get().slowTint) {
            ctx.fill(0, 0, w, h, 0x1A7FA3BF);
            int band = Math.max(20, h / 5);
            ctx.fillGradient(0, 0, w, band, 0x50162430, 0x00162430);
            ctx.fillGradient(0, h - band, w, h, 0x00162430, 0x50162430);
        }

        if (!ClientVaporState.powered) return;

        int x = 8;
        int y = h - 34;
        float vapor = ClientVaporState.vapor;

        // Mode readout
        Text formText = Text.translatable(ClientVaporState.form ? "hud.vaporous.form_on" : "hud.vaporous.form_off");
        ctx.drawTextWithShadow(font, formText, x, y - 24, ClientVaporState.form ? 0xD8ECF7 : 0x7F8C99);
        if (ClientVaporState.time) {
            Text timeText = Text.translatable("hud.vaporous.time", TimeDilation.CLIENT.factor());
            ctx.drawTextWithShadow(font, timeText, x, y - 13, 0x9FD3FF);
        }

        // Density gauge: full bar = solid body, empty = pure vapor
        int barW = 90;
        ctx.fill(x - 1, y - 1, x + barW + 1, y + 6, 0xA0000000);
        int vaporW = Math.round(barW * vapor);
        ctx.fill(x, y, x + barW - vaporW, y + 5, 0xFF5C6E7E);                       // remaining density
        ctx.fillGradient(x + barW - vaporW, y, x + barW, y + 5, 0xFFF2F8FF, 0xFFA9C6DA); // vapor
        String pct = Math.round((1f - vapor) * 100f) + "%";
        ctx.drawTextWithShadow(font, Text.translatable("hud.vaporous.density", pct), x, y + 8, 0xB8C7D3);

        // Charge meters
        int cy = h / 2 + 14;
        if (VaporKeys.blastHold > 0) drawCharge(ctx, font, w / 2, cy, VaporKeys.blastHold, "hud.vaporous.charge_blast");
        if (VaporKeys.vacuumHold > 0) drawCharge(ctx, font, w / 2, cy + 14, VaporKeys.vacuumHold, "hud.vaporous.charge_vacuum");
    }

    private static void drawCharge(DrawContext ctx, TextRenderer font, int cx, int y, int hold, String key) {
        int bw = 60;
        float f = hold / (float) VaporKeys.MAX_CHARGE_TICKS;
        ctx.fill(cx - bw / 2 - 1, y - 1, cx + bw / 2 + 1, y + 4, 0xA0000000);
        ctx.fill(cx - bw / 2, y, cx - bw / 2 + Math.round(bw * f), y + 3, f >= 1f ? 0xFFFFFFFF : 0xFFBFDFF2);
        Text label = Text.translatable(key);
        ctx.drawTextWithShadow(font, label, cx - font.getWidth(label) / 2, y + 5, 0xCFE3F0);
    }
}
