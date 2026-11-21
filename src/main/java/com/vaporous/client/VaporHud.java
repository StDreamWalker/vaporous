package com.vaporous.client;

import com.vaporous.VaporConfig;
import com.vaporous.time.TimeDilation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;

/** Density gauge, speedometer, mode readout, charge meters, and the cold haze while time is dilated. */
public final class VaporHud {
    private static final double MPH_PER_BPS = 2.23694;   // 1 block = 1 metre
    private static final double KMH_PER_BPS = 3.6;
    private static final double MACH_BPS = 343.0;        // speed of sound, m/s
    /** The dial's top end. Log scale, so walking, sprinting and supersonic all fit. */
    private static final double DIAL_MAX_MPH = 3000.0;
    /** Past this the dial turns cyan: speeds you can only reach relative to slowed time. */
    private static final double DIAL_REALSPEED_MPH = 150.0;

    private VaporHud() {}

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        TextRenderer font = mc.textRenderer;
        VaporConfig c = VaporConfig.get();

        if (TimeDilation.CLIENT.active() && c.slowTint) {
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
        Text formText = Text.translatable(ClientVaporState.flying ? "hud.vaporous.flying"
                : ClientVaporState.form ? "hud.vaporous.form_on" : "hud.vaporous.form_off");
        ctx.drawTextWithShadow(font, formText, x, y - 24,
                ClientVaporState.flying ? 0xFFFFFF : ClientVaporState.form ? 0xD8ECF7 : 0x7F8C99);
        if (ClientVaporState.time) {
            Text timeText = Text.translatable("hud.vaporous.time", TimeDilation.CLIENT.factor());
            ctx.drawTextWithShadow(font, timeText, x, y - 13, 0x9FD3FF);
        }

        // Density gauge: full bar = solid body, empty = pure vapor. The flight threshold is marked.
        int barW = 90;
        ctx.fill(x - 1, y - 1, x + barW + 1, y + 6, 0xA0000000);
        int vaporW = Math.round(barW * vapor);
        ctx.fill(x, y, x + barW - vaporW, y + 5, 0xFF5C6E7E);
        ctx.fillGradient(x + barW - vaporW, y, x + barW, y + 5, 0xFFF2F8FF, 0xFFA9C6DA);
        int flightMark = x + Math.round(barW * (1f - (float) c.flightVapor));
        ctx.fill(flightMark, y - 2, flightMark + 1, y + 7, 0xFF6FE3FF);
        String pct = Math.round((1f - vapor) * 100f) + "%";
        ctx.drawTextWithShadow(font, Text.translatable("hud.vaporous.density", pct), x, y + 8, 0xB8C7D3);

        if (c.showSpeedometer) drawSpeedometer(ctx, font, w, h, c);

        // Charge meters
        int cy = h / 2 + 14;
        if (VaporKeys.blastHold > 0) drawCharge(ctx, font, w / 2, cy, VaporKeys.blastHold, "hud.vaporous.charge_blast");
        if (VaporKeys.vacuumHold > 0) drawCharge(ctx, font, w / 2, cy + 14, VaporKeys.vacuumHold, "hud.vaporous.charge_vacuum");
        if (VaporKeys.needSpeedHint > 0) {
            Text hint = Text.translatable("hud.vaporous.need_speed");
            ctx.drawTextWithShadow(font, hint, w / 2 - font.getWidth(hint) / 2, cy, 0xC0CCD6);
        }
    }

    // ------------------------------------------------------------------ speedometer

