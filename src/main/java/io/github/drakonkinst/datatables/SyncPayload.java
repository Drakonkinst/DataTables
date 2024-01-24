package io.github.drakonkinst.datatables;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncPayload(Map<Identifier, DataTable> dataTables) implements CustomPayload {

    public static final Id<SyncPayload> ID = new CustomPayload.Id<>(DataTables.id("sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = CustomPayload.codecOf(
            SyncPayload::write, SyncPayload::new);

    private SyncPayload(PacketByteBuf buf) {
        this(new HashMap<>());
        int numDataTables = buf.readVarInt();
        for (int i = 0; i < numDataTables; ++i) {
            Identifier id = buf.readIdentifier();
            DataTable dataTable = DataTable.fromPacket(buf);
            dataTables.put(id, dataTable);
        }
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(dataTables.size());
        dataTables.forEach((id, dataTable) -> {
            buf.writeIdentifier(id);
            DataTable.writePacket(dataTable, buf);
        });
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
