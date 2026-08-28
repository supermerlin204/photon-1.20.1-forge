package com.lowdragmc.photon.client.gameobject.emitter.renderpipeline;

import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.core.mixins.accessor.ParticleEngineAccessor;
import com.lowdragmc.lowdraglib2.client.scene.ParticleRenderTypeRegistry;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * @author KilaBash
 * @date 2023/6/11
 * @implNote ParticleQueueRenderType
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ParticleQueueRenderType implements ParticleRenderType {

    public static final ParticleQueueRenderType OPAQUE_QUEUE = new ParticleQueueRenderType(false);
    public static final ParticleQueueRenderType TRANSLUCENT_QUEUE = new ParticleQueueRenderType(true);

    static {
        // Forge 1.20.1 has no ParticleRenderType.isTranslucent() contract. Register the custom
        // queue explicitly so LDLib2's scene renderer always sends it through the translucent pass,
        // including environments where reflective discovery is restricted by a class loader.
        ParticleRenderTypeRegistry.registerTranslucent(TRANSLUCENT_QUEUE);
    }

    public final RenderPassPipeline pipeline = new RenderPassPipeline();

    public final boolean isTranslucent;

    private ParticleQueueRenderType(boolean isTranslucent) {
        this.isTranslucent = isTranslucent;
    }

    public boolean isTranslucent() {
        return isTranslucent;
    }

    @Override
    public void begin(BufferBuilder bufferBuilder, TextureManager textureManager) {
        RenderPassPipeline.beginCollecting(pipeline);
    }

    @Override
    public void end(Tesselator tesselator) {
        RenderPassPipeline.finishCollecting(pipeline);
    }

    /**
     * Whether the currently rendering particle source still holds particles for this queue.
     *
     * <p>Used to answer "is another Photon build coming this frame?": the opaque queue always renders
     * before the translucent one, but by the time the opaque pipeline builds, the translucent pipeline
     * has not been fed yet — the pending particles are only visible on the source that owns them
     * (the editor scene's manager while it renders, otherwise the vanilla engine).
     */
    public boolean hasQueuedParticles() {
        var editorScene = PhotonParticleManager.getRenderingManager();
        var byRenderType = editorScene != null
                ? editorScene.particlesByRenderType()
                : ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine).getParticles();
        var queue = byRenderType.get(this);
        return queue != null && !queue.isEmpty();
    }
}
