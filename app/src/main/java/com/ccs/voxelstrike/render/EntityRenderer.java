package com.ccs.voxelstrike.render;

import com.ccs.voxelstrike.game.Combatant;
import com.ccs.voxelstrike.app.Cosmetics;
import com.ccs.voxelstrike.game.Projectile;
import com.ccs.voxelstrike.game.Team;
import com.ccs.voxelstrike.math.Frustum;
import com.ccs.voxelstrike.math.Mat4;
import com.ccs.voxelstrike.math.MathUtil;
import com.ccs.voxelstrike.model.Models;
import com.ccs.voxelstrike.model.VoxelModel;
import com.ccs.voxelstrike.voxel.Materials;
import com.ccs.voxelstrike.voxel.MeshData;
import com.ccs.voxelstrike.voxel.VoxelMesher;
import com.ccs.voxelstrike.weapon.WeaponType;
import com.ccs.voxelstrike.weapon.WeaponClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws characters, their weapons, thrown projectiles and the first person view model.
 *
 * <p>Characters are a six-part rig (head, torso, two arms, two legs) animated from the walk
 * cycle and aim angles. Every part is a greedy-meshed voxel model built at load time.
 */
public final class EntityRenderer {
    private static final float SCALE = Models.CHARACTER_SCALE;
    /** Longest weapon switch in the match loop; the swap animation is scaled to it. */
    private static final float SWITCH_SECONDS = 0.32f;
    private static final float LEG_HEIGHT = 12f * SCALE;
    private static final float TORSO_TOP = LEG_HEIGHT + 12f * SCALE;
    private static final float SHOULDER_Y = LEG_HEIGHT + 10.5f * SCALE;
    private static final float SHOULDER_X = 0.28f;
    /** Arm pitch that points the limb straight ahead, kept a touch under PI/2 so elbows drop. */
    private static final float ARM_READY = 1.46f;
    /** Shoulder-to-hand distance, slightly short of the model length for a bent elbow. */
    private static final float ARM_REACH = 10f * SCALE;

    private final Map<String, Rig> rigs = new HashMap<>();
    private final Map<String, WeaponMesh> weapons = new HashMap<>();
    private final Map<String, WeaponMesh> cosmeticWeapons = new HashMap<>();
    private final Map<Integer, WeaponMesh> charms = new HashMap<>();
    private final WeaponMesh[] objectiveRings = new WeaponMesh[3];
    private WeaponMesh objectiveCore;
    private WeaponMesh rocketMesh;

    private final float[] entity = new float[16];
    private final float[] previousEntity = new float[16];
    private final float[] local = new float[16];
    private final float[] world = new float[16];
    private final float[] previousWorld = new float[16];
    private final float[] camera = new float[16];
    private final float[] previousCamera = Mat4.create();
    private boolean previousCameraValid;
    /**
     * The view model's own local transform from the previous frame. Without it the motion vectors
     * only describe the camera, so sway, recoil and — worst of all — a weapon swap write zero
     * velocity, and the temporal passes smear the gun you just put away over the one you drew.
     */
    private final float[] previousViewLocal = Mat4.create();
    private final float[] viewRig = Mat4.create();
    private final float[] previousCharm = Mat4.create();
    private int previousCharmId = -1, previousWeaponSkin = -1;
    private boolean previousViewValid;
    private String previousViewModelKey;

