package com.ccs.voxelgun.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.ccs.voxelgun.BuildConfig;
import com.ccs.voxelgun.R;
import com.ccs.voxelgun.app.GameSettings;
import com.ccs.voxelgun.app.HudLayout;
import com.ccs.voxelgun.app.KeyBindings;
import com.ccs.voxelgun.app.Profile;
import com.ccs.voxelgun.app.CareerProgression;
import com.ccs.voxelgun.audio.AudioEngine;
import com.ccs.voxelgun.audio.Sfx;
import com.ccs.voxelgun.game.BattlefieldRules;
import com.ccs.voxelgun.game.Difficulty;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.game.HudSnapshot;
import com.ccs.voxelgun.game.KillFeedEntry;
import com.ccs.voxelgun.game.Match;
import com.ccs.voxelgun.game.MatchConfig;
import com.ccs.voxelgun.game.MatchListener;
import com.ccs.voxelgun.game.MatchResult;
import com.ccs.voxelgun.input.DeviceInput;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.input.KeyboardPresenceMonitor;
import com.ccs.voxelgun.render.GameRenderer;
import com.ccs.voxelgun.render.RenderSettings;
import com.ccs.voxelgun.weapon.WeaponClass;
import com.ccs.voxelgun.weapon.WeaponSlot;
import com.ccs.voxelgun.weapon.WeaponType;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

import java.util.EnumMap;
import java.util.Locale;
import java.util.UUID;

/** Hosts one match: the GL surface, the touch HUD, the pause menu and the results screen. */
public final class MatchActivity extends AppCompatActivity implements MatchListener {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_MAP = "map";
    public static final String EXTRA_BOTS = "bots";
    public static final String EXTRA_DIFFICULTY = "difficulty";
    private static final String EXTRA_MATCH_REQUEST = "match_request";

    private final InputState input = new InputState();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private GLSurfaceView surface;
    private HudView hud;
    private View loadingOverlay;
    private View pauseOverlay;
    private View resultsOverlay;
    private LinearProgressIndicator loadingProgress;

    private Match match;
    private GameRenderer renderer;
    private AudioEngine audio;
    private GameSettings settings;
    private HudLayout hudLayout;
    private KeyBindings bindings;
    private DeviceInput deviceInput;
    private KeyboardPresenceMonitor keyboardMonitor;
    private Profile profile;
    private com.ccs.voxelgun.app.MovementRecords movementRecords;
    private int savedCourseBest;
    private com.ccs.voxelgun.app.GhostReplayStore ghostStore;
    private java.util.concurrent.ExecutorService ghostWriter;
    private volatile int savedGhostRevision;
    private final java.util.concurrent.atomic.AtomicBoolean ghostSavePending = new java.util.concurrent.atomic.AtomicBoolean();
    private int courseModifiers;
    private long nextGhostSaveAttempt;
    private Vibrator vibrator;
    private RenderSettings renderSettings;

    private boolean resultsShown;
    private boolean surfaceRunning;

    // ---- buy menu
    /** Tab order of the buy menu, roughly cheapest family first. */
    private static final WeaponClass[] BUY_TABS = {WeaponClass.PISTOL, WeaponClass.SMG,
            WeaponClass.SHOTGUN, WeaponClass.RIFLE, WeaponClass.SNIPER, WeaponClass.LMG,
            WeaponClass.LAUNCHER, WeaponClass.GRENADE};
    private final EnumMap<WeaponType, Bitmap> buyPreviews = new EnumMap<>(WeaponType.class);
    private Dialog buyDialog;
    private GridLayout buyContainer;
    private LinearLayout buyTabs;
    private TextView buyTitleLabel;
    private TextView buyMoneyLabel;
    private TextView buyMoneyCaption;
    private TextView buyStatusLabel;
    private TextView buyTimerLabel;
    private TextView buyCategoryLabel;
    private WeaponClass buyTab = WeaponClass.RIFLE;
    /** What the open dialog was last drawn for, so it only rebuilds when something changed. */
    private long buyRenderedState = Long.MIN_VALUE;

