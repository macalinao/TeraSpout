package org.terasology.componentSystem.controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

import org.terasology.componentSystem.RenderSystem;
import org.terasology.components.CharacterMovementComponent;
import org.terasology.components.HealthComponent;
import org.terasology.components.InventoryComponent;
import org.terasology.components.ItemComponent;
import org.terasology.components.LocalPlayerComponent;
import org.terasology.components.PlayerComponent;
import org.terasology.components.world.BlockComponent;
import org.terasology.components.world.LocationComponent;
import org.terasology.entitySystem.EntityRef;
import org.terasology.entitySystem.EventHandlerSystem;
import org.terasology.entitySystem.ReceiveEvent;
import org.terasology.events.ActivateEvent;
import org.terasology.events.DamageEvent;
import org.terasology.events.NoHealthEvent;
import org.terasology.events.input.MouseXAxisEvent;
import org.terasology.events.input.MouseYAxisEvent;
import org.terasology.events.input.binds.AttackButton;
import org.terasology.events.input.binds.ForwardsMovementAxis;
import org.terasology.events.input.binds.FrobButton;
import org.terasology.events.input.binds.JumpButton;
import org.terasology.events.input.binds.RunButton;
import org.terasology.events.input.binds.StrafeMovementAxis;
import org.terasology.events.input.binds.ToolbarNextButton;
import org.terasology.events.input.binds.ToolbarPrevButton;
import org.terasology.events.input.binds.ToolbarSlotButton;
import org.terasology.events.input.binds.UseItemButton;
import org.terasology.events.input.binds.VerticalMovementAxis;
import org.terasology.game.CoreRegistry;
import org.terasology.input.ButtonState;
import org.terasology.logic.LocalPlayer;
import org.terasology.logic.manager.Config;
import org.terasology.logic.world.BlockEntityRegistry;
import org.terasology.logic.world.WorldProvider;
import org.terasology.math.TeraMath;
import org.terasology.math.Vector3i;
import org.terasology.model.structures.RayBlockIntersection;
import org.terasology.rendering.cameras.DefaultCamera;
import org.terasology.teraspout.TeraBlock;

import com.bulletphysics.linearmath.QuaternionUtil;

/**
 * @author Immortius <immortius@gmail.com>
 */
// TODO: This needs a really good cleanup
// TODO: Move more input stuff to a specific input system?
// TODO: Camera should become an entity/component, so it can follow the player naturally
public class LocalPlayerSystem implements RenderSystem, EventHandlerSystem {
    private LocalPlayer localPlayer;

    private WorldProvider worldProvider;
    private DefaultCamera playerCamera;

    private long lastTimeSpacePressed;
    private long lastInteraction;

    private boolean cameraBobbing = Config.getInstance().isCameraBobbing();
    private float bobFactor = 0;
    private float lastStepDelta = 0;

    private Vector3f relativeMovement = new Vector3f();

    @Override
    public void initialise() {
        worldProvider = CoreRegistry.get(WorldProvider.class);
        localPlayer = CoreRegistry.get(LocalPlayer.class);
    }

    @Override
    public void shutdown() {
    }

    public void setPlayerCamera(DefaultCamera camera) {
        playerCamera = camera;
    }

    @ReceiveEvent(components = LocalPlayerComponent.class)
    public void onMouseX(MouseXAxisEvent event, EntityRef entity) {
        LocalPlayerComponent localPlayer = entity.getComponent(LocalPlayerComponent.class);
        localPlayer.viewYaw = (localPlayer.viewYaw - event.getValue()) % 360;
        entity.saveComponent(localPlayer);
        LocationComponent loc = entity.getComponent(LocationComponent.class);
        if (loc != null) {
            QuaternionUtil.setEuler(loc.getLocalRotation(), TeraMath.DEG_TO_RAD * localPlayer.viewYaw, 0, 0);
            entity.saveComponent(loc);
        }
        event.consume();
    }