    /** Builds every mesh; call once with a current GL context. */
    public void create() {
        rigs.put("blue", new Rig(Materials.TEAM_BLUE, false));
        rigs.put("red", new Rig(Materials.TEAM_RED, false));
        rigs.put("none", new Rig(Materials.PLASTIC_ORANGE, false));
        rigs.put("zombie", new Rig(Materials.LEAVES, true));

        for (WeaponType type : WeaponType.values()) {
            String key = type.modelKey();
            if (!weapons.containsKey(key)) {
                weapons.put(key, new WeaponMesh(Models.weapon(key)));
            }
        }
        rocketMesh = new WeaponMesh(Models.rocket());
        int[] markerMaterials = {Materials.SUNNY, Materials.NEON_CYAN, Materials.EMERGENCY};
        for (int i = 0; i < objectiveRings.length; i++) {
            VoxelModel ring = new VoxelModel(17, 1, 17).pivot(8.5f, 0f, 8.5f);
            for (int z = 0; z < 17; z++) for (int x = 0; x < 17; x++) {
                float distance = (x - 8) * (x - 8) + (z - 8) * (z - 8);
                if (distance >= 52 && distance <= 68) ring.set(x, 0, z, markerMaterials[i]);
            }
            objectiveRings[i] = new WeaponMesh(ring);
        }
        objectiveCore = new WeaponMesh(new VoxelModel(5, 5, 5).box(0, 0, 0, 4, 4, 4,
                Materials.SUNNY).pivot(2.5f, 2.5f, 2.5f));
        for (int i = 1; i <= 2; i++) {
            VoxelModel charm = new VoxelModel(3, 7, 3).box(1, 3, 1, 1, 6, 1, Materials.GUN_METAL)
                    .box(0, 0, 0, 2, 2, 2, i == 1 ? Materials.MINT : Materials.SUNNY).pivot(1, 6, 1);
            charms.put(i, new WeaponMesh(charm));
        }
    }

    private Rig rigFor(Combatant combatant) {
        if (combatant.zombie) {
            return rigs.get("zombie");
        }
        if (combatant.team == Team.BLUE) {
            return rigs.get("blue");
        }
        if (combatant.team == Team.RED) {
            return rigs.get("red");
        }
        return rigs.get("none");
    }

    /** Draws every visible combatant except the local player. */
    public void drawCombatants(Shader shader, List<Combatant> combatants, Combatant viewer,
                               Frustum frustum) {
        for (int i = 0; i < combatants.size(); i++) {
            Combatant combatant = combatants.get(i);
            if (combatant == viewer || !combatant.alive) {
                continue;
            }
            float minX = combatant.position.x - 0.6f;
            float minZ = combatant.position.z - 0.6f;
            if (!frustum.containsBox(minX, combatant.position.y - 0.1f, minZ, minX + 1.2f,
                    combatant.position.y + 2.0f, minZ + 1.2f)) {
                continue;
            }
            boolean friendly = combatant.team != Team.NONE && combatant.team == viewer.team;
            shader.set("uCharacterTeam", friendly ? 1f : 2f);
            drawCharacter(shader, combatant);
        }
        shader.set("uCharacterTeam", 0f);
    }