    /** Builds the intent that starts a match with the given setup. */
    public static Intent intent(Context context, int mode, int map, int bots, int difficulty) {
        Intent intent = new Intent(context, MatchActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        intent.putExtra(EXTRA_MAP, map);
        intent.putExtra(EXTRA_BOTS, bots);
        intent.putExtra(EXTRA_DIFFICULTY, difficulty);
        intent.putExtra(EXTRA_MATCH_REQUEST, UUID.randomUUID().toString());
        return intent;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String request = intent.getStringExtra(EXTRA_MATCH_REQUEST);
        // singleTask also receives the original launch again when returning from Home.
        // Only an explicit new-match request should replace the live world.
        if (request != null && !request.equals(getIntent().getStringExtra(EXTRA_MATCH_REQUEST))) {
            setIntent(intent);
            // Use normal lifecycle teardown to stop GL/workers and release native memory.
            recreate();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Gameplay can remain idle while bots fight. Prevent only automatic screen sleep;
        // Android drops this window flag's effect when we leave the foreground.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_match);
        goFullscreen();

        settings = new GameSettings(this);
        hudLayout = new HudLayout(this);
        bindings = new KeyBindings(this);
        profile = new Profile(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        MatchConfig config = new MatchConfig();
        config.mode = GameMode.values()[clamp(getIntent().getIntExtra(EXTRA_MODE, 0),
                GameMode.values().length)];
        config.mapIndex = getIntent().getIntExtra(EXTRA_MAP, 0);
        config.totalPlayers = BattlefieldRules.sanitizeTotalPlayers(
                settings.battlefieldTotalPlayers);
        config.scoreLimit = BattlefieldRules.sanitizeScoreLimit(settings.battlefieldScoreLimit);
        config.timeLimitSeconds = BattlefieldRules.sanitizeTimeSeconds(
                settings.battlefieldTimeLimitSeconds);
        config.hardcore = settings.battlefieldHardcore;
        config.conquest = settings.battlefieldConquest;
        config.battlefieldClass = settings.battlefieldClass;
        config.battlefieldPrimary = settings.battlefieldPrimary;
        config.battlefieldSniper = settings.battlefieldSniper;
        config.battlefieldLmg = settings.battlefieldLmg;
        config.botCount = config.mode == GameMode.BATTLE_FIELD ? config.totalPlayers - 1
                : getIntent().getIntExtra(EXTRA_BOTS, 7);
        config.difficulty = Difficulty.values()[clamp(getIntent().getIntExtra(EXTRA_DIFFICULTY, 1),
                Difficulty.values().length)];
        config.playerName = profile.name();
        config.modifiers = settings.matchModifiers;
        config.modifiers = com.ccs.voxelgun.game.MatchModifier.sanitize(config.modifiers, config.mode);
        movementRecords = new com.ccs.voxelgun.app.MovementRecords(this);
        config.courseBestMillis = movementRecords.best(config.modifiers);
        savedCourseBest = config.courseBestMillis;
        config.weaponSkins = profile.weaponSkins();
        config.gloveColor = profile.equipped(com.ccs.voxelgun.app.Cosmetics.Category.GLOVES);
        config.charm = profile.equipped(com.ccs.voxelgun.app.Cosmetics.Category.CHARM);
        config.crosshair = profile.equipped(com.ccs.voxelgun.app.Cosmetics.Category.CROSSHAIR);
        config.seed = System.nanoTime();

        match = new Match(config);
        if (match.movementTrial() != null) {
            courseModifiers = config.modifiers;
            ghostStore = new com.ccs.voxelgun.app.GhostReplayStore(this);
            ghostStore.load(courseModifiers, match.movementTrial().ghost());
            match.movementTrial().restoreBestFromGhost();
            savedGhostRevision = match.movementTrial().ghost().bestRevision();
            ghostWriter = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "ghost-save"); thread.setDaemon(true); return thread;
            });
        }
        match.setListener(this);

        input.lookSensitivity = settings.lookSensitivity;
        input.adsSensitivity = settings.adsSensitivity;
        input.scopeSensitivity = settings.scopeSensitivity;
        input.autoJump = settings.autoJump;
        input.paused = false;

        renderSettings = settings.toRenderSettings();
        renderer = new GameRenderer(this, match, input, renderSettings);

        surface = findViewById(R.id.surface);
        surface.setEGLContextClientVersion(3);
        surface.setPreserveEGLContextOnPause(true);
        surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        renderer.attachSurfaceSizer((width, height) -> runOnUiThread(() -> {
            if (surfaceRunning) {
                surface.getHolder().setFixedSize(width, height);
            }
        }), metrics.widthPixels, metrics.heightPixels);
        surfaceRunning = true;
        surface.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(android.view.SurfaceHolder holder) {
                applyDisplayFrameRate();
            }

            @Override
            public void surfaceChanged(android.view.SurfaceHolder holder, int format, int width,
                                       int height) {
                applyDisplayFrameRate();
            }

            @Override
            public void surfaceDestroyed(android.view.SurfaceHolder holder) {}
        });

        hud = findViewById(R.id.hud);
        hud.bind(input, this::showPause);
        hud.setLayout(hudLayout);
        hud.setSouthpaw(settings.southpaw);
        hud.setAutoFire(settings.autoFire);
        hud.setAimToggle(settings.aimToggle);
        hud.setShowFps(settings.showFps);
        hud.setBhopKeyboardHint(bindings.bhopHint());
        hud.setKeyboardHints(bindings.keyboardCombatHint(), bindings.keyboardUtilityHint(false),
                bindings.keyboardUtilityHint(true));
        Bitmap minimap = Minimap.render(match.world());
        hud.setMinimap(minimap);
        hud.setFocusable(true);
        hud.setFocusableInTouchMode(true);
        hud.requestFocus();

        // Keyboard and mouse. The on-screen controls disappear the moment one is used.
        deviceInput = new DeviceInput(input, bindings);
        deviceInput.setPauseListener(() -> runOnUiThread(this::togglePause));
        deviceInput.setScoreboardListener(visible -> runOnUiThread(
                () -> hud.setScoreboardVisible(visible)));
        deviceInput.setActivityListener(this::enterDesktopMode);
        deviceInput.setKeyboardActivityListener(() -> hud.setKeyboardAvailable(true));
        keyboardMonitor = new KeyboardPresenceMonitor(this, present -> {
            hud.setKeyboardAvailable(present);
            if (!present) deviceInput.releaseAll();
        });
        hud.setCapturedPointerHandler(new HudView.CapturedPointerHandler() {
            @Override
            public boolean onCapturedPointer(MotionEvent event) {
                // Capture release is asynchronous; ignore late gameplay events during menus.
                return !input.paused && deviceInput.onCapturedPointer(event);
            }

            @Override
            public void onCaptureChanged(boolean captured) {
                deviceInput.setCaptured(captured);
            }
        });

        loadingOverlay = findViewById(R.id.loading_overlay);
        pauseOverlay = findViewById(R.id.pause_overlay);
        resultsOverlay = findViewById(R.id.results_overlay);
        loadingProgress = findViewById(R.id.loading_progress);
        ((TextView) findViewById(R.id.loading_title)).setText(match.map().displayName());
        ((TextView) findViewById(R.id.loading_subtitle))
                .setText(match.map().subtitle() + " · " + config.mode.label());

        findViewById(R.id.button_resume).setOnClickListener(v -> hidePause());
        findViewById(R.id.button_leave).setOnClickListener(v -> finish());
        findViewById(R.id.button_pause_settings).setOnClickListener(v -> showQuickSettings());
        View performanceDetails = findViewById(R.id.button_performance_details);
        performanceDetails.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        if (BuildConfig.DEBUG) {
            performanceDetails.setOnClickListener(v -> showPerformanceDetails());
        }
        MaterialButton courseRestart = findViewById(R.id.button_course_restart);
        if (config.mode == GameMode.MOVEMENT_TRAINING) {
            courseRestart.setVisibility(View.VISIBLE);
            courseRestart.setOnClickListener(v -> {
                hidePause();
                input.queueCourseRestart();
            });
            hud.setCourseRestartHint(bindings.courseRestartHint());
        }

        // The arsenal now lives in the HUD, where it can be moved and resized like any control.
        hud.setBuyListener(this::showBuyMenu);
        deviceInput.setBuyListener(() -> runOnUiThread(this::showBuyMenu));
        findViewById(R.id.button_continue).setOnClickListener(v -> finish());
        findViewById(R.id.button_rematch).setOnClickListener(v -> restart());

        audio = new AudioEngine();
        audio.start();
        audio.setMasterVolume(settings.effectsVolume);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (resultsOverlay.getVisibility() == View.VISIBLE) {
                    finish();
                } else if (pauseOverlay.getVisibility() == View.VISIBLE) {
                    hidePause();
                } else {
                    showPause();
                }
            }
        });

    }

    private static int clamp(int value, int count) {
        return value < 0 || value >= count ? 0 : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ keyboard and mouse

    /** Switches to keyboard and mouse: hide the touch layout and grab the pointer. */
    private void enterDesktopMode() {
        if (!hud.touchControlsVisible()) {
            return;
        }
        hud.setTouchControlsVisible(false);
        capturePointer();
    }

    private void capturePointer() {
        if (input.paused || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || hud.touchControlsVisible()
                || pauseOverlay.getVisibility() == View.VISIBLE
                || resultsOverlay.getVisibility() == View.VISIBLE) {
            return;
        }
        hud.requestPointerCapture();
    }

    private void releasePointer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hud.releasePointerCapture();
        }
        if (deviceInput != null) {
            deviceInput.setCaptured(false);
            deviceInput.releaseAll();
        }
    }

    /**
     * Asks the display for the chosen rate. On a 90 or 120 Hz panel this is what actually makes
     * the higher settings reachable; the render thread's own pacing does the rest.
     */
    private android.hardware.display.DisplayManager graphicsDisplayManager;
    private final android.hardware.display.DisplayManager.DisplayListener graphicsDisplayListener =
            new android.hardware.display.DisplayManager.DisplayListener() {
                @Override public void onDisplayAdded(int id) { updateRendererRefreshRate(); }
                @Override public void onDisplayRemoved(int id) { updateRendererRefreshRate(); }
                @Override public void onDisplayChanged(int id) { updateRendererRefreshRate(); }
            };

    private void updateRendererRefreshRate() {
        android.view.Display display = surface == null ? null : surface.getDisplay();
        if (renderer != null) renderer.setDisplayRefreshRate(display == null ? 60f : display.getRefreshRate());
    }

    private void applyDisplayFrameRate() {
        if (surface == null) return;
        updateRendererRefreshRate();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        android.view.Surface target = surface.getHolder().getSurface();
        if (target == null || !target.isValid()) {
            return;
        }
        int cap = settings.frameRateCap;
        try {
            target.setFrameRate(cap > 0 ? cap : 0f,
                    cap > 0 ? android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                            : android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        } catch (IllegalStateException error) {
            // The surface went away between the check and the call; nothing to do.
        }
    }

    private void togglePause() {
        if (pauseOverlay.getVisibility() == View.VISIBLE) {
            hidePause();
        } else {
            showPause();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Escape is always pause, including keyboards reporting an unknown source or old binds.
        if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && pauseOverlay != null && !resultsShown) {
                togglePause();
            }
            return true;
        }
        // DeviceInput refuses navigation keys, so back stays with the system.
        if (deviceInput != null && deviceInput.onKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!input.paused && deviceInput != null && event.isFromSource(InputDevice.SOURCE_MOUSE)
                && deviceInput.onGenericMotion(event)) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Paused/menu input belongs to the Window (Resume, settings, results), not the FPS.
        if (!input.paused && deviceInput != null && event.isFromSource(InputDevice.SOURCE_MOUSE)
                && !hud.touchControlsVisible()) {
            deviceInput.onMouseTouch(event);
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            goFullscreen();
            capturePointer();
        } else if (deviceInput != null) {
            deviceInput.releaseAll();
        }
    }

    private void goFullscreen() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /** Copies the published match snapshot into the HUD and keeps the audio listener in sync. */
    private final Runnable hudPump = new Runnable() {
        @Override
        public void run() {
            if (match != null) {
                HudSnapshot snapshot = match.snapshot();
                persistMovementBest(snapshot);
                hud.setSnapshot(snapshot);
                syncBuyMenu(snapshot);
                audio.setListener(match.cameraPosition.x, match.cameraPosition.y,
                        match.cameraPosition.z, match.cameraYaw);
                if (loadingOverlay.getVisibility() == View.VISIBLE) {
                    float progress = renderer.loadProgress();
                    loadingProgress.setProgress(Math.round(progress * 100));
                    if (renderer.ready()) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                }
            }
            handler.postDelayed(this, 16L);
        }
    };

    // ------------------------------------------------------------------ buy menu
    private void persistMovementBest(HudSnapshot snapshot) {
        persistMovementGhost();
        if (snapshot.movementTrial && snapshot.courseBestMillis > 0
                && snapshot.courseBestMillis != savedCourseBest) {
            movementRecords.saveBest(snapshot.modifiers, snapshot.courseBestMillis);
            savedCourseBest = snapshot.courseBestMillis;
        }
    }

    private void persistMovementGhost() {
        if (match == null || ghostWriter == null || ghostWriter.isShutdown()) return;
        com.ccs.voxelgun.game.MovementGhost ghost = match.movementTrial().ghost();
        int revision = ghost.bestRevision();
        long now = android.os.SystemClock.uptimeMillis();
        if (now < nextGhostSaveAttempt) return;
        if (!ghost.hasBest() || revision == savedGhostRevision || !ghostSavePending.compareAndSet(false, true)) return;
        nextGhostSaveAttempt = now + 5000L; // Full storage must not cause a retry every HUD tick.
        ghostWriter.execute(() -> {
            try { if (ghostStore.save(courseModifiers, ghost)) savedGhostRevision = revision; }
            finally { ghostSavePending.set(false); }
        });
    }

    private void showPerformanceDetails() {
        if (!BuildConfig.DEBUG) return;
        HudSnapshot state = match.snapshot();
        String[] names = {"Physics", "AI decisions / combat", "Weapons / projectiles", "Effects",
                "Objectives", "Chunk uploads", "Render submission (CPU)"};
        StringBuilder detail = new StringBuilder(400).append(state.fps).append(" FPS · p95 ")
                .append(state.frameP95).append(" ms\n\n");
        for (int i = 0; i < names.length; i++) detail.append(names[i]).append(": ")
                .append(String.format(Locale.US, "%.2f ms\n", state.cpuMicros[i] / 1000f));
        detail.append(String.format(Locale.US, "Meshing worker / chunk: %.2f ms\n", state.meshingMicros / 1000f))
                .append("View: ").append(Math.round(state.viewDistance)).append(" m · effects: ")
                .append(state.effectPercent).append("%\nThermal status: ").append(state.thermalStatus)
                .append("\n\nCPU timings are not GPU execution time.\nAdaptation changes only this match; saved settings stay intact.");
        new MaterialAlertDialogBuilder(this).setTitle(R.string.match_performance_details)
                .setMessage(detail).setPositiveButton(android.R.string.ok, null).show();
    }

    /** Shows or hides the buy entry point and keeps an open buy dialog in step with the round. */
    private void syncBuyMenu(HudSnapshot snapshot) {
        boolean open = snapshot.buyOpen && !resultsShown;
        hud.setBuyAvailable(open && buyDialog == null && !input.paused);
        if (buyDialog == null) {
            return;
        }
        updateBuyHeader(snapshot);
        long state = fingerprint(snapshot);
        if (state != buyRenderedState) {
            buyRenderedState = state;
            renderBuyList(snapshot);
        }
    }

    private static long fingerprint(HudSnapshot snapshot) {
        long value = snapshot.money * 31L + (snapshot.buyOpen ? 1 : 0);
        for (int ordinal : snapshot.kit) {
            value = value * 67L + ordinal;
        }
        return value;
    }

    private void showBuyMenu() {
        HudSnapshot snapshot = match == null ? null : match.snapshot();
        if (buyDialog != null || resultsShown || snapshot == null || !snapshot.buyOpen) {
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_buy_menu, null);
        buyContainer = content.findViewById(R.id.buy_container);
        buyTabs = content.findViewById(R.id.buy_tabs);
        buyTitleLabel = content.findViewById(R.id.buy_title_label);
        buyMoneyLabel = content.findViewById(R.id.buy_money);
        buyMoneyCaption = content.findViewById(R.id.buy_money_caption);
        buyStatusLabel = content.findViewById(R.id.buy_status);
        buyTimerLabel = content.findViewById(R.id.buy_timer);
        buyCategoryLabel = content.findViewById(R.id.buy_category_summary);

        for (WeaponClass family : BUY_TABS) {
            MaterialButton tab = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.item_buy_tab, buyTabs, false);
            tab.setCheckable(true);
            tab.setText(weaponClassLabel(family));
            tab.setTag(family);
            tab.setChecked(family == buyTab);
            tab.setOnClickListener(v -> {
                if (buyTab == family) {
                    tab.setChecked(true);
                    return;
                }
                buyTab = family;
                updateBuyTabChecks();
                buyRenderedState = Long.MIN_VALUE;
                HudSnapshot current = match == null ? null : match.snapshot();
                if (current != null) {
                    syncBuyMenu(current);
                }
            });
            buyTabs.addView(tab);
        }

        // Browsing is modal: the look and fire inputs are dropped while the list is up.
        input.paused = true;
        input.releaseAll();
        hud.clearAim();
        releasePointer();
        hud.setBuyAvailable(false);
        buyRenderedState = Long.MIN_VALUE;
        renderBuyList(snapshot);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) {
                return false;
            }
            // Consume the whole press before returning to gameplay, where Enter can fire.
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) dialog.dismiss();
            return true;
        });
        dialog.setOnDismissListener(ignored -> {
            buyDialog = null;
            clearBuyViews();
            if (!isFinishing() && !isDestroyed()
                    && pauseOverlay.getVisibility() != View.VISIBLE && !resultsShown) {
                input.paused = false;
                goFullscreen();
                capturePointer();
            }
        });
        content.findViewById(R.id.buy_close_top).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.buy_close).setOnClickListener(v -> dialog.dismiss());
        buyDialog = dialog;
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window,
                    window.getDecorView());
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            params.width = Math.min(metrics.widthPixels - dp(48), dp(980));
            params.height = Math.min(metrics.heightPixels - dp(40), dp(520));
            params.dimAmount = 0.68f;
            window.setAttributes(params);
        }
    }

    private void updateBuyTabChecks() {
        if (buyTabs == null) {
            return;
        }
        for (int i = 0; i < buyTabs.getChildCount(); i++) {
            View child = buyTabs.getChildAt(i);
            if (child instanceof MaterialButton) {
                ((MaterialButton) child).setChecked(child.getTag() == buyTab);
            }
        }
    }

    private void renderBuyList(HudSnapshot snapshot) {
        if (buyContainer == null) {
            return;
        }
        updateBuyHeader(snapshot);
        int count = 0;
        for (WeaponType type : WeaponType.values()) {
            if (type.weaponClass() == buyTab && !type.modeOnly()) {
                count++;
            }
        }
        buyCategoryLabel.setText(getString(R.string.buy_category_summary,
                weaponClassLabel(buyTab), count));
        buyContainer.removeAllViews();
        int itemIndex = 0;
        for (WeaponType type : WeaponType.values()) {
            if (type.weaponClass() != buyTab || type.modeOnly()) {
                continue;
            }
            View row = buildBuyRow(buyContainer, type, snapshot);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(108);
            params.rowSpec = GridLayout.spec(itemIndex / 2);
            params.columnSpec = GridLayout.spec(itemIndex % 2, 1f);
            params.setMargins(dp(4), dp(3), dp(4), dp(3));
            buyContainer.addView(row, params);
            itemIndex++;
        }
        if ((itemIndex & 1) != 0) {
            View spacer = new View(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 1;
            params.rowSpec = GridLayout.spec(itemIndex / 2);
            params.columnSpec = GridLayout.spec(1, 1f);
            params.setMargins(dp(4), 0, dp(4), 0);
            buyContainer.addView(spacer, params);
        }
    }

    private void updateBuyHeader(HudSnapshot snapshot) {
        if (buyTitleLabel == null) {
            return;
        }
        if (snapshot.freeArsenal) {
            buyTitleLabel.setText(R.string.buy_title_free);
            buyMoneyCaption.setText(R.string.buy_access);
            buyMoneyLabel.setText(R.string.buy_unlimited);
            buyStatusLabel.setText(R.string.buy_no_balance);
            buyTimerLabel.setText(R.string.buy_window_live);
        } else {
            buyTitleLabel.setText(R.string.buy_title);
            buyMoneyCaption.setText(R.string.buy_round_funds);
            buyMoneyLabel.setText(getString(R.string.buy_money, snapshot.money));
            if (snapshot.buyOpen) {
                buyStatusLabel.setText(R.string.buy_hint);
                buyTimerLabel.setText(getString(R.string.buy_window_time,
                        Math.max(0, (int) Math.ceil(snapshot.buyTimeLeft))));
            } else {
                buyStatusLabel.setText(R.string.buy_closed);
                buyTimerLabel.setText(R.string.buy_window_closed_short);
            }
        }
    }

    private View buildBuyRow(ViewGroup parent, WeaponType type, HudSnapshot snapshot) {
        View row = getLayoutInflater().inflate(R.layout.item_buy_weapon, parent, false);
        MaterialCardView card = row.findViewById(R.id.weapon_card);
        ((TextView) row.findViewById(R.id.weapon_name)).setText(type.displayName());
        ((TextView) row.findViewById(R.id.weapon_stats)).setText(statLine(type));
        ((TextView) row.findViewById(R.id.weapon_slot)).setText(slotLabel(type.slot()));
        ImageView preview = row.findViewById(R.id.weapon_preview);
        preview.setContentDescription(type.displayName());
        Bitmap bitmap = buyPreviews.get(type);
        if (bitmap == null || bitmap.isRecycled()) {
            bitmap = CosmeticPreview.renderWeapon(type, dp(96), dp(76));
            buyPreviews.put(type, bitmap);
        }
        preview.setImageBitmap(bitmap);
        setBar(row.findViewById(R.id.bar_damage), type.damage() / 140f);
        setBar(row.findViewById(R.id.bar_rate), type.roundsPerMinute() / 1150f);
        setBar(row.findViewById(R.id.bar_range), type.range() / 170f);

        MaterialButton action = row.findViewById(R.id.weapon_action);
        boolean carrying = snapshot.kit[type.slot().ordinal()] == type.ordinal();
        if (carrying) {
            action.setText(R.string.buy_owned);
            action.setEnabled(false);
            card.setStrokeColor(ContextCompat.getColor(this, R.color.game_accent));
            card.setCardBackgroundColor(0xF21A1511);
        } else {
            action.setText(snapshot.freeArsenal ? getString(R.string.buy_take)
                    : getString(R.string.buy_price, type.cost()));
            boolean available = snapshot.buyOpen
                    && (snapshot.freeArsenal || type.cost() <= snapshot.money);
            action.setEnabled(available);
            if (!available) {
                card.setAlpha(0.65f);
            }
            View.OnClickListener purchase = v -> {
                if (!action.isEnabled()) {
                    return;
                }
                input.queuePurchase(type.ordinal());
                action.setEnabled(false);
                if (audio != null) {
                    audio.playUi(Sfx.UI, 0.6f, 1.2f);
                }
            };
            action.setOnClickListener(purchase);
            card.setOnClickListener(purchase);
        }
        return row;
    }

    private String statLine(WeaponType type) {
        if (type.melee()) {
            return getString(R.string.buy_stats_melee, Math.round(type.damage()), type.range());
        }
        if (type.grenade()) {
            return getString(R.string.buy_stats_grenade, Math.round(type.splashDamage()),
                    type.splashRadius());
        }
        return getString(R.string.buy_stats_firearm, Math.round(type.damage()),
                Math.round(type.roundsPerMinute()), type.magazine());
    }

    private String weaponClassLabel(WeaponClass family) {
        switch (family) {
            case PISTOL: return getString(R.string.buy_class_pistol);
            case SMG: return getString(R.string.buy_class_smg);
            case SHOTGUN: return getString(R.string.buy_class_shotgun);
            case RIFLE: return getString(R.string.buy_class_rifle);
            case SNIPER: return getString(R.string.buy_class_sniper);
            case LMG: return getString(R.string.buy_class_lmg);
            case LAUNCHER: return getString(R.string.buy_class_launcher);
            case GRENADE: return getString(R.string.buy_class_grenade);
            case MELEE: return getString(R.string.buy_class_melee);
            default: return family.label();
        }
    }

    private String slotLabel(WeaponSlot slot) {
        switch (slot) {
            case PRIMARY: return getString(R.string.buy_slot_primary);
            case SECONDARY: return getString(R.string.buy_slot_secondary);
            case GRENADE: return getString(R.string.buy_slot_grenade);
            case MELEE: return getString(R.string.buy_slot_melee);
            default: return slot.name();
        }
    }

    private static void setBar(LinearProgressIndicator bar, float value) {
        bar.setProgress(Math.max(4, Math.min(100, Math.round(value * 100))));
    }

    private void clearBuyViews() {
        buyContainer = null;
        buyTabs = null;
        buyTitleLabel = null;
        buyMoneyLabel = null;
        buyMoneyCaption = null;
        buyStatusLabel = null;
        buyTimerLabel = null;
        buyCategoryLabel = null;
    }

    private void showPause() {
        if (resultsShown) {
            return;
        }
        input.paused = true;
        input.releaseAll();
        hud.clearAim();
        releasePointer();
        pauseOverlay.setVisibility(View.VISIBLE);
    }

    private void hidePause() {
        pauseOverlay.setVisibility(View.GONE);
        input.paused = false;
        goFullscreen();
        capturePointer();
    }

    /** In-match settings: graphics preset, volume, sensitivity and the HUD button sliders. */
    private void showQuickSettings() {
        View content = getLayoutInflater().inflate(R.layout.dialog_quick_settings, null);

        MaterialButtonToggleGroup qualityGroup = content.findViewById(R.id.quick_quality);
        RenderSettings.Quality[] presets = RenderSettings.Quality.values();
        for (int i = 0; i < presets.length; i++) {
            MaterialButton button = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(View.generateViewId());
            button.setText(presets[i].label());
            button.setTag(i);
            button.setTextSize(11f);
            MenuActivity.styleGraphicsButton(this, button);
            qualityGroup.addView(button, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (presets[i] == settings.quality) {
                qualityGroup.check(button.getId());
            }
        }
        qualityGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            View button = group.findViewById(checkedId);
            if (button != null && button.getTag() instanceof Integer) {
                settings.applyPreset(presets[(Integer) button.getTag()]);
                applyRenderSettings();
            }
        });

        MenuActivity.buildFrameRateGroup(this, content.findViewById(R.id.quick_fps),
                settings.frameRateCap, cap -> {
                    settings.frameRateCap = cap;
                    settings.save();
                    renderSettings.frameRateCap = cap;
                    surface.queueEvent(() -> renderer.settings().frameRateCap = cap);
                    applyDisplayFrameRate();
                });

        // Contrast and brightness are the two worth having in the pause menu: they are judged
        // against the map itself, so they apply to the live renderer on every slider step.
        Slider contrast = content.findViewById(R.id.quick_contrast);
        TextView contrastLabel = content.findViewById(R.id.quick_contrast_label);
        contrast.setValue(clamp(settings.contrast, contrast));
        contrastLabel.setText(toneLabel(R.string.settings_contrast, settings.contrast));
        contrast.addOnChangeListener((slider, value, fromUser) -> {
            settings.contrast = value;
            settings.save();
            contrastLabel.setText(toneLabel(R.string.settings_contrast, value));
            renderSettings.contrast = value;
            surface.queueEvent(() -> renderer.settings().contrast = value);
        });

        Slider brightness = content.findViewById(R.id.quick_brightness);
        TextView brightnessLabel = content.findViewById(R.id.quick_brightness_label);
        brightness.setValue(clamp(settings.brightness, brightness));
        brightnessLabel.setText(toneLabel(R.string.settings_brightness, settings.brightness));
        brightness.addOnChangeListener((slider, value, fromUser) -> {
            settings.brightness = value;
            settings.save();
            brightnessLabel.setText(toneLabel(R.string.settings_brightness, value));
            renderSettings.brightness = value;
            surface.queueEvent(() -> renderer.settings().brightness = value);
        });

        Slider gamma = content.findViewById(R.id.quick_gamma);
        TextView gammaLabel = content.findViewById(R.id.quick_gamma_label);
        gamma.setValue(clamp(settings.gamma, gamma));
        gammaLabel.setText(toneLabel(R.string.settings_gamma, settings.gamma));
        gamma.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            settings.gamma = value;
            settings.save();
            gammaLabel.setText(toneLabel(R.string.settings_gamma, value));
            renderSettings.gamma = value;
            surface.queueEvent(() -> renderer.settings().gamma = value);
        });

        Slider volume = content.findViewById(R.id.quick_volume);
        TextView volumeLabel = content.findViewById(R.id.quick_volume_label);
        volume.setValue(clamp(settings.effectsVolume, volume));
        volumeLabel.setText(getString(R.string.settings_sfx) + " · "
                + Math.round(settings.effectsVolume * 100) + "%");
        volume.addOnChangeListener((slider, value, fromUser) -> {
            settings.effectsVolume = value;
            settings.save();
            volumeLabel.setText(getString(R.string.settings_sfx) + " · "
                    + Math.round(value * 100) + "%");
            if (audio != null) {
                audio.setMasterVolume(value);
                audio.playUi(Sfx.UI, 0.5f, 1f);
            }
        });

        Slider sensitivity = content.findViewById(R.id.quick_sensitivity);
        TextView sensitivityLabel = content.findViewById(R.id.quick_sensitivity_label);
        sensitivity.setValue(clamp(settings.lookSensitivity, sensitivity));
        sensitivityLabel.setText(getString(R.string.settings_sensitivity) + " · "
                + String.format(Locale.US, "%.2f", settings.lookSensitivity));
        sensitivity.addOnChangeListener((slider, value, fromUser) -> {
            settings.lookSensitivity = value;
            settings.save();
            input.lookSensitivity = value;
            sensitivityLabel.setText(getString(R.string.settings_sensitivity) + " · "
                    + String.format(Locale.US, "%.2f", value));
        });

        MaterialSwitch autoJump = content.findViewById(R.id.quick_auto_jump);
        autoJump.setChecked(settings.autoJump);
        autoJump.setOnCheckedChangeListener((button, checked) -> {
            settings.autoJump = checked;
            settings.save();
            input.autoJump = checked;
        });

        MaterialSwitch playerHighlights = content.findViewById(R.id.quick_player_highlights);
        playerHighlights.setChecked(settings.playerHighlights);
        playerHighlights.setOnCheckedChangeListener((button, checked) -> {
            settings.playerHighlights = checked;
            settings.save();
            renderSettings.playerHighlights = checked;
            surface.queueEvent(() -> renderer.settings().playerHighlights = checked);
        });

        Slider hudSize = content.findViewById(R.id.quick_hud);
        TextView hudLabel = content.findViewById(R.id.quick_hud_label);
        hudSize.setValue(clamp(hudLayout.scale, hudSize));
        hudLabel.setText(getString(R.string.hud_size) + " · "
                + Math.round(hudLayout.scale * 100) + "%");
        hudSize.addOnChangeListener((slider, value, fromUser) -> {
            hudLayout.setScale(value);
            hudLayout.save();
            hud.setLayout(hudLayout);
            hudLabel.setText(getString(R.string.hud_size) + " · " + Math.round(value * 100) + "%");
        });

        Slider hudOpacity = content.findViewById(R.id.quick_opacity);
        TextView opacityLabel = content.findViewById(R.id.quick_opacity_label);
        hudOpacity.setValue(clamp(hudLayout.opacity, hudOpacity));
        opacityLabel.setText(getString(R.string.hud_opacity) + " · "
                + Math.round(hudLayout.opacity * 100) + "%");
        hudOpacity.addOnChangeListener((slider, value, fromUser) -> {
            hudLayout.opacity = value;
            hudLayout.save();
            hud.setLayout(hudLayout);
            opacityLabel.setText(getString(R.string.hud_opacity) + " · "
                    + Math.round(value * 100) + "%");
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_settings)
                .setView(content)
                .setPositiveButton(R.string.match_resume, null)
                .show();
    }

    private String toneLabel(int stringId, float value) {
        return getString(stringId) + " · " + String.format(Locale.US, "%.2f", value);
    }

    private static float clamp(float value, Slider slider) {
        return Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), value));
    }

    private void applyRenderSettings() {
        RenderSettings updated = settings.toRenderSettings();
        surface.queueEvent(() -> {
            RenderSettings live = renderer.settings();
            live.quality = updated.quality;
            live.rayTracing = updated.rayTracing;
            live.rtShadows = updated.rtShadows;
            live.rtGlobalIllumination = updated.rtGlobalIllumination;
            live.rtReflections = updated.rtReflections;
            live.denoiser = updated.denoiser;
            live.denoisePasses = updated.denoisePasses;
            live.temporalAntiAliasing = updated.temporalAntiAliasing;
            live.bloom = updated.bloom;
            live.filmGrain = updated.filmGrain;
            live.playerHighlights = updated.playerHighlights;
            live.adaptivePerformance = updated.adaptivePerformance;
            live.renderScale = updated.renderScale;
            live.traceScale = updated.traceScale;
            live.giSamples = updated.giSamples;
            live.shadowSteps = updated.shadowSteps;
            live.giSteps = updated.giSteps;
            live.giRange = updated.giRange;
            live.lightRange = updated.lightRange;
            live.viewDistance = updated.viewDistance;
            live.contrast = updated.contrast;
            live.brightness = updated.brightness;
            live.gamma = updated.gamma;
            live.fieldOfView = updated.fieldOfView;
            live.frameRateCap = updated.frameRateCap;
            renderer.onSettingsChanged();
        });
        applyDisplayFrameRate();
    }

    private void restart() {
        Intent intent = new Intent(getIntent());
        intent.putExtra(EXTRA_MATCH_REQUEST, UUID.randomUUID().toString());
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------------------ match callbacks

    @Override
    public void onSound(int kind, float x, float y, float z, float volume, float pitch) {
        if (audio != null) {
            boolean occluded = match != null && Sfx.gunshot(kind)
                    && !match.world().lineOfSight(x, y, z, match.player().eyeX(),
                    match.player().eyeY(), match.player().eyeZ());
            audio.play(kind, x, y, z, volume, pitch, occluded);
        }
    }

    @Override
    public void onLocalHit(boolean headshot, boolean lethal) {
        vibrate(lethal ? 26 : 12);
    }

    @Override
    public void onLocalDamage(float amount, float fromYaw) {
        vibrate(Math.min(40, 10 + (int) amount));
    }

    @Override
    public void onKillFeed(KillFeedEntry entry) {
        // The feed is rendered from the HUD snapshot; nothing extra to do here.
    }

    @Override
    public void onMatchEnd(MatchResult result) {
        handler.post(() -> showResults(result));
    }

    private void showResults(MatchResult result) {
        if (resultsShown) {
            return;
        }
        resultsShown = true;
        CareerProgression.Update progression = profile.completeMatch(result);
        input.paused = true;
        input.releaseAll();
        hud.clearAim();
        releasePointer();

        int levelsGained = progression.levelsGained();

        TextView title = findViewById(R.id.results_title);
        TextView detail = findViewById(R.id.results_detail);
        TextView rewards = findViewById(R.id.results_rewards);

        title.setText(result.draw ? R.string.match_draw
                : result.victory ? R.string.match_victory : R.string.match_defeat);
        StringBuilder body = new StringBuilder();
        body.append(result.mapName).append(" · ").append(result.mode.label()).append('\n');
        body.append("Kills ").append(result.playerKills)
                .append("   Deaths ").append(result.playerDeaths)
                .append("   Headshots ").append(result.headshots).append('\n');
        if (result.mode.teamBased()) {
            body.append("Blue ").append(result.blueScore).append(" — ").append(result.redScore)
                    .append(" Red\n");
        } else if (result.mode == GameMode.ZOMBIE) {
            body.append("Survived to wave ").append(result.wave).append('\n');
        }
        body.append("Best streak ").append(result.bestStreak);
        detail.setText(body.toString());

        // Coins are the persistent, cosmetic currency; round money died with the match.
        String reward = "+" + result.coinsEarned + " ¢   +" + result.experienceEarned + " XP";
        if (levelsGained > 0) {
            reward += "   LEVEL UP ×" + levelsGained;
        }
        if (!CareerProgression.eligible(result)) {
            reward = getString(R.string.career_practice);
        } else {
            if (progression.coins() > 0 || progression.xp() > 0) {
                reward += "\n" + getString(R.string.career_result_bonus, progression.coins(), progression.xp());
            }
            for (CareerProgression.Award award : progression.awards()) {
                reward += "\n✓ " + (award.weapon() == null ? getString(award.title())
                        : getString(award.title(), award.weapon().displayName(), award.tier()));
                if (award.cosmetic() != null) {
                    reward += " · " + getString(CareerProgression.cosmeticName(award.cosmetic()));
                }
            }
            CareerProgression.Goal next = null;
            for (CareerProgression.Goal goal : CareerProgression.GOALS) {
                int progress = profile.goalProgress(goal);
                if (progress >= goal.target()) continue;
                if (next == null || progress / (float) goal.target()
                        > profile.goalProgress(next) / (float) next.target()) next = goal;
            }
            if (next != null) {
                reward += "\n" + getString(R.string.career_result_next, getString(next.title()),
                        profile.goalProgress(next), next.target());
            }
        }
        rewards.setText(reward);

        resultsOverlay.setVisibility(View.VISIBLE);
        if (audio != null) {
            audio.playUi(result.victory ? Sfx.ROUND_START : Sfx.DEATH, 0.7f, 1f);
        }
    }

    private void vibrate(int milliseconds) {
        if (!settings.haptics || vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(milliseconds);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (keyboardMonitor != null) keyboardMonitor.start();
        if (renderer != null) renderer.startThermalMonitoring();
        graphicsDisplayManager = (android.hardware.display.DisplayManager)
                getSystemService(Context.DISPLAY_SERVICE);
        if (graphicsDisplayManager != null) {
            graphicsDisplayManager.registerDisplayListener(graphicsDisplayListener, handler);
        }
        updateRendererRefreshRate();
        goFullscreen();
        if (surfaceRunning) {
            surface.onResume();
        }
        if (audio != null) {
            audio.start();
            audio.setMasterVolume(settings.effectsVolume);
        }
        // onResume follows onCreate on the first launch. Always collapse any queued callback into
        // one self-posting chain so the HUD is not redrawn twice per display frame.
        handler.removeCallbacks(hudPump);
        handler.post(hudPump);
    }

    @Override
    protected void onPause() {
        if (renderer != null) renderer.stopThermalMonitoring();
        if (graphicsDisplayManager != null) {
            graphicsDisplayManager.unregisterDisplayListener(graphicsDisplayListener);
        }
        if (keyboardMonitor != null) keyboardMonitor.stop();
        handler.removeCallbacks(hudPump);
        releasePointer();
        if (!resultsShown) {
            showPause();
        }
        if (surfaceRunning) {
            surface.onPause();
        }
        if (match != null) {
            persistMovementBest(match.snapshot());
            // A finish immediately followed by Home/Back can precede the next HUD publication.
            int best = match.courseBestMillis();
            if (best > 0 && best != savedCourseBest) {
                movementRecords.saveBest(match.snapshot().modifiers, best);
                savedCourseBest = best;
            }
        }
        if (audio != null) {
            audio.release();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (keyboardMonitor != null) keyboardMonitor.stop();
        handler.removeCallbacksAndMessages(null);
        persistMovementGhost();
        if (ghostWriter != null) ghostWriter.shutdown(); // Drain already queued saves; never wait on UI.
        surfaceRunning = false;
        if (buyDialog != null) {
            buyDialog.setOnDismissListener(null);
            buyDialog.dismiss();
            buyDialog = null;
            clearBuyViews();
        }
        for (Bitmap bitmap : buyPreviews.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        buyPreviews.clear();
        // GL objects die with the context; stop the mesher threads before the volume is freed.
        renderer.shutdownWorkers();
        if (match != null) {
            match.release();
            match = null;
        }
        super.onDestroy();
    }
}
