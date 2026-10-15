package com.ccs.voxelgun.game;

/** One line of the kill feed. Reused from a ring buffer, never allocated during a match. */
public final class KillFeedEntry {
    public String attacker = "";
    public String victim = "";
    public String weapon = "";
    public int attackerColor;
    public int victimColor;
    public boolean headshot;
    public boolean involvesPlayer;
    public float life;
    /** Actual damage source, not the attacker's weapon at the time this entry is drawn. */
    public int weaponOrdinal = -1;
    public int flags;

    public void set(String attacker, int attackerColor, String victim, int victimColor,
                    String weapon, boolean headshot, boolean involvesPlayer) {
        set(attacker, attackerColor, victim, victimColor, weapon, headshot, involvesPlayer,
                -1, headshot ? KillContext.HEADSHOT : 0);
    }

    public void set(String attacker, int attackerColor, String victim, int victimColor,
                    String weapon, boolean headshot, boolean involvesPlayer,
                    int weaponOrdinal, int flags) {
        this.attacker = attacker;
        this.attackerColor = attackerColor;
        this.victim = victim;
        this.victimColor = victimColor;
        this.weapon = weapon;
        this.headshot = headshot;
        this.involvesPlayer = involvesPlayer;
        this.weaponOrdinal = weaponOrdinal;
        this.flags = headshot ? flags | KillContext.HEADSHOT : flags & ~KillContext.HEADSHOT;
        this.life = 5.5f;
    }

    /** Preserve age and metadata when shifting the reusable feed ring. */
    public void copyFrom(KillFeedEntry source) {
        attacker = source.attacker;
        attackerColor = source.attackerColor;
        victim = source.victim;
        victimColor = source.victimColor;
        weapon = source.weapon;
        weaponOrdinal = source.weaponOrdinal;
        flags = source.flags;
        headshot = source.headshot;
        involvesPlayer = source.involvesPlayer;
        life = source.life;
    }
}