    private void drawCharacter(Shader shader, Combatant combatant) {
        Rig rig = rigFor(combatant);
        float crouch = MathUtil.clamp(combatant.crouchBlend, 0f, 1f);
        float crouchDrop = -0.34f * crouch;
        Mat4.model(entity, combatant.position.x, combatant.position.y, combatant.position.z,
                combatant.yaw, 1f);
        if (combatant.renderPreviousValid) {
            Mat4.model(previousEntity, combatant.renderPreviousX, combatant.renderPreviousY,
                    combatant.renderPreviousZ, combatant.renderPreviousYaw, 1f);
        } else {
            Mat4.copy(previousEntity, entity);
        }

        float planar = (float) Math.sqrt(combatant.velocity.x * combatant.velocity.x
                + combatant.velocity.z * combatant.velocity.z);
        float stride = MathUtil.clamp(planar / 5.4f, 0f, 1.4f);
        float swing = (float) Math.sin(combatant.walkCycle) * 0.62f * stride * (1f - crouch * 0.55f);
        float bounce = Math.abs((float) Math.cos(combatant.walkCycle)) * 0.035f * stride;

        // Legs. Crouching folds the knees forward and splays the stance a little.
        float knee = 0.85f * crouch;
        float splay = 0.02f * crouch;
        part(shader, rig.legLeft, -0.115f - splay, LEG_HEIGHT + crouchDrop + bounce, 0.05f * crouch,
                0f, swing + knee, -0.10f * crouch);
        part(shader, rig.legRight, 0.115f + splay, LEG_HEIGHT + crouchDrop + bounce, 0.05f * crouch,
                0f, -swing + knee, 0.10f * crouch);

        // Torso leans forward when sprinting, and further when crouched.
        float lean = (combatant.sprinting ? 0.20f : 0.06f) + 0.34f * crouch;
        part(shader, rig.torso, 0f, LEG_HEIGHT + crouchDrop + bounce, 0f, 0f, -lean, 0f);

        // Head follows the aim pitch, compensating for the crouched torso lean.
        part(shader, rig.head, 0f, TORSO_TOP + crouchDrop + bounce - 0.02f - 0.10f * crouch,
                0.07f * crouch, 0f, combatant.pitch, 0f);

        // Arms. The pivot sits in the shoulder and the limb hangs down local -Y, so the pitch
        // that points it straight forward is +PI/2; the aim pitch then swings it with the look
        // direction. A negative angle would fold both arms behind the back.
        boolean melee = combatant.weaponType().melee();
        float aimPitch = combatant.pitch;
        float shoulderY = SHOULDER_Y + crouchDrop + bounce - 0.12f * crouch;
        float shoulderZ = 0.05f * crouch;
        // Melee winds up above the shoulder and chops back down to the ready pose.
        float armPitch = melee ? ARM_READY + combatant.meleeSwing * 0.85f : ARM_READY + aimPitch;
        part(shader, rig.armRight, SHOULDER_X, shoulderY, shoulderZ, 0f, armPitch, -0.12f);
        part(shader, rig.armLeft, -SHOULDER_X, shoulderY, shoulderZ, 0f,
                melee ? 0.28f + swing * 0.25f : armPitch - 0.10f, 0.18f);

        // Weapon sits in the right hand, which is wherever the arm rotation put it.
        WeaponMesh mesh = weaponMesh(combatant.weaponType().modelKey(), combatant.weaponSkin());
        if (mesh != null) {
            float handY = shoulderY - (float) Math.cos(armPitch) * ARM_REACH;
            float handZ = shoulderZ - (float) Math.sin(armPitch) * ARM_REACH;
            Mat4.identity(local);
            Mat4.translate(local, 0.24f, handY, handZ);
            Mat4.rotate(local, (float) Math.toDegrees(aimPitch), 1f, 0f, 0f);
            applyPivot(local, mesh.model, Models.weaponScale(mesh.model));
            submit(shader, entity, previousEntity);
            mesh.mesh.draw();
        }

        combatant.renderPreviousX = combatant.position.x;
        combatant.renderPreviousY = combatant.position.y;
        combatant.renderPreviousZ = combatant.position.z;
        combatant.renderPreviousYaw = combatant.yaw;
        combatant.renderPreviousValid = true;
    }

    /** Uploads the current and previous world transforms for the pose held in {@code local}. */
    private void submit(Shader shader, float[] current, float[] previous) {
        Mat4.multiply(world, current, local);
        Mat4.multiply(previousWorld, previous, local);
        shader.setMatrix("uModel", world);
        shader.setMatrix("uPreviousModel", previousWorld);
    }

    /** Places one rig part at a joint with the given rotation. */
    private void part(Shader shader, PartMesh mesh, float jointX, float jointY, float jointZ,
                      float yaw, float pitch, float roll) {
        Mat4.identity(local);
        Mat4.translate(local, jointX, jointY, jointZ);
        if (yaw != 0f) {
            Mat4.rotate(local, Mat4.yawDegrees(yaw), 0f, 1f, 0f);
        }
        if (pitch != 0f) {
            Mat4.rotate(local, (float) Math.toDegrees(pitch), 1f, 0f, 0f);
        }
        if (roll != 0f) {
            Mat4.rotate(local, (float) Math.toDegrees(roll), 0f, 0f, 1f);
        }
        applyPivot(local, mesh.model, SCALE);
        submit(shader, entity, previousEntity);
        mesh.mesh.draw();
    }

    private static void applyPivot(float[] matrix, VoxelModel model, float scale) {
        Mat4.translate(matrix, -model.pivotX * scale, -model.pivotY * scale,
                -model.pivotZ * scale);
        Mat4.scale(matrix, scale, scale, scale);
    }

