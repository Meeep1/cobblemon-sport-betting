package com.example.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

public class DebugMenuFactory implements NamedScreenHandlerFactory {
    public static NamedScreenHandlerFactory create() { return new DebugMenuFactory(); }

    @Override
    public Text getDisplayName() { return Text.literal("Betting Debug Menu"); }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new DebugMenuScreenHandler(syncId, inv);
    }
}
