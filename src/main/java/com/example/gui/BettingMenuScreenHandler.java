package com.example.gui;

import com.example.bet.BetManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class BettingMenuScreenHandler extends ScreenHandler {
    public static final int ROWS = 3;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    // Layout slots
    private static final int SLOT_A = 10;
    private static final int SLOT_B = 12;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_REFUND = 26; // bottom-right button to take back current bet
    private static final int SLOT_STAKE = 22; // player drops currency here

    private final Inventory inv = new SimpleInventory(SIZE);
    // Track current pending bet amount (bronze units) so players can add/remove before confirming
    private int currentStakeBronze = 0;
    private java.util.UUID contextUuid = null;
    private String selectedTarget = null;

    public BettingMenuScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId);

        // Fill with glass panes as decoration
    ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
    pane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ").formatted(Formatting.GRAY));
        for (int i = 0; i < SIZE; i++) inv.setStack(i, pane.copy());

        // Build A/B buttons
        refreshSelectionButtons();

    // Confirm and refund buttons
    refreshConfirmButton();
    ItemStack refund = new ItemStack(Items.BARRIER);
    refund.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Take Back Bet").formatted(Formatting.RED));
    inv.setStack(SLOT_REFUND, refund);

    // Stake slot starts empty; tooltip is the background item when empty (we show via decoration, but allow insert)
        inv.setStack(SLOT_STAKE, ItemStack.EMPTY);

        // capture player for context
        if (playerInventory.player != null) {
            this.contextUuid = playerInventory.player.getUuid();
        }

        // Add menu slots (our 9x3)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = col + row * 9;
                addSlot(new MenuSlot(inv, index, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory slots
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

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }

        if (slotIndex == SLOT_A) {
            selectedTarget = BetManager.INSTANCE.getPlayerA();
            serverPlayer.sendMessage(Text.literal("Selected target: " + selectedTarget), false);
            refreshSelectionButtons();
            refreshConfirmButton();
            return;
        }
        if (slotIndex == SLOT_B) {
            selectedTarget = BetManager.INSTANCE.getPlayerB();
            serverPlayer.sendMessage(Text.literal("Selected target: " + selectedTarget), false);
            refreshSelectionButtons();
            refreshConfirmButton();
            return;
        }
        if (slotIndex == SLOT_CONFIRM) {
            int stake = currentStakeBronze + countStakeBronze();
            if (selectedTarget == null) {
                serverPlayer.sendMessage(Text.literal("Select a target first."), false);
                return;
            }
            if (stake <= 0) {
                serverPlayer.sendMessage(Text.literal("Put currency in the stake slot (center)."), false);
                return;
            }

            // Prevent mixing targets: if you already bet on the other target, require refund first
            if (contextUuid != null) {
                String existingTarget = BetManager.INSTANCE.getConfirmedTargetFor(contextUuid);
                if (existingTarget != null && selectedTarget != null && !existingTarget.equalsIgnoreCase(selectedTarget)) {
                    serverPlayer.sendMessage(Text.literal("You already have a bet on " + existingTarget + ". Refund it first."), false);
                    return;
                }
            }

            // Add any coins in the stake slot to the running stake
            currentStakeBronze += countStakeBronze();
            if (!inv.getStack(SLOT_STAKE).isEmpty()) {
                inv.setStack(SLOT_STAKE, ItemStack.EMPTY);
            }

            // Set or update pending bet, then confirm using prepaid bronze units
            BetManager.INSTANCE.setPendingBet(serverPlayer, selectedTarget, currentStakeBronze);
            if (BetManager.INSTANCE.confirmPendingBetPrepaid(serverPlayer, currentStakeBronze)) {
                // Reset local tracker and keep menu open so players can add more
                currentStakeBronze = 0;
                refreshConfirmButton();
            }
            return;
        }

        if (slotIndex == SLOT_REFUND) {
            if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return;
            // Refund any unconfirmed stake in the slot first
            ItemStack stake = inv.getStack(SLOT_STAKE);
            if (!stake.isEmpty()) {
                inv.setStack(SLOT_STAKE, ItemStack.EMPTY);
                if (!sp.getInventory().insertStack(stake.copy())) sp.dropItem(stake.copy(), false);
            }
            // Then refund confirmed bets (only while betting is open)
            int refunded = BetManager.INSTANCE.refundConfirmedBetsFor(sp);
            if (refunded <= 0) {
                sp.sendMessage(Text.literal("No confirmed bet to refund or betting is closed."), false);
            }
            refreshConfirmButton();
            return;
        }

        // Allow only currency items in stake slot; otherwise prevent taking GUI decoration
        if (slotIndex == SLOT_STAKE) {
            // Let the default logic move items, then normalize the stake slot
            super.onSlotClick(slotIndex, button, actionType, player);
            mergeStakeIfPossible(serverPlayer);
            refreshConfirmButton();
            return;
        }

        // Prevent taking our decorative items
        if (slotIndex >= 0 && slotIndex < SIZE) {
            return; // block
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private int countStakeBronze() {
        ItemStack stack = inv.getStack(SLOT_STAKE);
        if (stack == null || stack.isEmpty()) return 0;
        if (!BetManager.INSTANCE.isCurrency(stack)) return 0;
        // Accept multiple stacks by letting players add, confirm, then reopen; within the single slot, we only have one stack at a time
        return BetManager.INSTANCE.toBronzeUnits(stack);
    }

    private void mergeStakeIfPossible(net.minecraft.server.network.ServerPlayerEntity player) {
        ItemStack stack = inv.getStack(SLOT_STAKE);
        if (stack.isEmpty()) return;
        if (!BetManager.INSTANCE.isCurrency(stack)) {
            if (!player.getInventory().insertStack(stack.copy())) player.dropItem(stack.copy(), false);
            inv.setStack(SLOT_STAKE, ItemStack.EMPTY);
            return;
        }
        // Keep as is; users can replace the stack. The confirm button reads both currentStakeBronze and this stack's bronze value.
    }

    private void refreshConfirmButton() {
        int preview = currentStakeBronze + countStakeBronze();
        ItemStack confirm = new ItemStack(Items.GOLD_INGOT);
        // Also show current confirmed bet (if any)
        int existing = 0;
        if (contextUuid != null) existing = BetManager.INSTANCE.getConfirmedAmountFor(contextUuid);
        String label = preview > 0 ? ("Confirm Bet (" + preview + " bronze)") : "Confirm Bet";
        if (existing > 0) label += " | Current: " + existing;
        confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal(label).formatted(Formatting.GOLD));
        inv.setStack(SLOT_CONFIRM, confirm);
    }

    private void refreshSelectionButtons() {
        // A
        ItemStack a = new ItemStack(Items.EMERALD);
        int oddsA = BetManager.INSTANCE.getOddsA();
        String nameA = BetManager.INSTANCE.getPlayerA() != null ? BetManager.INSTANCE.getPlayerA() : "Player A";
        a.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bet on " + nameA + " (" + (oddsA/100.0) + "x)").formatted(Formatting.GREEN));
        if (selectedTarget != null && selectedTarget.equalsIgnoreCase(nameA)) {
            a.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        inv.setStack(SLOT_A, a);

        // B
        ItemStack b = new ItemStack(Items.REDSTONE);
        int oddsB = BetManager.INSTANCE.getOddsB();
        String nameB = BetManager.INSTANCE.getPlayerB() != null ? BetManager.INSTANCE.getPlayerB() : "Player B";
        b.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bet on " + nameB + " (" + (oddsB/100.0) + "x)").formatted(Formatting.RED));
        if (selectedTarget != null && selectedTarget.equalsIgnoreCase(nameB)) {
            b.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        inv.setStack(SLOT_B, b);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        // Refund unconfirmed stake slot contents and tracked stake if any
        ItemStack stack = inv.getStack(SLOT_STAKE);
        if (!stack.isEmpty()) {
            inv.setStack(SLOT_STAKE, ItemStack.EMPTY);
            if (!player.getInventory().insertStack(stack.copy())) player.dropItem(stack.copy(), false);
        }
        if (currentStakeBronze > 0 && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            // Give back the tracked stake as currency stacks
            BetManager.INSTANCE.giveCurrencyToPlayer(sp, currentStakeBronze);
            currentStakeBronze = 0;
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        // Disable shift-clicking into our menu except stake slot for currency; keep it simple
        return ItemStack.EMPTY;
    }

    private static class MenuSlot extends Slot {
        public MenuSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) { return this.getIndex() == SLOT_STAKE; }

        @Override
        public boolean canInsert(ItemStack stack) { return this.getIndex() == SLOT_STAKE && BetManager.INSTANCE.isCurrency(stack); }
    }
}
