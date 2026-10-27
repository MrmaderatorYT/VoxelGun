package com.ccs.voxelstrike.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.appcompat.content.res.AppCompatResources;

import com.ccs.voxelstrike.R;
import com.ccs.voxelstrike.game.HudSnapshot;
import com.ccs.voxelstrike.game.KillContext;
import com.ccs.voxelstrike.weapon.WeaponType;

/** Compact, allocation-free kill-feed drawing with SVG-path Android vector glyphs. */
public final class KillFeedRenderer {
    private static final String ELLIPSIS = "…";
    private static final String EMPTY = "";
    private static final WeaponType[] WEAPONS = WeaponType.values();
    private static final int[] TRAITS = {KillContext.HEADSHOT, KillContext.SCOPED,
            KillContext.NO_SCOPE, KillContext.AIRBORNE, KillContext.BLINDED,
            KillContext.VICTIM_BLINDED, KillContext.WALLBANG};
    private static final int[] TRAIT_RESOURCES = {R.drawable.ic_kill_headshot,
            R.drawable.ic_kill_scoped, R.drawable.ic_kill_noscope, R.drawable.ic_kill_airborne,
            R.drawable.ic_kill_blinded, R.drawable.ic_kill_flash_victim,
            R.drawable.ic_kill_wallbang};

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable[] traits = new Drawable[TRAITS.length];
    private final Drawable rifle, knife, grenade, rocket, fall;