    public void drawProjectiles(Shader shader, Projectile[] projectiles) {
        for (Projectile projectile : projectiles) {
            if (!projectile.active) {
                continue;
            }
            WeaponMesh mesh = projectile.rocket ? rocketMesh : weapons.get(
                    projectile.type == null ? WeaponType.FRAG.modelKey()
                            : projectile.type.modelKey());
            if (mesh == null) {
                continue;
            }
            Mat4.identity(local);
            Mat4.translate(local, projectile.x, projectile.y, projectile.z);
            if (projectile.rocket) {
                float yaw = (float) Math.atan2(projectile.vx, -projectile.vz);
                float horizontal = (float) Math.sqrt(projectile.vx * projectile.vx
                        + projectile.vz * projectile.vz);
                float pitch = (float) Math.atan2(projectile.vy, horizontal);
                Mat4.rotate(local, Mat4.yawDegrees(yaw), 0f, 1f, 0f);
                Mat4.rotate(local, (float) Math.toDegrees(pitch), 1f, 0f, 0f);
            } else {
                Mat4.rotate(local, (float) Math.toDegrees(projectile.spin), 0.4f, 1f, 0.2f);
            }
            applyPivot(local, mesh.model, (projectile.rocket ? 0.05f : 0.045f) * mesh.model.voxelSize);
            shader.setMatrix("uModel", local);
            shader.setMatrix("uPreviousModel", local);
            mesh.mesh.draw();
        }
    }

    public void drawObjectives(Shader shader, com.ccs.voxelstrike.game.Match match) {
        com.ccs.voxelstrike.game.HudSnapshot state = match.snapshot();
        for (int i = 0; i < state.objectiveCount; i++) {
            int color = state.objectiveColor[i];
            int index = color == Team.BLUE.color() ? 1 : color == Team.RED.color() ? 2 : 0;
            boolean core = match.mode() == com.ccs.voxelstrike.game.GameMode.CRYSTAL_HEIST && i == 0;
            WeaponMesh mesh = core ? objectiveCore : objectiveRings[index];
            Mat4.identity(local);
            Mat4.translate(local, state.objectiveX[i], state.objectiveY[i] + (core ? 1f : 0.035f), state.objectiveZ[i]);
            if (core) Mat4.rotate(local, 45f, 0f, 1f, 0f);
            Mat4.scale(local, core ? 0.10f : 0.47f, core ? 0.10f : 0.055f, core ? 0.10f : 0.47f);
            Mat4.translate(local, -mesh.model.pivotX, -mesh.model.pivotY, -mesh.model.pivotZ);
            shader.setMatrix("uModel", local);
            shader.setMatrix("uPreviousModel", local);
            mesh.mesh.draw();
        }
    }

