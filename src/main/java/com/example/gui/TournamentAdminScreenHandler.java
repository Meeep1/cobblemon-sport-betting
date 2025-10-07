package com.example.gui;

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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TournamentAdminScreenHandler extends ScreenHandler {
    public static final int ROWS = 3;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    private static final int SLOT_CREATE = 10;
    private static final int SLOT_START = 11;
    private static final int SLOT_ANNOUNCE = 12;
    private static final int SLOT_CLEAR = 13;
    private static final int SLOT_ADD = 19;
    private static final int SLOT_REMOVE = 20;
    private static final int SLOT_FORCE_A = 21;
    private static final int SLOT_FORCE_B = 22;
    private static final int SLOT_SCORE_BOOK = 14;
    private static final int SLOT_SCORE_A_MINUS = 28;
    private static final int SLOT_SCORE_A_PLUS = 29;
    private static final int SLOT_SCORE_B_MINUS = 30;
    private static final int SLOT_SCORE_B_PLUS = 31;

    private final Inventory inv = new SimpleInventory(SIZE);

    public TournamentAdminScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId);

        // decor
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ").formatted(Formatting.GRAY));
        for (int i = 0; i < SIZE; i++) inv.setStack(i, pane.copy());

        refreshButtons();

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = col + row * 9;
                addSlot(new MenuSlot(inv, index, 8 + col * 18, 18 + row * 18));
            }
        }

        int invY = 84;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int hotbar = 0; hotbar < 9; ++hotbar) {
            this.addSlot(new Slot(playerInventory, hotbar, 8 + hotbar * 18, invY + 58));
        }
    }

    private ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.AQUA));
        return s;
    }

    private void refreshButtons() {
        inv.setStack(SLOT_CREATE, named(Items.NAME_TAG, "Create Tournament (uses held name)"));
        inv.setStack(SLOT_START, named(Items.LIME_DYE, "Start Tournament"));
        inv.setStack(SLOT_ANNOUNCE, named(Items.BELL, "Announce State"));
        inv.setStack(SLOT_CLEAR, named(Items.BARRIER, "Clear Active Tournament"));
    inv.setStack(SLOT_ADD, named(Items.PAPER, "Add Entrant (held item's name)"));
        inv.setStack(SLOT_REMOVE, named(Items.REDSTONE, "Remove Entrant (held item's name)"));
        inv.setStack(SLOT_FORCE_A, named(Items.EMERALD, "Force Winner: A (current match)"));
        inv.setStack(SLOT_FORCE_B, named(Items.DIAMOND, "Force Winner: B (current match)"));
    inv.setStack(SLOT_SCORE_BOOK, named(Items.BOOK, "Give Score Book (auto-updating)"));
    inv.setStack(SLOT_SCORE_A_MINUS, named(Items.RED_DYE, "A -1"));
    inv.setStack(SLOT_SCORE_A_PLUS, named(Items.LIME_DYE, "A +1"));
    inv.setStack(SLOT_SCORE_B_MINUS, named(Items.RED_DYE, "B -1"));
    inv.setStack(SLOT_SCORE_B_PLUS, named(Items.LIME_DYE, "B +1"));
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return;
        var server = sp.getServer();
        switch (slotIndex) {
            case SLOT_CREATE -> {
                String id = getHeldCustomName(sp);
                if (id == null) { sp.sendMessage(Text.literal("Hold a named item to use as the tournament ID."), false); return; }
                if (TournamentManager.INSTANCE.create(id)) sp.sendMessage(Text.literal("Created tournament '" + id + "'."), false);
                else sp.sendMessage(Text.literal("Couldn't create; finish or clear existing first."), false);
            }
            case SLOT_START -> {
                if (TournamentManager.INSTANCE.start()) sp.sendMessage(Text.literal("Tournament started."), false);
                else sp.sendMessage(Text.literal("Unable to start; ensure >=2 entrants and not already started."), false);
            }
            case SLOT_ANNOUNCE -> TournamentManager.INSTANCE.announceState(server);
            case SLOT_CLEAR -> {
                // simple clear: drop reference so a new one can be created
                try {
                    var f = com.example.tournament.TournamentManager.class.getDeclaredField("active");
                    f.setAccessible(true);
                    f.set(com.example.tournament.TournamentManager.INSTANCE, null);
                    sp.sendMessage(Text.literal("Cleared active tournament."), false);
                } catch (Exception e) {
                    sp.sendMessage(Text.literal("Failed to clear: " + e.getMessage()), false);
                }
            }
            case SLOT_ADD -> {
                String name = getHeldCustomName(sp);
                if (name == null) { sp.sendMessage(Text.literal("Hold a named item to use as entrant name."), false); return; }
                if (TournamentManager.INSTANCE.add(name)) sp.sendMessage(Text.literal("Added entrant: " + name), false);
                else sp.sendMessage(Text.literal("Failed to add entrant."), false);
            }
            case SLOT_REMOVE -> {
                String name = getHeldCustomName(sp);
                if (name == null) { sp.sendMessage(Text.literal("Hold a named item to use as entrant name."), false); return; }
                if (TournamentManager.INSTANCE.remove(name)) sp.sendMessage(Text.literal("Removed entrant: " + name), false);
                else sp.sendMessage(Text.literal("Failed to remove entrant."), false);
            }
            case SLOT_FORCE_A, SLOT_FORCE_B -> {
                var t = TournamentManager.INSTANCE.getActive();
                if (t == null || t.getCurrentMatch() == null) { sp.sendMessage(Text.literal("No current match."), false); return; }
                var m = t.getCurrentMatch();
                String winner = slotIndex == SLOT_FORCE_A ? m.a : m.b;
                TournamentManager.INSTANCE.reportWinnerAndAdvance(server, winner);
                sp.sendMessage(Text.literal("Forced winner: " + winner), false);
            }
            case SLOT_SCORE_BOOK -> {
                var t = TournamentManager.INSTANCE.getActive();
                String a = t != null && t.getCurrentMatch() != null ? t.getCurrentMatch().a : "A";
                String b = t != null && t.getCurrentMatch() != null ? t.getCurrentMatch().b : "B";
                var book = com.example.tournament.ScoreService.createScoreBook(a, b);
                if (!sp.giveItemStack(book)) sp.dropItem(book, false);
                sp.sendMessage(Text.literal("Given score book."), false);
            }
            case SLOT_SCORE_A_MINUS -> { com.example.tournament.ScoreService.addA(-1); sp.sendMessage(Text.literal("A score: " + com.example.tournament.ScoreService.getA()), false); }
            case SLOT_SCORE_A_PLUS -> { com.example.tournament.ScoreService.addA(1); sp.sendMessage(Text.literal("A score: " + com.example.tournament.ScoreService.getA()), false); }
            case SLOT_SCORE_B_MINUS -> { com.example.tournament.ScoreService.addB(-1); sp.sendMessage(Text.literal("B score: " + com.example.tournament.ScoreService.getB()), false); }
            case SLOT_SCORE_B_PLUS -> { com.example.tournament.ScoreService.addB(1); sp.sendMessage(Text.literal("B score: " + com.example.tournament.ScoreService.getB()), false); }
            default -> {}
        }
    }

    private String getHeldCustomName(net.minecraft.server.network.ServerPlayerEntity sp) {
        ItemStack held = sp.getMainHandStack();
        if (held.isEmpty()) return null;
        var name = held.get(DataComponentTypes.CUSTOM_NAME);
        return name != null ? name.getString() : null;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }

    private static class MenuSlot extends Slot {
        public MenuSlot(Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
        @Override public boolean canInsert(ItemStack stack) { return false; }
    }
}
