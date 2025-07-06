package io.github.drakonkinst.datatables;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
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
    private static final DataTableRegistry INSTANCE = new DataTableRegistry();

    public static Identifier id(String id) {
        return Identifier.of(MOD_ID, id);
    }

    public static DataTable get(Identifier id) {
        return INSTANCE.get(id);
    }

    public static Optional<DataTable> getOptional(Identifier id) {
        return INSTANCE.getOptional(id);
    }

    public static boolean contains(Identifier id) {
        return INSTANCE.contains(id);
    }

    public static Collection<Identifier> getDataTableIds() {
        return INSTANCE.getDataTableIds();
    }

    // Should only be called internally
    public static void syncDataTables(Map<Identifier, DataTable> dataTables) {
        INSTANCE.syncDataTables(dataTables);
    }

    @Override
    public void onInitialize() {
        // Create the registry
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(DataTableRegistry.ID, registries -> {
                    INSTANCE.setRegistries(registries);
                    return INSTANCE;
                });

        // Resolve tags on load
        CommonLifecycleEvents.TAGS_LOADED.register(((registries, client) -> {
                INSTANCE.resolve();
        }));

        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);

        // Add data tables to data pack syncing
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(((player, joined) -> {
            if (!INSTANCE.isResolved()) {
                LOGGER.error("Tags are not resolved on server side");
                return;
            }
            ServerPlayNetworking.send(player, INSTANCE.createSyncPacket());
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