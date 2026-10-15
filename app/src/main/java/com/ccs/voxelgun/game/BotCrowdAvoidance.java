package com.ccs.voxelgun.game;

import com.ccs.voxelgun.voxel.VoxelWorld;
import java.util.List;

/** Small velocity correction, never position teleportation or full actor-pair rigid physics. */
public final class BotCrowdAvoidance {
    private BotCrowdAvoidance() {}

    public static void separate(Combatant self, List<Combatant> actors, VoxelWorld world, float dt) {
        if (!self.alive) return;
        float pushX = 0f, pushZ = 0f;
        for (int i = 0; i < actors.size(); i++) {
            Combatant other = actors.get(i);
            if (other == self || !other.alive || Math.abs(other.position.y - self.position.y) >= 1.8f) continue;
            float dx = self.position.x - other.position.x, dz = self.position.z - other.position.z;
            float squared = dx * dx + dz * dz;
            if (squared >= 1.15f * 1.15f) continue;
            if (squared < .0001f) {
                int low = Math.min(self.id, other.id), high = Math.max(self.id, other.id);
                float angle = ((low * 31 + high * 17) & 15) * (float) Math.PI / 8f;
                float sign = self.id < other.id ? -1f : 1f;
                pushX += (float) Math.cos(angle) * sign;
                pushZ += (float) Math.sin(angle) * sign;
            } else {
                float distance = (float) Math.sqrt(squared);
                float strength = (1.15f - distance) / (1.15f * distance);
                pushX += dx * strength; pushZ += dz * strength;
            }
        }
        float length = (float) Math.sqrt(pushX * pushX + pushZ * pushZ);
        if (length < .001f) return;
        float scale = Math.min(1.8f, length * 1.8f) / length;
        float vx = self.velocity.x + pushX * scale, vz = self.velocity.z + pushZ * scale;
        // Separation redirects a moving bot; it must not manufacture extra bhop speed.
        float maxSpeed = Math.max(1.8f, (float) Math.hypot(self.velocity.x, self.velocity.z));
        float speed = (float) Math.hypot(vx, vz);
        if (speed > maxSpeed) { vx *= maxSpeed / speed; vz *= maxSpeed / speed; }
        if (!world.boxCollides(self.position.x + vx * dt, self.position.y,
                self.position.z + vz * dt, Combatant.RADIUS, self.height())) {
            self.velocity.x = vx; self.velocity.z = vz;
        }
    }
}
