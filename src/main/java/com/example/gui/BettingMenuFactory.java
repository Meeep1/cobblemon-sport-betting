package com.example.gui;

import com.example.bet.BetManager;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BettingMenuFactory {
    public static final Text TITLE = Text.literal("Cobblemon Betting");

    public static NamedScreenHandlerFactory create() {
        return new SimpleNamedScreenHandlerFactory((syncId, playerInventory, player) -> new BettingMenuScreenHandler(syncId, playerInventory), TITLE);
    }
}
