package com.lowdragmc.photon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;

@EventBusSubscriber(modid = Photon.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public class PhotonCommonListeners {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        List<LiteralArgumentBuilder<CommandSourceStack>> commands = ServerCommands.createServerCommands();
        commands.forEach(dispatcher::register);
    }
}
