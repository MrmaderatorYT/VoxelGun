import com.ccs.voxelgun.game.*;
import com.ccs.voxelgun.input.InputState;
import com.ccs.voxelgun.map.Maps;
import com.ccs.voxelgun.weapon.WeaponType;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Runs the real Match, AI, collision, weapon and mode loops on the desktop JVM.
 * This deliberately uses Java voxel queries, not Android rendering/audio or native frame timings.
 * No mocking, immortal actors, fixed positions or artificial combat commands are used.
 */
public final class BotScenarioRunner {
    private static final float WARNING_STALL_FRACTION = .10f, FAILURE_STALL_FRACTION = .30f;
    private static final float WARNING_IDLE_FRACTION = .25f, FAILURE_IDLE_FRACTION = .65f;
    private static final float WARNING_MAX_STALL = 6f, FAILURE_MAX_STALL = 15f;
    private static final float WARNING_SPIN_FRACTION = .02f, FAILURE_SPIN_FRACTION = .10f;
    private static final double WARNING_P95_MILLIS = 20.0;
    private static final double WARNING_REPEATS_PER_BOT_MINUTE = 12.0;
    private static final int MOVEMENT_MODIFIERS = MatchModifier.DOUBLE_SPEED.bit
            | MatchModifier.BUNNY_HOP.bit | MatchModifier.DOUBLE_JUMP.bit
            | MatchModifier.LOW_GRAVITY.bit | MatchModifier.NO_FALL_DAMAGE.bit;
    private static final int SENSE_MODIFIERS = MatchModifier.FOG.bit | MatchModifier.NIGHT.bit;
    private static final int CHAOS_MODIFIERS = MatchModifier.RANDOM_WEAPONS.bit
            | MatchModifier.EXPLOSIVE_ROUNDS.bit | MatchModifier.EXPLOSIVE_DEATH.bit;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        Options options = Options.parse(args);
        Files.createDirectories(options.output);
        List<Scenario> scenarios = scenarios(options);
        List<Result> results = new ArrayList<>();
        int failures = 0, warnings = 0;
        long started = System.nanoTime();
        for (Scenario scenario : scenarios) {
            Result result = run(scenario, options);
            results.add(result);
            if (result.status.equals("FAIL")) failures++;
            else if (result.status.equals("WARN")) warnings++;
            System.out.printf("%2d/%d %-20s %-18s %-7s %s move=%5.1f%% hold=%5.1f%% stall=%5.1f%% idle=%5.1f%% max=%.1fs%s%n",
                    results.size(), scenarios.size(), scenario.mapId, scenario.mode,
                    scenario.difficulty, result.status, 100 * result.movingFraction(),
                    100 * result.holdFraction(), 100 * result.stallFraction(),
                    100 * result.idleFraction(), result.metrics.maxStallSeconds,
                    result.issues.isEmpty() ? "" : " " + String.join("; ", result.issues));
            // Checkpoint after every scenario, preserving usable evidence if a later case fails.
            writeReports(options.output, results, scenarios.size(), options.seconds);
        }
        System.out.printf("%d scenarios, %d failures, %d warnings, %.1fs wall time. Reports: %s%n",
                results.size(), failures, warnings, (System.nanoTime() - started) / 1e9,
                options.output.toAbsolutePath());
        if (failures > 0 || (options.strict && warnings > 0)) System.exit(1);
    }

    private static List<Scenario> scenarios(Options options) {
        List<Scenario> scenarios = new ArrayList<>();
        GameMode[] arenaModes = {GameMode.TEAM_DEATHMATCH, GameMode.DEATHMATCH,
                GameMode.CAPTURE_POINT, GameMode.KING_OF_THE_HILL, GameMode.BOMB_PLANT,
                GameMode.CRYSTAL_HEIST, GameMode.GUN_GAME, GameMode.KNIFE_FIGHT};
        for (int map = 0; map < Maps.count(); map++) {
            if (!options.includes(map)) continue;
            if (!options.longRun) {
                // Every map gets its recommended objective rules, with all four skills represented.
                scenarios.add(new Scenario(map, Maps.recommended(map),
                        Difficulty.values()[map % Difficulty.values().length], 0,
                        options.seed + map * 101L, Maps.isBattlefield(map) && map % 2 == 0));
                continue;
            }
            GameMode[] modes = Maps.isBattlefield(map)
                    ? new GameMode[] {GameMode.BATTLE_FIELD} : arenaModes;
            for (int pass = 0; pass < 2; pass++) for (int mode = 0; mode < modes.length; mode++) {
                int modifiers = switch ((map + mode + pass) % 4) {
                    case 1 -> MOVEMENT_MODIFIERS;
                    case 2 -> SENSE_MODIFIERS;
                    case 3 -> MatchModifier.HEADSHOTS_ONLY.bit | MatchModifier.MIRROR_LOADOUT.bit;
                    default -> 0;
                };
                scenarios.add(new Scenario(map, modes[mode], Difficulty.values()[(map + mode + pass) % 4],
                        modifiers, options.seed + map * 101L + pass * 10_007L + mode,
                        Maps.isBattlefield(map) && pass == 1));
            }
        }
        // Explicit controls exercise passive targets plus loadout/movement/sensory restrictions.
        add(scenarios, options, 0, GameMode.DEATHMATCH, Difficulty.RECRUIT, 0);
        add(scenarios, options, 1, GameMode.GUN_GAME, Difficulty.REGULAR, MOVEMENT_MODIFIERS);
        add(scenarios, options, 2, GameMode.KNIFE_FIGHT, Difficulty.VETERAN, MOVEMENT_MODIFIERS);
        add(scenarios, options, 3, GameMode.ZOMBIE, Difficulty.REGULAR, SENSE_MODIFIERS);
        add(scenarios, options, 4, GameMode.TEAM_DEATHMATCH, Difficulty.ELITE, CHAOS_MODIFIERS);
        add(scenarios, options, 5, GameMode.TEAM_DEATHMATCH, Difficulty.VETERAN,
                MatchModifier.HEADSHOTS_ONLY.bit | MatchModifier.MIRROR_LOADOUT.bit);
        add(scenarios, options, 6, GameMode.CRYSTAL_HEIST, Difficulty.ELITE, MOVEMENT_MODIFIERS);
        add(scenarios, options, 7, GameMode.RANGE, Difficulty.VETERAN, 0);
        add(scenarios, options, 8, GameMode.DEATHMATCH, Difficulty.REGULAR,
                MatchModifier.ONE_SHOT_RAIL.bit | SENSE_MODIFIERS);
        add(scenarios, options, 9, GameMode.CAPTURE_POINT, Difficulty.VETERAN,
                MatchModifier.NO_RELOAD.bit | MatchModifier.INFINITE_AMMO.bit);
        return scenarios;
    }

    private static void add(List<Scenario> scenarios, Options options, int map, GameMode mode,
                            Difficulty difficulty, int modifiers) {
        if (map < Maps.count() && options.includes(map)) scenarios.add(new Scenario(map, mode,
                difficulty, modifiers, options.seed + 90_000L + map * 101L, false));
    }

    private static Result run(Scenario scenario, Options options) {
        MatchConfig config = new MatchConfig();
        config.mapIndex = scenario.map;
        config.mode = scenario.mode;
        config.difficulty = scenario.difficulty;
        config.seed = scenario.seed;
        config.modifiers = scenario.modifiers;
        config.botCount = 7;
        config.totalPlayers = options.players;
        config.conquest = scenario.conquest;
        // Mirror-headshot regression includes a launcher in the kit; sidearm remains viable.
        if ((config.modifiers & MatchModifier.MIRROR_LOADOUT.bit) != 0) config.primary = WeaponType.RPG_V;
        Match match = new Match(config);
        Result result = new Result(scenario);
        try {
            match.enableBotDiagnostics();
            InputState input = new InputState();
            int ticks = Math.round((options.seconds + 3.5f) / Match.STEP);
            long[] updateNanos = new long[ticks];
            for (int tick = 0; tick < ticks; tick++) {
                long start = System.nanoTime();
                match.update(Match.STEP, input);
                updateNanos[result.ticks++] = System.nanoTime() - start;
                for (Combatant combatant : match.combatants()) {
                    if (!Float.isFinite(combatant.position.x)
                            || !Float.isFinite(combatant.position.y)
                            || !Float.isFinite(combatant.position.z)) result.invalidPositions++;
                }
                if (match.state() == Match.State.FINISHED) break;
            }
            result.metrics = match.botDiagnostics().snapshot();
            result.finished = match.state() == Match.State.FINISHED;
            for (Combatant combatant : match.combatants()) result.kills += combatant.kills;
            HudSnapshot hud = match.snapshot();
            result.blueScore = hud.blueScore; result.redScore = hud.redScore;
            result.simulatedSeconds = result.ticks * Match.STEP;
            long total = 0;
            for (int tick = 0; tick < result.ticks; tick++) total += updateNanos[tick];
            Arrays.sort(updateNanos, 0, result.ticks);
            result.meanUpdateMillis = total / (double) result.ticks / 1e6;
            result.p95UpdateMillis = updateNanos[Math.max(0, (int) Math.ceil(result.ticks * .95) - 1)] / 1e6;
            result.maxUpdateMillis = updateNanos[result.ticks - 1] / 1e6;
            result.evaluate();
            return result;
        } finally { match.release(); }
    }

    private static void writeReports(Path directory, List<Result> results, int planned,
                                      float requestedSeconds) throws IOException {
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(directory.resolve("scenarios.csv"),
                StandardCharsets.UTF_8))) {
            csv.println("map,mode,difficulty,seed,modifiers,conquest,status,simulated_seconds,bot_seconds,moving_seconds,hold_seconds,idle_seconds,movement_intent_seconds,stalled_seconds,spinning_seconds,max_stall_seconds,distance,turn_radians,path_failures,repeated_goal_failures,path_successes,path_deferrals,recoveries,abandoned_goals,kills,blue_score,red_score,mean_update_ms,p95_update_ms,max_update_ms,issues");
            for (Result r : results) {
                BotDiagnostics.Snapshot m = r.metrics;
                csv.printf(Locale.ROOT,"%s,%s,%s,%d,%d,%s,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,\"%s\"%n",
                        r.scenario.mapId, r.scenario.mode, r.scenario.difficulty, r.scenario.seed,
                        r.scenario.modifiers, r.scenario.conquest, r.status, r.simulatedSeconds,
                        m.activeSeconds, m.movingSeconds, m.holdSeconds, m.idleSeconds,
                        m.movementIntentSeconds, m.stalledSeconds, m.spinningSeconds,
                        m.maxStallSeconds, m.distance, m.turnRadians, m.pathFailures,
                        m.repeatedGoalFailures, m.pathSuccesses, m.pathDeferrals, m.recoveries,
                        m.abandonedGoals, r.kills, r.blueScore, r.redScore, r.meanUpdateMillis,
                        r.p95UpdateMillis, r.maxUpdateMillis, String.join("; ", r.issues));
            }
        }
        try (PrintWriter json = new PrintWriter(Files.newBufferedWriter(directory.resolve("scenarios.json"),
                StandardCharsets.UTF_8))) {
            json.printf("{\"plannedScenarios\":%d,\"completedScenarios\":%d,\"requestedSeconds\":%.3f,%n",
                    planned, results.size(), requestedSeconds);
            json.println("\"timingScope\":\"Desktop JVM full Match.update, Java voxel fallback; excludes map build; not Android/GPU performance\",");
            json.printf("\"thresholds\":{\"stallGraceSeconds\":%.3f,\"progressDistance\":%.3f,\"warningStallFraction\":%.3f,\"failureStallFraction\":%.3f,\"warningIdleFraction\":%.3f,\"failureIdleFraction\":%.3f,\"warningMaxStallSeconds\":%.3f,\"failureMaxStallSeconds\":%.3f,\"warningSpinFraction\":%.3f,\"failureSpinFraction\":%.3f,\"warningP95Millis\":%.3f,\"warningRepeatedGoalsPerBotMinute\":%.3f},%n",
                    BotDiagnostics.STALL_GRACE_SECONDS, BotDiagnostics.PROGRESS_DISTANCE,
                    WARNING_STALL_FRACTION, FAILURE_STALL_FRACTION, WARNING_IDLE_FRACTION,
                    FAILURE_IDLE_FRACTION, WARNING_MAX_STALL, FAILURE_MAX_STALL,
                    WARNING_SPIN_FRACTION, FAILURE_SPIN_FRACTION, WARNING_P95_MILLIS,
                    WARNING_REPEATS_PER_BOT_MINUTE);
            json.println("\"scenarios\":[");
            for (int index = 0; index < results.size(); index++) {
                Result r = results.get(index);
                BotDiagnostics.Snapshot m = r.metrics;
                if (index > 0) json.println(",");
                json.printf("{\"map\":%s,\"mapIndex\":%d,\"mode\":%s,\"difficulty\":%s,\"seed\":%d,\"modifiers\":%d,\"conquest\":%s,\"status\":%s,\"finished\":%s,\"simulatedSeconds\":%.3f,\"kills\":%d,\"blueScore\":%d,\"redScore\":%d,\"meanUpdateMillis\":%.3f,\"p95UpdateMillis\":%.3f,\"maxUpdateMillis\":%.3f,\"issues\":[",
                        quote(r.scenario.mapId), r.scenario.map, quote(r.scenario.mode.name()),
                        quote(r.scenario.difficulty.name()), r.scenario.seed, r.scenario.modifiers,
                        r.scenario.conquest, quote(r.status), r.finished, r.simulatedSeconds,
                        r.kills, r.blueScore, r.redScore, r.meanUpdateMillis,
                        r.p95UpdateMillis, r.maxUpdateMillis);
                for (int i = 0; i < r.issues.size(); i++) {
                    if (i > 0) json.print(',');
                    json.print(quote(r.issues.get(i)));
                }
                json.printf("],\"totalPlayers\":%d,\"activeSeconds\":%.3f,\"movingSeconds\":%.3f,\"holdSeconds\":%.3f,\"idleSeconds\":%.3f,\"movementIntentSeconds\":%.3f,\"stalledSeconds\":%.3f,\"spinningSeconds\":%.3f,\"maxStallSeconds\":%.3f,\"distance\":%.3f,\"turnRadians\":%.3f,\"pathFailures\":%d,\"repeatedGoalFailures\":%d,\"pathSuccesses\":%d,\"pathDeferrals\":%d,\"recoveries\":%d,\"abandonedGoals\":%d,\"teleports\":%d,\"invalidSamples\":%d,\"bots\":[",
                        m.bots.length + 1, m.activeSeconds, m.movingSeconds, m.holdSeconds, m.idleSeconds,
                        m.movementIntentSeconds, m.stalledSeconds, m.spinningSeconds, m.maxStallSeconds,
                        m.distance, m.turnRadians, m.pathFailures, m.repeatedGoalFailures,
                        m.pathSuccesses, m.pathDeferrals, m.recoveries, m.abandonedGoals,
                        m.teleports, m.invalidSamples);
                for (int i = 0; i < m.bots.length; i++) {
                    BotDiagnostics.BotSnapshot b = m.bots[i];
                    if (i > 0) json.print(',');
                    json.printf("{\"id\":%d,\"activeSeconds\":%.3f,\"movingSeconds\":%.3f,\"holdSeconds\":%.3f,\"combatHold\":%.3f,\"objectiveHold\":%.3f,\"coverHold\":%.3f,\"rangeHold\":%.3f,\"investigationHold\":%.3f,\"idleSeconds\":%.3f,\"stalledSeconds\":%.3f,\"maxStallSeconds\":%.3f,\"spinningSeconds\":%.3f,\"distance\":%.3f,\"turnRadians\":%.3f,\"pathFailures\":%d,\"repeatedGoalFailures\":%d,\"recoveries\":%d,\"abandonedGoals\":%d}",
                            b.id, b.activeSeconds, b.movingSeconds, b.holdSeconds,
                            b.holdSecondsByReason[BotDiagnostics.COMBAT],
                            b.holdSecondsByReason[BotDiagnostics.OBJECTIVE],
                            b.holdSecondsByReason[BotDiagnostics.COVER],
                            b.holdSecondsByReason[BotDiagnostics.RANGE],
                            b.holdSecondsByReason[BotDiagnostics.INVESTIGATION],
                            b.idleSeconds, b.stalledSeconds, b.maxStallSeconds, b.spinningSeconds,
                            b.distance, b.turnRadians, b.pathFailures, b.repeatedGoalFailures,
                            b.recoveries, b.abandonedGoals);
                }
                json.print("]}");
            }
            json.println("\n]}");
        }
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record Scenario(int map, GameMode mode, Difficulty difficulty, int modifiers,
                            long seed, boolean conquest, String mapId) {
        Scenario(int map, GameMode mode, Difficulty difficulty, int modifiers, long seed,
                 boolean conquest) {
            this(map, mode, difficulty, MatchModifier.sanitize(modifiers, mode), seed, conquest,
                    Maps.create(map).id());
        }
    }

    private static final class Result {
        final Scenario scenario;
        final List<String> issues = new ArrayList<>();
        BotDiagnostics.Snapshot metrics;
        int ticks, kills, blueScore, redScore, invalidPositions;
        float simulatedSeconds;
        double meanUpdateMillis, p95UpdateMillis, maxUpdateMillis;
        boolean finished;
        String status = "PASS";
        Result(Scenario scenario) { this.scenario = scenario; }
        double fraction(double seconds) { return seconds / Math.max(.001, metrics.activeSeconds); }
        double movingFraction() { return fraction(metrics.movingSeconds); }
        double holdFraction() { return fraction(metrics.holdSeconds); }
        double stallFraction() { return fraction(metrics.stalledSeconds); }
        double idleFraction() { return fraction(metrics.idleSeconds); }
        void issue(boolean failure, String detail) {
            if (failure) status = "FAIL";
            else if (!status.equals("FAIL")) status = "WARN";
            issues.add(detail);
        }
        void threshold(double value, double warning, double failure, String label) {
            if (value > warning) issue(value > failure, String.format("%s %.3f", label, value));
        }
        void evaluate() {
            if (invalidPositions > 0 || metrics.invalidSamples > 0) issue(true, "non-finite position");
            if (metrics.activeSeconds < 1.0) issue(true, "no active bot observations");
            threshold(stallFraction(), WARNING_STALL_FRACTION, FAILURE_STALL_FRACTION, "stalled fraction");
            threshold(idleFraction(), WARNING_IDLE_FRACTION, FAILURE_IDLE_FRACTION, "unexplained idle fraction");
            threshold(metrics.maxStallSeconds, WARNING_MAX_STALL, FAILURE_MAX_STALL, "longest stall seconds");
            threshold(fraction(metrics.spinningSeconds), WARNING_SPIN_FRACTION, FAILURE_SPIN_FRACTION, "spinning fraction");
            // Team averages must not hide one completely stranded soldier among 99 moving bots.
            // Apply the same stated limits to each bot after enough active time to be meaningful.
            for (BotDiagnostics.BotSnapshot bot : metrics.bots) if (bot.activeSeconds >= 10.0) {
                threshold(bot.stalledSeconds / bot.activeSeconds, WARNING_STALL_FRACTION,
                        FAILURE_STALL_FRACTION, "bot " + bot.id + " stalled fraction");
                threshold(bot.idleSeconds / bot.activeSeconds, WARNING_IDLE_FRACTION,
                        FAILURE_IDLE_FRACTION, "bot " + bot.id + " unexplained idle fraction");
                threshold(bot.spinningSeconds / bot.activeSeconds, WARNING_SPIN_FRACTION,
                        FAILURE_SPIN_FRACTION, "bot " + bot.id + " spinning fraction");
            }
            if (metrics.activeSeconds >= 20.0) {
                double repeats = metrics.repeatedGoalFailures * 60.0 / metrics.activeSeconds;
                if (repeats > WARNING_REPEATS_PER_BOT_MINUTE) issue(false,
                        String.format("repeated failed goals %.1f per bot-minute", repeats));
            }
            // Host CPU timing is evidence only; a different laptop must not break correctness CI.
            if (p95UpdateMillis > WARNING_P95_MILLIS) issue(false,
                    String.format("desktop update p95 %.1fms", p95UpdateMillis));
            if (scenario.mode.passiveBots() && (metrics.stalledSeconds > .1 || metrics.spinningSeconds > .1))
                issue(true, "passive range targets classified as stalled/spinning");
            if (!scenario.mode.passiveBots() && metrics.activeSeconds >= 40 && movingFraction() < .05)
                issue(true, "less than 5% movement in active combat");
        }
    }

    private static final class Options {
        Path output = Path.of("output", "bot-scenarios");
        float seconds = 35f;
        long seed = 20261014L;
        boolean longRun, strict;
        int players = 16;
        int[] maps;
        boolean includes(int map) {
            if (maps == null) return true;
            for (int allowed : maps) if (allowed == map) return true;
            return false;
        }
        static Options parse(String[] args) {
            Options result = new Options();
            boolean customSeconds = false;
            for (String arg : args) {
                if (arg.equals("--long")) result.longRun = true;
                else if (arg.equals("--strict")) result.strict = true;
                else if (arg.startsWith("--output=")) result.output = Path.of(arg.substring(9));
                else if (arg.startsWith("--seconds=")) {
                    result.seconds = Float.parseFloat(arg.substring(10)); customSeconds = true;
                } else if (arg.startsWith("--seed=")) result.seed = Long.parseLong(arg.substring(7));
                else if (arg.startsWith("--players=")) result.players = Integer.parseInt(arg.substring(10));
                else if (arg.startsWith("--maps=")) result.maps = Arrays.stream(arg.substring(7).split(","))
                        .mapToInt(Integer::parseInt).toArray();
                else throw new IllegalArgumentException("Unknown argument " + arg
                        + "; use --long --strict --seconds=N --seed=N --players=N --maps=0,1 --output=DIR");
            }
            if (result.longRun && !customSeconds) result.seconds = 90f;
            if (!Float.isFinite(result.seconds) || result.seconds < 5 || result.seconds > 3600)
                throw new IllegalArgumentException("--seconds must be between 5 and 3600");
            if (result.players < 2 || result.players > 100 || (result.players & 1) != 0)
                throw new IllegalArgumentException("--players must be an even Battle Field player count from 2 to 100");
            if (result.maps != null) for (int map : result.maps)
                if (map < 0 || map >= Maps.count()) throw new IllegalArgumentException("Invalid map " + map);
            return result;
        }
    }
}
