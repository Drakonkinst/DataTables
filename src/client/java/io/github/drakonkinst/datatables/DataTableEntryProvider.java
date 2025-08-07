package io.github.drakonkinst.datatables;

import io.github.drakonkinst.datatables.DataTableRegistry.DataTableEntry;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricCodecDataProvider;
import net.minecraft.data.DataOutput.OutputType;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.util.Identifier;

public abstract class DataTableEntryProvider extends FabricCodecDataProvider<DataTableEntry> {

    private static final String NAME = DataTables.id("data_table").toString();

    protected DataTableEntryProvider(FabricDataOutput dataOutput,
            CompletableFuture<WrapperLookup> registriesFuture) {
        super(dataOutput, registriesFuture, OutputType.DATA_PACK, "data_tables", DataTableRegistry.DATA_TABLE_ENTRY_CODEC);
    }

    @Override
    protected void configure(BiConsumer<Identifier, DataTableEntry> provider, WrapperLookup lookup) {
        accept(provider);
    }

    protected abstract void accept(BiConsumer<Identifier, DataTableEntry> provider);

    @Override
    public String getName() {
        return NAME;
    }
}
