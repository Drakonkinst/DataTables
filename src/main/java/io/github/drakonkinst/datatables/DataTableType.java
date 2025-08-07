package io.github.drakonkinst.datatables;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

public enum DataTableType implements StringIdentifiable {
    BLOCK(() -> Registries.BLOCK),
    ENTITY(() -> Registries.ENTITY_TYPE),
    ITEM(() -> Registries.ITEM),
    MISC(() -> null);

    public static final Codec<DataTableType> CODEC = StringIdentifiable.createBasicCodec(
            DataTableType::values);

    private final String name;
    private final Supplier<DefaultedRegistry<?>> registry;

    DataTableType(Supplier<DefaultedRegistry<?>> registry) {
        this.name = name().toLowerCase();
        this.registry = registry;
    }

    String getName() {
        return name;
    }

    public DefaultedRegistry<?> getRegistry() {
        return registry.get();
    }

    @Override
    public String asString() {
        return getName();
    }
}
