package io.github.drakonkinst.datatables;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class DataTable {

    public static void writePacket(DataTable table, PacketByteBuf buf) {
        buf.writeEnumConstant(table.type);
        buf.writeVarInt(table.entryTable.defaultReturnValue());
        buf.writeVarInt(table.entryTable.size());
        table.entryTable.forEach((key, value) -> {
            buf.writeIdentifier(key);
            buf.writeVarInt(value);
        });
    }

    public static DataTable fromPacket(PacketByteBuf buf) {
        DataTableType type = buf.readEnumConstant(DataTableType.class);
        int defaultValue = buf.readVarInt();
        int size = buf.readVarInt();
        Object2IntMap<Identifier> entryTable = new Object2IntArrayMap<>();
        for (int i = 0; i < size; ++i) {
            Identifier key = buf.readIdentifier();
            int value = buf.readVarInt();
            entryTable.put(key, value);
        }
        return new DataTable(type, defaultValue, entryTable, null);
    }

    private final DataTableType type;
    private final Object2IntMap<Identifier> entryTable;
    private Object2IntMap<Identifier> unresolvedTags;

    public DataTable(DataTableType type, int defaultValue, Object2IntMap<Identifier> entryTable,
            Object2IntMap<Identifier> unresolvedTags) {
        this.type = type;
        this.entryTable = entryTable;
        this.unresolvedTags = unresolvedTags;
        this.entryTable.defaultReturnValue(defaultValue);
    }

    public void merge(Object2IntMap<Identifier> otherEntryTable,
            Object2IntMap<Identifier> otherUnresolvedTags) {
        for (Object2IntMap.Entry<Identifier> entry : otherEntryTable.object2IntEntrySet()) {
            entryTable.put(entry.getKey(), entry.getIntValue());
        }
        if (otherUnresolvedTags != null) {
            if (unresolvedTags == null) {
                unresolvedTags = otherUnresolvedTags;
            } else {
                unresolvedTags.putAll(otherUnresolvedTags);
            }
        }
    }

    public List<Identifier> resolveTags() {
        if (unresolvedTags == null || unresolvedTags.isEmpty()) {
            return null;
        }

        if (type == DataTableType.BLOCK) {
            return resolveBlockTags();
        }
        if (type == DataTableType.ENTITY) {
            return resolveEntityTypeTags();
        }
        if (type == DataTableType.ITEM) {
            return resolveItemTags();
        }
        return unresolvedTags.keySet().stream().toList();
    }

    private List<Identifier> resolveBlockTags() {
        Registries.BLOCK.streamTags().forEach(tagEntry -> {
            Identifier tagId = tagEntry.getTag().id();
            if (!unresolvedTags.containsKey(tagId)) {
                return;
            }

            int value = unresolvedTags.getInt(tagId);
            tagEntry.getStorage().ifRight(list -> list.forEach(blockRegistryEntry -> {
                Identifier blockId = Registries.BLOCK.getId(blockRegistryEntry.value());
                entryTable.putIfAbsent(blockId, value);
            }));
            unresolvedTags.removeInt(tagId);
        });

        List<Identifier> failedTags = null;
        if (!unresolvedTags.isEmpty()) {
            failedTags = unresolvedTags.keySet().stream().toList();
        }
        unresolvedTags = null;
        return failedTags;
    }

    private List<Identifier> resolveEntityTypeTags() {
        Registries.ENTITY_TYPE.streamTags().forEach(tagEntry -> {
            Identifier tagId = tagEntry.getTag().id();
            if (!unresolvedTags.containsKey(tagId)) {
                return;
            }

            int value = unresolvedTags.getInt(tagId);
            tagEntry.getStorage().ifRight(list -> list.forEach(blockRegistryEntry -> {
                Identifier entityId = Registries.ENTITY_TYPE.getId(blockRegistryEntry.value());
                entryTable.putIfAbsent(entityId, value);
            }));
            unresolvedTags.removeInt(tagId);
        });

        List<Identifier> failedTags = null;
        if (!unresolvedTags.isEmpty()) {
            failedTags = unresolvedTags.keySet().stream().toList();
        }
        unresolvedTags = null;
        return failedTags;
    }

    private List<Identifier> resolveItemTags() {
        Registries.ITEM.getTags().forEach(tagEntry -> {
            Identifier tagId = tagEntry.getTag().id();
            if (!unresolvedTags.containsKey(tagId)) {
                return;
            }

            int value = unresolvedTags.getInt(tagId);
            tagEntry.getStorage().ifRight(list -> list.forEach(blockRegistryEntry -> {
                Identifier itemId = Registries.ITEM.getId(blockRegistryEntry.value());
                entryTable.putIfAbsent(itemId, value);
            }));
            unresolvedTags.removeInt(tagId);
        });

        List<Identifier> failedTags = null;
        if (!unresolvedTags.isEmpty()) {
            failedTags = unresolvedTags.keySet().stream().toList();
        }
        unresolvedTags = null;
        return failedTags;
    }

    public boolean contains(Identifier id) {
        return entryTable.containsKey(id);
    }

    public boolean contains(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return contains(id);
    }

    public boolean contains(Entity entity) {
        Identifier id = EntityType.getId(entity.getType());
        return contains(id);
    }

    public boolean contains(BlockState blockState) {
        Identifier id = Registries.BLOCK.getId(blockState.getBlock());
        return contains(id);
    }

    public int getInt(Identifier id) {
        return entryTable.getInt(id);
    }

    public int getIntForItem(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return this.getInt(id);
    }

    public int getIntForEntity(Entity entity) {
        Identifier id = EntityType.getId(entity.getType());
        return this.getInt(id);
    }

    public int getIntForBlock(BlockState blockState) {
        Identifier id = Registries.BLOCK.getId(blockState.getBlock());
        return this.getInt(id);
    }

    public DataTableType getType() {
        return type;
    }
}

