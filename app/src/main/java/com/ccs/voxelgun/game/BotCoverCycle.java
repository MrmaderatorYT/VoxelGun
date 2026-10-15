package com.ccs.voxelgun.game;

/** A finite move-hide-peek-return sequence. It never moves bodies or supplies enemy knowledge. */
public final class BotCoverCycle {
    private int cover = -1, peek = -1;
    private boolean exposing, arrived, relocate;
    private float remaining, phaseTime, previousDurability, damage;
    private int hits;
    private float skill;

    public void start(int coverNode, int peekNode, Combatant c, Difficulty difficulty) {
        cover = coverNode; peek = peekNode; skill = difficulty.tactics();
        remaining = 9f; phaseTime = 1.3f - skill * .4f;
        previousDurability = c.health + c.armor; damage = 0f; hits = 0;
        exposing = arrived = relocate = false;
    }

    public void reset() { cover = peek = -1; remaining = 0f; exposing = arrived = relocate = false; }
    public boolean active() { return cover >= 0 && remaining > 0f; }
    public int destination() { return exposing ? peek : cover; }
    public int coverNode() { return cover; }
    public boolean settled() { return active() && arrived; }
    public boolean hidden() { return active() && !exposing && arrived; }
    public boolean canFire() { return active() && exposing && arrived; }
    public boolean needsRelocation() { return relocate; }

    public void update(Combatant c, NavGrid nav, float dt) {
        if (!active()) return;
        remaining -= dt;
        if (!c.alive || !nav.valid(cover) || !nav.valid(peek)) { reset(); return; }
        float durability = c.health + c.armor;
        if (durability < previousDurability - .1f) { damage += previousDurability - durability; hits++; }
        previousDurability = durability;
        if (hits >= 2 || damage >= 25f) { relocate = true; remaining = 0f; return; }
        boolean reloading = c.weapon() != null && (c.weapon().reloading() || c.weapon().empty());
        if (exposing && reloading) {
            exposing = arrived = false;
            phaseTime = 1.3f - skill * .4f;
        }
        int destination = destination();
        float dx = nav.cellX(destination) + .5f - c.position.x;
        float dz = nav.cellZ(destination) + .5f - c.position.z;
        float arrivalSquared = exposing ? .0225f : .36f;
        arrived = dx * dx + dz * dz < arrivalSquared && Math.abs(nav.nodeY(destination) - c.position.y) < 1f;
        if (!arrived) return;
        if (!exposing && reloading) return;
        phaseTime -= dt;
        if (phaseTime <= 0f || exposing && reloading) {
            exposing = !exposing; arrived = false;
            phaseTime = exposing ? .8f + skill * .4f : 1.3f - skill * .4f;
        }
    }
}
