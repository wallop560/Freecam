package net.xolt.freecam.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.Packet;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.xolt.freecam.config.ModConfig;

import java.util.Optional;
import java.util.UUID;

import static net.xolt.freecam.Freecam.MC;

public class FreeCamera extends ClientPlayerEntity {

    private static final ClientPlayNetworkHandler NETWORK_HANDLER = new ClientPlayNetworkHandler(MC, MC.currentScreen, MC.getNetworkHandler().getConnection(), MC.getCurrentServerEntry(), new GameProfile(UUID.randomUUID(), "FreeCamera"), MC.getTelemetryManager().createWorldSession(false, null)) {
        @Override
        public void sendPacket(Packet<?> packet) {
        }
    };

    public FreeCamera(int id) {
        this(id, new FreecamPosition(MC.player));
    }

    public FreeCamera(int id, FreecamPosition position) {
        super(MC, MC.world, NETWORK_HANDLER, MC.player.getStatHandler(), MC.player.getRecipeBook(), false, false);

        setId(id);
        applyPosition(position);
        getAbilities().flying = true;
        input = new KeyboardInput(MC.options);
    }

    public void applyPosition(FreecamPosition position) {
        refreshPositionAndAngles(position.x, position.y, position.z, position.yaw, position.pitch);
        super.setPose(position.pose);
        renderPitch = getPitch();
        renderYaw = getYaw();
        lastRenderPitch = renderPitch; // Prevents camera from rotating upon entering freecam.
        lastRenderYaw = renderYaw;
    }

    // Mutate the position and rotation based on perspective
    // If checkCollision is true, move as far as possible without colliding
    // Return an optional error message
    public Optional<Text> applyPerspective(ModConfig.Perspective perspective, boolean checkCollision) {
        FreecamPosition position = new FreecamPosition(this);
        boolean successful = true;

        switch (perspective) {
            case INSIDE:
                // No-op
                break;
            case FIRST_PERSON:
                // Move just in front of the player's eyes
                successful = moveForwardUntilCollision(position, 0.4, checkCollision);
                break;
            case THIRD_PERSON_MIRROR:
                // Invert the rotation and fallthrough into the THIRD_PERSON case
                position.mirrorRotation();
            case THIRD_PERSON:
                // Move back as per F5 mode
                successful = moveForwardUntilCollision(position, -4.0, checkCollision);
                break;
        }

        return successful ? Optional.empty() : Optional.of(Text.translatable("msg.freecam.collisionError").formatted(Formatting.RED));
    }

    // Move FreeCamera forward using FreecamPosition.moveForward.
    // If checkCollision is true, stop moving forward before hitting a collision.
    // Return true if successfully able to move.
    private boolean moveForwardUntilCollision(FreecamPosition position, double distance, boolean checkCollision) {
        if (!checkCollision) {
            position.moveForward(distance);
            applyPosition(position);
            return true;
        }
        return moveForwardUntilCollision(position, distance);
    }

    // Same as above, but always check collision.
    private boolean moveForwardUntilCollision(FreecamPosition position, double maxDistance) {
        boolean negative = maxDistance < 0;
        maxDistance = negative ? -1 * maxDistance : maxDistance;
        double increment = 0.1;

        // Move forward by increment until we reach maxDistance or hit a collision
        for (double distance = 0.0; distance < maxDistance; distance += increment) {
            FreecamPosition oldPosition = new FreecamPosition(this);

            position.moveForward(negative ? -1 * increment : increment);
            applyPosition(position);

            if (!wouldPoseNotCollide(getPose())) {
                // Revert to last non-colliding position and return whether we were unable to move at all
                applyPosition(oldPosition);
                return distance > 0;
            }
        }

        return true;
    }



    public void spawn() {
        if (clientWorld != null) {
            clientWorld.addEntity(getId(), this);
        }
    }

    public void despawn() {
        if (clientWorld != null && clientWorld.getEntityById(getId()) != null) {
            clientWorld.removeEntity(getId(), RemovalReason.DISCARDED);
        }
    }

    // Prevents fall damage sound when FreeCamera touches ground with noClip disabled.
    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
    }

    // Needed for hand swings to be shown in freecam since the player is replaced by FreeCamera in HeldItemRenderer.renderItem()
    @Override
    public float getHandSwingProgress(float tickDelta) {
        return MC.player.getHandSwingProgress(tickDelta);
    }

    // Needed for item use animations to be shown in freecam since the player is replaced by FreeCamera in HeldItemRenderer.renderItem()
    @Override
    public int getItemUseTimeLeft() {
        return MC.player.getItemUseTimeLeft();
    }

    // Also needed for item use animations to be shown in freecam.
    @Override
    public boolean isUsingItem() {
        return MC.player.isUsingItem();
    }

    // Prevents slow down from ladders/vines.
    @Override
    public boolean isClimbing() {
        return false;
    }

    // Prevents slow down from water.
    @Override
    public boolean isTouchingWater() {
        return false;
    }

    // Makes night vision apply to FreeCamera when Iris is enabled.
    @Override
    public StatusEffectInstance getStatusEffect(StatusEffect effect) {
        return MC.player.getStatusEffect(effect);
    }

    // Prevents pistons from moving FreeCamera when noClip is enabled.
    @Override
    public PistonBehavior getPistonBehavior() {
        return ModConfig.INSTANCE.noClip ? PistonBehavior.IGNORE : PistonBehavior.NORMAL;
    }

    // Prevents pose from changing when clipping through blocks.
    @Override
    public void setPose(EntityPose pose) {
        if (pose.equals(EntityPose.STANDING) || (pose.equals(EntityPose.CROUCHING) && !getPose().equals(EntityPose.STANDING))) {
            super.setPose(pose);
        }
    }

    @Override
    public void tickMovement() {
        noClip = ModConfig.INSTANCE.noClip;
        if (ModConfig.INSTANCE.flightMode.equals(ModConfig.FlightMode.DEFAULT)) {
            getAbilities().setFlySpeed(0);
            Motion.doMotion(this, ModConfig.INSTANCE.horizontalSpeed, ModConfig.INSTANCE.verticalSpeed);
        } else {
            getAbilities().setFlySpeed((float) ModConfig.INSTANCE.verticalSpeed / 10);
        }
        super.tickMovement();
        getAbilities().flying = true;
        onGround = false;
    }
}
