package com.lowdragmc.photon.test;

import com.lowdragmc.kilagraph.rendertype.nodes.fragment.FragmentBaseColorBlock;
import com.lowdragmc.kilagraph.rendertype.nodes.scene.ScreenPositionNode;
import com.lowdragmc.kilagraph.rendertype.nodes.input.UVNode;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.*;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.SpawnFlags;
import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.photon.client.shadergraph.ShaderGraph;
import com.lowdragmc.photon.client.shadergraph.runtime.ShaderGraphRuntime;
import com.lowdragmc.photon.gui.editor.resource.ShaderGraphResource;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.ShaderGraphMaterial;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;

/** Image rows stay top-origin; screen samples alone use bottom-origin framebuffer UVs. */
final class PreviewOrientationProbe {
    static void verify() {
        var mc = Minecraft.getInstance();
        var resources = ShaderGraphResource.INSTANCE;
        var provider = new BuiltinResourceProvider<net.minecraft.nbt.CompoundTag>("orientation-test", resources.getResourceInstance());
        resources.getResourceInstance().addBuiltinProvider(provider);
        var target = new TextureTarget(32, 32, true, Minecraft.ON_OSX);
        try (var state = com.lowdragmc.lowdraglib2.client.UIRenderStateScope.capture();
             var surface = com.lowdragmc.lowdraglib2.gui.ui.rendering.UISurface.push(
                     new com.lowdragmc.lowdraglib2.gui.ui.rendering.RenderTargetSurface(target))) {
            RenderSystem.backupProjectionMatrix();
            var model = RenderSystem.getModelViewStack();
            model.pushPose();
            model.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 32, 32, 0, -100, 100),
                    com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            try {
                for (boolean screen : new boolean[]{true, false}) {
                    var graph = new ShaderGraph();
                    var node = graph.graphModel.createNodeWithType(CustomNodeModelImpl.class, "uv", new Vector2f(), null,
                            n -> n.initCustomNode(screen ? new ScreenPositionNode() : new UVNode()), SpawnFlags.DEFAULT);
                    var context = (ContextNodeModel) graph.getFragmentStageModel();
                    var block = new CustomBlockNodeModelImpl();
                    block.setGraphModel(graph.graphModel);
                    block.setSpawnFlags(SpawnFlags.DEFAULT);
                    block.initCustomNode(new FragmentBaseColorBlock());
                    block.setContextNodeModel(context);
                    block.onCreateNode();
                    context.insertBlock(block, -1);
                    graph.graphModel.createWire(block.getInputsById().get("color"), node.getOutputsById().get("out"));
                    var path = provider.createSubPath(screen ? "screen" : "mesh");
                    provider.addResource(path, resources.serializeGraph(graph));
                    try {
                        var material = new ShaderGraphMaterial(path);
                        target.setClearColor(0, 0, 0, 0);
                        target.clear(Minecraft.ON_OSX);
                        target.bindWrite(true);
                        var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                        material.preview.draw(graphics, 0, 0, 0, 0, 32, 32, 0);
                        graphics.flush();
                        int top = green(8, 24), bottom = green(8, 8);
                        // The menu's lightmap/fog may dim the quad; compare direction, not brightness.
                        if (screen ? top <= bottom + 3 : bottom <= top + 3)
                            throw new AssertionError("UV orientation screen=" + screen + " top=" + top + " bottom=" + bottom);
                        var runtimeSource = ShaderGraphRuntime.get(path).getCompiled().fragmentSource();
                        if (screen && !runtimeSource.contains("gl_FragCoord")) throw new AssertionError("runtime screen UV changed");
                    } finally { ShaderGraphRuntime.invalidate(path); }
                }
                // Asymmetric real texture, not only a graph that displays UV coordinates.
                var pixels = new com.mojang.blaze3d.platform.NativeImage(2, 2, false);
                for (int x = 0; x < 2; x++) {
                    pixels.setPixelRGBA(x, 0, 0xff000000);
                    pixels.setPixelRGBA(x, 1, 0xff00ff00);
                }
                var texture = new net.minecraft.client.renderer.texture.DynamicTexture(pixels);
                var textureId = mc.getTextureManager().register("photon_orientation_probe", texture);
                try {
                    var material = new com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial(textureId);
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                    material.preview.draw(graphics, 0, 0, 0, 0, 32, 32, 0);
                    graphics.flush();
                    // Input row 0 is black, row 1 green: UI must show black ABOVE green.
                    if (green(8, 8) <= green(8, 24) + 3) throw new AssertionError("texture preview orientation");
                } finally { mc.getTextureManager().release(textureId); }
                if (GL11.glGetError() != 0) throw new AssertionError("preview GL error");
            } finally {
                model.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
        } finally {
            target.destroyBuffers();
            resources.getResourceInstance().removeBuiltinProvider(provider);
        }
    }
    private static int green(int x, int y) {
        var pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return pixel.get(1) & 255;
    }
}
