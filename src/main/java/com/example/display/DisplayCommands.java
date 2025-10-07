package com.example.display;

import com.example.tournament.TournamentManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class DisplayCommands {
    public static void register() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Placeholder: map board command (just creates an empty map and gives it)
            dispatcher.register(CommandManager.literal("tourneyboard")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                    if (p == null) { ctx.getSource().sendError(Text.literal("Only players.")); return 0; }
                    var map = new net.minecraft.item.ItemStack(net.minecraft.item.Items.FILLED_MAP);
                    if (!p.giveItemStack(map)) p.dropItem(map, false);
                    ctx.getSource().sendFeedback(() -> Text.literal("Given a blank tournament map (placeholder)."), false);
                    return 1;
                })
            );
        });
    }

    // Utility to clear nearby holograms (invisible armor stands with visible names)
    public static int clearNearbyHolograms(ServerPlayerEntity player, double radius) {
        var w = player.getWorld();
        var pos = player.getPos();
        var box = new net.minecraft.util.math.Box(pos.add(-radius, -radius, -radius), pos.add(radius, radius, radius));
        var list = w.getEntitiesByClass(net.minecraft.entity.decoration.ArmorStandEntity.class, box, e -> e.isInvisible() && e.isCustomNameVisible());
        int count = 0;
        for (var e : list) { e.discard(); count++; }
        return count;
    }
}
