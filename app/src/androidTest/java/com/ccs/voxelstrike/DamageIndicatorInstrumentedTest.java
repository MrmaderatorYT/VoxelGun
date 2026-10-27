package com.ccs.voxelstrike;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.ccs.voxelstrike.game.HudSnapshot;
import com.ccs.voxelstrike.ui.HudView;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DamageIndicatorInstrumentedTest {
    @Test public void arrowPointsOutwardInAllFourDirections() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            HudView hud = new HudView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            hud.layout(0, 0, 400, 400);
            HudSnapshot state = new HudSnapshot();
            state.damageAmount = 1f;
            try {
                Method draw = HudView.class.getDeclaredMethod("drawDamageIndicator",
                        Canvas.class, HudSnapshot.class, float.class);
                draw.setAccessible(true);
                for (int quarter = 0; quarter < 4; quarter++) {
                    state.damageDirection = quarter * (float) Math.PI / 2f;
                    Bitmap image = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
                    try {
                        draw.invoke(hud, new Canvas(image), state, 1f);
                        int outer = widthAt(image, quarter, 86);
                        int inner = widthAt(image, quarter, 74);
                        assertTrue("The tip must be farther from the player: " + quarter,
                                outer > 0 && inner > outer * 2);
                    } finally { image.recycle(); }
                }
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }

    private static int widthAt(Bitmap image, int quarter, int distance) {
        int count = 0;
        for (int offset = -30; offset <= 30; offset++) {
            int x = offset, y = -distance;
            for (int turn = 0; turn < quarter; turn++) {
                int oldX = x;
                x = -y;
                y = oldX;
            }
            if ((image.getPixel(200 + x, 200 + y) >>> 24) > 0) count++;
        }
        return count;
    }
}
