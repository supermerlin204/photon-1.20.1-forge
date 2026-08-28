package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import com.lowdragmc.lowdraglib2.client.UIRenderStateScope;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.photon.core.mixins.accessor.BlendModeAccessor;
import com.lowdragmc.photon.core.mixins.accessor.ShaderInstanceAccessor;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.HolderLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * @author KilaBash
 * @date 2023/6/1
 * @implNote ShaderInstanceMaterial
 */
@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
public abstract class ShaderInstanceMaterial implements IMaterial {

    private static final BlendMode PREVIEW_BLEND = new BlendMode(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ZERO, GL14.GL_FUNC_ADD);

    public final ShaderTexture preview = new ShaderTexture();

    abstract public ShaderInstance getShader(MaterialContext context);

    public void setupUniform(MaterialContext context) {
    }

    @Override
    public ShaderInstance begin(MaterialContext context) {
        setupUniform(context);
        return getShader(context);
    }

    @Override
    public final CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return IMaterial.super.serializeNBT(provider);
    }

    @Override
    public IGuiTexture preview() {
        return preview;
    }

    public class ShaderTexture implements IGuiTexture {

        @Override
        public void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width, float height, float partialTicks) {
            graphics.flush();

            // 1.20.1 keeps the last ShaderInstance blend mode in a static cache. Restore that cache
            // together with LDLib's GL snapshot, otherwise a focused button can change whether the
            // next material preview reapplies blending at all.
            var previousBlendCache = BlendModeAccessor.photon$getLastApplied();
            try {
                try (var state = UIRenderStateScope.capture()) {
                    drawPreview(graphics, x, y, width, height);
                }
            } finally {
                BlendModeAccessor.photon$setLastApplied(previousBlendCache);
            }
        }

        private void drawPreview(GuiGraphics graphics, float x, float y, float width, float height) {
            float imageU = 0;
            float imageV = 0;
            float imageWidth = 1;
            float imageHeight = 1;
            ShaderInstance shader = null;
            boolean began = false;
            var lightTexture = Minecraft.getInstance().gameRenderer.lightTexture();
            BlendMode materialBlend = null;
            try {
                shader = begin(MaterialContext.PREVIEW);
                began = true;
                var shaderAccessor = (ShaderInstanceAccessor) shader;
                materialBlend = shaderAccessor.photon$getBlend();
                shaderAccessor.photon$setBlend(PREVIEW_BLEND);
                lightTexture.turnOnLightLayer();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                var mat = graphics.pose().last().pose();
                var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

                buffer.vertex(mat, x, y + height, 0).color(-1).uv(imageU, imageV + imageHeight)
                        .uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
                buffer.vertex(mat, x + width, y + height, 0).color(-1).uv(imageU + imageWidth, imageV + imageHeight)
                        .uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
                buffer.vertex(mat, x + width, y, 0).color(-1).uv(imageU + imageWidth, imageV)
                        .uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
                buffer.vertex(mat, x, y, 0).color(-1).uv(imageU, imageV)
                        .uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();

                var previewShader = shader;
                RenderSystem.setShader(() -> previewShader);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                // Resource grids draw several previews between LDLib UI draws that modify GL blending
                // directly. Force ShaderInstance.apply() to reapply our preview blend instead of trusting
                // BlendMode.lastApplied, whose cached value may no longer match the actual GL state.
                BlendModeAccessor.photon$setLastApplied(null);
                BufferUploader.drawWithShader(buffer.end());
            } finally {
                if (shader != null && materialBlend != null) {
                    ((ShaderInstanceAccessor) shader).photon$setBlend(materialBlend);
                }
                if (began) end(MaterialContext.PREVIEW);
                lightTexture.turnOffLightLayer();
            }
        }
    }

}
