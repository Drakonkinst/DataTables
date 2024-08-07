package io.github.drakonkinst.datatables;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataTables implements ModInitializer {

    public static final String MOD_ID = "datatables";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String id) {
        return Identifier.of(MOD_ID, id);
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

        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);

        // Add data tables to data pack syncing
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(((player, joined) -> {
            if (!DataTableRegistry.INSTANCE.areTagsResolved()) {
                LOGGER.error("Tags are not resolved on server side");
                return;
            }
            ServerPlayNetworking.send(player, DataTableRegistry.INSTANCE.createSyncPacket());
        }));

        // Register command
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> DataTables.createCommands(dispatcher,
                        registryAccess));
    }

    private static void createCommands(CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess) {
        TableCommand.register(dispatcher, registryAccess);
    }
}