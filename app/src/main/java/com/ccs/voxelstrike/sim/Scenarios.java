package com.ccs.voxelstrike.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The starting world: eight regions along one axis away from the hostile border, and the firms in
 * them.
 *
 * <p>The geography is deliberately plain — distance from the border is the only thing that
 * distinguishes one region's exposure from another's — so that anything interesting in the results
 * comes from sectors, zones and policy rather than from a hand-drawn map. Note that most regions
 * carry {@link EconomicZone#NONE}: zones are the exception, and the no-zone regions are the control
 * group every zone effect is measured against.
 */
public final class Scenarios {
    private Scenarios() {}

    /** Region name, zone (often none), km from the border, workforce, infrastructure. */
    private record Seed(String name, EconomicZone zone, float km, float labor, float infra,
                        Sector[] sectors, float[] weights) {}

    private static final Seed[] DEFAULT_REGIONS = {
        new Seed("Border East", EconomicZone.MINING_BASIN, 40f, 620f, 0.78f,
                new Sector[] {Sector.MINING, Sector.HEAVY, Sector.SERVICES},
                new float[] {0.50f, 0.35f, 0.15f}),
        new Seed("Eastern Steppe", EconomicZone.NONE, 110f, 480f, 0.62f,
                new Sector[] {Sector.AGRICULTURE, Sector.HEAVY, Sector.SERVICES},
                new float[] {0.45f, 0.35f, 0.20f}),
        new Seed("Industrial South", EconomicZone.INDUSTRIAL_PARK, 190f, 910f, 0.81f,
                new Sector[] {Sector.HEAVY, Sector.LIGHT, Sector.SERVICES},
                new float[] {0.50f, 0.30f, 0.20f}),
        new Seed("Central Plain", EconomicZone.NONE, 300f, 740f, 0.70f,
                new Sector[] {Sector.AGRICULTURE, Sector.LIGHT, Sector.SERVICES},
                new float[] {0.40f, 0.25f, 0.35f}),
        new Seed("Capital", EconomicZone.TECH_HUB, 380f, 1_450f, 0.92f,
                new Sector[] {Sector.IT, Sector.SERVICES, Sector.LIGHT},
                new float[] {0.35f, 0.45f, 0.20f}),
        new Seed("Northern Forest", EconomicZone.NONE, 470f, 390f, 0.58f,
                new Sector[] {Sector.AGRICULTURE, Sector.LIGHT, Sector.SERVICES},
                new float[] {0.45f, 0.30f, 0.25f}),
        new Seed("Western March", EconomicZone.LOGISTICS_HUB, 620f, 680f, 0.74f,
                new Sector[] {Sector.LIGHT, Sector.SERVICES, Sector.AGRICULTURE},
                new float[] {0.40f, 0.35f, 0.25f}),
        new Seed("Far West", EconomicZone.NONE, 760f, 520f, 0.66f,
                new Sector[] {Sector.AGRICULTURE, Sector.SERVICES, Sector.IT},
                new float[] {0.40f, 0.40f, 0.20f}),
    };

    /** Builds the default eight-region country with {@code firmCount} firms spread over it. */
    public static WarSim defaultCountry(SimConfig config, int firmCount) {
        Random random = new Random(config.seed ^ 0x5DEECE66DL);
        List<Region> regions = new ArrayList<>();
        for (int i = 0; i < DEFAULT_REGIONS.length; i++) {
            Seed seed = DEFAULT_REGIONS[i];
            regions.add(new Region(i, seed.name(), seed.zone(), seed.km(), seed.labor(),
                    seed.infra()));
        }

        // Capital is spread in proportion to each region's workforce, so the map has a real
        // industrial east and a service-heavy centre rather than eight identical provinces.
        float totalLabor = 0f;
        for (Seed seed : DEFAULT_REGIONS) {
            totalLabor += seed.labor();
        }
        List<Firm> firms = new ArrayList<>();
        int nextId = 0;
        for (int i = 0; i < DEFAULT_REGIONS.length; i++) {
            Seed seed = DEFAULT_REGIONS[i];
            Region region = regions.get(i);
            int count = Math.max(1, Math.round(firmCount * seed.labor() / totalLabor));
            for (int f = 0; f < count; f++) {
                Sector sector = pick(seed.sectors(), seed.weights(), random);
                double capital = 40.0 + random.nextDouble() * 160.0;
                if (random.nextFloat() < 0.08f) {
                    capital *= 4.0 + random.nextDouble() * 6.0; // a few large employers
                }
                float inertia = 0.15f + random.nextFloat() * 0.7f;
                float riskTolerance = 0.2f + random.nextFloat() * 0.6f;
                Firm firm = new Firm(nextId++, sector, region, capital, inertia, riskTolerance);
                region.firms.add(firm);
                firms.add(firm);
            }
        }
        return new WarSim(config, regions, firms);
    }

    /**
     * The same country with every zone stripped out. Run this against
     * {@link #defaultCountry(SimConfig, int)} on the same seed and the difference is the zones.
     */
    public static WarSim countryWithoutZones(SimConfig config, int firmCount) {
        WarSim sim = defaultCountry(config, firmCount);
        for (Region region : sim.regions()) {
            region.setZone(EconomicZone.NONE);
        }
        return sim;
    }

    private static Sector pick(Sector[] sectors, float[] weights, Random random) {
        float roll = random.nextFloat();
        float acc = 0f;
        for (int i = 0; i < sectors.length; i++) {
            acc += weights[i];
            if (roll <= acc) {
                return sectors[i];
            }
        }
        return sectors[sectors.length - 1];
    }
}
