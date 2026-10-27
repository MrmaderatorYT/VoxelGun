package com.ccs.voxelstrike.sim;

import java.io.IOException;
import java.util.List;

/**
 * Writes a run out as CSV: one row per tick, one file per run.
 *
 * <p>This is the whole point of the fixed tick — a run is a table, and a batch of runs is a table
 * with a seed column. Nothing here formats for humans; it formats for a spreadsheet.
 */
public final class SimLog {
    public static final String HEADER =
            "tick,gdp,gdp_index,capital_domestic,capital_abroad,capital_lost,"
            + "capital_relocated_inland,capital_share_outside_zones,abroad_share,"
            + "firms_operating,firms_relocating,firms_abroad,firms_lost,"
            + "moves_inland,moves_abroad,treasury,revenue,spending,defence_spend,"
            + "relocation_spend,grant_coverage,price_level,defence_strength,"
            + "front_advance_km,front_movement_km,regions_occupied,regions_contested,"
            + "regions_threatened,labor_mobilised,labor_displaced,labor_held,unemployment";

    private SimLog() {}

    /** One tick as a CSV row, in {@link #HEADER} order. */
    public static String row(SimMetrics m) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(m.tick).append(',')
                .append(fmt(m.gdp)).append(',')
                .append(fmt(m.gdpIndex())).append(',')
                .append(fmt(m.capitalDomestic)).append(',')
                .append(fmt(m.capitalAbroad)).append(',')
                .append(fmt(m.capitalLost)).append(',')
                .append(fmt(m.capitalRelocatedInland)).append(',')
                .append(fmt(m.capitalShareOutsideZones)).append(',')
                .append(fmt(m.abroadShare())).append(',')
                .append(m.firmsOperating).append(',')
                .append(m.firmsRelocating).append(',')
                .append(m.firmsAbroad).append(',')
                .append(m.firmsLost).append(',')
                .append(m.movesStartedInland).append(',')
                .append(m.movesStartedAbroad).append(',')
                .append(fmt(m.treasury)).append(',')
                .append(fmt(m.revenue)).append(',')
                .append(fmt(m.spending)).append(',')
                .append(fmt(m.defenceSpend)).append(',')
                .append(fmt(m.relocationSpend)).append(',')
                .append(fmt(m.grantCoverage)).append(',')
                .append(fmt(m.priceLevel)).append(',')
                .append(fmt(m.defenceStrength)).append(',')
                .append(fmt(m.frontAdvanceKm)).append(',')
                .append(fmt(m.frontMovementKm)).append(',')
                .append(m.regionsOccupied).append(',')
                .append(m.regionsContested).append(',')
                .append(m.regionsThreatened).append(',')
                .append(fmt(m.laborMobilised)).append(',')
                .append(fmt(m.laborDisplaced)).append(',')
                .append(fmt(m.laborHeld)).append(',')
                .append(fmt(m.unemployment));
        return sb.toString();
    }

    /** The whole run, header included. */
    public static String toCsv(List<SimMetrics> history) {
        StringBuilder sb = new StringBuilder(history.size() * 220 + HEADER.length() + 2);
        sb.append(HEADER).append('\n');
        for (SimMetrics m : history) {
            sb.append(row(m)).append('\n');
        }
        return sb.toString();
    }

    /** Streams a run into any sink, so Android can write to app storage and tests to a string. */
    public static void write(List<SimMetrics> history, Appendable out) throws IOException {
        out.append(HEADER).append('\n');
        for (SimMetrics m : history) {
            out.append(row(m)).append('\n');
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
