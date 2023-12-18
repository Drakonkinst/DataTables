package io.github.drakonkinst.datatables;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataTables implements ModInitializer {

    public static final String MOD_ID = "datatables";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier DATA_TABLE_PACKET_ID = DataTables.id("data_table");

    public static Identifier id(String id) {
        return new Identifier(MOD_ID, id);
    }

    @Override
    public void onInitialize() {
        // Create the registry
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new DataTableRegistry());

        // Resolve tags on load
        CommonLifecycleEvents.TAGS_LOADED.register(((registries, client) -> {
            DataTableRegistry.INSTANCE.resolveTags();
        }));

        // Add data tables to data pack syncing
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(((player, joined) -> {
            if (!DataTableRegistry.INSTANCE.areTagsResolved()) {
                LOGGER.error("Tags are not resolved on server side");
                return;
            }

            LOGGER.info("Syncing " + DataTableRegistry.INSTANCE.getDataTableIds().size()
                    + " data tables");

            PacketByteBuf buf = PacketByteBufs.create();
            DataTableRegistry.INSTANCE.writePacket(buf);
            ServerPlayNetworking.send(player, DATA_TABLE_PACKET_ID, buf);
        }));

        // Register command
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> DataTables.createCommands(dispatcher));
    }

    private static void createCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        TableCommand.register(dispatcher);
    }
}