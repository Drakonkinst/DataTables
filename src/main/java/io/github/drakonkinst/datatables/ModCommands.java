package io.github.drakonkinst.datatables;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;

public final class ModCommands {

    public static final int PERMISSION_LEVEL_GAMEMASTER = 2;
    
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> ModCommands.createCommands(
                        dispatcher));
    }

    private static void createCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        TableCommand.register(dispatcher);
    }

    private ModCommands() {}
}