    @ReceiveEvent(components = LocalPlayerComponent.class)
    public void onMouseY(MouseYAxisEvent event, EntityRef entity) {
        LocalPlayerComponent localPlayer = entity.getComponent(LocalPlayerComponent.class);
        localPlayer.viewPitch = TeraMath.clamp(localPlayer.viewPitch - event.getValue(), -89, 89);
        entity.saveComponent(localPlayer);
        event.consume();
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class, CharacterMovementComponent.class})
    public void onJump(JumpButton event, EntityRef entity) {
        if (event.getState() == ButtonState.DOWN) {
            CharacterMovementComponent characterMovement = entity.getComponent(CharacterMovementComponent.class);
            characterMovement.jump = true;
            if (System.currentTimeMillis() - lastTimeSpacePressed < 200) {
                characterMovement.isGhosting = !characterMovement.isGhosting;
            }
            lastTimeSpacePressed = System.currentTimeMillis();
            event.consume();
        }
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class, CharacterMovementComponent.class})
    public void updateForwardsMovement(ForwardsMovementAxis event, EntityRef entity) {
        relativeMovement.z = event.getValue();
        event.consume();
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class, CharacterMovementComponent.class})
    public void updateStrafeMovement(StrafeMovementAxis event, EntityRef entity) {
        relativeMovement.x = event.getValue();
        event.consume();
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class, CharacterMovementComponent.class})
    public void updateVerticalMovement(VerticalMovementAxis event, EntityRef entity) {
        relativeMovement.y = event.getValue();
        event.consume();
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class, CharacterMovementComponent.class})
    public void onRun(RunButton event, EntityRef entity) {
        CharacterMovementComponent characterMovement = entity.getComponent(CharacterMovementComponent.class);
        characterMovement.isRunning = event.isDown();
        event.consume();
    }

    @Override
    public void renderOverlay() {
        // TODO: Don't render if not in first person?
        // Display the block the player is aiming at
        if (Config.getInstance().isPlacingBox()) {
            RayBlockIntersection.Intersection selectedBlock = calcSelectedBlock();
            if (selectedBlock != null) {
                TeraBlock block = worldProvider.getBlock(selectedBlock.getBlockPosition());
                if (block.isRenderBoundingBox()) {
                    block.getBounds(selectedBlock.getBlockPosition()).render(2f);
                }
            }
        }
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class})
    public void onDeath(NoHealthEvent event, EntityRef entity) {
        LocalPlayerComponent localPlayer = entity.getComponent(LocalPlayerComponent.class);
        localPlayer.isDead = true;
        localPlayer.respawnWait = 1.0f;
        entity.saveComponent(localPlayer);
    }

    private void updateMovement(LocalPlayerComponent localPlayerComponent, CharacterMovementComponent characterMovementComponent, LocationComponent location) {
        Vector3f relMove = new Vector3f(relativeMovement);
        relMove.y = 0;
        if (characterMovementComponent.isGhosting || characterMovementComponent.isSwimming) {
            Quat4f viewRot = new Quat4f();
            QuaternionUtil.setEuler(viewRot, TeraMath.DEG_TO_RAD * localPlayerComponent.viewYaw, TeraMath.DEG_TO_RAD * localPlayerComponent.viewPitch, 0);
            QuaternionUtil.quatRotate(viewRot, relMove, relMove);
            relMove.y += relativeMovement.y;
        } else {
            QuaternionUtil.quatRotate(location.getLocalRotation(), relMove, relMove);
        }
        float lengthSquared = relMove.lengthSquared();
        if (lengthSquared > 1) relMove.normalize();
        characterMovementComponent.setDrive(relMove);
    }

    private boolean checkRespawn(float deltaSeconds, EntityRef entity, LocalPlayerComponent localPlayerComponent, CharacterMovementComponent characterMovementComponent, LocationComponent location, PlayerComponent playerComponent) {
        localPlayerComponent.respawnWait -= deltaSeconds;
        if (localPlayerComponent.respawnWait > 0) {
            characterMovementComponent.getDrive().set(0, 0, 0);
            characterMovementComponent.jump = false;
            return false;
        }

        // Respawn
        localPlayerComponent.isDead = false;
        HealthComponent health = entity.getComponent(HealthComponent.class);
        if (health != null) {
            health.currentHealth = health.maxHealth;
            entity.saveComponent(health);
        }
        location.setWorldPosition(playerComponent.spawnPosition);
        entity.saveComponent(location);
        return true;
    }

    private void updateCamera(CharacterMovementComponent charMovementComp, Vector3f position, Quat4f rotation) {
        // The camera position is the player's position plus the eye offset
        Vector3d cameraPosition = new Vector3d();
        // TODO: don't hardset eye position
        cameraPosition.add(new Vector3d(position), new Vector3d(0, 0.6f, 0));

        playerCamera.getPosition().set(cameraPosition);
        Vector3f viewDir = new Vector3f(0, 0, 1);
        QuaternionUtil.quatRotate(rotation, viewDir, viewDir);
        playerCamera.getViewingDirection().set(viewDir);

        float stepDelta = charMovementComp.footstepDelta - lastStepDelta;
        if (stepDelta < 0) stepDelta += charMovementComp.distanceBetweenFootsteps;
        bobFactor += stepDelta;
        lastStepDelta = charMovementComp.footstepDelta;

        if (cameraBobbing) {
            playerCamera.setBobbingRotationOffsetFactor(calcBobbingOffset(0.0f, 0.01f, 2.5f));
            playerCamera.setBobbingVerticalOffsetFactor(calcBobbingOffset((float) java.lang.Math.PI / 4f, 0.025f, 3f));
        } else {
            playerCamera.setBobbingRotationOffsetFactor(0.0);
            playerCamera.setBobbingVerticalOffsetFactor(0.0);
        }

        if (charMovementComp.isGhosting) {
            playerCamera.extendFov(24);
        } else {
            playerCamera.resetFov();
        }
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class, InventoryComponent.class})
    public void onAttackRequest(AttackButton event, EntityRef entity) {
        if (!event.isDown() || System.currentTimeMillis() - lastInteraction < 200) {
            return;
        }

        LocalPlayerComponent localPlayerComp = entity.getComponent(LocalPlayerComponent.class);
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        if (localPlayerComp.isDead) return;

        EntityRef selectedItemEntity = inventory.itemSlots.get(localPlayerComp.selectedTool);
        attack(event.getTarget(), entity, selectedItemEntity);

        lastInteraction = System.currentTimeMillis();
        localPlayerComp.handAnimation = 0.5f;
        entity.saveComponent(localPlayerComp);
        event.consume();
    }

    private void attack(EntityRef target, EntityRef player, EntityRef selectedItemEntity) {
        // TODO: Should send an attack event to self, and another system common to all creatures should handle this
        int damage = 1;
        ItemComponent item = selectedItemEntity.getComponent(ItemComponent.class);
        if (item != null) {
            damage = item.baseDamage;

            BlockComponent blockComp = target.getComponent(BlockComponent.class);
            if (blockComp != null) {
                TeraBlock block = worldProvider.getBlock(blockComp.getPosition());
                if (item.getPerBlockDamageBonus().containsKey(block.getBlockFamily().getTitle())) {
                    damage += item.getPerBlockDamageBonus().get(block.getBlockFamily().getTitle());
                }
            }
        }
        target.send(new DamageEvent(damage, player));
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class})
    public void onFrobRequest(FrobButton event, EntityRef entity) {
        if (event.getState() != ButtonState.DOWN) {
            return;
        }

        LocalPlayerComponent localPlayerComp = entity.getComponent(LocalPlayerComponent.class);
        if (localPlayerComp.isDead) return;

        event.getTarget().send(new ActivateEvent(entity, entity));
        event.consume();
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class})
    public void onNextItem(ToolbarNextButton event, EntityRef entity) {
        LocalPlayerComponent localPlayerComp = localPlayer.getEntity().getComponent(LocalPlayerComponent.class);
        localPlayerComp.selectedTool = (localPlayerComp.selectedTool + 1) % 9;
        localPlayer.getEntity().saveComponent(localPlayerComp);
        event.consume();
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class})
    public void onPrevItem(ToolbarPrevButton event, EntityRef entity) {
        LocalPlayerComponent localPlayerComp = localPlayer.getEntity().getComponent(LocalPlayerComponent.class);
        localPlayerComp.selectedTool = (localPlayerComp.selectedTool - 1) % 9;
        if (localPlayerComp.selectedTool < 0) {
            localPlayerComp.selectedTool = 9 + localPlayerComp.selectedTool;
        }
        localPlayer.getEntity().saveComponent(localPlayerComp);
        event.consume();
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class})
    public void onSlotButton(ToolbarSlotButton event, EntityRef entity) {
        LocalPlayerComponent localPlayerComp = entity.getComponent(LocalPlayerComponent.class);
        localPlayerComp.selectedTool = event.getSlot();
        localPlayer.getEntity().saveComponent(localPlayerComp);
    }

    @ReceiveEvent(components = {LocalPlayerComponent.class, InventoryComponent.class})
    public void onUseItemRequest(UseItemButton event, EntityRef entity) {
        if (!event.isDown() || System.currentTimeMillis() - lastInteraction < 200) {
            return;
        }

        LocalPlayerComponent localPlayerComp = entity.getComponent(LocalPlayerComponent.class);
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        if (localPlayerComp.isDead) return;

        EntityRef selectedItemEntity = inventory.itemSlots.get(localPlayerComp.selectedTool);

        ItemComponent item = selectedItemEntity.getComponent(ItemComponent.class);
        if (item != null && item.usage != ItemComponent.UsageType.NONE) {
            useItem(entity, selectedItemEntity);
        } else {
            attack(event.getTarget(), entity, selectedItemEntity);
        }

        lastInteraction = System.currentTimeMillis();
        localPlayerComp.handAnimation = 0.5f;
        entity.saveComponent(localPlayerComp);
        event.consume();
    }

    private void useItem(EntityRef player, EntityRef item) {
        // TODO: Raytrace against entities too
        // TODO: Or should more information be included with events (surface normal?)
        // TODO: Do we even need the surface normal?
        RayBlockIntersection.Intersection blockIntersection = calcSelectedBlock();
        if (blockIntersection != null) {
            Vector3i centerPos = blockIntersection.getBlockPosition();

            item.send(new ActivateEvent(CoreRegistry.get(BlockEntityRegistry.class).getOrCreateEntityAt(centerPos), player, new Vector3f(playerCamera.getPosition()), new Vector3f(playerCamera.getViewingDirection()), blockIntersection.getSurfaceNormal()));
        } else {
            item.send(new ActivateEvent(player, new Vector3f(playerCamera.getPosition()), new Vector3f(playerCamera.getViewingDirection())));
        }
    }

    /**
     * Calculates the currently targeted block in front of the player.
     *
     * @return Intersection point of the targeted block
     */
    private RayBlockIntersection.Intersection calcSelectedBlock() {
        // TODO: Proper and centralised ray tracing support though world
        List<RayBlockIntersection.Intersection> inters = new ArrayList<RayBlockIntersection.Intersection>();

        Vector3f pos = new Vector3f(playerCamera.getPosition());

        int blockPosX, blockPosY, blockPosZ;

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    // Make sure the correct block positions are calculated relatively to the position of the player
                    blockPosX = (int) (pos.x + (pos.x >= 0 ? 0.5f : -0.5f)) + x;
                    blockPosY = (int) (pos.y + (pos.y >= 0 ? 0.5f : -0.5f)) + y;
                    blockPosZ = (int) (pos.z + (pos.z >= 0 ? 0.5f : -0.5f)) + z;

                    TeraBlock block = worldProvider.getBlock(blockPosX, blockPosY, blockPosZ);

                    // Ignore special blocks
                    if (block.isSelectionRayThrough()) {
                        continue;
                    }

                    // The ray originates from the "player's eye"
                    List<RayBlockIntersection.Intersection> iss = RayBlockIntersection.executeIntersection(worldProvider, blockPosX, blockPosY, blockPosZ, playerCamera.getPosition(), playerCamera.getViewingDirection());

                    if (iss != null) {
                        inters.addAll(iss);
                    }
                }
            }
        }

        /**
         * Calculated the closest intersection.
         */
        if (inters.size() > 0) {
            Collections.sort(inters);
            return inters.get(0);
        }

        return null;
    }

    private double calcBobbingOffset(float phaseOffset, float amplitude, float frequency) {
        return java.lang.Math.sin(bobFactor * frequency + phaseOffset) * amplitude;
    }


    @Override
    public void renderOpaque() {

    }

    @Override
    public void renderTransparent() {

    }

    @Override
    public void renderFirstPerson() {
    }

}
