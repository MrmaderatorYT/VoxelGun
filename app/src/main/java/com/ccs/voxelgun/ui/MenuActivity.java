package com.ccs.voxelgun.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.ccs.voxelgun.R;
import com.ccs.voxelgun.app.Cosmetics;
import com.ccs.voxelgun.app.GameSettings;
import com.ccs.voxelgun.app.HudLayout;
import com.ccs.voxelgun.app.KeyBindings;
import com.ccs.voxelgun.app.Profile;
import com.ccs.voxelgun.app.ProfileBackup;
import com.ccs.voxelgun.audio.AudioEngine;
import com.ccs.voxelgun.audio.Sfx;
import com.ccs.voxelgun.game.BattlefieldClass;
import com.ccs.voxelgun.game.BattlefieldRules;
import com.ccs.voxelgun.game.Difficulty;
import com.ccs.voxelgun.game.GameMode;
import com.ccs.voxelgun.game.MatchModifier;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.render.RenderSettings;
import com.ccs.voxelgun.weapon.WeaponType;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;

/** Game front end: profile, match setup, arsenal, settings and career stats. */
public final class MenuActivity extends AppCompatActivity {
    private static final int SCREEN_MAIN = 0;
    private static final int SCREEN_PLAY = 1;
    private static final int SCREEN_ARSENAL = 2;
    private static final int SCREEN_SETTINGS = 3;
    private static final int SCREEN_STATS = 4;
    private static final int SCREEN_HUD = 5;
    private static final int SCREEN_KEYS = 6;

    /** The one-tap contrasty look: deeper shadows and a pulled back exposure. */
    static final float CONTRAST_PRESET = 1.3f;
    static final float BRIGHTNESS_PRESET = 0.8f;

    private Profile profile;
    private GameSettings settings;
    private HudLayout hudLayout;
    private KeyBindings bindings;
    private AudioEngine audio;
    private ViewFlipper flipper;

