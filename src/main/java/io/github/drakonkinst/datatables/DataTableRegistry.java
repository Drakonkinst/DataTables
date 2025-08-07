package io.github.drakonkinst.datatables;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.dynamic.Codecs.TagEntryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataTableRegistry implements SimpleSynchronousResourceReloadListener {

    public static final String RESOURCE_FOLDER = "data_tables";
    public static final Identifier ID = DataTables.id(RESOURCE_FOLDER);
    private static final UnboundedMapCodec<TagEntryId, Integer> ENTRIES_CODEC = Codec.unboundedMap(
            Codecs.TAG_ENTRY_ID, Codec.INT);
    private static final Codec<List<Identifier>> PARENTS_CODEC = Identifier.CODEC.listOf();
    private static final DataTableType DEFAULT_DATA_TABLE_TYPE = DataTableType.MISC;
    private static final int DEFAULT_DEFAULT_VALUE = 0;
    private static final DataTable DUMMY = new DataTable(DEFAULT_DATA_TABLE_TYPE, DEFAULT_DEFAULT_VALUE,
            new Object2IntArrayMap<>(), new Object2IntArrayMap<>());
    public static final Codec<DataTableEntry> DATA_TABLE_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
           DataTableType.CODEC.optionalFieldOf("type", DEFAULT_DATA_TABLE_TYPE).forGetter(DataTableEntry::type),
           PARENTS_CODEC.optionalFieldOf("parents", Collections.emptyList()).forGetter(DataTableEntry::parents),
           Codec.INT.optionalFieldOf("default_value", DEFAULT_DEFAULT_VALUE).forGetter(DataTableEntry::defaultValue),
           ENTRIES_CODEC.optionalFieldOf("entries", Collections.emptyMap()).forGetter(DataTableEntry::entries)
    ).apply(instance, DataTableEntry::new));

    private final Map<Identifier, DataTable> dataTables = new HashMap<>();
    private final Map<Identifier, DataTableEntry> unresolved = new HashMap<>();
    @Nullable
    private RegistryWrapper.WrapperLookup registries;
    private boolean resolved = false;

    protected DataTableRegistry() {}

    @Override
    public void reload(ResourceManager manager) {
        this.clear();
        this.loadIntoMap(manager, this.unresolved);
        DataTables.LOGGER.info("Loaded {} data tables", this.unresolved.size());
    }

    public void resolve() {
        new Resolver(this.unresolved).resolve(this.dataTables::put);
        this.markResolved();
        DataTables.LOGGER.info("Resolved {} data tables", this.dataTables.size());
    }

    protected void markResolved() {
        this.unresolved.clear();
        this.resolved = true;
    }

    private void loadIntoMap(ResourceManager manager, Map<Identifier, DataTableEntry> map) {
        ResourceFinder finder = ResourceFinder.json(RESOURCE_FOLDER);

        assert this.registries != null;
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, this.registries);

        for (Map.Entry<Identifier, Resource> entry : finder.findResources(manager).entrySet()) {
            Identifier resourcePath = entry.getKey();
            Resource resource = entry.getValue();

            Identifier resourceId = finder.toResourceId(resourcePath);

            try (BufferedReader reader = resource.getReader()) {
                List<Identifier> parents = List.of();
                Map<TagEntryId, Integer> entries = new HashMap<>();
                DataTableType type = DataTableType.MISC;

                JsonElement json = JsonParser.parseReader(reader);

                JsonObject object = JsonHelper.asObject(json, "data_table");

                int defaultValue = JsonHelper.getInt(object, "default_value", 0);
                if (object.has("parents")) {
                    parents = PARENTS_CODEC.decode(ops, JsonHelper.getElement(object, "parents"))
                            .getOrThrow(JsonSyntaxException::new)
                            .getFirst();
                }
                if (object.has("entries")) {
                    entries = ENTRIES_CODEC.decode(ops, JsonHelper.getElement(object, "entries"))
                            .getOrThrow(JsonSyntaxException::new)
                            .getFirst();
                }
                if (object.has("type")) {
                    type = DataTableType.CODEC.decode(ops, JsonHelper.getElement(object, "type"))
                            .getOrThrow(JsonSyntaxException::new)
                            .getFirst();
                }

                map.put(resourceId,
                        new DataTableEntry(type, parents, defaultValue, entries));
            } catch (Exception exception) {
                DataTables.LOGGER.error("Couldn't read data table {} from {} in data pack {}",
                        resourceId, resourcePath, resource.getPackId(), exception);
            }
        }
    }

    protected void clear() {
        this.unresolved.clear();
        this.dataTables.clear();
        this.resolved = false;
    }

    public SyncPayload createSyncPacket() {
        return new SyncPayload(dataTables);
    }

    public void syncDataTables(Map<Identifier, DataTable> dataTables) {
        clear();
        this.dataTables.putAll(dataTables);
        markResolved();
        DataTables.LOGGER.info("Synced {} data tables from server", this.dataTables.size());
    }

    public void setRegistries(@NotNull RegistryWrapper.WrapperLookup registries) {
        this.registries = registries;
    }

    public DataTable get(Identifier id) {
        return dataTables.getOrDefault(id, DUMMY);
    }

    public Optional<DataTable> getOptional(Identifier id) {
        return Optional.ofNullable(dataTables.get(id));
    }

    public boolean contains(Identifier id) {
        return dataTables.containsKey(id);
    }

    public Collection<Identifier> getDataTableIds() {
        return dataTables.keySet();
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    public boolean isResolved() {
        return resolved;
    }

    private static class Resolver {

        private final Map<Identifier, DataTableEntry> unresolved;
        private final Map<Identifier, DataTableEntry> resolved = new HashMap<>();
        private final Set<Identifier> toResolve = new HashSet<>();

        Resolver(Map<Identifier, DataTableEntry> unresolved) {
            this.unresolved = unresolved;
        }

        public void resolve(BiConsumer<Identifier, DataTable> dataTableConsumer) {
            Map<Identifier, DataTableEntry> dataTableEntries = new HashMap<>();

            for (Entry<Identifier, DataTableEntry> entry : this.unresolved.entrySet()) {
                try {
                    Identifier id = entry.getKey();
                    DataTableEntry unresolved = entry.getValue();
                    if (unresolved.entries().isEmpty()) {
                        continue;
                    }

                    DataTableEntry resolved = this.getOrResolve(id);
                    dataTableEntries.put(id, resolved);
                } catch (Exception e) {
                    DataTables.LOGGER.error("Failed to load {}", entry.getKey(), e);
                }
            }

            for (Entry<Identifier, DataTableEntry> entry : dataTableEntries.entrySet()) {
                DataTableEntry table = entry.getValue();
                Object2IntMap<Identifier> elementEntryTable = new Object2IntOpenHashMap<>();
                Object2IntMap<Identifier> tagEntryTable = new Object2IntOpenHashMap<>();
                for (Entry<TagEntryId, Integer> tableEntry : table.entries().entrySet()) {
                    TagEntryId tableEntryId = tableEntry.getKey();
                    if (tableEntryId.tag()) {
                        tagEntryTable.put(tableEntryId.id(), tableEntry.getValue().intValue());
                    } else {
                        elementEntryTable.put(tableEntryId.id(), tableEntry.getValue().intValue());
                    }
                }
                dataTableConsumer.accept(entry.getKey(),
                        new DataTable(table.type(), table.defaultValue(), elementEntryTable,
                                tagEntryTable));
            }
        }

        DataTableEntry getOrResolve(Identifier id) throws Exception {
            if (this.resolved.containsKey(id)) {
                return this.resolved.get(id);
            }

            if (this.toResolve.contains(id)) {
                throw new IllegalStateException("Circular reference while loading " + id);
            }
            this.toResolve.add(id);
            DataTableEntry unresolved = this.unresolved.get(id);
            if (unresolved == null) {
                throw new FileNotFoundException(id.toString());
            }

            Map<TagEntryId, Integer> entries = new HashMap<>();
            for (Identifier parentId : unresolved.parents()) {
                try {
                    DataTableEntry parent = this.getOrResolve(parentId);
                    entries.putAll(parent.entries());
                } catch (Exception e) {
                    DataTables.LOGGER.error("Unable to resolve parent {} referenced from {}",
                            parentId, id, e);
                }
            }

            entries.putAll(unresolved.entries);
            DataTableEntry resolved = new DataTableEntry(unresolved.type(),
                    unresolved.parents(), unresolved.defaultValue(), entries);

            this.resolved.put(id, resolved);
            this.toResolve.remove(id);

            return resolved;
        }
    }

    // Represents an unresolved or unmerged data table
    public record DataTableEntry(DataTableType type, List<Identifier> parents,
                                    int defaultValue, Map<TagEntryId, Integer> entries) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private DataTableType type = DEFAULT_DATA_TABLE_TYPE;
            private int defaultValue = DEFAULT_DEFAULT_VALUE;
            private final List<Identifier> parents = new ArrayList<>();
            private final Map<TagEntryId, Integer> entries = new HashMap<>();

            public Builder() {
            }

            public Builder type(DataTableType type) {
                this.type = type;
                return this;
            }

            public Builder defaultValue(int value) {
                this.defaultValue = value;
                return this;
            }

            public Builder parent(Identifier id) {
                parents.add(id);
                return this;
            }

            public Builder parents(List<Identifier> ids) {
                parents.addAll(ids);
                return this;
            }

            public Builder entry(Identifier id, int value) {
                entries.put(new TagEntryId(id, false), value);
                return this;
            }

            public Builder entry(Item item, int value) {
                Identifier id = Registries.ITEM.getId(item);
                return entry(id, value);
            }

            public Builder entry(Block block, int value) {
                Identifier id = Registries.BLOCK.getId(block);
                return entry(id, value);
            }

            public Builder entry(EntityType<?> entityType, int value) {
                Identifier id = EntityType.getId(entityType);
                return entry(id, value);
            }

            public Builder tag(Identifier id, int value) {
                entries.put(new TagEntryId(id, true), value);
                return this;
            }

            public Builder tag(TagKey<?> tag, int value) {
                Identifier id = tag.id();
                return tag(id, value);
            }

            public DataTableEntry build() {
                return new DataTableEntry(type, parents, defaultValue, entries);
            }
        }
    }
}

