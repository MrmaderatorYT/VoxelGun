package com.ccs.voxelgun.game;

/** Hooks the simulation uses to reach audio, haptics and the HUD without depending on them. */
public interface MatchListener {
    /** Positional sound request; {@code kind} indexes the procedural synth bank. */
    void onSound(int kind, float x, float y, float z, float volume, float pitch);

    /** The local player landed a hit; drives the hit marker and the confirmation blip. */
    void onLocalHit(boolean headshot, boolean lethal);

    /** The local player took damage from the given world-space direction. */
    void onLocalDamage(float amount, float fromYaw);

    void onKillFeed(KillFeedEntry entry);

    void onMatchEnd(MatchResult result);
}
