package com.lowdragmc.photon;

import com.lowdragmc.photon.command.EntityEffectCommand;
import com.lowdragmc.photon.command.FxLocationArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class PhotonCommonProxy {
    static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARG_TYPES =
            DeferredRegister.create(ForgeRegistries.COMMAND_ARGUMENT_TYPES, Photon.MOD_ID);
    static final RegistryObject<ArgumentTypeInfo<FxLocationArgument, ?>> FX_LOCATION_ARG_TYPE =
            ARG_TYPES.register("fx_location", () -> SingletonArgumentInfo.contextFree(FxLocationArgument::new));
    static final RegistryObject<ArgumentTypeInfo<EntityEffectCommand.AutoRotateType, ?>> AUTO_ROTATE_ARG_TYPE =
            ARG_TYPES.register("fx_auto_rotate", () ->
                    SingletonArgumentInfo.contextFree(EntityEffectCommand.AutoRotateType::new));

    public PhotonCommonProxy(IEventBus eventBus) {
        eventBus.addListener(this::commonSetup);
        ARG_TYPES.register(eventBus);
        PhotonNetworking.init();
        PhotonRegistries.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArgumentTypeInfos.registerByClass(FxLocationArgument.class, FX_LOCATION_ARG_TYPE.get());
            ArgumentTypeInfos.registerByClass(EntityEffectCommand.AutoRotateType.class, AUTO_ROTATE_ARG_TYPE.get());
        });
    }
}