    /**
     * Draws the player's own weapon in front of the camera.
     *
     * @param sway     accumulated look delta, used for the weapon lag
     * @param adsBlend 0 hip fire, 1 fully aimed
     */
    public void drawViewModel(Shader shader, Combatant player, float cameraX, float cameraY,
                              float cameraZ, float yaw, float pitch, float swayX, float swayY,
                              float adsBlend, float bob, float recoil) {
        String modelKey = player.weaponType().modelKey();
        WeaponMesh mesh = weaponMesh(modelKey, player.weaponSkin());
        if (mesh == null) {
            previousViewValid = false;
            previousViewModelKey = null;
            return;
        }
        Mat4.identity(camera);
        Mat4.translate(camera, cameraX, cameraY, cameraZ);
        Mat4.rotate(camera, Mat4.yawDegrees(yaw), 0f, 1f, 0f);
        Mat4.rotate(camera, (float) Math.toDegrees(pitch), 1f, 0f, 0f);

        WeaponType type = player.weaponType();
        boolean melee = type.melee();
        boolean grenade = type.grenade();
        boolean pistol = type.weaponClass() == WeaponClass.PISTOL;
        float aim = MathUtil.clamp(adsBlend, 0f, 1f);
        float sizeBlend = MathUtil.lerp(1f, .92f, aim);
        // Move the stock away from the eye and show the receiver's side. A rear-on close-up
        // magnified a few stock voxels until they hid the entire detailed part of the weapon.
        float hipX = melee ? .24f : pistol || grenade ? .23f : .29f;
        float hipY = melee ? -.22f : pistol || grenade ? -.20f : -.24f;
        float hipZ = melee ? -.58f : Math.min(-.62f, -mesh.rearExtent - .40f);
        float adsY = -mesh.sightLift * sizeBlend;
        float adsZ = -Math.max(.46f, mesh.rearExtent + .22f) * Math.max(1f, type.zoom());
        float crouch = MathUtil.clamp(player.crouchBlend, 0f, 1f);
        float swap = MathUtil.clamp(player.switchTimer / SWITCH_SECONDS, 0f, 1f);
        float reload = player.weapon().reloading()
                ? (float) Math.sin(Math.PI * player.weapon().reloadProgress()) : 0f;
        float offsetX = MathUtil.lerp(hipX, 0f, aim) + swayX * .05f - .015f * crouch;
        float offsetY = MathUtil.lerp(hipY, adsY, aim) + swayY * .05f + bob
                - .045f * crouch - swap * swap * .55f - reload * .045f;
        float offsetZ = MathUtil.lerp(hipZ, adsZ, aim) + recoil * .06f + .035f * crouch;

        Mat4.identity(viewRig);
        Mat4.translate(viewRig, offsetX, offsetY, offsetZ);
        Mat4.rotate(viewRig, (melee ? 36f : grenade ? 15f : 18f) * (1f - aim)
                + Mat4.yawDegrees(swayX * .35f) + reload * 12f, 0f, 1f, 0f);
        Mat4.rotate(viewRig, (melee ? 20f : 0f) + (float) Math.toDegrees(swayY * .35f
                + recoil * .22f - swap * 1.05f) - reload * 12f, 1f, 0f, 0f);
        Mat4.rotate(viewRig, (melee ? -8f : -3f) * (1f - aim) - reload * 22f, 0f, 0f, 1f);
        if (melee) {
            Mat4.rotate(viewRig, (float) Math.toDegrees(-player.meleeSwing * 1.8f), 1f, .35f, 0f);
        }
        if (!previousCameraValid) {
            Mat4.copy(previousCamera, camera);
            previousCameraValid = true;
        }
        int weaponSkin = player.weaponSkin();
        boolean reset = !previousViewValid || !modelKey.equals(previousViewModelKey)
                || weaponSkin != previousWeaponSkin;
        Mat4.copy(local, viewRig);
        applyPivot(local, mesh.model, mesh.firstPersonScale * sizeBlend);
        drawViewPart(shader, mesh, previousViewLocal, reset);

        WeaponMesh charm = charms.get(player.charm);
        if (charm != null && !grenade && !melee) {
            drawAttachment(shader, charm, .075f, .025f, -.10f,
                    (float) Math.sin(player.walkCycle) * .22f + recoil * .3f, 0f, .016f,
                    previousCharm, reset || player.charm != previousCharmId);
        }
        // Keep the old camera until every attached part has written its motion vector.
        Mat4.copy(previousCamera, camera);
        previousViewValid = true;
        previousViewModelKey = modelKey;
        previousWeaponSkin = weaponSkin;
        previousCharmId = player.charm;
    }

    /** A hidden rig has no immediately preceding geometry to reproject after it returns. */
    public void invalidateViewHistory() {
        previousCameraValid = false;
        previousViewValid = false;
    }

    private void drawViewPart(Shader shader, WeaponMesh mesh, float[] previous, boolean reset) {
        if (reset) Mat4.copy(previous, local);
        Mat4.multiply(world, camera, local);
        Mat4.multiply(previousWorld, previousCamera, previous);
        shader.setMatrix("uModel", world);
        shader.setMatrix("uPreviousModel", previousWorld);
        mesh.mesh.draw();
        Mat4.copy(previous, local);
    }

