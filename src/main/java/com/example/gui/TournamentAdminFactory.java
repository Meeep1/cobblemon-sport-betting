package com.example.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

public class TournamentAdminFactory implements NamedScreenHandlerFactory {
    public static NamedScreenHandlerFactory create() { return new TournamentAdminFactory(); }

    @Override public Text getDisplayName() { return Text.literal("Tournament Admin"); }

    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TournamentAdminScreenHandler(syncId, playerInventory);
    }
}
