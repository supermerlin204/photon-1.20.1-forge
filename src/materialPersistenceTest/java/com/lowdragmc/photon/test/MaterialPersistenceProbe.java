package com.lowdragmc.photon.test;

import com.lowdragmc.lowdraglib2.editor.resource.*;
import com.lowdragmc.lowdraglib2.configurator.ui.*;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.*;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.*;
import com.lowdragmc.photon.gui.editor.resource.MaterialResource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;

/** Opt-in real-registry/file roundtrip; never loads or writes an artist's material. */
@Mod.EventBusSubscriber(modid="photon", value=Dist.CLIENT)
public final class MaterialPersistenceProbe {
    private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (done || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) || mc.getOverlay()!=null) return;
        done=true;
        Path report=Path.of(System.getProperty("photon.materialReport"));
        try {
            var dir=Files.createTempDirectory(report.getParent(), "material-roundtrip-").toFile();
            var resource=MaterialResource.INSTANCE;
            var provider=new FileResourceProvider<IMaterial>(resource.getResourceInstance(), dir);
            var material=new ShaderGraphMaterial(new BuiltinPath("test:graph"));
            var path=provider.createSubPath("test");
            if(!provider.addResource(path,material)) throw new AssertionError("initial save");
            var rowClass=Class.forName(ShaderGraphMaterial.class.getName()+"$VariableRow");
            var ctor=rowClass.getDeclaredConstructor(ShaderGraphMaterial.class,String.class,TypeHandle.class,Object.class);
            ctor.setAccessible(true);
            int[] notifications={0};
            for (var type : new TypeHandle[]{TypeHandles.FLOAT,TypeHandles.COLOR}) {
                Object value=type.equals(TypeHandles.COLOR)?Integer.valueOf(-65536):(Object)Float.valueOf(2.7f);
                var row=(IFieldValueConfigurable)ctor.newInstance(material,type.getIdentification(),type,type.getDefaultValue());
                var group=new ConfiguratorGroup();
                row.buildConfigurator(group);
                group.addEventListener(Configurator.CHANGE_EVENT,e->{
                    notifications[0]++;
                    if(!provider.addResource(path,material)) throw new AssertionError("edit save");
                });
                // No widget CHANGE event: exactly the callback path previously missed.
                row.setValue(value);
            }
            if(notifications[0]!=2) throw new AssertionError("setter did not persist each edit: "+notifications[0]);
            var reopened=new FileResourceProvider<IMaterial>(resource.getResourceInstance(),dir);
            var loaded=(ShaderGraphMaterial)reopened.getResource(path);
            if(!material.serializeAdditionalNBT(null).equals(loaded.serializeAdditionalNBT(null))) throw new AssertionError("file reload lost overrides");
            if(!material.getGraphPath().equals(loaded.getGraphPath())) throw new AssertionError("graph reference lost");
            WarmupProbe.verify();
            Files.writeString(report,"PASS: material persistence; offscreen warmup first tick, repeat, definition isolation, GL/FBO/viewport restoration, invalid input and empty FX");
        } catch(Throwable t) {
            Files.writeString(report,"FAIL: "+t); t.printStackTrace();
        } finally { mc.stop(); }
    }
}
