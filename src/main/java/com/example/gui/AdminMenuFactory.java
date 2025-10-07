package com.example.gui;

import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;

public class AdminMenuFactory {
    public static final Text TITLE = Text.literal("Betting Admin");

    public static NamedScreenHandlerFactory create() {
        return new SimpleNamedScreenHandlerFactory((syncId, inv, player) -> new AdminMenuScreenHandler(syncId, inv), TITLE);
    }
}