    private final java.util.concurrent.ExecutorService previewWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private int previewRequest;
    private int previewMap = -1;
    private boolean previewTraining;
    private android.widget.ImageView mapPreview;
    private CosmeticTurntable turntable;
    /** SAF pickers for the profile backup; registered in {@link #onCreate}. */
    private androidx.activity.result.ActivityResultLauncher<String> exportPicker;
    private androidx.activity.result.ActivityResultLauncher<String[]> importPicker;
    /** Which weapon the arsenal's skin section is painting. */
    private int skinWeaponIndex;
    private TextView mapDetail;
    private View legacyBotOptions;
    private View battlefieldOptions;
    private SelectorBinding mapSelector;
    private final MaterialSwitch[] modifierSwitches = new MaterialSwitch[MatchModifier.values().length];
    private boolean updatingModifiers;
    /** Where the arrange screen should return to, since it opens from two places. */
    private int hudReturnScreen = SCREEN_SETTINGS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_menu);
        hideSystemBars();

        profile = new Profile(this);
        settings = new GameSettings(this);
        hudLayout = new HudLayout(this);
        bindings = new KeyBindings(this);
        audio = new AudioEngine();
        audio.start();
        audio.setMasterVolume(settings.effectsVolume * 0.7f);

        registerBackupPickers();

        flipper = findViewById(R.id.flipper);

        setupMainScreen();
        setupPlayScreen();
        setupArsenalScreen();
        setupSettingsScreen();
        setupHudScreen();
        setupKeysScreen();
        setupStatsScreen();
        animateMainEntrance();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                int screen = flipper.getDisplayedChild();
                if (screen == SCREEN_MAIN) {
                    finish();
                } else if (screen == SCREEN_HUD) {
                    show(hudReturnScreen);
                } else if (screen == SCREEN_KEYS) {
                    show(SCREEN_SETTINGS);
                } else {
                    show(SCREEN_MAIN);
                }
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                    && flipper != null && flipper.getDisplayedChild() != SCREEN_MAIN) {
                getOnBackPressedDispatcher().onBackPressed();
            }
            // There is no match to pause on the title screen; Escape must not finish the app.
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void show(int screen) {
        flipper.setDisplayedChild(screen);
        // The captured battle frame needs no animated backdrop drawing behind it.
        findViewById(R.id.menu_backdrop).setVisibility(screen == SCREEN_MAIN ? View.INVISIBLE : View.VISIBLE);
        View page = flipper.getCurrentView();
        page.animate().cancel();
        page.setAlpha(0f);
        page.setTranslationX(screen == SCREEN_MAIN ? -dp(14) : dp(14));
        page.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(210L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        click();
        if (screen == SCREEN_MAIN) {
            refreshProfileCard();
        } else if (screen == SCREEN_STATS) {
            refreshStats();
        } else if (screen == SCREEN_ARSENAL) {
            refreshArsenal();
        }
    }

    /** Staggers the first frame so the menu lands like a game title screen, not a form. */
    private void animateMainEntrance() {
        View logo = findViewById(R.id.menu_wordmark);
        View battleScene = findViewById(R.id.menu_battle_scene);
        View actions = findViewById(R.id.main_menu_actions);
        View profileCard = findViewById(R.id.card_profile);
        logo.setAlpha(0f);
        logo.setTranslationY(-dp(10));
        battleScene.setAlpha(0f);
        actions.setAlpha(0f);
        actions.setTranslationY(dp(14));
        profileCard.setAlpha(0f);
        profileCard.setTranslationX(dp(18));
        logo.post(() -> {
            android.view.animation.Interpolator easing =
                    new android.view.animation.DecelerateInterpolator();
            logo.animate().alpha(1f).translationY(0f).setDuration(360L)
                    .setInterpolator(easing).start();
            battleScene.animate().alpha(1f).setStartDelay(40L)
                    .setDuration(440L).setInterpolator(easing).start();
            actions.animate().alpha(1f).translationY(0f).setStartDelay(150L)
                    .setDuration(320L).setInterpolator(easing).start();
            profileCard.animate().alpha(1f).translationX(0f).setStartDelay(220L)
                    .setDuration(320L).setInterpolator(easing).start();
        });
    }

    private void click() {
        if (audio != null) {
            audio.playUi(Sfx.UI, 0.4f, 1.1f);
        }
    }

    // ------------------------------------------------------------------ main

    private void setupMainScreen() {
        findViewById(R.id.menu_backdrop).setVisibility(View.INVISIBLE);
        findViewById(R.id.button_play).setOnClickListener(v -> show(SCREEN_PLAY));
        findViewById(R.id.button_arsenal).setOnClickListener(v -> show(SCREEN_ARSENAL));
        findViewById(R.id.button_settings).setOnClickListener(v -> show(SCREEN_SETTINGS));
        findViewById(R.id.button_stats).setOnClickListener(v -> show(SCREEN_STATS));
        findViewById(R.id.button_controls).setOnClickListener(v -> {
            hudReturnScreen = SCREEN_MAIN;
            show(SCREEN_HUD);
        });
        findViewById(R.id.card_profile).setOnClickListener(v -> promptForName());
        refreshProfileCard();
    }

    private void refreshProfileCard() {
        ((TextView) findViewById(R.id.text_player_name)).setText(profile.name());
        int level = profile.level();
        ((TextView) findViewById(R.id.text_level)).setText(getString(R.string.menu_home_level, level));
        LinearProgressIndicator progress = findViewById(R.id.progress_level);
        progress.setProgress(Math.round(profile.levelProgress() * 100));
        ((TextView) findViewById(R.id.text_coins)).setText(profile.coins() + " ¢");
        ((TextView) findViewById(R.id.text_kd)).setText(String.format(Locale.US, "K/D %.2f",
                profile.killDeathRatio()));
    }

    private void promptForName() {
        final android.widget.EditText field = new android.widget.EditText(this);
        field.setText(profile.name());
        field.setSingleLine(true);
        int padding = Math.round(getResources().getDisplayMetrics().density * 20);
        field.setPadding(padding, padding, padding, padding);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Call sign")
                .setView(field)
                .setPositiveButton("Save", (dialog, which) -> {
                    profile.setName(field.getText().toString());
                    refreshProfileCard();
                })
                .setNegativeButton(R.string.menu_back, null)
                .show();
    }

    // ------------------------------------------------------------------ play setup

    private void setupPlayScreen() {
        findViewById(R.id.play_back).setOnClickListener(v -> show(SCREEN_MAIN));

        LinearLayout modeContainer = findViewById(R.id.mode_container);
        GameMode[] modes = GameMode.values();
        settings.lastMode = Math.max(0, Math.min(modes.length - 1, settings.lastMode));
        settings.lastMap = Math.max(0, Math.min(Maps.count() - 1, settings.lastMap));
        String[] modeNames = new String[modes.length];
        for (int i = 0; i < modes.length; i++) modeNames[i] = modes[i].label();
        addSelector(modeContainer, modeNames, settings.lastMode, index -> {
            settings.lastMode = index;
            updateModeSelection();
        });
        TextView modifiersTitle = new TextView(this);
        modifiersTitle.setText(R.string.match_modifiers);
        modifiersTitle.setTextAppearance(R.style.TextAppearance_Section);
        modeContainer.addView(modifiersTitle);
        for (MatchModifier modifier : MatchModifier.values()) {
            MaterialSwitch control = new MaterialSwitch(this);
            control.setTag(modifier);
            control.setText(modifier.label);
            control.setTextSize(13f);
            control.setOnCheckedChangeListener((button, checked) -> {
                if (updatingModifiers) return;
                if (checked) settings.matchModifiers |= modifier.bit;
                else settings.matchModifiers &= ~modifier.bit;
                if (checked && modifier == MatchModifier.RANDOM_WEAPONS)
                    settings.matchModifiers &= ~(MatchModifier.ONE_SHOT_RAIL.bit | MatchModifier.MIRROR_LOADOUT.bit);
                if (checked && modifier == MatchModifier.MIRROR_LOADOUT)
                    settings.matchModifiers &= ~MatchModifier.RANDOM_WEAPONS.bit;
                updateModeSelection(); settings.save();
            });
            modifierSwitches[modifier.ordinal()] = control;
            modeContainer.addView(control);
        }

        LinearLayout mapContainer = findViewById(R.id.map_container);
        String[] mapNames = new String[Maps.count()];
        for (int i = 0; i < mapNames.length; i++) mapNames[i] = Maps.name(i);
        mapSelector = addSelector(mapContainer, mapNames, settings.lastMap, index -> {
            settings.lastMap = index;
            if (Maps.isBattlefield(index)) {
                settings.lastBattlefieldMap = index;
            } else {
                settings.lastArenaMap = index;
            }
            settings.save();
            updateMapSelection();
        }, index -> settings.lastMode == GameMode.BATTLE_FIELD.ordinal()
                ? Maps.isBattlefield(index) : !Maps.isBattlefield(index));
        mapPreview = new android.widget.ImageView(this);
        mapPreview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        mapPreview.setBackgroundResource(R.drawable.bg_map_preview);
        mapPreview.setPadding(dp(4), dp(4), dp(4), dp(4));
        mapContainer.addView(mapPreview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
        mapDetail = new TextView(this);
        mapDetail.setTextSize(12f);
        mapDetail.setTextColor(getColor(R.color.game_text_muted));
        mapDetail.setPadding(0, dp(5), 0, 0);
        mapContainer.addView(mapDetail);

        MaterialButtonToggleGroup difficulties = findViewById(R.id.difficulty_group);
        Difficulty[] levels = Difficulty.values();
        for (int i = 0; i < levels.length; i++) {
            MaterialButton button = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(View.generateViewId());
            button.setText(levels[i].label());
            button.setTag(i);
            button.setTextSize(11f);
            button.setBackgroundTintList(androidx.appcompat.content.res.AppCompatResources
                    .getColorStateList(this, R.color.game_buy_tab_background));
            button.setTextColor(androidx.appcompat.content.res.AppCompatResources
                    .getColorStateList(this, R.color.game_buy_tab_text));
            button.setMinWidth(0);
            button.setSingleLine(true);
            button.setPaddingRelative(dp(6), 0, dp(6), 0);
            button.setInsetTop(0);
            button.setInsetBottom(0);
            difficulties.addView(button, new LinearLayout.LayoutParams(0,
                    dp(48), 1f));
            if (i == settings.lastDifficulty) {
                difficulties.check(button.getId());
            }
        }
        difficulties.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            View button = group.findViewById(checkedId);
            if (button != null && button.getTag() instanceof Integer) {
                settings.lastDifficulty = (Integer) button.getTag();
                settings.save();
                click();
            }
        });

        Slider bots = findViewById(R.id.slider_bots);
        TextView botsLabel = findViewById(R.id.text_bots);
        settings.lastBotCount = Math.max(1, Math.min(11, settings.lastBotCount));
        bots.setValue(settings.lastBotCount);
        botsLabel.setText(getString(R.string.menu_bots) + " · " + settings.lastBotCount);
        bots.addOnChangeListener((slider, value, fromUser) -> {
            settings.lastBotCount = Math.round(value);
            settings.save();
            botsLabel.setText(getString(R.string.menu_bots) + " · " + settings.lastBotCount);
        });

        legacyBotOptions = findViewById(R.id.legacy_bot_options);
        battlefieldOptions = findViewById(R.id.battlefield_options);
        setupBattlefieldOptions();

        findViewById(R.id.button_start).setOnClickListener(v -> {
            click();
            startActivity(MatchActivity.intent(this, settings.lastMode, settings.lastMap,
                    settings.lastBotCount, settings.lastDifficulty));
        });

        updateModeSelection();
        updateMapSelection();
    }

    /** Binds the large-team rules and the three independent weapon picks used by Battle Field. */
    private void setupBattlefieldOptions() {
        Slider players = findViewById(R.id.slider_battlefield_players);
        TextView playersLabel = findViewById(R.id.text_battlefield_players);
        settings.battlefieldTotalPlayers = BattlefieldRules.sanitizeTotalPlayers(
                settings.battlefieldTotalPlayers);
        players.setValue(settings.battlefieldTotalPlayers);
        updateBattlefieldPlayersLabel(playersLabel);
        players.addOnChangeListener((slider, value, fromUser) -> {
            settings.battlefieldTotalPlayers = BattlefieldRules.sanitizeTotalPlayers(
                    Math.round(value));
            updateBattlefieldPlayersLabel(playersLabel);
            settings.save();
        });

        Slider score = findViewById(R.id.slider_battlefield_score);
        TextView scoreLabel = findViewById(R.id.text_battlefield_score);
        settings.battlefieldScoreLimit = BattlefieldRules.sanitizeScoreLimit(
                settings.battlefieldScoreLimit);
        score.setValue(settings.battlefieldScoreLimit);
        scoreLabel.setText(getString(R.string.battlefield_score_limit,
                settings.battlefieldScoreLimit));
        score.addOnChangeListener((slider, value, fromUser) -> {
            settings.battlefieldScoreLimit = BattlefieldRules.sanitizeScoreLimit(
                    Math.round(value));
            scoreLabel.setText(getString(R.string.battlefield_score_limit,
                    settings.battlefieldScoreLimit));
            settings.save();
        });

        Slider time = findViewById(R.id.slider_battlefield_time);
        TextView timeLabel = findViewById(R.id.text_battlefield_time);
        settings.battlefieldTimeLimitSeconds = BattlefieldRules.sanitizeTimeSeconds(
                settings.battlefieldTimeLimitSeconds);
        int minutes = Math.round(settings.battlefieldTimeLimitSeconds / 60f);
        time.setValue(minutes);
        timeLabel.setText(getString(R.string.battlefield_time, minutes));
        time.addOnChangeListener((slider, value, fromUser) -> {
            int selectedMinutes = Math.round(value);
            settings.battlefieldTimeLimitSeconds = BattlefieldRules.sanitizeTimeSeconds(
                    selectedMinutes * 60f);
            timeLabel.setText(getString(R.string.battlefield_time, selectedMinutes));
            settings.save();
        });

        MaterialSwitch hardcore = findViewById(R.id.switch_battlefield_hardcore);
        MaterialSwitch conquest = findViewById(R.id.switch_battlefield_conquest);
        conquest.setChecked(settings.battlefieldConquest);
        conquest.setOnCheckedChangeListener((button, checked) -> {
            settings.battlefieldConquest = checked;
            settings.save();
        });
        hardcore.setChecked(settings.battlefieldHardcore);
        hardcore.setOnCheckedChangeListener((button, checked) -> {
            settings.battlefieldHardcore = checked;
            settings.save();
        });

        BattlefieldClass[] classes = BattlefieldClass.values();
        String[] classNames = {
                getString(R.string.battlefield_class_rifleman),
                getString(R.string.battlefield_class_sniper),
                getString(R.string.battlefield_class_machine_gunner)
        };
        addSelector(findViewById(R.id.battlefield_class_container), classNames,
                settings.battlefieldClass.ordinal(), index -> {
                    settings.battlefieldClass = classes[index];
                    settings.save();
                    updateBattlefieldWeaponVisibility();
                });

        bindBattlefieldWeaponSelector(R.id.battlefield_rifle_container,
                BattlefieldRules.RIFLES, settings.battlefieldPrimary, selected -> {
                    settings.battlefieldPrimary = selected;
                    settings.save();
                });
        bindBattlefieldWeaponSelector(R.id.battlefield_sniper_container,
                BattlefieldRules.SNIPERS, settings.battlefieldSniper, selected -> {
                    settings.battlefieldSniper = selected;
                    settings.save();
                });
        bindBattlefieldWeaponSelector(R.id.battlefield_lmg_container,
                BattlefieldRules.MACHINE_GUNS, settings.battlefieldLmg, selected -> {
                    settings.battlefieldLmg = selected;
                    settings.save();
                });
        updateBattlefieldWeaponVisibility();
    }

    /** A class owns exactly one long-gun family; the other two pickers must not imply otherwise. */
    private void updateBattlefieldWeaponVisibility() {
        boolean rifleman = settings.battlefieldClass == BattlefieldClass.RIFLEMAN;
        boolean sniper = settings.battlefieldClass == BattlefieldClass.SNIPER;
        setVisible(R.id.battlefield_rifle_label, rifleman);
        setVisible(R.id.battlefield_rifle_container, rifleman);
        setVisible(R.id.battlefield_sniper_label, sniper);
        setVisible(R.id.battlefield_sniper_container, sniper);
        setVisible(R.id.battlefield_lmg_label, !rifleman && !sniper);
        setVisible(R.id.battlefield_lmg_container, !rifleman && !sniper);
    }

    private void setVisible(int viewId, boolean visible) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updateBattlefieldPlayersLabel(TextView label) {
        int players = settings.battlefieldTotalPlayers;
        label.setText(getString(R.string.battlefield_players, players, players / 2));
    }

    private void bindBattlefieldWeaponSelector(int containerId, WeaponType[] choices,
                                               WeaponType selected,
                                               java.util.function.Consumer<WeaponType> onPicked) {
        String[] names = new String[choices.length];
        int selectedIndex = 0;
        for (int i = 0; i < choices.length; i++) {
            names[i] = choices[i].displayName();
            if (choices[i] == selected) {
                selectedIndex = i;
            }
        }
        addSelector(findViewById(containerId), names, selectedIndex,
                index -> onPicked.accept(choices[index]));
    }

    private MaterialCardView inflateChoice(ViewGroup parent, String title, String subtitle) {
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(this)
                .inflate(R.layout.item_choice, parent, false);
        ((TextView) card.findViewById(R.id.choice_title)).setText(title);
        ((TextView) card.findViewById(R.id.choice_subtitle)).setText(subtitle);
        return card;
    }

    private void updateModeSelection() {
        GameMode mode = GameMode.values()[settings.lastMode];
        boolean battlefield = mode == GameMode.BATTLE_FIELD;
        boolean training = mode == GameMode.MOVEMENT_TRAINING;
        if (mapSelector != null) mapSelector.root.setVisibility(training ? View.GONE : View.VISIBLE);
        if (legacyBotOptions != null) {
            legacyBotOptions.setVisibility(battlefield || training ? View.GONE : View.VISIBLE);
        }
        if (battlefieldOptions != null) {
            battlefieldOptions.setVisibility(battlefield ? View.VISIBLE : View.GONE);
        }
        int selectedMap = battlefield ? settings.lastBattlefieldMap : settings.lastArenaMap;
        if (battlefield && !Maps.isBattlefield(selectedMap)) {
            selectedMap = Maps.firstBattlefieldIndex();
            settings.lastBattlefieldMap = selectedMap;
        } else if (!battlefield && (selectedMap < 0 || selectedMap >= Maps.count()
                || Maps.isBattlefield(selectedMap))) {
            selectedMap = 0;
            settings.lastArenaMap = selectedMap;
        }
        if (settings.lastMap != selectedMap) {
            settings.lastMap = selectedMap;
            if (mapSelector != null) {
                mapSelector.select(settings.lastMap, false);
            }
            updateMapSelection();
        }
        settings.matchModifiers = MatchModifier.sanitize(settings.matchModifiers, mode);
        boolean botsEnabled = (settings.matchModifiers & MatchModifier.NO_BOTS.bit) == 0;
        findViewById(R.id.slider_bots).setEnabled(botsEnabled);
        findViewById(R.id.slider_battlefield_players).setEnabled(botsEnabled);
        MaterialButtonToggleGroup difficulty = findViewById(R.id.difficulty_group);
        for (int i = 0; i < difficulty.getChildCount(); i++) difficulty.getChildAt(i).setEnabled(botsEnabled);
        ((TextView) findViewById(R.id.text_bots)).setText(botsEnabled
                ? getString(R.string.menu_bots) + " · " + settings.lastBotCount : "Solo exploration · 0 bots");
        TextView playersLabel = findViewById(R.id.text_battlefield_players);
        if (botsEnabled) updateBattlefieldPlayersLabel(playersLabel);
        else playersLabel.setText("Solo exploration · 1 player");
        updatingModifiers = true;
        for (MatchModifier modifier : MatchModifier.values()) {
            MaterialSwitch control = modifierSwitches[modifier.ordinal()];
            if (control == null) continue;
            control.setEnabled(modifier.supports(mode));
            control.setChecked((settings.matchModifiers & modifier.bit) != 0);
        }
        updatingModifiers = false;
        settings.save();
        updateMapSelection();
    }

    private void updateMapSelection() {
        if (mapPreview == null) return;
        int index = settings.lastMap;
        boolean training = GameMode.values()[settings.lastMode] == GameMode.MOVEMENT_TRAINING;
        if (previewMap == index && previewTraining == training) return;
        previewMap = index;
        previewTraining = training;
        int request = ++previewRequest;
        mapPreview.setImageDrawable(null);
        mapPreview.setContentDescription(training ? "Movement Course" : Maps.name(index));
        mapDetail.setText(training ? "Movement Course · ordered checkpoints · personal best"
                : Maps.tags(index) + "\n" + getString(R.string.recommended_mode, Maps.recommended(index).label()));
        previewWorker.execute(() -> {
            com.ccs.voxelgun.map.GameMap map = training ? new com.ccs.voxelgun.map.maps.MovementCourse() : Maps.create(index);
            com.ccs.voxelgun.voxel.VoxelWorld world = map.build(20260726L);
            android.graphics.Bitmap bitmap;
            try { bitmap = MapPreview.render(world, map); }
            finally { world.release(); }
            java.util.StringJoiner callouts = new java.util.StringJoiner(" · ");
            for (com.ccs.voxelgun.map.scenery.Landmark landmark : map.scenery().landmarks()) {
                callouts.add(landmark.name);
            }
            String places = callouts.toString();
            runOnUiThread(() -> {
                if (!isDestroyed() && request == previewRequest) {
                    mapPreview.setImageBitmap(bitmap);
                    if (!places.isEmpty()) mapDetail.append("\n" + places);
                }
                else bitmap.recycle();
            });
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private SelectorBinding addSelector(LinearLayout parent, String[] names, int current,
                                        java.util.function.IntConsumer onPicked) {
        return addSelector(parent, names, current, onPicked, index -> true);
    }

    private SelectorBinding addSelector(LinearLayout parent, String[] names, int current,
                                        java.util.function.IntConsumer onPicked,
                                        java.util.function.IntPredicate allowed) {
        View selector = LayoutInflater.from(this).inflate(R.layout.item_selector, parent, false);
        TextView value = selector.findViewById(R.id.selector_value);
        View previous = selector.findViewById(R.id.selector_previous);
        View next = selector.findViewById(R.id.selector_next);
        SelectorBinding binding = new SelectorBinding(selector, value, names, current, onPicked,
                allowed);
        previous.setContentDescription(getString(R.string.selector_previous));
        next.setContentDescription(getString(R.string.selector_next));
        previous.setOnClickListener(v -> binding.move(-1));
        next.setOnClickListener(v -> binding.move(1));
        value.setOnClickListener(v -> binding.move(1));
        parent.addView(selector);
        return binding;
    }

    /** Mutable selector handle, used to move the map choice when a mode has its own map pool. */
    private final class SelectorBinding {
        private final View root;
        private final TextView value;
        private final String[] names;
        private final java.util.function.IntConsumer onPicked;
        private final java.util.function.IntPredicate allowed;
        private int selected;

        SelectorBinding(View root, TextView value, String[] names, int current,
                        java.util.function.IntConsumer onPicked,
                        java.util.function.IntPredicate allowed) {
            this.root = root;
            this.value = value;
            this.names = names;
            this.onPicked = onPicked;
            this.allowed = allowed;
            selected = Math.max(0, Math.min(names.length - 1, current));
            showSelection();
        }

        void move(int direction) {
            int candidate = selected;
            for (int attempt = 0; attempt < names.length; attempt++) {
                candidate = Math.floorMod(candidate + direction, names.length);
                if (allowed.test(candidate)) {
                    select(candidate, true);
                    click();
                    return;
                }
            }
        }

        void select(int index, boolean notify) {
            if (index < 0 || index >= names.length || !allowed.test(index)) {
                return;
            }
            selected = index;
            showSelection();
            if (notify) {
                onPicked.accept(selected);
            }
        }

        private void showSelection() {
            value.setText(names[selected]);
            root.setContentDescription(names[selected]);
        }
    }

    // ------------------------------------------------------------------ arsenal

    private void setupArsenalScreen() {
        findViewById(R.id.arsenal_back).setOnClickListener(v -> show(SCREEN_MAIN));
        turntable = findViewById(R.id.inspect_turntable);
        refreshArsenal();
    }

    /** Points the inspector at an item without equipping it, so skins can be looked at first. */
    private void inspect(Cosmetics.Item item) {
        if (turntable == null) {
            return;
        }
        boolean skin = item.category() == Cosmetics.Category.SKIN;
        WeaponType weapon = skin ? skinWeapon() : null;
        turntable.setItem(item, weapon);
        ((TextView) findViewById(R.id.inspect_name))
                .setText(skin ? weapon.displayName() + " · " + item.name() : item.name());
        int equipped = skin ? profile.equippedSkin(weapon) : profile.equipped(item.category());
        String detail = equipped == item.value() ? getString(R.string.skins_equipped)
                : profile.owns(item) ? getString(R.string.skins_owned)
                        : getString(R.string.skins_price, item.cost());
        ((TextView) findViewById(R.id.inspect_detail)).setText(detail);
    }

    private static String skinName(int value) {
        for (Cosmetics.Item item : Cosmetics.ITEMS) {
            if (item.category() == Cosmetics.Category.SKIN && item.value() == value) {
                return item.name();
            }
        }
        return "";
    }

    /** Weapons that can wear a paint: everything a player can actually carry into a round. */
    private static WeaponType[] paintableWeapons() {
        java.util.List<WeaponType> list = new java.util.ArrayList<>();
        for (WeaponType type : WeaponType.values()) {
            if (!type.modeOnly() && !type.grenade()) {
                list.add(type);
            }
        }
        return list.toArray(new WeaponType[0]);
    }

    /** The weapon the skin section is currently pointed at. */
    private WeaponType skinWeapon() {
        WeaponType[] weapons = paintableWeapons();
        return weapons[Math.max(0, Math.min(weapons.length - 1, skinWeaponIndex))];
    }

    /**
     * Weapons are not chosen here any more — they are taken from the buy menu inside a round.
     * This screen is where the coins go: skins, once there are skins.
     */
    private void refreshArsenal() {
        ((TextView) findViewById(R.id.arsenal_coins))
                .setText(getString(R.string.coins_for_skins, profile.coins()));
        LinearLayout container = findViewById(R.id.cosmetics_container);
        container.removeAllViews();
        WeaponType painted = skinWeapon();
        for (Cosmetics.Category category : Cosmetics.Category.values()) {
            TextView heading = new TextView(this);
            heading.setText(category.name()); heading.setTextSize(16f);
            heading.setPadding(0, dp(12), 0, dp(4));
            container.addView(heading);
            if (category == Cosmetics.Category.SKIN) {
                // Paints are per weapon now, so the section starts by picking the gun.
                WeaponType[] weapons = paintableWeapons();
                String[] names = new String[weapons.length];
                for (int i = 0; i < weapons.length; i++) {
                    int value = profile.equippedSkin(weapons[i]);
                    names[i] = weapons[i].displayName()
                            + (value == 0 ? "" : " · " + skinName(value));
                }
                addSelector(container, names, skinWeaponIndex, index -> {
                    skinWeaponIndex = index;
                    refreshArsenal();
                });
            }
            android.widget.RadioGroup choices = new android.widget.RadioGroup(this);
            for (Cosmetics.Item item : Cosmetics.ITEMS) {
                if (item.category() != category) continue;
                boolean skin = category == Cosmetics.Category.SKIN;
                com.google.android.material.radiobutton.MaterialRadioButton button =
                        new com.google.android.material.radiobutton.MaterialRadioButton(this);
                button.setId(View.generateViewId());
                String cost = profile.owns(item) ? "" : "  /  " + item.cost() + " coins";
                button.setText(item.name() + cost);
                button.setTextSize(14f);
                button.setEnabled(profile.owns(item) || profile.coins() >= item.cost());
                // The preview renders the actual item, the way the map picker renders the map.
                android.graphics.Bitmap art = CosmeticPreview.render(item, skin ? painted : null,
                        dp(56), dp(40));
                android.graphics.drawable.Drawable swatch =
                        new android.graphics.drawable.BitmapDrawable(getResources(), art);
                swatch.setBounds(0, 0, dp(56), dp(40));
                button.setCompoundDrawables(null, null, swatch, null);
                button.setCompoundDrawablePadding(dp(8));
                choices.addView(button);
                button.setChecked(skin ? profile.equippedSkin(painted) == item.value()
                        : profile.equipped(category) == item.value());
                // Tapping shows the item in the inspector; equipping only happens if it is owned
                // or affordable, so a locked skin can still be turned around and looked at.
                button.setOnClickListener(v -> {
                    inspect(item);
                    if (skin) {
                        profile.equipSkin(skinWeapon(), item);
                    } else {
                        profile.equip(item);
                    }
                    refreshArsenal();
                    click();
                });
            }
            container.addView(choices);
        }
        Cosmetics.Item shown = turntable == null ? null : turntable.item();
        if (shown == null) {
            for (Cosmetics.Item candidate : Cosmetics.ITEMS) {
                if (candidate.category() == Cosmetics.Category.SKIN
                        && profile.equippedSkin(painted) == candidate.value()) {
                    shown = candidate;
                    break;
                }
            }
        }
        if (shown != null) {
            inspect(shown);
        }
        for (Difficulty difficulty : Difficulty.values()) {
            TextView badge = new TextView(this);
            badge.setText(difficulty.label() + " / " + getString(profile.hasBadge(difficulty)
                    ? R.string.badge_earned : R.string.badge_locked));
            badge.setPadding(0, dp(8), 0, 0);
            container.addView(badge);
        }
    }

    // ------------------------------------------------------------------ settings

    private void setupSettingsScreen() {
        findViewById(R.id.settings_back).setOnClickListener(v -> show(SCREEN_MAIN));

        MaterialButtonToggleGroup qualityGroup = findViewById(R.id.quality_group);
        TextView qualityHint = findViewById(R.id.text_quality_hint);
        RenderSettings.Quality[] presets = RenderSettings.Quality.values();
        for (int i = 0; i < presets.length; i++) {
            MaterialButton button = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(View.generateViewId());
            button.setText(presets[i].label());
            button.setTag(i);
            button.setTextSize(11f);
            styleGraphicsButton(this, button);
            qualityGroup.addView(button, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (presets[i] == settings.quality) {
                qualityGroup.check(button.getId());
            }
        }
        qualityHint.setText(settings.quality.description());

        MaterialSwitch rtShadows = findViewById(R.id.switch_rt_shadows);
        MaterialSwitch rtGi = findViewById(R.id.switch_rt_gi);
        MaterialSwitch rtReflections = findViewById(R.id.switch_rt_reflections);
        MaterialSwitch denoiser = findViewById(R.id.switch_denoiser);
        MaterialSwitch taa = findViewById(R.id.switch_taa);
        MaterialSwitch bloom = findViewById(R.id.switch_bloom);
        Slider renderScale = findViewById(R.id.slider_render_scale);
        Slider traceScale = findViewById(R.id.slider_trace_scale);
        TextView renderScaleLabel = findViewById(R.id.label_render_scale);
        TextView traceScaleLabel = findViewById(R.id.label_trace_scale);

        qualityGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            View button = group.findViewById(checkedId);
            if (button == null || !(button.getTag() instanceof Integer)) {
                return;
            }
            settings.applyPreset(presets[(Integer) button.getTag()]);
            qualityHint.setText(settings.quality.description());
            rtShadows.setChecked(settings.rtShadows);
            rtGi.setChecked(settings.rtGlobalIllumination);
            rtReflections.setChecked(settings.rtReflections);
            denoiser.setChecked(settings.denoiser);
            taa.setChecked(settings.temporalAntiAliasing);
            bloom.setChecked(settings.bloom);
            renderScale.setValue(clampSlider(settings.renderScale, renderScale));
            traceScale.setValue(clampSlider(settings.traceScale, traceScale));
            renderScaleLabel.setText(percentLabel(R.string.settings_render_scale,
                    settings.renderScale));
            traceScaleLabel.setText(percentLabel(0, settings.traceScale));
            click();
        });

        rtShadows.setChecked(settings.rtShadows);
        rtShadows.setOnCheckedChangeListener((view, checked) -> {
            settings.rtShadows = checked;
            settings.save();
        });
        rtGi.setChecked(settings.rtGlobalIllumination);
        rtGi.setOnCheckedChangeListener((view, checked) -> {
            settings.rtGlobalIllumination = checked;
            settings.save();
        });
        rtReflections.setChecked(settings.rtReflections);
        rtReflections.setOnCheckedChangeListener((view, checked) -> {
            settings.rtReflections = checked;
            settings.save();
        });
        denoiser.setChecked(settings.denoiser);
        denoiser.setOnCheckedChangeListener((view, checked) -> {
            settings.denoiser = checked;
            settings.save();
        });
        taa.setChecked(settings.temporalAntiAliasing);
        taa.setOnCheckedChangeListener((view, checked) -> {
            settings.temporalAntiAliasing = checked;
            settings.save();
        });
        bloom.setChecked(settings.bloom);
        bloom.setOnCheckedChangeListener((view, checked) -> {
            settings.bloom = checked;
            settings.save();
        });

        renderScale.setValue(clampSlider(settings.renderScale, renderScale));
        renderScaleLabel.setText(percentLabel(R.string.settings_render_scale, settings.renderScale));
        renderScale.addOnChangeListener((slider, value, fromUser) -> {
            settings.renderScale = value;
            settings.save();
            renderScaleLabel.setText(percentLabel(R.string.settings_render_scale, value));
        });

        traceScale.setValue(clampSlider(settings.traceScale, traceScale));
        traceScaleLabel.setText(percentLabel(0, settings.traceScale));
        traceScale.addOnChangeListener((slider, value, fromUser) -> {
            settings.traceScale = value;
            settings.save();
            traceScaleLabel.setText(percentLabel(0, value));
        });

        Slider contrast = findViewById(R.id.slider_contrast);
        Slider brightness = findViewById(R.id.slider_brightness);
        TextView contrastLabel = findViewById(R.id.label_contrast);
        TextView brightnessLabel = findViewById(R.id.label_brightness);
        MaterialButton picturePreset = findViewById(R.id.button_picture_preset);

        contrast.setValue(clampSlider(settings.contrast, contrast));
        contrast.addOnChangeListener((slider, value, fromUser) -> {
            settings.contrast = value;
            settings.save();
            contrastLabel.setText(valueLabel(R.string.settings_contrast, value));
            picturePreset.setText(pictureToggleLabel());
        });
        contrastLabel.setText(valueLabel(R.string.settings_contrast, settings.contrast));

        brightness.setValue(clampSlider(settings.brightness, brightness));
        brightness.addOnChangeListener((slider, value, fromUser) -> {
            settings.brightness = value;
            settings.save();
            brightnessLabel.setText(valueLabel(R.string.settings_brightness, value));
            picturePreset.setText(pictureToggleLabel());
        });
        brightnessLabel.setText(valueLabel(R.string.settings_brightness, settings.brightness));

        Slider gamma = findViewById(R.id.slider_gamma);
        TextView gammaLabel = findViewById(R.id.label_gamma);
        gamma.setValue(clampSlider(settings.gamma, gamma));
        gammaLabel.setText(valueLabel(R.string.settings_gamma, settings.gamma));
        gamma.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            settings.gamma = value;
            settings.save();
            gammaLabel.setText(valueLabel(R.string.settings_gamma, value));
        });

        picturePreset.setText(pictureToggleLabel());
        // One tap between the untouched picture and the darker, punchier one; the sliders stay
        // available for anything in between and their change listeners relabel this button.
        picturePreset.setOnClickListener(v -> {
            boolean neutral = neutralPicture();
            contrast.setValue(neutral ? CONTRAST_PRESET : 1f);
            brightness.setValue(neutral ? BRIGHTNESS_PRESET : 1f);
            click();
        });

        Slider sensitivity = findViewById(R.id.slider_sensitivity);
        TextView sensitivityLabel = findViewById(R.id.label_sensitivity);
        sensitivity.setValue(clampSlider(settings.lookSensitivity, sensitivity));
        sensitivityLabel.setText(valueLabel(R.string.settings_sensitivity,
                settings.lookSensitivity));
        sensitivity.addOnChangeListener((slider, value, fromUser) -> {
            settings.lookSensitivity = value;
            settings.save();
            sensitivityLabel.setText(valueLabel(R.string.settings_sensitivity, value));
        });

        Slider adsSensitivity = findViewById(R.id.slider_ads_sensitivity);
        TextView adsLabel = findViewById(R.id.label_ads_sensitivity);
        adsSensitivity.setValue(clampSlider(settings.adsSensitivity, adsSensitivity));
        adsLabel.setText(valueLabel(R.string.settings_ads_sensitivity, settings.adsSensitivity));
        adsSensitivity.addOnChangeListener((slider, value, fromUser) -> {
            settings.adsSensitivity = value;
            settings.save();
            adsLabel.setText(valueLabel(R.string.settings_ads_sensitivity, value));
        });

        Slider scopeSensitivity = findViewById(R.id.slider_scope_sensitivity);
        TextView scopeLabel = findViewById(R.id.label_scope_sensitivity);
        scopeSensitivity.setValue(clampSlider(settings.scopeSensitivity, scopeSensitivity));
        scopeLabel.setText(valueLabel(R.string.settings_scope_sensitivity, settings.scopeSensitivity));
        scopeSensitivity.addOnChangeListener((slider, value, fromUser) -> {
            settings.scopeSensitivity = value;
            settings.save();
            scopeLabel.setText(valueLabel(R.string.settings_scope_sensitivity, value));
        });

        Slider fov = findViewById(R.id.slider_fov);
        TextView fovLabel = findViewById(R.id.label_fov);
        fov.setValue(clampSlider(settings.fieldOfView, fov));
        fovLabel.setText(getString(R.string.settings_fov) + " · "
                + Math.round(settings.fieldOfView));
        fov.addOnChangeListener((slider, value, fromUser) -> {
            settings.fieldOfView = value;
            settings.save();
            fovLabel.setText(getString(R.string.settings_fov) + " · " + Math.round(value));
        });

        buildFrameRateGroup(this, findViewById(R.id.fps_group), settings.frameRateCap, cap -> {
            settings.frameRateCap = cap;
            settings.save();
            click();
        });
        bindSwitch(R.id.switch_adaptive_performance, settings.adaptivePerformance, checked -> {
            settings.adaptivePerformance = checked;
            settings.save();
        });

        bindSwitch(R.id.switch_aim_toggle, settings.aimToggle, checked -> {
            settings.aimToggle = checked;
            settings.save();
        });
        bindSwitch(R.id.switch_auto_fire, settings.autoFire, checked -> {
            settings.autoFire = checked;
            settings.save();
        });
        bindSwitch(R.id.switch_auto_jump, settings.autoJump, checked -> {
            settings.autoJump = checked;
            settings.save();
        });
        bindSwitch(R.id.switch_player_highlights, settings.playerHighlights, checked -> {
            settings.playerHighlights = checked;
            settings.save();
        });
        bindSwitch(R.id.switch_southpaw, settings.southpaw, checked -> {
            settings.southpaw = checked;
            settings.save();
            HudView preview = findViewById(R.id.hud_preview);
            if (preview != null) {
                preview.setSouthpaw(checked);
            }
        });
        bindSwitch(R.id.switch_haptics, settings.haptics, checked -> {
            settings.haptics = checked;
            settings.save();
        });
        bindSwitch(R.id.switch_fps, settings.showFps, checked -> {
            settings.showFps = checked;
            settings.save();
        });

        Slider volume = findViewById(R.id.slider_volume);
        TextView volumeLabel = findViewById(R.id.label_volume);
        volume.setValue(clampSlider(settings.effectsVolume, volume));
        volumeLabel.setText(percentLabel(R.string.settings_sfx, settings.effectsVolume));
        volume.addOnChangeListener((slider, value, fromUser) -> {
            settings.effectsVolume = value;
            settings.save();
            volumeLabel.setText(percentLabel(R.string.settings_sfx, value));
            if (audio != null) {
                audio.setMasterVolume(value * 0.7f);
                audio.playUi(Sfx.UI, 0.5f, 1f);
            }
        });

        findViewById(R.id.button_keys).setOnClickListener(v -> show(SCREEN_KEYS));

        findViewById(R.id.button_hud).setOnClickListener(v -> {
            hudReturnScreen = SCREEN_SETTINGS;
            show(SCREEN_HUD);
        });

        findViewById(R.id.button_export_profile).setOnClickListener(v -> {
            click();
            exportPicker.launch(ProfileBackup.suggestedFileName());
        });
        findViewById(R.id.button_import_profile).setOnClickListener(v -> {
            click();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.settings_backup_confirm_title)
                    .setMessage(R.string.settings_backup_confirm)
                    .setPositiveButton(R.string.settings_backup_confirm_action,
                            (dialog, which) -> importPicker.launch(new String[] {"application/json",
                                    "text/plain", "*/*"}))
                    .setNegativeButton(R.string.menu_back, null)
                    .show();
        });

        findViewById(R.id.button_reset).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.settings_reset)
                        .setMessage("This clears your level, coins and unlocked weapons.")
                        .setPositiveButton(R.string.settings_reset, (dialog, which) -> {
                            profile.reset();
                            refreshProfileCard();
                            refreshArsenal();
                        })
                        .setNegativeButton(R.string.menu_back, null)
                        .show());
    }

    /**
     * Hooks up the system file pickers. Backups go through the document picker rather than a
     * fixed path, so the file lands somewhere the player controls — cloud drive included — and
     * survives the app being uninstalled.
     */
    private void registerBackupPickers() {
        exportPicker = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
                        "application/json"), uri -> {
                    if (uri == null) {
                        return;
                    }
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                        int values = ProfileBackup.export(this, out);
                        toast(getString(R.string.settings_backup_done, values));
                    } catch (Exception error) {
                        toast(getString(R.string.settings_backup_failed));
                    }
                });
        importPicker = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        return;
                    }
                    try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                        int values = ProfileBackup.restore(this, in);
                        // Every screen reads from the stores that were just replaced.
                        profile = new Profile(this);
                        settings = new GameSettings(this);
                        hudLayout = new HudLayout(this);
                        bindings = new KeyBindings(this);
                        recreate();
                        toast(getString(R.string.settings_backup_restored, values));
                    } catch (Exception error) {
                        toast(getString(R.string.settings_backup_failed));
                    }
                });
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }

    private void bindSwitch(int id, boolean initial, CheckedListener listener) {
        MaterialSwitch view = findViewById(id);
        view.setChecked(initial);
        view.setOnCheckedChangeListener((button, checked) -> listener.onChecked(checked));
    }

    private static float clampSlider(float value, Slider slider) {
        return Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), value));
    }

    private String percentLabel(int stringId, float value) {
        String base = stringId == 0 ? "RT resolution" : getString(stringId);
        return base + " · " + Math.round(value * 100) + "%";
    }

    private String valueLabel(int stringId, float value) {
        return getString(stringId) + String.format(Locale.US, " · %.2f", value);
    }

    private boolean neutralPicture() {
        return Math.abs(settings.contrast - 1f) < 0.01f
                && Math.abs(settings.brightness - 1f) < 0.01f;
    }

    private String pictureToggleLabel() {
        return getString(neutralPicture() ? R.string.settings_picture_contrasty
                : R.string.settings_picture_neutral);
    }

    // ------------------------------------------------------------------ key bindings

    private void setupKeysScreen() {
        findViewById(R.id.keys_back).setOnClickListener(v -> show(SCREEN_SETTINGS));
        findViewById(R.id.keys_reset).setOnClickListener(v -> {
            bindings.reset();
            refreshBindings();
            Slider sensitivity = findViewById(R.id.keys_mouse_sensitivity);
            sensitivity.setValue(clampSlider(bindings.mouseSensitivity, sensitivity));
            ((MaterialSwitch) findViewById(R.id.keys_invert_y)).setChecked(bindings.invertY);
            click();
        });

        Slider sensitivity = findViewById(R.id.keys_mouse_sensitivity);
        TextView sensitivityLabel = findViewById(R.id.keys_mouse_label);
        sensitivity.setValue(clampSlider(bindings.mouseSensitivity, sensitivity));
        sensitivityLabel.setText(valueLabel(R.string.keys_mouse_sensitivity,
                bindings.mouseSensitivity));
        sensitivity.addOnChangeListener((slider, value, fromUser) -> {
            bindings.mouseSensitivity = value;
            bindings.save();
            sensitivityLabel.setText(valueLabel(R.string.keys_mouse_sensitivity, value));
        });

        MaterialSwitch invert = findViewById(R.id.keys_invert_y);
        invert.setChecked(bindings.invertY);
        invert.setOnCheckedChangeListener((view, checked) -> {
            bindings.invertY = checked;
            bindings.save();
        });

        refreshBindings();
    }

    private void refreshBindings() {
        LinearLayout container = findViewById(R.id.keys_container);
        container.removeAllViews();
        for (KeyBindings.Action action : KeyBindings.Action.values()) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_binding, container, false);
            ((TextView) row.findViewById(R.id.binding_action)).setText(action.label());
            ((TextView) row.findViewById(R.id.binding_key))
                    .setText(KeyBindings.describe(bindings.keyFor(action)));
            row.setOnClickListener(v -> promptForKey(action));
            container.addView(row);
        }
    }

    /** Modal that swallows the next key press and binds it to the action. */
    private void promptForKey(KeyBindings.Action action) {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.keys_press, action.label()))
                .setNegativeButton(R.string.keys_press_cancel, null)
                .create();
        dialog.setOnKeyListener((target, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                target.dismiss();
                return true;
            }
            bindings.bind(action, keyCode);
            refreshBindings();
            click();
            target.dismiss();
            return true;
        });
        dialog.show();
    }

    // ------------------------------------------------------------------ hud layout

    private void setupHudScreen() {
        HudView preview = findViewById(R.id.hud_preview);
        preview.setLayout(hudLayout);
        preview.setSouthpaw(settings.southpaw);
        preview.setEditMode(true);

        Slider size = findViewById(R.id.hud_size);
        TextView sizeLabel = findViewById(R.id.hud_size_label);
        size.setValue(clampSlider(hudLayout.scale, size));
        sizeLabel.setText(percentLabel(R.string.hud_size, hudLayout.scale));
        size.addOnChangeListener((slider, value, fromUser) -> {
            hudLayout.setScale(value);
            hudLayout.save();
            sizeLabel.setText(percentLabel(R.string.hud_size, value));
            preview.setLayout(hudLayout);
        });

        Slider itemSize = findViewById(R.id.hud_item_size);
        TextView itemLabel = findViewById(R.id.hud_item_label);
        TextView itemPercent = findViewById(R.id.hud_item_percent);
        MaterialButton resetItem = findViewById(R.id.hud_reset_item);
        MaterialButton smaller = findViewById(R.id.hud_item_smaller);
        MaterialButton bigger = findViewById(R.id.hud_item_bigger);

        // Picking a control in the preview points the per-control slider at it.
        preview.setOnItemSelectedListener(index -> {
            boolean selected = index >= 0;
            itemSize.setEnabled(selected);
            resetItem.setEnabled(selected);
            smaller.setEnabled(selected);
            bigger.setEnabled(selected);
            if (selected) {
                itemLabel.setText(preview.labelOf(index));
                itemSize.setValue(clampSlider(hudLayout.itemScale(index), itemSize));
                itemPercent.setText(getString(R.string.hud_percent,
                        Math.round(hudLayout.itemScale(index) * 100)));
            } else {
                itemLabel.setText(R.string.hud_item_none);
                itemPercent.setText("");
            }
        });
        // The steppers are the precise way in: a slider this short moves in visible jumps.
        smaller.setOnClickListener(v ->
                nudgeItemScale(preview, itemSize, itemPercent, -itemSize.getStepSize()));
        bigger.setOnClickListener(v ->
                nudgeItemScale(preview, itemSize, itemPercent, itemSize.getStepSize()));
        itemSize.addOnChangeListener((slider, value, fromUser) -> {
            int index = preview.selectedItem();
            if (!fromUser || index < 0) {
                return;
            }
            hudLayout.setItemScale(index, value);
            hudLayout.save();
            itemPercent.setText(getString(R.string.hud_percent, Math.round(value * 100)));
            preview.setLayout(hudLayout);
        });
        resetItem.setOnClickListener(v -> {
            int index = preview.selectedItem();
            if (index < 0) {
                return;
            }
            hudLayout.resetItem(index);
            itemSize.setValue(clampSlider(hudLayout.itemScale(index), itemSize));
            itemPercent.setText(getString(R.string.hud_percent,
                    Math.round(hudLayout.itemScale(index) * 100)));
            preview.setLayout(hudLayout);
            click();
        });

        Slider opacity = findViewById(R.id.hud_opacity);
        TextView opacityLabel = findViewById(R.id.hud_opacity_label);
        opacity.setValue(clampSlider(hudLayout.opacity, opacity));
        opacityLabel.setText(percentLabel(R.string.hud_opacity, hudLayout.opacity));
        opacity.addOnChangeListener((slider, value, fromUser) -> {
            hudLayout.opacity = value;
            hudLayout.save();
            opacityLabel.setText(percentLabel(R.string.hud_opacity, value));
            preview.setLayout(hudLayout);
        });

        MaterialSwitch staticJoystick = findViewById(R.id.hud_static_joystick);
        staticJoystick.setChecked(hudLayout.staticJoystick);
        staticJoystick.setOnCheckedChangeListener((button, checked) -> {
            hudLayout.staticJoystick = checked;
            hudLayout.save();
            preview.setLayout(hudLayout);
            click();
        });

        buildHudPresets(preview, size, opacity, itemSize, itemPercent);

        View sheet = findViewById(R.id.hud_sheet);
        findViewById(R.id.hud_more).setOnClickListener(v -> {
            sheet.setVisibility(sheet.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            click();
        });

        findViewById(R.id.hud_back).setOnClickListener(v -> show(hudReturnScreen));
        findViewById(R.id.hud_reset).setOnClickListener(v -> {
            hudLayout.reset();
            size.setValue(clampSlider(hudLayout.scale, size));
            opacity.setValue(clampSlider(hudLayout.opacity, opacity));
            staticJoystick.setChecked(hudLayout.staticJoystick);
            itemSize.setValue(1f);
            itemPercent.setText(preview.selectedItem() < 0 ? ""
                    : getString(R.string.hud_percent, 100));
            preview.setLayout(hudLayout);
            click();
        });
    }

    /**
     * Preset chips and the three personal slots.
     *
     * <p>A layout takes real work to arrange and, as this project found out the hard way, one
     * wipe to lose. Presets give a sane starting point; the slots let a player park an
     * arrangement before experimenting on it.
     */
    private void buildHudPresets(HudView preview, Slider size, Slider opacity, Slider itemSize,
                                 TextView itemPercent) {
        ChipGroup presets = findViewById(R.id.hud_presets);
        presets.removeAllViews();
        for (HudLayout.Preset preset : HudLayout.Preset.values()) {
            Chip chip = new Chip(this);
            chip.setText(preset.label());
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                hudLayout.apply(preset);
                syncHudControls(preview, size, opacity, itemSize, itemPercent);
                click();
            });
            presets.addView(chip);
        }

        LinearLayout slots = findViewById(R.id.hud_slots);
        slots.removeAllViews();
        for (int index = 0; index < HudLayout.SLOT_COUNT; index++) {
            int slot = index;
            MaterialButton button = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(getString(R.string.hud_slot, slot + 1));
            button.setTextSize(11f);
            button.setAlpha(hudLayout.hasSlot(slot) ? 1f : 0.55f);
            // Tap restores, long press stores: one control, and the destructive half is the one
            // that takes a deliberate hold.
            button.setOnClickListener(v -> {
                if (hudLayout.loadSlot(slot)) {
                    syncHudControls(preview, size, opacity, itemSize, itemPercent);
                    toast(getString(R.string.hud_slot_loaded, slot + 1));
                } else {
                    toast(getString(R.string.hud_slot_empty));
                }
                click();
            });
            button.setOnLongClickListener(v -> {
                hudLayout.saveSlot(slot);
                v.setAlpha(1f);
                toast(getString(R.string.hud_slot_saved, slot + 1));
                click();
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(slot == HudLayout.SLOT_COUNT - 1 ? 0 : dp(6));
            slots.addView(button, params);
        }
    }

    /** Pushes a freshly loaded layout back into the preview and every slider that shows it. */
    private void syncHudControls(HudView preview, Slider size, Slider opacity, Slider itemSize,
                                 TextView itemPercent) {
        size.setValue(clampSlider(hudLayout.scale, size));
        opacity.setValue(clampSlider(hudLayout.opacity, opacity));
        MaterialSwitch staticJoystick = findViewById(R.id.hud_static_joystick);
        if (staticJoystick.isChecked() != hudLayout.staticJoystick) {
            staticJoystick.setChecked(hudLayout.staticJoystick);
        }
        int selected = preview.selectedItem();
        if (selected >= 0) {
            itemSize.setValue(clampSlider(hudLayout.itemScale(selected), itemSize));
            itemPercent.setText(getString(R.string.hud_percent,
                    Math.round(hudLayout.itemScale(selected) * 100)));
        }
        preview.setLayout(hudLayout);
    }

    /** One stepper press: moves the selected control's size by a single slider step. */
    private void nudgeItemScale(HudView preview, Slider itemSize, TextView percent, float delta) {
        int index = preview.selectedItem();
        if (index < 0) {
            return;
        }
        // Snapped to the slider's own step so the thumb never lands between notches.
        float step = itemSize.getStepSize();
        float target = Math.round((hudLayout.itemScale(index) + delta) / step) * step;
        itemSize.setValue(clampSlider(target, itemSize));
        hudLayout.setItemScale(index, itemSize.getValue());
        hudLayout.save();
        percent.setText(getString(R.string.hud_percent, Math.round(itemSize.getValue() * 100)));
        preview.setLayout(hudLayout);
        click();
    }

    // ------------------------------------------------------------------ stats

    private void setupStatsScreen() {
        findViewById(R.id.stats_back).setOnClickListener(v -> show(SCREEN_MAIN));
    }

    /**
     * The career page: a level header, a grid of tiles that lead with the number, and the
     * difficulty badges. Rates are derived here so the tiles carry their own context.
     */
    private void refreshStats() {
        int level = profile.level();
        ((TextView) findViewById(R.id.stats_name)).setText(profile.name());
        ((TextView) findViewById(R.id.stats_level)).setText(getString(R.string.stats_level_line,
                level, profile.experienceIntoLevel(), Profile.experienceForLevel(level)));
        ((TextView) findViewById(R.id.stats_coins)).setText(profile.coins() + " \u00A2");
        LinearProgressIndicator progress = findViewById(R.id.stats_progress);
        progress.setProgress(Math.round(profile.levelProgress() * 100));

        int matches = profile.matches();
        int kills = profile.kills();
        GridLayout grid = findViewById(R.id.stats_grid);
        grid.removeAllViews();
        addStat(grid, getString(R.string.stat_matches), String.valueOf(matches),
                matches == 0 ? getString(R.string.stats_no_matches) : null);
        addStat(grid, getString(R.string.stat_wins), String.valueOf(profile.wins()),
                matches > 0 ? getString(R.string.stats_wins_of, matches) : null);
        addStat(grid, getString(R.string.stat_win_rate), percent(profile.wins(), matches), null);
        addStat(grid, getString(R.string.stat_kills), String.valueOf(kills), null);
        addStat(grid, getString(R.string.stat_deaths), String.valueOf(profile.deaths()), null);
        addStat(grid, getString(R.string.stat_kd),
                String.format(Locale.US, "%.2f", profile.killDeathRatio()), null);
        addStat(grid, getString(R.string.stat_headshots), String.valueOf(profile.headshots()), null);
        addStat(grid, getString(R.string.stat_headshot_rate), percent(profile.headshots(), kills), null);
        long minutes = profile.playtimeSeconds() / 60;
        addStat(grid, getString(R.string.stat_playtime), minutes / 60 + "h " + minutes % 60 + "m", null);
        addStat(grid, getString(R.string.stat_experience), String.valueOf(profile.experience()), null);
        addStat(grid, getString(R.string.stat_coins), profile.coins() + " \u00A2", null);

        refreshWeaponStats();

        ChipGroup badges = findViewById(R.id.stats_badges);
        badges.removeAllViews();
        for (Difficulty difficulty : Difficulty.values()) {
            boolean earned = profile.hasBadge(difficulty);
            Chip chip = new Chip(this);
            chip.setText(earned ? difficulty.label()
                    : getString(R.string.stats_badge_locked, difficulty.label()));
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setChipIconVisible(true);
            chip.setChipIconResource(earned ? android.R.drawable.star_on : android.R.drawable.star_off);
            chip.setAlpha(earned ? 1f : 0.55f);
            badges.addView(chip);
        }
    }

    /**
     * Weapons the player has actually used, best first. Guns that were never fired are left out:
     * a table of twenty zeroes says nothing, and the ranking is the point.
     */
    private void refreshWeaponStats() {
        LinearLayout container = findViewById(R.id.stats_weapons);
        container.removeAllViews();

        java.util.List<int[]> rows = new java.util.ArrayList<>();
        WeaponType[] types = WeaponType.values();
        for (int i = 0; i < types.length; i++) {
            int[] stats = profile.weaponStats(types[i]);
            if (stats[0] == 0 && stats[2] == 0) {
                continue;
            }
            rows.add(new int[] {i, stats[0], stats[1], stats[2], stats[3]});
        }
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.stats_weapons_empty);
            empty.setPadding(dp(16), dp(10), dp(16), dp(10));
            empty.setTextColor(getColor(android.R.color.darker_gray));
            container.addView(empty);
            return;
        }
        rows.sort((a, b) -> b[3] - a[3]);

        container.addView(weaponRow(container, getString(R.string.stats_weapon_header),
                getString(R.string.stats_weapon_kills), getString(R.string.stats_weapon_headshots),
                getString(R.string.stats_weapon_accuracy), true));
        for (int[] row : rows) {
            container.addView(weaponRow(container, types[row[0]].displayName(),
                    String.valueOf(row[3]), String.valueOf(row[4]),
                    percent(row[2], row[1]), false));
        }
    }

    private View weaponRow(ViewGroup parent, String name, String kills, String headshots,
                           String accuracy, boolean header) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_weapon_stat, parent, false);
        TextView nameView = row.findViewById(R.id.weapon_name);
        TextView killsView = row.findViewById(R.id.weapon_kills);
        TextView headshotView = row.findViewById(R.id.weapon_headshots);
        TextView accuracyView = row.findViewById(R.id.weapon_accuracy);
        nameView.setText(name);
        killsView.setText(kills);
        headshotView.setText(headshots);
        accuracyView.setText(accuracy);
        if (header) {
            // The header reuses the row so the columns line up by construction, not by guesswork.
            for (TextView view : new TextView[] {nameView, killsView, headshotView, accuracyView}) {
                view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
                view.setAllCaps(true);
            }
        }
        return row;
    }

    /** Rates read better than raw counts, and an undefined rate stays a dash rather than 0%. */
    private String percent(int part, int total) {
        return total <= 0 ? "\u2013" : Math.round(part * 100f / total) + "%";
    }

    /** One tile in the stats grid; the grid stretches the columns evenly across the page. */
    private void addStat(GridLayout parent, String label, String value, String hint) {
        View tile = LayoutInflater.from(this).inflate(R.layout.item_stat, parent, false);
        ((TextView) tile.findViewById(R.id.stat_label)).setText(label);
        ((TextView) tile.findViewById(R.id.stat_value)).setText(value);
        TextView hintView = tile.findViewById(R.id.stat_hint);
        if (hint == null) {
            hintView.setVisibility(View.GONE);
        } else {
            hintView.setVisibility(View.VISIBLE);
            hintView.setText(hint);
        }
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        parent.addView(tile, params);
    }

    /** The frame rate options, in the order they appear; 0 means the display decides. */
    static final int[] FRAME_RATES = {30, 60, 90, 120, 0};

    static String frameRateLabel(android.content.Context context, int cap) {
        return cap > 0 ? cap + " FPS" : context.getString(R.string.frame_rate_display);
    }

    /**
     * Fills a toggle group with the frame rate options and reports the chosen cap.
     */
    static void buildFrameRateGroup(android.content.Context context,
                                    MaterialButtonToggleGroup group, int current,
                                    java.util.function.IntConsumer onPicked) {
        group.clearOnButtonCheckedListeners();
        group.removeAllViews();
        for (int i = 0; i < FRAME_RATES.length; i++) {
            MaterialButton button = new MaterialButton(context, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(View.generateViewId());
            button.setText(frameRateLabel(context, FRAME_RATES[i]));
            button.setTag(i);
            button.setTextSize(10f);
            styleGraphicsButton(context, button);
            group.addView(button, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (FRAME_RATES[i] == current) {
                group.check(button.getId());
            }
        }
        group.addOnButtonCheckedListener((target, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            View button = target.findViewById(checkedId);
            if (button != null && button.getTag() instanceof Integer) {
                onPicked.accept(FRAME_RATES[(Integer) button.getTag()]);
            }
        });
    }

    /** Same checked-state appearance in menu and pause graphics controls. */
    static void styleGraphicsButton(android.content.Context context, MaterialButton button) {
        button.setCheckable(true);
        button.setBackgroundTintList(androidx.appcompat.content.res.AppCompatResources
                .getColorStateList(context, R.color.game_buy_tab_background));
        button.setTextColor(androidx.appcompat.content.res.AppCompatResources
                .getColorStateList(context, R.color.game_buy_tab_text));
    }

    /** Small functional interface so switch wiring stays one line each. */
    private interface CheckedListener {
        void onChecked(boolean checked);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        // MatchActivity owns a separate settings instance. Reload before any menu callback
        // can save, and rebuild controls without retaining stale listeners or checked values.
        settings = new GameSettings(this);
        int shown = flipper.getDisplayedChild();
        View settingsPage = getLayoutInflater().inflate(R.layout.screen_settings, flipper, false);
        flipper.removeViewAt(SCREEN_SETTINGS);
        flipper.addView(settingsPage, SCREEN_SETTINGS);
        flipper.setDisplayedChild(shown);
        setupSettingsScreen();
        profile = new Profile(this);
        refreshProfileCard();
        if (audio != null) {
            audio.start();
            audio.setMasterVolume(settings.effectsVolume * 0.7f);
        }
    }

    @Override
    protected void onPause() {
        if (audio != null) {
            audio.release();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        previewRequest++;
        previewWorker.shutdownNow();
        super.onDestroy();
    }
}