    public KillFeedRenderer(Context context) {
        for (int i = 0; i < traits.length; i++) {
            traits[i] = vector(context, TRAIT_RESOURCES[i]);
        }
        rifle = vector(context, R.drawable.ic_kill_rifle);
        knife = vector(context, R.drawable.ic_kill_knife);
        grenade = vector(context, R.drawable.ic_kill_grenade);
        rocket = vector(context, R.drawable.ic_kill_rocket);
        fall = vector(context, R.drawable.ic_kill_fall);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private static Drawable vector(Context context, int resource) {
        Drawable glyph = AppCompatResources.getDrawable(context, resource);
        return glyph == null ? null : glyph.mutate();
    }

    /** All text is drawn directly from snapshot strings; resources are loaded only once. */
    public void draw(Canvas canvas, HudSnapshot state, int width, int height, float unit) {
        draw(canvas, state, width, height, unit, height);
    }

    /** Old feed anchors and spacing; width is only a clipping limit, never a fixed-size card. */
    static float rightEdge(int width, float unit) {
        return width - Math.min(22f * unit, width * .04f);
    }

    static float leftEdge(int width, float unit) {
        float right = rightEdge(width, unit);
        return right - Math.min(320f * unit, right - width * .52f);
    }

    static float firstBaseline(HudSnapshot state, float unit) {
        return (state.killCam ? 94f : 56f) * unit;
    }

    /** The HUD supplies the first readout/control that occupies this lane as the lower edge. */
    public void draw(Canvas canvas, HudSnapshot state, int width, int height, float unit,
                     float bottomLimit) {
        if (state == null || state.feedCount <= 0 || width <= 0 || height <= 0 || unit <= 0f
                || !(bottomLimit > 0f)) {
            return;
        }
        float right = rightEdge(width, unit);
        float left = leftEdge(width, unit);
        float available = right - left;
        float baseline = firstBaseline(state, unit);
        float bottom = Math.min(height, bottomLimit);
        float rowStep = 28f * unit;
        float weaponSize = Math.min(13f * unit, available);
        float nameGap = 4f * unit;
        if (available <= 0f || baseline + 15f * unit > bottom) return;
        int clipped = canvas.save();
        canvas.clipRect(left, baseline - 13f * unit, right, bottom);
        int rows = Math.min(state.feedCount, HudSnapshot.MAX_FEED);
        for (int i = 0; i < rows && baseline + 15f * unit <= bottom; i++) {
            float life = state.feedLife[i];
            if (!(life > 0f)) continue;
            int alpha = Math.round(255f * Math.min(1f, life / 1.2f));
            int shade = alpha << 24;

            // Pack short names together exactly like the original right-aligned text feed.
            // Long names share the available budget without allocating formatted strings.
            String attacker = state.feedAttacker[i] == null ? EMPTY : state.feedAttacker[i];
            String victim = state.feedVictim[i] == null ? EMPTY : state.feedVictim[i];
            text.setTextSize(12f * unit);
            float nameBudget = Math.max(0f, available - weaponSize - 2f * nameGap);
            float attackerBudget = Math.min(text.measureText(attacker), nameBudget * .5f);
            float victimBudget = Math.min(text.measureText(victim), nameBudget - attackerBudget);
            attackerBudget = Math.min(text.measureText(attacker), nameBudget - victimBudget);
            float attackerWidth = fittedWidth(attacker, attackerBudget);
            float victimWidth = fittedWidth(victim, victimBudget);
            float x = right - attackerWidth - victimWidth - weaponSize - 2f * nameGap;
            text.setColor(shade | readableColor(state.feedAttackerColor[i]));
            drawText(canvas, attacker, x, baseline, attackerBudget);
            x += attackerWidth + nameGap;
            drawGlyph(canvas, weaponGlyph(state.feedWeaponOrdinal[i]), x, baseline - 11f * unit,
                    weaponSize, alpha);
            x += weaponSize + nameGap;
            text.setColor(shade | readableColor(state.feedVictimColor[i]));
            drawText(canvas, victim, x, baseline, victimBudget);

            int flags = state.feedFlags[i];
            if (state.feedHeadshot[i]) flags |= KillContext.HEADSHOT; // legacy snapshots
            float traitSize = Math.min(11f * unit, available);
            float gap = 2f * unit;
            int capacity = traitSize <= 0f ? 0
                    : Math.max(0, (int) ((available + gap) / (traitSize + gap)));
            int count = Math.min(traitCount(flags), capacity);
            float traitsWidth = count == 0 ? 0f : count * traitSize + (count - 1) * gap;
            float traitX = right - traitsWidth;
            int shown = 0;
            for (int t = 0; t < TRAITS.length && shown < count; t++) {
                if ((flags & TRAITS[t]) == 0) continue;
                drawGlyph(canvas, traits[t], traitX, baseline + 3f * unit, traitSize, alpha);
                traitX += traitSize + gap;
                shown++;
            }
            text.setTextSize(10f * unit);
            text.setColor(shade | readableColor(state.feedAttackerColor[i]));
            float labelGap = count == 0 ? 0f : 4f * unit;
            float labelBudget = Math.max(0f, available - traitsWidth - labelGap);
            float labelWidth = fittedWidth(state.feedWeapon[i], labelBudget);
            drawText(canvas, state.feedWeapon[i], right - traitsWidth - labelGap - labelWidth,
                    baseline + 12f * unit, labelBudget);
            baseline += rowStep;
        }
        canvas.restoreToCount(clipped);
    }

    private static int readableColor(int argb) {
        int rgb = argb & 0x00FFFFFF;
        return rgb == 0 ? 0x00FFFFFF : rgb;
    }

    private static int traitCount(int flags) {
        int count = 0;
        for (int trait : TRAITS) if ((flags & trait) != 0) count++;
        return count;
    }

    private Drawable weaponGlyph(int ordinal) {
        if (ordinal < 0 || ordinal >= WEAPONS.length) return fall;
        WeaponType weapon = WEAPONS[ordinal];
        if (weapon.melee()) return knife;
        if (weapon.grenade()) return grenade;
        if (weapon.projectile()) return rocket;
        return rifle;
    }

    private static void drawGlyph(Canvas canvas, Drawable glyph, float x, float y, float size,
                                  int alpha) {
        if (glyph == null || size <= 0f) return;
        glyph.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size));
        glyph.setAlpha(alpha);
        glyph.draw(canvas);
    }

    /** Uses ranges instead of substring/TextUtils, preserving complete UTF-16 code points. */
    private void drawText(Canvas canvas, String value, float x, float baseline, float available) {
        String name = value == null ? EMPTY : value;
        if (available <= 0f || name.isEmpty()) return;
        if (text.measureText(name) <= available) {
            canvas.drawText(name, 0, name.length(), x, baseline, text);
            return;
        }
        float suffix = text.measureText(ELLIPSIS);
        if (available < suffix) return;
        int end = safePrefix(name, available - suffix);
        canvas.drawText(name, 0, end, x, baseline, text);
        canvas.drawText(ELLIPSIS, x + text.measureText(name, 0, end), baseline, text);
    }

    private float fittedWidth(String value, float available) {
        String name = value == null ? EMPTY : value;
        if (available <= 0f || name.isEmpty()) return 0f;
        float measured = text.measureText(name);
        if (measured > available) {
            float suffix = text.measureText(ELLIPSIS);
            if (available < suffix) return 0f;
            int end = safePrefix(name, available - suffix);
            measured = text.measureText(name, 0, end) + suffix;
        }
        return measured;
    }

    private int safePrefix(String value, float width) {
        int end = text.breakText(value, true, Math.max(0f, width), null);
        if (end > 0 && end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) end--;
        return end;
    }
}
