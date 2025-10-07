package com.example.gui;

import com.example.bet.BetManager;
import com.example.config.ConfigManager;
import com.google.gson.JsonObject;
import com.example.region.RegionService;
import com.example.region.Region;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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

public class AdminMenuScreenHandler extends ScreenHandler {
    public static final int ROWS = 3;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    private static final int SLOT_TOGGLE = 13; // center toggle house pays
    private static final int SLOT_AUTO_OPEN = 11; // toggle auto-open betting
    private static final int SLOT_SET_A_POS1 = 19;
    private static final int SLOT_SET_A_POS2 = 20;
    private static final int SLOT_SET_B_POS1 = 21;
    private static final int SLOT_SET_B_POS2 = 22;
    private static final int SLOT_SET_ARENA_POS1 = 23;
    private static final int SLOT_SET_ARENA_POS2 = 24;
    private static final int SLOT_SET_FIREWORKS_POS = 15;
    private static final int SLOT_RESET_AREAS = 17;
    private static final int SLOT_TOGGLE_RECEIPTS = 5;
    private static final int SLOT_TOGGLE_SOUNDS = 6;
    private static final int SLOT_TOGGLE_SELF = 7;

    private final Inventory inv = new SimpleInventory(SIZE);

    public AdminMenuScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId);

        // fill decor
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ").formatted(Formatting.GRAY));
        for (int i = 0; i < SIZE; i++) inv.setStack(i, pane.copy());

        // buttons
        refreshButtons();

        // add menu slots
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = col + row * 9;
                addSlot(new MenuSlot(inv, index, 8 + col * 18, 18 + row * 18));
            }
        }

        // player inv
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
        boolean on = BetManager.INSTANCE.isHousePays();
        ItemStack item = new ItemStack(on ? Items.EMERALD_BLOCK : Items.REDSTONE_BLOCK);
        item.set(DataComponentTypes.CUSTOM_NAME, Text.literal("House Pays: " + (on ? "ENABLED" : "DISABLED")).formatted(on ? Formatting.GREEN : Formatting.RED));
        inv.setStack(SLOT_TOGGLE, item);

        boolean auto = RegionService.INSTANCE.isAutoOpenEnabled();
        ItemStack autoItem = new ItemStack(auto ? Items.LIME_DYE : Items.GRAY_DYE);
        autoItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Auto-Open: " + (auto ? "ON" : "OFF")).formatted(auto ? Formatting.GREEN : Formatting.GRAY));
        inv.setStack(SLOT_AUTO_OPEN, autoItem);

        inv.setStack(SLOT_SET_A_POS1, named(Items.GREEN_CONCRETE, "Set Area A Pos1"));
        inv.setStack(SLOT_SET_A_POS2, named(Items.GREEN_WOOL, "Set Area A Pos2"));
        inv.setStack(SLOT_SET_B_POS1, named(Items.RED_CONCRETE, "Set Area B Pos1"));
        inv.setStack(SLOT_SET_B_POS2, named(Items.RED_WOOL, "Set Area B Pos2"));
        inv.setStack(SLOT_SET_ARENA_POS1, named(Items.BLUE_CONCRETE, "Set Arena Pos1"));
        inv.setStack(SLOT_SET_ARENA_POS2, named(Items.BLUE_WOOL, "Set Arena Pos2"));
        inv.setStack(SLOT_SET_FIREWORKS_POS, named(Items.FIREWORK_ROCKET, "Set Win Fireworks Pos"));
    inv.setStack(SLOT_RESET_AREAS, named(Items.LAVA_BUCKET, "Reset Areas/Fireworks"));

        boolean rec = com.example.config.FeatureFlags.isBetReceipts();
        ItemStack recItem = new ItemStack(rec ? Items.PAPER : Items.MAP);
        recItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bet Receipts: " + (rec ? "ON" : "OFF")).formatted(rec ? Formatting.GREEN : Formatting.GRAY));
        inv.setStack(SLOT_TOGGLE_RECEIPTS, recItem);

        boolean snd = com.example.config.FeatureFlags.isSoundCues();
        ItemStack sndItem = new ItemStack(snd ? Items.NOTE_BLOCK : Items.BARRIER);
        sndItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Sound Cues: " + (snd ? "ON" : "OFF")).formatted(snd ? Formatting.GREEN : Formatting.GRAY));
        inv.setStack(SLOT_TOGGLE_SOUNDS, sndItem);

        boolean self = com.example.config.FeatureFlags.isSelfBetRestrict();
        ItemStack selfItem = new ItemStack(self ? Items.SHIELD : Items.TNT);
        selfItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Self-Bet Restrict: " + (self ? "ON" : "OFF")).formatted(self ? Formatting.GREEN : Formatting.GRAY));
        inv.setStack(SLOT_TOGGLE_SELF, selfItem);
    }

    private ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.AQUA));
        return s;
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == SLOT_TOGGLE) {
            BetManager.INSTANCE.setHousePays(!BetManager.INSTANCE.isHousePays());
            // save config
            JsonObject obj = ConfigManager.load();
            obj.addProperty("housePays", BetManager.INSTANCE.isHousePays());
            ConfigManager.save(obj);
            refreshButtons();
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                sp.sendMessage(Text.literal("House pays is now " + (BetManager.INSTANCE.isHousePays() ? "ENABLED" : "DISABLED")), false);
            }
            return;
        }
        if (slotIndex == SLOT_AUTO_OPEN) {
            RegionService.INSTANCE.setAutoOpenEnabled(!RegionService.INSTANCE.isAutoOpenEnabled());
            refreshButtons();
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                sp.sendMessage(Text.literal("Auto-Open is now " + (RegionService.INSTANCE.isAutoOpenEnabled() ? "ON" : "OFF")), false);
            }
            return;
        }
        if (slotIndex == SLOT_TOGGLE_RECEIPTS) {
            com.example.config.FeatureFlags.setBetReceipts(!com.example.config.FeatureFlags.isBetReceipts());
            com.example.config.FeatureFlags.saveToConfig();
            refreshButtons();
            return;
        }
        if (slotIndex == SLOT_TOGGLE_SOUNDS) {
            com.example.config.FeatureFlags.setSoundCues(!com.example.config.FeatureFlags.isSoundCues());
            com.example.config.FeatureFlags.saveToConfig();
            refreshButtons();
            return;
        }
        if (slotIndex == SLOT_TOGGLE_SELF) {
            com.example.config.FeatureFlags.setSelfBetRestrict(!com.example.config.FeatureFlags.isSelfBetRestrict());
            com.example.config.FeatureFlags.saveToConfig();
            refreshButtons();
            return;
        }

        if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            // Capture pos from player's current position in their world
            Identifier dim = sp.getServerWorld().getRegistryKey().getValue();
            BlockPos pos = sp.getBlockPos();
            if (slotIndex == SLOT_RESET_AREAS) {
                RegionService.INSTANCE.setAreaA(null);
                RegionService.INSTANCE.setAreaB(null);
                RegionService.INSTANCE.setArena(null);
                RegionService.INSTANCE.setFireworks(null, null);
                sp.sendMessage(Text.literal("Areas and fireworks positions reset."), false);
                return;
            }
            if (slotIndex == SLOT_SET_A_POS1 || slotIndex == SLOT_SET_A_POS2) {
                Region existing = RegionService.INSTANCE.getAreaA();
                BlockPos p1 = existing != null ? existing.min : pos;
                BlockPos p2 = existing != null ? existing.max : pos;
                if (slotIndex == SLOT_SET_A_POS1) p1 = pos; else p2 = pos;
                RegionService.INSTANCE.setAreaA(new Region(dim, p1, p2));
                sp.sendMessage(Text.literal("Area A updated."), false);
                return;
            }
            if (slotIndex == SLOT_SET_B_POS1 || slotIndex == SLOT_SET_B_POS2) {
                Region existing = RegionService.INSTANCE.getAreaB();
                BlockPos p1 = existing != null ? existing.min : pos;
                BlockPos p2 = existing != null ? existing.max : pos;
                if (slotIndex == SLOT_SET_B_POS1) p1 = pos; else p2 = pos;
                RegionService.INSTANCE.setAreaB(new Region(dim, p1, p2));
                sp.sendMessage(Text.literal("Area B updated."), false);
                return;
            }
            if (slotIndex == SLOT_SET_ARENA_POS1 || slotIndex == SLOT_SET_ARENA_POS2) {
                Region existing = RegionService.INSTANCE.getArena();
                BlockPos p1 = existing != null ? existing.min : pos;
                BlockPos p2 = existing != null ? existing.max : pos;
                if (slotIndex == SLOT_SET_ARENA_POS1) p1 = pos; else p2 = pos;
                RegionService.INSTANCE.setArena(new Region(dim, p1, p2));
                sp.sendMessage(Text.literal("Arena updated."), false);
                return;
            }
            if (slotIndex == SLOT_SET_FIREWORKS_POS) {
                RegionService.INSTANCE.setFireworks(dim, pos);
                sp.sendMessage(Text.literal("Win fireworks position set."), false);
                return;
            }
        }
        if (slotIndex >= 0 && slotIndex < SIZE) {
            return; // block decor movement
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }

    private static class MenuSlot extends Slot {
        public MenuSlot(Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
        @Override public boolean canInsert(ItemStack stack) { return false; }
    }
}
