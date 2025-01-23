package io.github.drakonkinst.datatables;

import com.google.gson.JsonElement;
import io.github.drakonkinst.datatables.json.JsonStack;
import io.github.drakonkinst.datatables.json.JsonType;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.profiler.Profiler;
import org.apache.commons.lang3.StringUtils;

// TODO: This is quite hacky, should look into better solutions that parse DataTable directly later
public class DataTableRegistry extends JsonDataLoader<JsonElement> implements
        IdentifiableResourceReloadListener {

    public static final String RESOURCE_FOLDER = "data_tables";
    private static final DataTable DUMMY = new DataTable(DataTableType.MISC, 0,
            new Object2IntArrayMap<>(), null);
    private static final Identifier IDENTIFIER = DataTables.id(RESOURCE_FOLDER);
    private static final DataTableType[] DATA_TABLE_TYPES = DataTableType.values();
    public static DataTableRegistry INSTANCE;
    private final Map<Identifier, DataTable> dataTables = new HashMap<>();
    private boolean tagsResolved = false;

    public DataTableRegistry() {
        super(Codecs.JSON_ELEMENT, ResourceFinder.json(RESOURCE_FOLDER));
        INSTANCE = this;
    }

    public SyncPayload createSyncPacket() {
        return new SyncPayload(dataTables);
    }

    public void updateContents(Map<Identifier, DataTable> dataTables) {
        this.dataTables.clear();
        this.dataTables.putAll(dataTables);
        tagsResolved = true;
    }

    public DataTable get(Identifier id) {
        warnIfTagsUnresolved(id);
        return dataTables.getOrDefault(id, DUMMY);
    }

    public Optional<DataTable> getOptional(Identifier id) {
        warnIfTagsUnresolved(id);
        return Optional.ofNullable(dataTables.get(id));
    }

    public boolean contains(Identifier id) {
        warnIfTagsUnresolved(id);
        return dataTables.containsKey(id);
    }

    private void warnIfTagsUnresolved(Identifier id) {
        if (!tagsResolved) {
            DataTables.LOGGER.warn(
                    "Attempting to access data table {} before tags are fully resolved, results may be inaccurate",
                    id);
        }
    }

    public Collection<Identifier> getDataTableIds() {
        return dataTables.keySet();
    }

    public void resolveTags() {
        int numResolvedTables = 0;
        boolean anyErrors = false;
        for (Map.Entry<Identifier, DataTable> entry : dataTables.entrySet()) {
            DataTable dataTable = entry.getValue();
            List<Identifier> failedTags = dataTable.resolveTags();
            if (failedTags != null && !failedTags.isEmpty()) {
                if (dataTable.getType() == DataTableType.MISC) {
                    DataTables.LOGGER.warn(
                            "Data table {} is of type {} and is unable to resolve tags. Specify a type to resolve.",
                            entry.getKey(), dataTable.getType());
                } else {
                    DataTables.LOGGER.error(
                            "Failed to resolve tags for data table {}: Unrecognized tags {}",
                            entry.getKey(), StringUtils.join(
                                    failedTags.stream().map(Identifier::toString).toList()));
                    anyErrors = true;
                }
            } else {
                numResolvedTables++;
            }
        }
        DataTables.LOGGER.info("Resolved tags for {} data tables", numResolvedTables);
        if (!anyErrors) {
            tagsResolved = true;
        }
    }

    @Override
    public Identifier getFabricId() {
        return IDENTIFIER;
    }

    public boolean areTagsResolved() {
        return tagsResolved;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager,
            Profiler profiler) {
        tagsResolved = false;
        dataTables.clear();
        data.forEach(this::loadDataTable);
        DataTables.LOGGER.info("Loaded {} data tables", dataTables.size());
    }

    private void loadDataTable(Identifier dataTableId, JsonElement element) {
        JsonStack jsonStack = new JsonStack(element);
        jsonStack.allow("replace", "type", "default", "entries");

        boolean replace = jsonStack.getBooleanOrElse("replace", false);
        int defaultValue = jsonStack.maybeInt("default").orElse(0);
        Optional<String> typeStr = jsonStack.maybeString("type");
        DataTableType type = typeStr.map(str -> getMatchingType(jsonStack, str.toLowerCase()))
                .orElse(DataTableType.MISC);

        Object2IntMap<Identifier> entryTable = new Object2IntArrayMap<>();
        Object2IntMap<Identifier> unresolvedTags = null;
        jsonStack.push("entries");
        for (Map.Entry<String, JsonElement> entry : jsonStack.peek().entrySet()) {
            String key = entry.getKey();
            JsonElement valueEl = entry.getValue();
            int value;
            if (JsonType.NUMBER.is(valueEl)) {
                value = JsonType.NUMBER.cast(valueEl).getAsInt();
            } else {
                jsonStack.addError("Expected data table entry value to be an integer");
                continue;
            }

            if (key.startsWith("#")) {
                if (unresolvedTags == null) {
                    unresolvedTags = new Object2IntArrayMap<>();
                }
                String tag = key.substring(1);
                unresolvedTags.put(Identifier.of(tag), value);
            } else {
                entryTable.put(Identifier.of(key), value);
            }
        }

        List<String> errors = jsonStack.getErrors();
        if (!errors.isEmpty()) {
            DataTables.LOGGER.error("Failed to parse data table {}: {}", dataTableId,
                    StringUtils.join(errors));
            return;
        }

        if (dataTables.containsKey(dataTableId) && !replace) {
            DataTable existingDataTable = dataTables.get(dataTableId);
            if (existingDataTable.getType() != type) {
                DataTables.LOGGER.warn(
                        "Tried to override data table but data table types do not match: expected {}, got {}",
                        existingDataTable.getType().getName(), type.getName());
                return;
            }
            existingDataTable.merge(entryTable, unresolvedTags);
        } else {
            dataTables.put(dataTableId,
                    new DataTable(type, defaultValue, entryTable, unresolvedTags));
        }
    }

    private DataTableType getMatchingType(JsonStack jsonStack, String typeStr) {
        for (DataTableType type : DATA_TABLE_TYPES) {
            if (type.getName().equals(typeStr)) {
                return type;
            }
        }
        jsonStack.addError("Unrecognized data table type " + typeStr);
        return DataTableType.MISC;
    }
}

