package com.example.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

public class TournamentViewerFactory implements NamedScreenHandlerFactory {
    public static NamedScreenHandlerFactory create() { return new TournamentViewerFactory(); }

    @Override public Text getDisplayName() { return Text.literal("Tournament Viewer"); }

    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TournamentViewerScreenHandler(syncId, playerInventory);
    }
}
