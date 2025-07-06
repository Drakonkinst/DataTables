package io.github.drakonkinst.datatables;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncPayload(Map<Identifier, DataTable> dataTables) implements CustomPayload {

    public static final Id<SyncPayload> ID = new CustomPayload.Id<>(DataTables.id("sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.map(HashMap::new, Identifier.PACKET_CODEC, DataTable.PACKET_CODEC),
            SyncPayload::dataTables, SyncPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
