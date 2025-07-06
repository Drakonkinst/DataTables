package io.github.drakonkinst.datatables;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class DataTable {

    public static final Codec<DataTable> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            DataTableType.CODEC.fieldOf("type").forGetter(DataTable::getType),
                            Codec.INT.optionalFieldOf("default_value", 0)
                                    .forGetter(dataTable -> dataTable.defaultValue),
                            object2IntMap(Identifier.CODEC).fieldOf("element_entries")
                                    .forGetter(dataTable -> dataTable.elementEntryTable),
                            object2IntMap(Identifier.CODEC).fieldOf("tag_entries")
                                    .forGetter(dataTable -> dataTable.tagEntryTable))
                    .apply(instance, DataTable::new));
    public static final PacketCodec<RegistryByteBuf, DataTable> PACKET_CODEC = PacketCodecs.registryCodec(
            CODEC);

    // TODO: Move to utils?
    private static <T> Codec<Object2IntMap<T>> object2IntMap(Codec<T> keyCodec) {
        return Codec.unboundedMap(keyCodec, Codec.INT)
                .xmap(Object2IntOpenHashMap::new, Object2ObjectOpenHashMap::new);
    }

    private final DataTableType type;
    private final Object2IntMap<Identifier> elementEntryTable;
    private final Object2IntMap<Identifier> tagEntryTable;
    private final int defaultValue;
    private final Object2IntMap<Identifier> cache = new Object2IntOpenHashMap<>();

    public DataTable(DataTableType type, int defaultValue,
            Object2IntMap<Identifier> elementEntryTable, Object2IntMap<Identifier> tagEntryTable) {
        this.type = type;
        this.elementEntryTable = elementEntryTable;
        this.tagEntryTable = tagEntryTable;
        this.defaultValue = defaultValue;
    }

    public int query(Identifier id) {
        if (this.cache.containsKey(id)) {
            return this.cache.getInt(id);
        }

        int value;
        // Specific items take precedence
        if (elementEntryTable.containsKey(id)) {
            value = elementEntryTable.getInt(id);
        } else {
            value = getValueFromTag(id).orElse(defaultValue);
        }

        this.cache.put(id, value);
        return value;
    }

    public int query(Item item) {
        return query(Registries.ITEM.getId(item));
    }

    public int query(Entity entity) {
        return query(EntityType.getId(entity.getType()));
    }

    public int query(BlockState blockState) {
        return query(blockState.getBlock());
    }

    public int query(Block block) {
        return query(Registries.BLOCK.getId(block));
    }

    public DataTableType getType() {
        return type;
    }

    private OptionalInt getValueFromTag(Identifier id) {
        DefaultedRegistry<?> registry = this.type.getRegistry();
        if (registry == null) {
            return OptionalInt.empty();
        }
        Optional<? extends Reference<?>> optionalEntry = registry.getEntry(id);
        if (optionalEntry.isEmpty()) {
            return OptionalInt.empty();
        }
        // TODO: Can build some conflict resolution here: take highest, take lowest? Right now, it's take first
        Optional<? extends TagKey<?>> matchingTag = optionalEntry.get()
                .streamTags()
                .filter(tag -> this.tagEntryTable.containsKey(tag.id()))
                .findFirst();
        return matchingTag.map(tagKey -> OptionalInt.of(this.tagEntryTable.getInt(tagKey.id())))
                .orElseGet(OptionalInt::empty);
    }
}

