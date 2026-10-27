package com.ccs.voxelstrike;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ccs.voxelstrike.game.GameMode;
import com.ccs.voxelstrike.game.HudSnapshot;
import com.ccs.voxelstrike.game.KillContext;
import com.ccs.voxelstrike.game.Team;
import com.ccs.voxelstrike.ui.HudView;
import com.ccs.voxelstrike.ui.KillFeedRenderer;
import com.ccs.voxelstrike.weapon.WeaponSlot;
import com.ccs.voxelstrike.weapon.WeaponType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/** Real Android vector inflation and Canvas rendering; never alters settings or game saves. */
@RunWith(AndroidJUnit4.class)
public class KillFeedRendererInstrumentedTest {
    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test public void allKillGlyphsInflateAsNativeVectorDrawables() {
        int[] resources = {R.drawable.ic_kill_headshot, R.drawable.ic_kill_scoped,
                R.drawable.ic_kill_noscope, R.drawable.ic_kill_airborne, R.drawable.ic_kill_knife,
                R.drawable.ic_kill_blinded, R.drawable.ic_kill_flash_victim,
                R.drawable.ic_kill_wallbang, R.drawable.ic_kill_grenade,
                R.drawable.ic_kill_rocket, R.drawable.ic_kill_fall, R.drawable.ic_kill_rifle};
        for (int resource : resources) {
            var drawable = AppCompatResources.getDrawable(context(), resource);
            assertNotNull(drawable);
            assertTrue(drawable.getIntrinsicWidth() > 0);
            assertTrue(drawable.getIntrinsicHeight() > 0);
        }
    }

