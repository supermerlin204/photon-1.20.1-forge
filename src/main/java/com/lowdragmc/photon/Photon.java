package com.lowdragmc.photon;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.PhotonClientProxy;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Mod(Photon.MOD_ID)
public class Photon {
    public static final String MOD_ID = "photon";
    public static final String NAME = "Photon";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public Photon() {
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        Photon.init();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PhotonConfig.CONFIG_SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new PhotonClientProxy(eventBus);
        } else {
            new PhotonCommonProxy(eventBus);
        }
    }

    public static void init() {
        LOGGER.info("{} is initializing on platform: {}", NAME, Platform.platformName());
        if (new File(LDLib2.getAssetsDir(), "photon").mkdirs()) {
            LOGGER.info("Created photon assets folder");
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** @see com.lowdragmc.photon.client.compat.iris.IrisCompat */
    public static boolean isUsingShaderPack() {
        return FMLEnvironment.dist == Dist.CLIENT && IrisCompat.isUsingShaderPack();
    }
}
