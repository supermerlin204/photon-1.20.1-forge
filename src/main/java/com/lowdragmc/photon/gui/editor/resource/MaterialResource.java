package com.lowdragmc.photon.gui.editor.resource;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.Resource;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class MaterialResource extends Resource<IMaterial> {
    public static final MaterialResource INSTANCE = new MaterialResource();

    @Override
    public void buildBuiltin(BuiltinResourceProvider<IMaterial> provider) {
        provider.addResource("missing", IMaterial.MISSING);
        provider.addResource("block_atlas", BlockTextureSheetMaterial.INSTANCE);

        addBuiltinShaderMaterial(provider, "circle");
        addBuiltinTextureMaterial(provider, "kila_tail");
        addBuiltinTextureMaterial(provider, "laser");
        addBuiltinTextureMaterial(provider, "smoke");
        addBuiltinTextureMaterial(provider, "thaumcraft");
        addBuiltinTextureMaterial(provider, "ring");
    }

    private void addVanillaTextureMaterial(BuiltinResourceProvider<IMaterial> builtin, String name) {
        builtin.addResource(name, new TextureMaterial(ResourceLocation.parse("textures/particle/%s.png".formatted(name))));
    }

    private void addBuiltinTextureMaterial(BuiltinResourceProvider<IMaterial> builtin, String name) {
        builtin.addResource(name, new TextureMaterial(ResourceLocation.parse("photon:textures/particle/%s.png".formatted(name))));
    }

    private void addBuiltinShaderMaterial(BuiltinResourceProvider<IMaterial> builtin, String name) {
        builtin.addResource(name, new CustomShaderMaterial(ResourceLocation.parse("photon:%s".formatted(name))));
    }

    @Override
    public IGuiTexture getIcon() {
        return Icons.MATERIAL;
    }

    @Override
    public String getName() {
        return "material";
    }

    @Nullable
    @Override
    public Tag serializeResource(IMaterial material, HolderLookup.Provider provider) {
        return material.serializeWrapper();
    }

    @Override
    public IMaterial deserializeResource(Tag tag, HolderLookup.Provider provider) {
        return IMaterial.deserializeWrapper(tag);
    }

    /**
     * Fired (with the clicked path) whenever a material tile is selected in ANY container of this
     * resource — set transiently by {@code IMaterialConfigurator}'s selector dialog to receive the
     * chosen <em>path</em> (the stock selector callback only reports the value), cleared on close.
     */
    @Nullable
    private java.util.function.Consumer<IResourcePath> pathSelectListener;

    public void setPathSelectListener(@Nullable java.util.function.Consumer<IResourcePath> listener) {
        this.pathSelectListener = listener;
    }

    @Override
    public ResourceProviderContainer<IMaterial> createResourceProviderContainer(IResourceProvider<IMaterial> provider) {
        var container = new ResourceProviderContainer<>(provider) {
            @Override
            public void selectResource(IResourcePath resourcePath) {
                super.selectResource(resourcePath);
                if (pathSelectListener != null && resourcePath != null && provider.hasResource(resourcePath)) {
                    pathSelectListener.accept(resourcePath);
                }
            }
        };
        container.setUiSupplier(path -> new UIElement().layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightPercent(100);
                }).style(style -> style.backgroundTexture(
                        com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture.of(() -> {
                            var material = provider.getResource(path);
                            return material == null ? IGuiTexture.MISSING_TEXTURE : material.preview();
                        }))));
        container.setOnEdit((c, path) -> {
            // Dirty tracking belongs to this provider, so inspect the instance it actually owns.
            var material = provider.getResource(path);
            if (material == null) return;
            // The inspector owns this editing instance. A provider refresh may replace its cached
            // instance, and the browser may dispose this container when navigating directories.
            // Queuing only a path would then save a different object (or never flush at all).
            // Commit the edited instance before a later tick can reload the file instead.
            var lastSaved = new java.util.concurrent.atomic.AtomicReference<net.minecraft.nbt.CompoundTag>();
            Runnable saveEdit = () -> {
                if (!provider.canEdit(path) || !provider.hasResource(path)) return;
                // A type-driven setter and its ordinary widget may both notify for one edit.
                var snapshot = material.serializeWrapper();
                if (snapshot == null) {
                    com.lowdragmc.photon.Photon.LOGGER.error("Failed to serialize edited material {}", path);
                    return;
                }
                if (snapshot.equals(lastSaved.get())) return;
                if (provider.addResource(path, material)) {
                    lastSaved.set(snapshot.copy());
                    provider.getResourceInstance().clearCache();
                    c.reloadSpecificResource(path);
                } else {
                    com.lowdragmc.photon.Photon.LOGGER.error("Failed to save edited material {}", path);
                }
            };
            c.getEditor().inspectorView.inspect(material, configurator -> saveEdit.run(), null, saveEdit);
        });

        container.setOnDragProvider(UIResourceMaterial::new);

        if (provider.supportAdd()) {
            container.setOnCreateMenu((c, m) -> m.branch(Icons.ADD_FILE, "ldlib.gui.editor.menu.add_resource", menu -> {
                for (var holder : PhotonRegistries.MATERIALS) {
                    var name = holder.annotation().name();
                    if (name.equals("missing") || name.equals("block_atlas") || name.equals("ui_resource_material")) continue;
                    menu.leaf(name, () -> {
                        var material = holder.value().get();
                        c.addNewResource(material);
                    });
                }
            }));
        }
        return container;
    }
}
