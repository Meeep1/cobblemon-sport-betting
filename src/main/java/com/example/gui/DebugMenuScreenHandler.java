package com.example.gui;

import com.example.bet.BetManager;
import com.example.integration.CobblemonIntegration;
import com.example.region.RegionService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.registry.Registries;

import java.util.Comparator;
import java.util.List;

public class DebugMenuScreenHandler extends ScreenHandler {
    public static final int ROWS = 3;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    private static final int SLOT_SET_MATCH_NPC = 8;
    private static final int SLOT_SET_MATCH_NEAREST = 10;
    private static final int SLOT_SET_MATCH_DUMMY = 9;
    private static final int SLOT_SET_ODDS_EVEN = 11;
    private static final int SLOT_OPEN_BETTING = 12;
    private static final int SLOT_OPEN_BET_MENU = 13;
    private static final int SLOT_RESOLVE_A = 14;
    private static final int SLOT_RESOLVE_B = 15;
    private static final int SLOT_START_LIVE = 1;
    private static final int SLOT_STOP_LIVE = 2;
    private static final int SLOT_DEC_A = 3;
    private static final int SLOT_DEC_B = 4;
    private static final int SLOT_CLEAR_BETS = 16;
    private static final int SLOT_TOGGLE_HOUSE_PAYS = 19;
    private static final int SLOT_TEST_FIREWORK = 20;
    private static final int SLOT_SET_FIREWORKS_HERE = 21;

    private final net.minecraft.inventory.Inventory inv = new net.minecraft.inventory.SimpleInventory(SIZE);

