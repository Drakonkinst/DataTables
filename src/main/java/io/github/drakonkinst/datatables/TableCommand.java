package io.github.drakonkinst.datatables;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class TableCommand {

    public static final int PERMISSION_LEVEL_GAMEMASTER = 2;
    private static final SuggestionProvider<ServerCommandSource> SUGGESTION_PROVIDER = (context, builder) -> {
        Collection<Identifier> dataTableIds = DataTableRegistry.INSTANCE.getDataTableIds();
        return CommandSource.suggestIdentifiers(dataTableIds.stream(), builder);
    };
    private static final DynamicCommandExceptionType UNKNOWN_TABLE_EXCEPTION = new DynamicCommandExceptionType(
            name -> Text.stringifiedTranslatable("commands.table.tableNotFound", name));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("table").requires(
                        source -> source.hasPermissionLevel(PERMISSION_LEVEL_GAMEMASTER))
                .then(literal("list").executes(TableCommand::listTables))
                .then(literal("get").then(
                        argument("data_table_id", IdentifierArgumentType.identifier()).suggests(
                                        SUGGESTION_PROVIDER)
                                .then(literal("block").then(
                                        argument("pos", BlockPosArgumentType.blockPos()).executes(
                                                TableCommand::getTableEntryForBlock))))));
    }

    private static int listTables(CommandContext<ServerCommandSource> context) {
        Collection<Identifier> dataTableIds = DataTableRegistry.INSTANCE.getDataTableIds();
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

    private static int getTableEntryForBlock(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        BlockPos blockPos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
        Identifier id = IdentifierArgumentType.getIdentifier(context, "data_table_id");
        Optional<DataTable> table = DataTableRegistry.INSTANCE.getOptional(id);
        if (table.isEmpty()) {
            throw UNKNOWN_TABLE_EXCEPTION.create(id.toString());
        }
        int value = table.get()
                .getIntForBlock(context.getSource().getWorld().getBlockState(blockPos));
        context.getSource()
                .sendFeedback(
                        () -> Text.translatable("commands.table.get.block", value, id.toString()),
                        false);
        return value;
    }
}
