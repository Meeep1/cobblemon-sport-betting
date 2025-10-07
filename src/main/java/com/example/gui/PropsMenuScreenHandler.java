package com.example.gui;

import com.example.bet.BetManager;
import com.example.bet.PropCondition;
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

import java.util.ArrayList;
import java.util.List;

public class PropsMenuScreenHandler extends ScreenHandler {
    public static final int ROWS = 3;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    private static final int SLOT_FIRST_A = 10;
    private static final int SLOT_FIRST_B = 12;
    private static final int SLOT_TOTAL_OVER = 13;
    private static final int SLOT_CONFIRM = 16;
    private static final int SLOT_STAKE = 22;

    private final Inventory inv = new SimpleInventory(SIZE);
    private final List<PropCondition> legs = new ArrayList<>();
    private int currentStakeBronze = 0;

    public PropsMenuScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId);

        ItemStack pane = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
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

    private void refreshButtons() {
        String a = BetManager.INSTANCE.getPlayerA() != null ? BetManager.INSTANCE.getPlayerA() : "A";
        String b = BetManager.INSTANCE.getPlayerB() != null ? BetManager.INSTANCE.getPlayerB() : "B";

    int ffMult = com.example.config.PropsConfig.getFirstFaintMultiplier();
    ItemStack firstA = new ItemStack(Items.EMERALD);
    firstA.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Prop: First Faint " + a + " (" + (ffMult/100.0) + "x)").formatted(Formatting.GREEN));
        inv.setStack(SLOT_FIRST_A, firstA);

    ItemStack firstB = new ItemStack(Items.REDSTONE);
    firstB.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Prop: First Faint " + b + " (" + (ffMult/100.0) + "x)").formatted(Formatting.RED));
        inv.setStack(SLOT_FIRST_B, firstB);

    int thr = com.example.config.PropsConfig.getTotalFaintsOverThreshold();
    int toMult = com.example.config.PropsConfig.getTotalFaintsOverMultiplier();
    ItemStack totalOver = new ItemStack(Items.PAPER);
    totalOver.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Prop: Total Faints > " + thr + " (" + (toMult/100.0) + "x)").formatted(Formatting.AQUA));
        inv.setStack(SLOT_TOTAL_OVER, totalOver);

        ItemStack confirm = new ItemStack(Items.GOLD_INGOT);
        String label = "Place Parlay (" + legs.size() + " legs)";
        if (currentStakeBronze > 0) label += " | Stake: " + currentStakeBronze;
        confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal(label).formatted(Formatting.GOLD));
        inv.setStack(SLOT_CONFIRM, confirm);

        inv.setStack(SLOT_STAKE, ItemStack.EMPTY);
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) { super.onSlotClick(slotIndex, button, actionType, player); return; }

        if (slotIndex == SLOT_FIRST_A || slotIndex == SLOT_FIRST_B) {
            String which = (slotIndex == SLOT_FIRST_A) ? "A" : "B";
            legs.add(new PropCondition(PropCondition.Type.FIRST_FAINT, which, com.example.config.PropsConfig.getFirstFaintMultiplier()));
            sp.sendMessage(Text.literal("Added leg: First Faint " + which), false);
            refreshButtons();
            return;
        }
        if (slotIndex == SLOT_TOTAL_OVER) {
            int thr = com.example.config.PropsConfig.getTotalFaintsOverThreshold();
            legs.add(new PropCondition(PropCondition.Type.TOTAL_FAINTS_OVER, String.valueOf(thr), com.example.config.PropsConfig.getTotalFaintsOverMultiplier()));
            sp.sendMessage(Text.literal("Added leg: Total Faints > " + thr), false);
            refreshButtons();
            return;
        }
        if (slotIndex == SLOT_CONFIRM) {
            int stake = currentStakeBronze + countStakeBronze();
            if (legs.isEmpty()) { sp.sendMessage(Text.literal("Add 1-2 legs first."), false); return; }
            if (stake <= 0) { sp.sendMessage(Text.literal("Put currency in the stake slot."), false); return; }
            // consume stake in slot
            currentStakeBronze += countStakeBronze();
            if (!inv.getStack(SLOT_STAKE).isEmpty()) inv.setStack(SLOT_STAKE, ItemStack.EMPTY);
            // Place parlay
            if (BetManager.INSTANCE.addParlayBetPrepaid(sp, new ArrayList<>(legs), currentStakeBronze)) {
                legs.clear();
                currentStakeBronze = 0;
                refreshButtons();
            }
            return;
        }
        if (slotIndex == SLOT_STAKE) {
            super.onSlotClick(slotIndex, button, actionType, player);
            mergeStakeIfPossible((net.minecraft.server.network.ServerPlayerEntity) player);
            refreshButtons();
            return;
        }
        if (slotIndex >= 0 && slotIndex < SIZE) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private int countStakeBronze() {
        ItemStack stack = inv.getStack(SLOT_STAKE);
        if (stack == null || stack.isEmpty()) return 0;
        if (!BetManager.INSTANCE.isCurrency(stack)) return 0;
        return BetManager.INSTANCE.toBronzeUnits(stack);
    }

    private void mergeStakeIfPossible(net.minecraft.server.network.ServerPlayerEntity player) {
        ItemStack stack = inv.getStack(SLOT_STAKE);
        if (stack.isEmpty()) return;
        if (!BetManager.INSTANCE.isCurrency(stack)) {
            if (!player.getInventory().insertStack(stack.copy())) player.dropItem(stack.copy(), false);
            inv.setStack(SLOT_STAKE, ItemStack.EMPTY);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        // Allow shift-clicking currency from player inventory/hotbar into the stake slot
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return ItemStack.EMPTY;
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot clicked = this.slots.get(index);
        if (clicked.inventory == this.inv) return ItemStack.EMPTY; // don't shift-click our own menu slots
        if (!clicked.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = clicked.getStack();
        if (!BetManager.INSTANCE.isCurrency(stack)) return ItemStack.EMPTY;

        ItemStack stake = inv.getStack(SLOT_STAKE);
        if (stake.isEmpty()) {
            inv.setStack(SLOT_STAKE, stack.copy());
            clicked.setStack(ItemStack.EMPTY);
            mergeStakeIfPossible(sp);
            refreshButtons();
            return ItemStack.EMPTY;
        }
        // Try to merge if same item type
        if (ItemStack.areItemsEqual(stake, stack)) {
            int canMove = Math.min(stack.getCount(), stake.getMaxCount() - stake.getCount());
            if (canMove > 0) {
                stake.increment(canMove);
                stack.decrement(canMove);
                if (stack.isEmpty()) clicked.setStack(ItemStack.EMPTY);
                inv.setStack(SLOT_STAKE, stake);
                mergeStakeIfPossible(sp);
                refreshButtons();
            }
        }
        return ItemStack.EMPTY;
    }

    private static class MenuSlot extends Slot {
        public MenuSlot(Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean canTakeItems(PlayerEntity playerEntity) { return this.getIndex() == SLOT_STAKE; }
        @Override public boolean canInsert(ItemStack stack) { return this.getIndex() == SLOT_STAKE && BetManager.INSTANCE.isCurrency(stack); }
    }
}