    public DebugMenuScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId);

        // decor
        ItemStack pane = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
        pane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" ").formatted(Formatting.GRAY));
        for (int i = 0; i < SIZE; i++) inv.setStack(i, pane.copy());

        refreshButtons(playerInventory.player);

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

    private void refreshButtons(PlayerEntity player) {
        inv.setStack(SLOT_SET_MATCH_NPC, named(Items.VILLAGER_SPAWN_EGG, "Set Match: You vs NPC Trainer"));
    inv.setStack(SLOT_SET_MATCH_DUMMY, named(Items.WOODEN_SWORD, "Set Match: You vs Dummy"));
    inv.setStack(SLOT_SET_MATCH_NEAREST, named(Items.DIAMOND_SWORD, "Set Match: You vs Nearest"));
        inv.setStack(SLOT_SET_ODDS_EVEN, named(Items.PAPER, "Set Odds: Even (1.0x)"));
        inv.setStack(SLOT_OPEN_BETTING, named(Items.LIME_CONCRETE, "Open Betting"));
        inv.setStack(SLOT_OPEN_BET_MENU, named(Items.BOOK, "Open Bet Menu"));
        inv.setStack(SLOT_RESOLVE_A, named(Items.GREEN_CONCRETE, "Resolve Winner: A"));
        inv.setStack(SLOT_RESOLVE_B, named(Items.RED_CONCRETE, "Resolve Winner: B"));
    inv.setStack(SLOT_START_LIVE, named(Items.LIME_WOOL, "Start Live Now"));
    inv.setStack(SLOT_STOP_LIVE, named(Items.RED_WOOL, "Stop Live"));
    inv.setStack(SLOT_DEC_A, named(Items.GREEN_WOOL, "A: -1 remaining"));
    inv.setStack(SLOT_DEC_B, named(Items.RED_WOOL, "B: -1 remaining"));
        inv.setStack(SLOT_CLEAR_BETS, named(Items.BARRIER, "Clear Bets"));
        boolean hp = BetManager.INSTANCE.isHousePays();
        ItemStack house = new ItemStack(hp ? Items.EMERALD_BLOCK : Items.REDSTONE_BLOCK);
        house.set(DataComponentTypes.CUSTOM_NAME, Text.literal("House Pays: " + (hp ? "ENABLED" : "DISABLED")).formatted(hp ? Formatting.GREEN : Formatting.RED));
        inv.setStack(SLOT_TOGGLE_HOUSE_PAYS, house);
        inv.setStack(SLOT_TEST_FIREWORK, named(Items.FIREWORK_ROCKET, "Test Firework"));
        inv.setStack(SLOT_SET_FIREWORKS_HERE, named(Items.FIRE_CHARGE, "Set Fireworks Here"));
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
        if (!(player instanceof ServerPlayerEntity sp)) {
            if (slotIndex >= 0 && slotIndex < SIZE) return;
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }

        if (slotIndex == SLOT_SET_MATCH_NEAREST) {
            ServerPlayerEntity other = findNearestOther(sp, 64.0);
            if (other == null) {
                sp.sendMessage(Text.literal("No nearby players found."), false);
                return;
            }
            String a = sp.getGameProfile().getName();
            String b = other.getGameProfile().getName();
            BetManager.INSTANCE.setOdds(a, 100, b, 100);
            sp.sendMessage(Text.literal("Match set: " + a + " vs " + b + " (even odds)"), false);
            return;
        }
        if (slotIndex == SLOT_SET_MATCH_NPC) {
            String a = sp.getGameProfile().getName();
            LivingEntity npc = findNearestTrainerNpc(sp, 64.0);
            if (npc == null) {
                sp.sendMessage(Text.literal("No trainer NPCs found nearby."), false);
                return;
            }
            String b = npc.getDisplayName() != null ? npc.getDisplayName().getString() : "Trainer";
            if (b == null || b.isBlank()) b = "Trainer";
            BetManager.INSTANCE.setOdds(a, 100, b, 100);
            sp.sendMessage(Text.literal("Match set: " + a + " vs " + b + " (even odds)"), false);
            return;
        }
        if (slotIndex == SLOT_SET_MATCH_DUMMY) {
            String a = sp.getGameProfile().getName();
            String b = "Dummy";
            BetManager.INSTANCE.setOdds(a, 100, b, 100);
            sp.sendMessage(Text.literal("Match set: " + a + " vs Dummy (even odds)"), false);
            return;
        }
        if (slotIndex == SLOT_SET_ODDS_EVEN) {
            String a = BetManager.INSTANCE.getPlayerA();
            String b = BetManager.INSTANCE.getPlayerB();
            if (a == null || b == null) {
                sp.sendMessage(Text.literal("No players set. Use 'Set Match' first."), false);
                return;
            }
            BetManager.INSTANCE.setOdds(a, 100, b, 100);
            sp.sendMessage(Text.literal("Odds set to 1.0x for both."), false);
            return;
        }
        if (slotIndex == SLOT_OPEN_BETTING) {
            String a = BetManager.INSTANCE.getPlayerA();
            String b = BetManager.INSTANCE.getPlayerB();
            if (a == null || b == null) {
                sp.sendMessage(Text.literal("No players set. Use 'Set Match' first."), false);
                return;
            }
            BetManager.INSTANCE.openBettingForBattle(a, b);
            sp.sendMessage(Text.literal("Betting opened for " + a + " vs " + b), false);
            return;
        }
        if (slotIndex == SLOT_START_LIVE) {
            String a = BetManager.INSTANCE.getPlayerA();
            String b = BetManager.INSTANCE.getPlayerB();
            if (a == null || b == null) {
                sp.sendMessage(Text.literal("No players set. Use 'Set Match' first."), false);
                return;
            }
            // Close betting and show live bossbar
            BetManager.INSTANCE.closeBetting();
            RegionService.INSTANCE.onBattleLiveStart(a, b);
            // Initialize 6-6 if unset
            if (RegionService.INSTANCE.getLiveRemaining(a) < 0) RegionService.INSTANCE.setLiveRemaining(a, 6);
            if (RegionService.INSTANCE.getLiveRemaining(b) < 0) RegionService.INSTANCE.setLiveRemaining(b, 6);
            sp.sendMessage(Text.literal("Live started: " + a + " vs " + b), false);
            return;
        }
        if (slotIndex == SLOT_STOP_LIVE) {
            RegionService.INSTANCE.onBattleLiveEnd();
            sp.sendMessage(Text.literal("Live stopped."), false);
            return;
        }
        if (slotIndex == SLOT_DEC_A || slotIndex == SLOT_DEC_B) {
            String a = BetManager.INSTANCE.getPlayerA();
            String b = BetManager.INSTANCE.getPlayerB();
            if (a == null || b == null) {
                sp.sendMessage(Text.literal("No players set. Use 'Set Match' first."), false);
                return;
            }
            String target = (slotIndex == SLOT_DEC_A) ? a : b;
            RegionService.INSTANCE.adjustLiveRemaining(target, -1);
            int ra = RegionService.INSTANCE.getLiveRemaining(a);
            int rb = RegionService.INSTANCE.getLiveRemaining(b);
            sp.sendMessage(Text.literal("Remaining: " + a + "=" + ra + ", " + b + "=" + rb), false);
            return;
        }
        if (slotIndex == SLOT_OPEN_BET_MENU) {
            if (!BetManager.INSTANCE.isBettingOpen()) {
                sp.sendMessage(Text.literal("Betting is closed."), false);
                return;
            }
            sp.openHandledScreen(BettingMenuFactory.create());
            return;
        }
        if (slotIndex == SLOT_RESOLVE_A || slotIndex == SLOT_RESOLVE_B) {
            String a = BetManager.INSTANCE.getPlayerA();
            String b = BetManager.INSTANCE.getPlayerB();
            if (a == null || b == null) {
                sp.sendMessage(Text.literal("No players set. Use 'Set Match' first."), false);
                return;
            }
            String winner = slotIndex == SLOT_RESOLVE_A ? a : b;
            CobblemonIntegration.onBattleEnd(sp.getServer(), winner);
            sp.sendMessage(Text.literal("Resolved winner: " + winner), false);
            return;
        }
        if (slotIndex == SLOT_CLEAR_BETS) {
            BetManager.INSTANCE.clearBets();
            sp.sendMessage(Text.literal("Bets cleared."), false);
            return;
        }
        if (slotIndex == SLOT_TOGGLE_HOUSE_PAYS) {
            boolean newVal = !BetManager.INSTANCE.isHousePays();
            BetManager.INSTANCE.setHousePays(newVal);
            refreshButtons(sp);
            sp.sendMessage(Text.literal("House Pays is now " + (newVal ? "ENABLED" : "DISABLED")), false);
            return;
        }
        if (slotIndex == SLOT_TEST_FIREWORK) {
            RegionService.INSTANCE.triggerFireworkForDebug();
            sp.sendMessage(Text.literal("Firework triggered."), false);
            return;
        }
        if (slotIndex == SLOT_SET_FIREWORKS_HERE) {
            Identifier dim = sp.getServerWorld().getRegistryKey().getValue();
            BlockPos pos = sp.getBlockPos();
            RegionService.INSTANCE.setFireworks(dim, pos);
            sp.sendMessage(Text.literal("Fireworks location set here."), false);
            return;
        }

        if (slotIndex >= 0 && slotIndex < SIZE) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private ServerPlayerEntity findNearestOther(ServerPlayerEntity me, double maxDistance) {
        List<ServerPlayerEntity> list = me.getServerWorld().getPlayers(p -> p != me);
        return list.stream()
                .filter(p -> p.squaredDistanceTo(me) <= maxDistance * maxDistance)
                .min(Comparator.comparingDouble(p -> p.squaredDistanceTo(me)))
                .orElse(null);
    }

    private LivingEntity findNearestTrainerNpc(ServerPlayerEntity me, double maxDistance) {
        Box box = me.getBoundingBox().expand(maxDistance);
        List<LivingEntity> list = me.getServerWorld().getEntitiesByClass(LivingEntity.class, box, e -> !(e instanceof ServerPlayerEntity) && isTrainerLike(e));
        return list.stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(me)))
                .orElse(null);
    }

    private boolean isTrainerLike(LivingEntity e) {
        try {
            Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
            String ns = id != null ? id.getNamespace().toLowerCase() : "";
            String path = id != null ? id.getPath().toLowerCase() : "";
            // Heuristics for Radical Cobblemon Trainers and similar
            if (ns.contains("radical") || ns.contains("trainer") || ns.contains("rctrainer") || ns.contains("rctrainers")) return true;
            if (path.contains("trainer")) return true;
            String cls = e.getClass().getName().toLowerCase();
            if (cls.contains("trainer") && (cls.contains("cobblemon") || cls.contains("radical"))) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }

    private static class MenuSlot extends Slot {
        public MenuSlot(net.minecraft.inventory.Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
        @Override public boolean canInsert(ItemStack stack) { return false; }
    }
}