    private static void drawSpeedometer(DrawContext ctx, TextRenderer font, int w, int h, VaporConfig c) {
        int factor = TimeDilation.CLIENT.factor();
        boolean slowed = factor > 1;
        double bps = ClientVaporState.perceivedSpeed;
        double mph = bps * MPH_PER_BPS;

        int r = 38;
        int cx = w - r - 12;
        int cy = h - 62;

        ctx.fill(cx - r - 7, cy - r - 7, cx + r + 7, cy + 26, 0x78000000);
        if (slowed) {
            // a cold rim while time is dilated
            ctx.fill(cx - r - 7, cy - r - 7, cx + r + 7, cy - r - 6, 0xFF6FE3FF);
            ctx.fill(cx - r - 7, cy + 25, cx + r + 7, cy + 26, 0xFF6FE3FF);
        }

        double logMax = Math.log10(1.0 + DIAL_MAX_MPH);
        double frac = Math.min(1.0, Math.log10(1.0 + mph) / logMax);
        double realLimit = Math.log10(1.0 + DIAL_REALSPEED_MPH) / logMax;

        // Dial: half circle from the left, over the top, to the right.
        int segments = 36;
        for (int i = 0; i < segments; i++) {
            double f = (i + 0.5) / segments;
            double angle = Math.PI + f * Math.PI;
            int sx = cx + (int) Math.round(Math.cos(angle) * r);
            int sy = cy + (int) Math.round(Math.sin(angle) * r);
            boolean lit = f <= frac;
            boolean beyond = f > realLimit;
            int color = lit
                    ? (beyond ? 0xFF6FE3FF : 0xFFE6F2FA)
                    : (beyond ? 0x553A6A80 : 0x55556677);
            ctx.fill(sx - 2, sy - 2, sx + 2, sy + 2, color);
        }

        // Needle
        double needle = Math.PI + frac * Math.PI;
        int needleColor = frac > realLimit ? 0xFF9FF0FF : 0xFFFF6B5A;
        for (int i = 3; i < r - 5; i++) {
            int nx = cx + (int) Math.round(Math.cos(needle) * i);
            int ny = cy + (int) Math.round(Math.sin(needle) * i);
            ctx.fill(nx, ny, nx + 2, ny + 2, needleColor);
        }
        ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);

        // Big digital readout
        String value = formatSpeed(bps, c.speedUnits);
        ctx.getMatrices().push();
        ctx.getMatrices().translate((float) cx, (float) (cy + 5), 0f);
        ctx.getMatrices().scale(1.5f, 1.5f, 1f);
        ctx.drawTextWithShadow(font, value, -font.getWidth(value) / 2, 0, slowed ? 0x9FF0FF : 0xFFFFFF);
        ctx.getMatrices().pop();

        Text unit = Text.translatable("hud.vaporous.unit." + unitKey(c.speedUnits));
        ctx.drawTextWithShadow(font, unit, cx - font.getWidth(unit) / 2, cy + 17, 0xA0B0BC);

        // Above the dial: how much time is stretching your speed, and the sound barrier.
        int topY = cy - r - 18;
        if (slowed) {
            Text perceived = Text.translatable("hud.vaporous.perceived", factor);
            ctx.drawTextWithShadow(font, perceived, cx - font.getWidth(perceived) / 2, topY, 0x6FE3FF);
            topY -= 10;
        }
        double mach = bps / MACH_BPS;
        if (mach >= 0.8) {
            Text machText = Text.literal(String.format(Locale.ROOT, "MACH %.1f", mach));
            ctx.drawTextWithShadow(font, machText, cx - font.getWidth(machText) / 2, topY, mach >= 1.0 ? 0xFFFFFF : 0xB8C7D3);
        }
    }

    private static String unitKey(String units) {
        String u = units.toLowerCase(Locale.ROOT);
        return switch (u) {
            case "kmh", "km/h", "kph" -> "kmh";
            case "bps", "blocks" -> "bps";
            default -> "mph";
        };
    }

    private static String formatSpeed(double bps, String units) {
        double v = switch (unitKey(units)) {
            case "kmh" -> bps * KMH_PER_BPS;
            case "bps" -> bps;
            default -> bps * MPH_PER_BPS;
        };
        if (v >= 10000) return String.format(Locale.ROOT, "%,.0f", v);
        if (v >= 100) return String.format(Locale.ROOT, "%.0f", v);
        return String.format(Locale.ROOT, "%.1f", v);
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
