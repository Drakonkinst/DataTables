package io.github.drakonkinst.datatables;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class DataTablesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, ((payload, context) -> {
            DataTableRegistry.INSTANCE.updateContents(payload.dataTables());
            DataTables.LOGGER.info("Loaded " + DataTableRegistry.INSTANCE.getDataTableIds().size()
                    + " data tables");
        }));
    }
}