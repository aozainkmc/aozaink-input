package com.aozainkmc.input.mixin.client;

import com.aozainkmc.input.client.TalismanCameraTransition;
import com.aozainkmc.input.client.BindingRitualCameraTransition;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void aozainkInput$applyTalismanTransition(
        BlockGetter level,
        Entity entity,
        boolean detached,
        boolean mirror,
        float partialTick,
        CallbackInfo callback
    ) {
        TalismanCameraTransition.CameraPose pose = TalismanCameraTransition.cameraPose(System.nanoTime());
        if (pose != null) {
            this.detached = true;
            setPosition(pose.position());
            setRotation(pose.yaw(), pose.pitch());
            return;
        }
        BindingRitualCameraTransition.CameraPose bindingPose =
            BindingRitualCameraTransition.cameraPose(System.nanoTime());
        if (bindingPose == null) return;
        this.detached = true;
        setPosition(bindingPose.position());
        setRotation(bindingPose.yaw(), bindingPose.pitch());
    }
}