    /** Returns one cached mesh for the exact geometry/finish pair, for both hands and world rigs. */
    private WeaponMesh weaponMesh(String modelKey, int skin) {
        WeaponMesh original = weapons.get(modelKey);
        if (skin <= 0 || original == null) {
            return original;
        }
        String key = modelKey + ":" + skin;
        WeaponMesh painted = cosmeticWeapons.get(key);
        if (painted == null) {
            VoxelModel model = Cosmetics.applyWeaponSkin(original.model.copy(), skin);
            painted = new WeaponMesh(model);
            cosmeticWeapons.put(key, painted);
        }
        return painted;
    }

    private void drawAttachment(Shader shader, WeaponMesh mesh, float x, float y, float z,
                                float roll, float pitchDegrees, float scale, float[] previous, boolean reset) {
        Mat4.copy(local, viewRig);
        Mat4.translate(local, x, y, z);
        Mat4.rotate(local, (float) Math.toDegrees(roll), 0f, 0f, 1f);
        Mat4.rotate(local, pitchDegrees, 1f, 0f, 0f);
        applyPivot(local, mesh.model, scale);
        drawViewPart(shader, mesh, previous, reset);
    }

    public void release() {
        for (WeaponMesh mesh : objectiveRings) if (mesh != null) mesh.mesh.release();
        if (objectiveCore != null) { objectiveCore.mesh.release(); objectiveCore = null; }
        for (WeaponMesh mesh : cosmeticWeapons.values()) mesh.mesh.release();
        for (WeaponMesh mesh : charms.values()) mesh.mesh.release();
        cosmeticWeapons.clear(); charms.clear();
        for (Rig rig : rigs.values()) {
            rig.release();
        }
        rigs.clear();
        for (WeaponMesh mesh : weapons.values()) {
            mesh.mesh.release();
        }
        weapons.clear();
        if (rocketMesh != null) {
            rocketMesh.mesh.release();
            rocketMesh = null;
        }
        previousCameraValid = false;
        previousViewValid = false;
        previousViewModelKey = null;
    }

    /** One meshed rig part plus its source model, kept for the pivot. */
    private static final class PartMesh {
        final VoxelModel model;
        final GpuMesh mesh;

        PartMesh(VoxelModel model) {
            this.model = model;
            MeshData data = VoxelMesher.meshModel(model.voxels, model.sizeX, model.sizeY,
                    model.sizeZ, 1f, VoxelMesher.PASS_OPAQUE);
            this.mesh = new GpuMesh(data);
        }
    }

    private static final class WeaponMesh {
        final VoxelModel model;
        final GpuMesh mesh;
        /** How far the sight line sits above the pivot, in world units. */
        final float sightLift;
        final float firstPersonScale;
        final float rearExtent;

        WeaponMesh(VoxelModel model) {
            this.model = model;
            MeshData data = VoxelMesher.meshModel(model.voxels, model.sizeX, model.sizeY,
                    model.sizeZ, 1f, VoxelMesher.PASS_OPAQUE);
            this.mesh = new GpuMesh(data);
            this.firstPersonScale = Models.firstPersonScale(model);
            this.sightLift = Models.sightLift(model) * firstPersonScale;
            this.rearExtent = Math.max(0f, model.sizeZ - model.pivotZ)
                    * firstPersonScale;
        }

    }

    private static final class Rig {
        final PartMesh head;
        final PartMesh torso;
        final PartMesh armLeft;
        final PartMesh armRight;
        final PartMesh legLeft;
        final PartMesh legRight;

        Rig(int accent, boolean zombie) {
            head = new PartMesh(Models.head(accent, zombie));
            torso = new PartMesh(Models.torso(accent, zombie));
            VoxelModel arm = Models.arm(accent, zombie);
            armLeft = new PartMesh(arm);
            armRight = new PartMesh(arm.copy());
            VoxelModel leg = Models.leg(zombie);
            legLeft = new PartMesh(leg);
            legRight = new PartMesh(leg.copy());
        }

        void release() {
            head.mesh.release();
            torso.mesh.release();
            armLeft.mesh.release();
            armRight.mesh.release();
            legLeft.mesh.release();
            legRight.mesh.release();
        }
    }
}
