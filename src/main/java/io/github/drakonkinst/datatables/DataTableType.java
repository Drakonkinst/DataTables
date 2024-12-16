package io.github.drakonkinst.datatables;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

public enum DataTableType implements StringIdentifiable {
    BLOCK, ENTITY, ITEM, MISC;

    public static final Codec<DataTableType> CODEC = StringIdentifiable.createBasicCodec(
            DataTableType::values);

    private final String name;

    DataTableType() {
        this.name = name().toLowerCase();
    }

    String getName() {
        return name;
    }

    @Override
    public String asString() {
        return getName();
    }
}
