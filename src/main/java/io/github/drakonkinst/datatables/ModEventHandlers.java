package io.github.drakonkinst.datatables;

import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class ModEventHandlers {

    public static final Identifier DATA_TABLE_PACKET_ID = DataTables.id("data_table");

    public static void initialize() {
        CommonLifecycleEvents.TAGS_LOADED.register(((registries, client) -> {
            DataTableRegistry.INSTANCE.resolveTags();
        }));

        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(((player, joined) -> {
            if (!DataTableRegistry.INSTANCE.areTagsResolved()) {
                DataTables.LOGGER.error("Tags are not resolved on server side");
                return;
            }

            DataTables.LOGGER.info("Syncing " + DataTableRegistry.INSTANCE.getDataTableIds().size()
                    + " data tables");

            PacketByteBuf buf = PacketByteBufs.create();
            DataTableRegistry.INSTANCE.writePacket(buf);
            ServerPlayNetworking.send(player, DATA_TABLE_PACKET_ID, buf);
        }));
    }

    private ModEventHandlers() {}
}
