package io.github.drakonkinst.datatables;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
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
    private static final DataTable DUMMY = new DataTable(DataTableType.MISC, 0,
            new Object2IntArrayMap<>(), new Object2IntArrayMap<>());
    private final Map<Identifier, DataTable> dataTables = new HashMap<>();
    private final Map<Identifier, UnresolvedTable> unresolved = new HashMap<>();
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

    private void loadIntoMap(ResourceManager manager, Map<Identifier, UnresolvedTable> map) {
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

                JsonObject object = JsonHelper.asObject(json, "item_components");

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
                        new UnresolvedTable(resourceId, type, parents, defaultValue, entries));
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
        DataTables.LOGGER.info("Resolved {} data tables for client", this.dataTables.size());
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

        private final Map<Identifier, UnresolvedTable> unresolved;
        private final Map<Identifier, UnmergedTable> resolved = new HashMap<>();
        private final Set<Identifier> toResolve = new HashSet<>();

        Resolver(Map<Identifier, UnresolvedTable> unresolved) {
            this.unresolved = unresolved;
        }

        public void resolve(BiConsumer<Identifier, DataTable> dataTableConsumer) {
            List<UnmergedTable> unmergedTables = new ArrayList<>();

            for (Entry<Identifier, UnresolvedTable> entry : this.unresolved.entrySet()) {
                try {
                    Identifier id = entry.getKey();
                    UnresolvedTable unresolved = entry.getValue();
                    if (unresolved.entries().isEmpty()) {
                        continue;
                    }

                    UnmergedTable resolved = this.getOrResolve(id);
                    unmergedTables.add(resolved);
                } catch (Exception e) {
                    DataTables.LOGGER.error("Failed to load {}", entry.getKey(), e);
                }
            }

            for (UnmergedTable table : unmergedTables) {
                Object2IntMap<Identifier> elementEntryTable = new Object2IntOpenHashMap<>();
                Object2IntMap<Identifier> tagEntryTable = new Object2IntOpenHashMap<>();
                for (Entry<TagEntryId, Integer> entry : table.entries().entrySet()) {
                    TagEntryId entryId = entry.getKey();
                    if (entryId.tag()) {
                        tagEntryTable.put(entryId.id(), entry.getValue().intValue());
                    } else {
                        elementEntryTable.put(entryId.id(), entry.getValue().intValue());
                    }
                }
                dataTableConsumer.accept(table.id(),
                        new DataTable(table.type(), table.defaultValue(), elementEntryTable,
                                tagEntryTable));
            }
        }

        UnmergedTable getOrResolve(Identifier id) throws Exception {
            if (this.resolved.containsKey(id)) {
                return this.resolved.get(id);
            }

            if (this.toResolve.contains(id)) {
                throw new IllegalStateException("Circular reference while loading " + id);
            }
            this.toResolve.add(id);
            UnresolvedTable unresolved = this.unresolved.get(id);
            if (unresolved == null) {
                throw new FileNotFoundException(id.toString());
            }

            Map<TagEntryId, Integer> entries = new HashMap<>();
            for (Identifier parentId : unresolved.parents()) {
                try {
                    UnmergedTable parent = this.getOrResolve(parentId);
                    entries.putAll(parent.entries());
                } catch (Exception e) {
                    DataTables.LOGGER.error("Unable to resolve parent {} referenced from {}",
                            parentId, id, e);
                }
            }

            entries.putAll(unresolved.entries);
            UnmergedTable resolved = new UnmergedTable(unresolved.id(), unresolved.type(),
                    unresolved.parents(), unresolved.defaultValue(), entries);

            this.resolved.put(id, resolved);
            this.toResolve.remove(id);

            return resolved;
        }
    }

    protected record UnresolvedTable(Identifier id, DataTableType type, List<Identifier> parents,
                                     int defaultValue, Map<TagEntryId, Integer> entries) {}

    protected record UnmergedTable(Identifier id, DataTableType type, List<Identifier> parents,
                                   int defaultValue, Map<TagEntryId, Integer> entries) {}
}

