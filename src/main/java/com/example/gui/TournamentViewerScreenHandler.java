package com.example.gui;

import com.example.tournament.Tournament;
import com.example.tournament.TournamentManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class TournamentViewerScreenHandler extends ScreenHandler {
    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    private final Inventory inv = new SimpleInventory(SIZE);

    public TournamentViewerScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);

        // Fill background
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ").formatted(Formatting.GRAY));
        for (int i = 0; i < SIZE; i++) inv.setStack(i, pane.copy());

        refreshMatches();

        // Menu slots
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = col + row * 9;
                addSlot(new MenuSlot(inv, index, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory
        int invY = 18 + ROWS * 18 + 14;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int hotbar = 0; hotbar < 9; ++hotbar) {
            this.addSlot(new Slot(playerInventory, hotbar, 8 + hotbar * 18, invY + 58));
        }
    }

    private void refreshMatches() {
        Tournament t = TournamentManager.INSTANCE.getActive();
        if (t == null) {
            setCenterItem(named(Items.BARRIER, "No active tournament"), 0);
            return;
        }
        List<Tournament.Match> list = t.getMatches();
        // Build columns by round index
        int maxRound = 0;
        for (var m : list) maxRound = Math.max(maxRound, m.round);
        maxRound = Math.max(maxRound, 1);
        // Headers
        for (int r = 1; r <= maxRound && r <= COLUMNS; r++) {
            int headerIndex = (r - 1) + 0 * 9; // top row
            ItemStack header = new ItemStack(Items.OAK_SIGN);
            header.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Round " + r).formatted(Formatting.AQUA));
            inv.setStack(headerIndex, header);
        }
        // Place matches per round in their column
        for (var m : list) {
            int col = Math.min(m.round, COLUMNS) - 1;
            // find next empty slot in this column from row 1 downward
            for (int row = 1; row < ROWS; row++) {
                int index = col + row * 9;
                if (index >= SIZE) break;
                if (inv.getStack(index).isEmpty()) {
                    ItemStack it;
                    Formatting f;
                    if (m.winner != null) { it = new ItemStack(Items.LIME_STAINED_GLASS_PANE); f = Formatting.GREEN; }
                    else if (m == t.getCurrentMatch()) { it = new ItemStack(Items.GOLD_BLOCK); f = Formatting.GOLD; }
                    else { it = new ItemStack(Items.WHITE_STAINED_GLASS_PANE); f = Formatting.WHITE; }
                    it.set(DataComponentTypes.CUSTOM_NAME, Text.literal(m.a + " vs " + m.b).formatted(f));
                    if (m.winner != null) {
                        it.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(java.util.List.of(
                                Text.literal("Winner: " + m.winner).formatted(Formatting.GREEN)
                        )));
                    }
                    inv.setStack(index, it);
                    break;
                }
            }
        }
    }

    private ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.AQUA));
        return s;
    }

    private void setCenterItem(ItemStack stack, int row) {
        int col = 4;
        int index = col + row * 9;
        if (index >= 0 && index < SIZE) inv.setStack(index, stack);
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }

    private static class MenuSlot extends Slot {
        public MenuSlot(Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
        @Override public boolean canInsert(ItemStack stack) { return false; }
    }
}
