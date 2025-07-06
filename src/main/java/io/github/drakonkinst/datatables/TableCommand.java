package io.github.drakonkinst.datatables;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.ItemSlotArgumentType;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.StackReference;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class TableCommand {

    public static final int PERMISSION_LEVEL_GAMEMASTER = 2;
    private static final SuggestionProvider<ServerCommandSource> SUGGESTION_PROVIDER = (context, builder) -> {
        Collection<Identifier> dataTableIds = DataTables.getDataTableIds();
        return CommandSource.suggestIdentifiers(dataTableIds.stream(), builder);
    };
    private static final DynamicCommandExceptionType UNKNOWN_TABLE_EXCEPTION = new DynamicCommandExceptionType(
            name -> Text.stringifiedTranslatable("commands.table.tableNotFound", name));
    private static final SimpleCommandExceptionType SLOT_NOT_FOUND = new SimpleCommandExceptionType(
            Text.translatable("commands.table.slotNotFound"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("table").requires(
                        source -> source.hasPermissionLevel(PERMISSION_LEVEL_GAMEMASTER))
                .then(literal("list").executes(TableCommand::executeList))
                .then(literal("get").then(
                        argument("data_table_id", IdentifierArgumentType.identifier()).suggests(
                                        SUGGESTION_PROVIDER)
                                .then(literal("block").then(
                                        argument("pos", BlockPosArgumentType.blockPos()).executes(
                                                TableCommand::executeGetBlock)))
                                .then(literal("entity").then(
                                        argument("target", EntityArgumentType.entity()).executes(
                                                TableCommand::executeGetEntity)))
                                .then(literal("slot").then(
                                        argument("target", EntityArgumentType.entity()).then(
                                                argument("slot",
                                                        ItemSlotArgumentType.itemSlot()).executes(
                                                        TableCommand::executeGetItemSlot))))
                                .then(literal("item").then(argument("item",
                                        ItemStackArgumentType.itemStack(registryAccess)).executes(
                                        TableCommand::executeGetItemId))))));
    }

    private static int executeList(CommandContext<ServerCommandSource> context) {
        Collection<Identifier> dataTableIds = DataTables.getDataTableIds();
        boolean first = true;
        StringBuilder str = new StringBuilder();
        for (Identifier id : dataTableIds) {
            if (first) {
                first = false;
                str.append(id.toString());
            } else {
                str.append(", ").append(id.toString());
            }
        }
        final String tableListStr = str.toString();
        context.getSource()
                .sendFeedback(() -> Text.translatable("commands.table.list", dataTableIds.size())
                        .append(tableListStr), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeGetBlock(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        Identifier id = IdentifierArgumentType.getIdentifier(context, "data_table_id");
        Optional<DataTable> table = DataTables.getOptional(id);
        if (table.isEmpty()) {
            throw UNKNOWN_TABLE_EXCEPTION.create(id.toString());
        }

        BlockPos blockPos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
        int value = table.get().query(context.getSource().getWorld().getBlockState(blockPos));
        context.getSource()
                .sendFeedback(
                        () -> Text.translatable("commands.table.get.block", value, id.toString()),
                        false);
        return value;
    }

    private static int executeGetEntity(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        Identifier id = IdentifierArgumentType.getIdentifier(context, "data_table_id");
        Optional<DataTable> table = DataTables.getOptional(id);
        if (table.isEmpty()) {
            throw UNKNOWN_TABLE_EXCEPTION.create(id.toString());
        }

        Entity entity = EntityArgumentType.getEntity(context, "target");
        int value = table.get().query(entity);
        context.getSource()
                .sendFeedback(() -> Text.translatable("commands.table.get.entity",
                        entity.getDisplayName(), value, id.toString()), false);
        return value;
    }

    private static int executeGetItemSlot(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        Identifier id = IdentifierArgumentType.getIdentifier(context, "data_table_id");
        Optional<DataTable> table = DataTables.getOptional(id);
        if (table.isEmpty()) {
            throw UNKNOWN_TABLE_EXCEPTION.create(id.toString());
        }
        Entity entity = EntityArgumentType.getEntity(context, "target");
        int slot = ItemSlotArgumentType.getItemSlot(context, "slot");
        StackReference stackReference = entity.getStackReference(slot);
        if (stackReference == StackReference.EMPTY) {
            throw SLOT_NOT_FOUND.create();
        }
        int value = table.get().query(stackReference.get().getItem());
        context.getSource()
                .sendFeedback(
                        () -> Text.translatable("commands.table.get.item", value, id.toString()),
                        false);
        return value;
    }

    private static int executeGetItemId(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        Identifier id = IdentifierArgumentType.getIdentifier(context, "data_table_id");
        Optional<DataTable> table = DataTables.getOptional(id);
        if (table.isEmpty()) {
            throw UNKNOWN_TABLE_EXCEPTION.create(id.toString());
        }

        ItemStackArgument stackArgument = ItemStackArgumentType.getItemStackArgument(context,
                "item");
        int value = table.get().query(stackArgument.getItem());
        context.getSource()
                .sendFeedback(
                        () -> Text.translatable("commands.table.get.item", value, id.toString()),
                        false);
        return value;
    }

}