    @Test public void combinedTraitsAndLongNamesStayInsideTheFeedOnSmallAndNormalScreens() {
        KillFeedRenderer renderer = new KillFeedRenderer(context());
        HudSnapshot state = sample();
        state.feedAttacker[0] = "Дуже довге ім’я гравця 🧑‍🚀 — extra-long attacker name";
        state.feedVictim[0] = "Ще довше ім’я другого гравця 🐉🐉🐉 — extra-long victim name";
        state.feedFlags[0] = KillContext.HEADSHOT | KillContext.NO_SCOPE | KillContext.AIRBORNE
                | KillContext.BLINDED | KillContext.VICTIM_BLINDED | KillContext.WALLBANG;
        SnapshotMetadata before = new SnapshotMetadata(state);
        for (int width : new int[] {96, 320, 1600}) {
            Bitmap image = render(renderer, state, width, 540, 1f);
            try {
                int[] pixels = pixels(image);
                int left = (int) (width * .52f);
                int drawn = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < width; x++) {
                        if ((pixels[y * width + x] >>> 24) == 0) continue;
                        drawn++;
                        assertTrue("kill feed leaked into the central HUD", x >= left - 2);
                        // The first name baseline is the original 56 dp, not a card top edge.
                        assertTrue("kill feed exceeded its original top band", y >= 43);
                    }
                }
                assertTrue(drawn > 0);
                before.assertUnchanged(state); // drawing cannot consume or rewrite game metadata
            } finally { image.recycle(); }
        }
    }

    @Test public void fiveRowsKeepTheOriginalTwentyEightDpStepAndFitInOneHundredFortyDp() {
        KillFeedRenderer renderer = new KillFeedRenderer(context());
        HudSnapshot state = sample();
        for (int unit : new int[] {1, 2}) {
            // A 300 dp tall surface must fit all five rows without switching to a larger layout.
            Bitmap image = render(renderer, state, 1000 * unit, 300 * unit, unit);
            try {
                int[] pixels = pixels(image);
                int firstY = image.getHeight();
                int lastY = -1;
                int[] rowPixels = new int[HudSnapshot.MAX_FEED];
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((pixels[y * image.getWidth() + x] >>> 24) == 0) continue;
                        firstY = Math.min(firstY, y);
                        lastY = Math.max(lastY, y);
                        int row = (y - 43 * unit) / (28 * unit);
                        assertTrue("feed escaped its five original-height rows", row >= 0
                                && row < HudSnapshot.MAX_FEED && y >= 43 * unit);
                        rowPixels[row]++;
                    }
                }
                for (int i = 0; i < rowPixels.length; i++) {
                    assertTrue("compact row " + i + " was dropped or moved", rowPixels[i] > 0);
                }
                assertTrue("five kill entries grew taller than the original feed",
                        lastY - firstY + 1 <= 140 * unit);
            } finally { image.recycle(); }
        }
    }

    @Test public void shortPlainEntryUsesNaturalWidthInsteadOfAFullWidthCard() {
        KillFeedRenderer renderer = new KillFeedRenderer(context());
        HudSnapshot state = sample();
        state.feedCount = 1;
        state.feedAttacker[0] = "A";
        state.feedVictim[0] = "B";
        state.feedWeapon[0] = "AWM-X";
        state.feedFlags[0] = 0;
        state.feedHeadshot[0] = false;
        for (int unit : new int[] {1, 2}) {
            Bitmap image = render(renderer, state, 1000 * unit, 300 * unit, unit);
            try {
                int[] pixels = pixels(image);
                int left = image.getWidth(), right = -1;
                int top = image.getHeight(), bottom = -1;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((pixels[y * image.getWidth() + x] >>> 24) == 0) continue;
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                        top = Math.min(top, y);
                        bottom = Math.max(bottom, y);
                    }
                }
                assertTrue("the short entry did not draw", right >= left);
                assertTrue("short names still reserved a wide background card",
                        right - left + 1 <= 130 * unit);
                assertTrue("short names did not stay tucked into the upper-right corner",
                        left >= image.getWidth() - 150 * unit);
                assertTrue("one compact entry grew beyond its original row height",
                        bottom - top + 1 <= 28 * unit);
            } finally { image.recycle(); }
        }
    }

    @Test public void legacyHeadshotLooksTheSameAsTheNewFlagAndLifeFadesTheWholeRow() {
        KillFeedRenderer renderer = new KillFeedRenderer(context());
        HudSnapshot state = sample();
        state.feedCount = 1;
        state.feedFlags[0] = KillContext.HEADSHOT;
        Bitmap flagged = render(renderer, state, 1000, 400, 1f);
        state.feedFlags[0] = 0;
        state.feedHeadshot[0] = true;
        Bitmap legacy = render(renderer, state, 1000, 400, 1f);
        state.feedLife[0] = .25f;
        Bitmap faded = render(renderer, state, 1000, 400, 1f);
        try {
            assertTrue(Arrays.equals(pixels(flagged), pixels(legacy)));
            assertTrue(alphaSum(faded) < alphaSum(flagged) * .6);
        } finally { flagged.recycle(); legacy.recycle(); faded.recycle(); }
    }

    @Test public void expiredEntriesAndInvalidSizesDoNotDraw() {
        KillFeedRenderer renderer = new KillFeedRenderer(context());
        HudSnapshot state = sample();
        Arrays.fill(state.feedLife, 0f);
        Bitmap image = render(renderer, state, 600, 400, 1f);
        try {
            assertEquals(0L, alphaSum(image));
            Canvas canvas = new Canvas(image);
            renderer.draw(canvas, null, 600, 400, 1f);
            renderer.draw(canvas, state, 0, 400, 1f);
            renderer.draw(canvas, state, 600, 400, 0f);
            assertEquals(0L, alphaSum(image));
        } finally { image.recycle(); }
    }

    @Test public void explicitHudLimitProtectsTheReticleAndTheBottomReadouts() {
        KillFeedRenderer renderer = new KillFeedRenderer(context());
        Bitmap image = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888);
        try {
            renderer.draw(new Canvas(image), sample(), 1200, 900, 2f, 240f);
            int[] pixels = pixels(image);
            int drawn = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((pixels[y * image.getWidth() + x] >>> 24) == 0) continue;
                    drawn++;
                    assertTrue("feed crossed the central-reticle safe area", x >= 623);
                    assertTrue("feed crossed the ammo/control limit", y < 240);
                }
            }
            assertTrue(drawn > 0);
        } finally { image.recycle(); }
    }

    @Test public void denseActualHudLeavesAmmoAndTheCentralReticleUnchanged() {
        HudSnapshot state = sample();
        Bitmap baseline = Bitmap.createBitmap(1680, 1050, Bitmap.Config.ARGB_8888);
        Bitmap feed = Bitmap.createBitmap(1680, 1050, Bitmap.Config.ARGB_8888);
        RectF ammoBounds = new RectF();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                HudView hud = new HudView(context());
                hud.layout(0, 0, 1680, 1050);
                hud.setSnapshot(state);
                state.feedCount = 0;
                hud.draw(new Canvas(baseline));
                state.feedCount = HudSnapshot.MAX_FEED;
                hud.draw(new Canvas(feed));
                try {
                    var bounds = HudView.class.getDeclaredMethod("weaponInfoBounds", RectF.class);
                    bounds.setAccessible(true);
                    bounds.invoke(hud, ammoBounds);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            });
            int[] original = pixels(baseline), updated = pixels(feed);
            int changed = 0;
            for (int y = 0; y < 1050; y++) {
                for (int x = 0; x < 1680; x++) {
                    int at = y * 1680 + x;
                    if (original[at] == updated[at]) continue;
                    changed++;
                    assertTrue("feed reached the center of the actual HUD", x >= 1680 * .52f - 1);
                    assertTrue("feed covered the weapon/ammo readout", !ammoBounds.contains(x, y));
                }
            }
            assertTrue("feed disappeared instead of fitting compact rows", changed > 0);
        } finally { baseline.recycle(); feed.recycle(); }
    }

    /** Saves deterministic visual evidence in private cache for reviewer screenshot inspection. */
    @Test public void renderPreviewWithTheActualHudAndRepresentativeKillFamilies() throws Exception {
        Context context = context();
        HudSnapshot state = sample();
        savePreview(context, state, 1680, 1050, "kill-feed-preview.png");
        savePreview(context, state, 3360, 2100, "kill-feed-preview-large.png");
    }

    private static void savePreview(Context context, HudSnapshot state, int width, int height,
                                    String name) throws Exception {
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Canvas canvas = new Canvas(image);
                canvas.drawColor(0xFF17242D);
                HudView hud = new HudView(context);
                hud.layout(0, 0, image.getWidth(), image.getHeight());
                hud.setSnapshot(state);
                hud.draw(canvas);
            });
            try (FileOutputStream output = new FileOutputStream(
                    new File(context.getCacheDir(), name))) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            assertTrue(alphaSum(image) > 0L);
        } finally { image.recycle(); }
    }

    private static HudSnapshot sample() {
        HudSnapshot state = new HudSnapshot();
        state.mode = GameMode.TEAM_DEATHMATCH;
        state.alive = true;
        state.health = 100;
        state.armor = 40;
        state.ammo = 5;
        state.reserve = 25;
        state.zoom = 1;
        state.hasScope = true;
        state.weaponName = WeaponType.AWM_X.displayName();
        state.slot = WeaponSlot.PRIMARY.ordinal();
        Arrays.fill(state.kit, -1);
        state.kit[WeaponSlot.PRIMARY.ordinal()] = WeaponType.AWM_X.ordinal();
        state.kit[WeaponSlot.MELEE.ordinal()] = WeaponType.KNIFE.ordinal();
        state.scoreLimit = 50;
        state.blueScore = 12;
        state.redScore = 9;
        state.timed = true;
        state.timeLeft = 184;
        state.feedCount = HudSnapshot.MAX_FEED;
        String[] attackers = {"You", "Falcon", "You", "Ranger", "World"};
        String[] victims = {"Ranger", "Echo", "Viper", "Falcon", "Echo"};
        WeaponType[] weapons = {WeaponType.AWM_X, WeaponType.AWM_X, WeaponType.KNIFE,
                WeaponType.RPG_V, null};
        int[] flags = {KillContext.HEADSHOT | KillContext.SCOPED | KillContext.VICTIM_BLINDED,
                KillContext.NO_SCOPE | KillContext.AIRBORNE | KillContext.WALLBANG,
                KillContext.AIRBORNE | KillContext.BLINDED, 0, 0};
        for (int i = 0; i < state.feedCount; i++) {
            state.feedAttacker[i] = attackers[i];
            state.feedVictim[i] = victims[i];
            state.feedWeapon[i] = weapons[i] == null ? "Fall" : weapons[i].displayName();
            state.feedWeaponOrdinal[i] = weapons[i] == null ? -1 : weapons[i].ordinal();
            state.feedAttackerColor[i] = i == 4 ? Team.NONE.color() : Team.BLUE.color();
            state.feedVictimColor[i] = Team.RED.color();
            state.feedFlags[i] = flags[i];
            state.feedInvolvesPlayer[i] = i == 0 || i == 2;
            state.feedLife[i] = 5.5f;
        }
        return state;
    }

    private static Bitmap render(KillFeedRenderer renderer, HudSnapshot state, int width,
                                 int height, float unit) {
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        renderer.draw(new Canvas(image), state, width, height, unit);
        return image;
    }

    private static int[] pixels(Bitmap image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        return pixels;
    }

    private static long alphaSum(Bitmap image) {
        long sum = 0;
        for (int pixel : pixels(image)) sum += pixel >>> 24;
        return sum;
    }

    /** All feed values are game-owned: rendering may measure or clip, never mutate them. */
    private static final class SnapshotMetadata {
        private final int count;
        private final String[] attackers, victims, weapons;
        private final int[] attackerColors, victimColors, ordinals, flags;
        private final boolean[] headshots, involvesPlayer;
        private final float[] lives;

        private SnapshotMetadata(HudSnapshot state) {
            count = state.feedCount;
            attackers = state.feedAttacker.clone();
            victims = state.feedVictim.clone();
            weapons = state.feedWeapon.clone();
            attackerColors = state.feedAttackerColor.clone();
            victimColors = state.feedVictimColor.clone();
            ordinals = state.feedWeaponOrdinal.clone();
            flags = state.feedFlags.clone();
            headshots = state.feedHeadshot.clone();
            involvesPlayer = state.feedInvolvesPlayer.clone();
            lives = state.feedLife.clone();
        }

        private void assertUnchanged(HudSnapshot state) {
            assertEquals(count, state.feedCount);
            assertTrue(Arrays.equals(attackers, state.feedAttacker));
            assertTrue(Arrays.equals(victims, state.feedVictim));
            assertTrue(Arrays.equals(weapons, state.feedWeapon));
            assertTrue(Arrays.equals(attackerColors, state.feedAttackerColor));
            assertTrue(Arrays.equals(victimColors, state.feedVictimColor));
            assertTrue(Arrays.equals(ordinals, state.feedWeaponOrdinal));
            assertTrue(Arrays.equals(flags, state.feedFlags));
            assertTrue(Arrays.equals(headshots, state.feedHeadshot));
            assertTrue(Arrays.equals(involvesPlayer, state.feedInvolvesPlayer));
            assertTrue(Arrays.equals(lives, state.feedLife));
        }
    }
}
