package com.example.gui;

import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;

public class PropsMenuFactory {
    public static NamedScreenHandlerFactory create() {
        return new SimpleNamedScreenHandlerFactory((syncId, playerInventory, player) -> new PropsMenuScreenHandler(syncId, playerInventory), Text.literal("Props & Parlays"));
    }
}
