package com.lowdragmc.photon;

import com.lowdragmc.photon.command.BlockEffectCommand;
import com.lowdragmc.photon.command.EntityEffectCommand;
import com.lowdragmc.photon.command.RemoveBlockEffectCommand;
import com.lowdragmc.photon.command.RemoveEntityEffectCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PhotonNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(Photon.id("main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        int id = 0;
        CHANNEL.messageBuilder(BlockEffectCommand.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlockEffectCommand::encode)
                .decoder(BlockEffectCommand::decodePacket)
                .consumerMainThread(BlockEffectCommand::execute)
                .add();
        CHANNEL.messageBuilder(EntityEffectCommand.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EntityEffectCommand::encode)
                .decoder(EntityEffectCommand::decodePacket)
                .consumerMainThread(EntityEffectCommand::execute)
                .add();
        CHANNEL.messageBuilder(RemoveBlockEffectCommand.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoveBlockEffectCommand::encode)
                .decoder(RemoveBlockEffectCommand::decodePacket)
                .consumerMainThread(RemoveBlockEffectCommand::execute)
                .add();
        CHANNEL.messageBuilder(RemoveEntityEffectCommand.class, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoveEntityEffectCommand::encode)
                .decoder(RemoveEntityEffectCommand::decodePacket)
                .consumerMainThread(RemoveEntityEffectCommand::execute)
                .add();
    }

    public static void sendToAllPlayers(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos pos, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(pos.x, pos.z)), packet);
    }
}
