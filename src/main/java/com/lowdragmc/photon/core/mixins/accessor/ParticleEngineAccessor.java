package com.lowdragmc.photon.core.mixins.accessor;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Queue;

/**
 * @author KilaBash
 * @date 2023/6/5
 * @implNote ParticleEngineAccessor
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor
    Map<ParticleRenderType, Queue<Particle>> getParticles();

    @Accessor("spriteSets")
    Map<ResourceLocation, SpriteSet> getSpriteSets();
}
