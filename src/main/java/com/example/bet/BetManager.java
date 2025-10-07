package com.example.bet;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BetManager: manages odds, bets, currency, and resolution for the current battle.
 */
public class BetManager {
    public static final BetManager INSTANCE = new BetManager();

    // Active battlers and odds
    private String playerA; // display label for side A (can be team label)
    private int oddsA;
    private String playerB; // display label for side B (can be team label)
    private int oddsB;

    private boolean bettingOpen = false;

    // Pending and confirmed bets
    private final Map<UUID, Bet> pendingBets = new ConcurrentHashMap<>();
    private final List<Bet> confirmedBets = Collections.synchronizedList(new ArrayList<>());

    // Props/Parlays
    private final List<ParlayBet> parlayBets = Collections.synchronizedList(new ArrayList<>());

    // Team membership for current battle sides (store player names; case-insensitive checks)
    private final Set<String> teamA = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> teamB = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Currency tag for Numismatic coins; configurable via datapack tags
    // Use our mod id namespace so datapacks can tag items under `modid:currency`
    private static final TagKey<Item> CURRENCY_TAG = TagKey.of(RegistryKeys.ITEM, Identifier.of(com.example.ExampleMod.MOD_ID, "currency"));

    // Numismatic coin IDs
    private static final Identifier BRONZE_ID = Identifier.of("numismatic-overhaul", "bronze_coin");
    private static final Identifier SILVER_ID = Identifier.of("numismatic-overhaul", "silver_coin");
    private static final Identifier GOLD_ID = Identifier.of("numismatic-overhaul", "gold_coin");

    private static Item bronzeCoin() {
        Item it = Registries.ITEM.get(BRONZE_ID);
        return it != null ? it : Items.GOLD_NUGGET;
    }
    private static Item silverCoin() {
        Item it = Registries.ITEM.get(SILVER_ID);
        return it != null ? it : Items.IRON_NUGGET;
    }
    private static Item goldCoin() {
        Item it = Registries.ITEM.get(GOLD_ID);
        return it != null ? it : Items.GOLD_INGOT;
    }

    private BetManager() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {}

    public synchronized void setOdds(String playerA, int oddsA, String playerB, int oddsB) {
        this.playerA = playerA;
        this.oddsA = oddsA;
        this.playerB = playerB;
        this.oddsB = oddsB;
        this.bettingOpen = true;
        this.pendingBets.clear();
        this.confirmedBets.clear();
        this.parlayBets.clear();
    }

    public synchronized void openBettingForBattle(String playerA, String playerB) {
        if (!Objects.equals(this.playerA, playerA) || !Objects.equals(this.playerB, playerB)) {
            this.playerA = playerA;
            this.playerB = playerB;
            if (this.oddsA == 0 || this.oddsB == 0) {
                this.oddsA = 100;
                this.oddsB = 100;
            }
        }
        this.bettingOpen = true;
        this.pendingBets.clear();
        this.confirmedBets.clear();
        this.parlayBets.clear();
        // Default teams to single participants matching labels
        this.teamA.clear(); if (playerA != null) this.teamA.add(playerA);
        this.teamB.clear(); if (playerB != null) this.teamB.add(playerB);
    }

    /**
     * Open betting with team participants for each side (supports multi/doubles).
     * Display labels can be team-composed strings; membership sets are used for self-bet restriction
     * and to map faint/winner events.
     */
    public synchronized void openBettingForBattleTeams(String displayA, Set<String> namesA, String displayB, Set<String> namesB) {
        this.playerA = displayA;
        this.playerB = displayB;
        if (this.oddsA == 0) this.oddsA = 100;
        if (this.oddsB == 0) this.oddsB = 100;
        this.bettingOpen = true;
        this.pendingBets.clear();
        this.confirmedBets.clear();
        this.parlayBets.clear();
        this.teamA.clear();
        this.teamB.clear();
        if (namesA != null) this.teamA.addAll(namesA);
        if (namesB != null) this.teamB.addAll(namesB);
    }

    public synchronized void closeBetting() {
        this.bettingOpen = false;
        this.pendingBets.clear();
    }

    public boolean isBettingOpen() { return bettingOpen; }

    private boolean housePays = false;
    public boolean isHousePays() { return housePays; }
    public void setHousePays(boolean value) { this.housePays = value; }

    public OptionalInt getOddsForTarget(String targetName) {
        if (targetName == null) return OptionalInt.empty();
        if (targetName.equalsIgnoreCase(playerA)) return OptionalInt.of(oddsA);
        if (targetName.equalsIgnoreCase(playerB)) return OptionalInt.of(oddsB);
        return OptionalInt.empty();
    }

    public String getPlayerA() { return playerA; }
    public String getPlayerB() { return playerB; }
    public int getOddsA() { return oddsA; }
    public int getOddsB() { return oddsB; }
    public Set<String> getTeamA() { return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(teamA)); }
    public Set<String> getTeamB() { return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(teamB)); }
    public boolean isInTeamA(String name) { return name != null && teamA.stream().anyMatch(s -> s.equalsIgnoreCase(name)); }
    public boolean isInTeamB(String name) { return name != null && teamB.stream().anyMatch(s -> s.equalsIgnoreCase(name)); }

    public void setPendingBet(ServerPlayerEntity player, String targetName, int amount) {
        OptionalInt odds = getOddsForTarget(targetName);
        if (odds.isEmpty()) {
            player.sendMessage(Text.literal("Invalid bet target."), false);
            return;
        }
        if (!isBettingOpen()) {
            player.sendMessage(Text.literal("Betting is currently closed."), false);
            return;
        }
        if (com.example.config.FeatureFlags.isSelfBetRestrict()) {
            String pn = player.getGameProfile().getName();
            if (pn != null && (isInTeamA(pn) || isInTeamB(pn) || pn.equalsIgnoreCase(playerA) || pn.equalsIgnoreCase(playerB))) {
                player.sendMessage(Text.literal("You cannot bet on a match you're in."), false);
                return;
            }
        }
        pendingBets.put(player.getUuid(), new Bet(player.getUuid(), targetName, amount, odds.getAsInt()));
        player.sendMessage(Text.literal("Pending bet: " + amount + " on " + targetName + " @ " + (odds.getAsInt()/100.0) + "x"), false);
    }

    public Bet getPendingBet(UUID playerUuid) { return pendingBets.get(playerUuid); }

    public void clearPendingBet(UUID playerUuid) { pendingBets.remove(playerUuid); }

    public boolean confirmPendingBet(ServerPlayerEntity player) {
        if (!isBettingOpen()) {
            player.sendMessage(Text.literal("Betting is currently closed."), false);
            return false;
        }
        Bet bet = pendingBets.remove(player.getUuid());
        if (bet == null) {
            player.sendMessage(Text.literal("No pending bet to confirm."), false);
            return false;
        }
        int taken = takeCurrencyFromPlayer(player, bet.getAmount());
        if (taken < bet.getAmount()) {
            if (taken > 0) giveCurrencyToPlayer(player, taken);
            player.sendMessage(Text.literal("Not enough currency to place this bet."), false);
            return false;
        }
        confirmedBets.add(bet);
        player.sendMessage(Text.literal("Bet confirmed! Good luck."), false);
        if (com.example.config.FeatureFlags.isSoundCues()) {
            player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.2f);
        }
        if (com.example.config.FeatureFlags.isBetReceipts()) {
            ItemStack slip = new ItemStack(Items.PAPER, 1);
            slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Bet Slip: " + bet.getTargetName()).formatted(net.minecraft.util.Formatting.AQUA));
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Wager: " + bet.getAmount() + " bronze"));
            lore.add(Text.literal("Odds: " + (bet.getOddsMultiplier()/100.0) + "x"));
            slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
            slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (!player.giveItemStack(slip)) player.dropItem(slip, false);
        }
        return true;
    }

    public synchronized void resolveBets(MinecraftServer server, String winnerName) {
        this.bettingOpen = false;
        if (winnerName == null || winnerName.isEmpty()) return;

        // Build per-player summary for result slips
        class Summary { int stake; String target; boolean onWinner; }
        Map<UUID, Summary> summaries = new HashMap<>();
        for (Bet b : confirmedBets) {
            Summary s = summaries.computeIfAbsent(b.getPlayerUuid(), k -> new Summary());
            s.stake += b.getAmount();
            if (s.target == null) s.target = b.getTargetName();
            if (b.getTargetName().equalsIgnoreCase(winnerName)) s.onWinner = true;
        }

        Map<UUID, Integer> payouts = new HashMap<>();
        int totalLoserPool = 0;
        for (Bet bet : confirmedBets) {
            boolean win = bet.getTargetName().equalsIgnoreCase(winnerName);
            if (win) {
                int payout = (int)Math.floor(bet.getAmount() * (bet.getOddsMultiplier() / 100.0));
                payouts.merge(bet.getPlayerUuid(), payout, Integer::sum);
            } else {
                totalLoserPool += bet.getAmount();
            }
        }

        int sumRequested = payouts.values().stream().mapToInt(i -> i).sum();
        if (sumRequested <= 0) {
            if (com.example.config.FeatureFlags.isBetReceipts()) {
                for (Map.Entry<UUID, Summary> e : summaries.entrySet()) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey()); if (p == null) continue;
                    Summary s = e.getValue();
                    ItemStack slip = new ItemStack(Items.PAPER, 1);
                    slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Bet Result Slip").formatted(net.minecraft.util.Formatting.GOLD));
                    List<Text> lore = new ArrayList<>();
                    lore.add(Text.literal("Result: LOST").formatted(net.minecraft.util.Formatting.RED));
                    if (s.target != null) lore.add(Text.literal("Target: " + s.target));
                    lore.add(Text.literal("Lost: " + s.stake + " bronze"));
                    slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                    slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
                    if (!p.giveItemStack(slip)) p.dropItem(slip, false);
                }
            }
            confirmedBets.clear();
            return;
        }

        if (housePays) {
            for (Map.Entry<UUID, Integer> e : payouts.entrySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                if (player != null && e.getValue() > 0) {
                    giveCurrencyToPlayer(player, e.getValue());
                    player.sendMessage(Text.literal("Bet won! (House pays) Payout: " + e.getValue() + " coins."), false);
                }
            }
            if (com.example.config.FeatureFlags.isBetReceipts()) {
                for (Map.Entry<UUID, Summary> e : summaries.entrySet()) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey()); if (p == null) continue;
                    Summary s = e.getValue(); boolean win = s.onWinner;
                    ItemStack slip = new ItemStack(Items.PAPER, 1);
                    slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Bet Result Slip").formatted(net.minecraft.util.Formatting.GOLD));
                    List<Text> lore = new ArrayList<>();
                    if (win) {
                        int pay = payouts.getOrDefault(e.getKey(), 0);
                        lore.add(Text.literal("Result: WON (House Pays)").formatted(net.minecraft.util.Formatting.GREEN));
                        if (s.target != null) lore.add(Text.literal("Target: " + s.target));
                        lore.add(Text.literal("Stake: " + s.stake + " bronze"));
                        lore.add(Text.literal("Payout: " + pay + " bronze"));
                        slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                    } else {
                        lore.add(Text.literal("Result: LOST").formatted(net.minecraft.util.Formatting.RED));
                        if (s.target != null) lore.add(Text.literal("Target: " + s.target));
                        lore.add(Text.literal("Lost: " + s.stake + " bronze"));
                        slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
                    }
                    slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                    if (!p.giveItemStack(slip)) p.dropItem(slip, false);
                }
            }
        } else {
            if (totalLoserPool == 0) {
                Map<UUID, Integer> refunds = new HashMap<>();
                for (Bet bet : confirmedBets) {
                    if (bet.getTargetName().equalsIgnoreCase(winnerName)) {
                        refunds.merge(bet.getPlayerUuid(), bet.getAmount(), Integer::sum);
                    }
                }
                for (Map.Entry<UUID, Integer> e : refunds.entrySet()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                    if (player != null && e.getValue() > 0) {
                        giveCurrencyToPlayer(player, e.getValue());
                        player.sendMessage(Text.literal("No opposing bets. Your stake was refunded: " + e.getValue() + " coins."), false);
                    }
                }
                if (com.example.config.FeatureFlags.isBetReceipts()) {
                    for (Map.Entry<UUID, Summary> e : summaries.entrySet()) {
                        if (!e.getValue().onWinner) continue;
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey()); if (p == null) continue;
                        int ref = refunds.getOrDefault(e.getKey(), 0);
                        ItemStack slip = new ItemStack(Items.PAPER, 1);
                        slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Bet Result Slip").formatted(net.minecraft.util.Formatting.GOLD));
                        List<Text> lore = new ArrayList<>();
                        lore.add(Text.literal("Result: REFUND").formatted(net.minecraft.util.Formatting.YELLOW));
                        if (e.getValue().target != null) lore.add(Text.literal("Target: " + e.getValue().target));
                        lore.add(Text.literal("Refunded: " + ref + " bronze"));
                        slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                        slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
                        if (!p.giveItemStack(slip)) p.dropItem(slip, false);
                    }
                }
                confirmedBets.clear();
                return;
            }

            double scale = Math.min(1.0, totalLoserPool / (double) sumRequested);
            List<Map.Entry<UUID, Integer>> winners = new ArrayList<>(payouts.entrySet());
            Map<UUID, Integer> pay = new HashMap<>();
            double[] fractions = new double[winners.size()];
            int sumFloors = 0;
            for (int i = 0; i < winners.size(); i++) {
                Map.Entry<UUID, Integer> e = winners.get(i);
                double exact = e.getValue() * scale;
                int floor = (int)Math.floor(exact);
                pay.put(e.getKey(), floor);
                fractions[i] = exact - floor;
                sumFloors += floor;
            }
            int remainder = Math.max(0, totalLoserPool - sumFloors);
            while (remainder > 0) {
                int bestIndex = -1; double bestFrac = -1;
                for (int i = 0; i < winners.size(); i++) {
                    if (fractions[i] > bestFrac) { bestFrac = fractions[i]; bestIndex = i; }
                }
                if (bestIndex < 0 || bestFrac <= 0) break;
                UUID u = winners.get(bestIndex).getKey();
                pay.put(u, pay.getOrDefault(u, 0) + 1);
                fractions[bestIndex] = 0; remainder--;
            }
            for (Map.Entry<UUID, Integer> e : pay.entrySet()) {
                int adjusted = e.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                if (player != null && adjusted > 0) {
                    giveCurrencyToPlayer(player, adjusted);
                    if (scale < 1.0) {
                        int pct = (int)Math.floor(scale * 100);
                        player.sendMessage(Text.literal("Payout scaled to ~" + pct + "% due to small loser pool. You received: " + adjusted + " coins."), false);
                    } else {
                        player.sendMessage(Text.literal("Bet won! Payout: " + adjusted + " coins."), false);
                    }
                }
            }
            if (com.example.config.FeatureFlags.isBetReceipts()) {
                int pct = (int)Math.floor(scale * 100);
                for (Map.Entry<UUID, Summary> e : summaries.entrySet()) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey()); if (p == null) continue;
                    Summary s = e.getValue(); boolean win = s.onWinner;
                    ItemStack slip = new ItemStack(Items.PAPER, 1);
                    slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Bet Result Slip").formatted(net.minecraft.util.Formatting.GOLD));
                    List<Text> lore = new ArrayList<>();
                    if (win) {
                        int adjusted = pay.getOrDefault(e.getKey(), 0);
                        lore.add(Text.literal("Result: WON" + (scale < 1.0 ? " (Scaled ~" + pct + "%)" : "")).formatted(net.minecraft.util.Formatting.GREEN));
                        if (s.target != null) lore.add(Text.literal("Target: " + s.target));
                        lore.add(Text.literal("Stake: " + s.stake + " bronze"));
                        lore.add(Text.literal("Payout: " + adjusted + " bronze"));
                        slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                    } else {
                        lore.add(Text.literal("Result: LOST").formatted(net.minecraft.util.Formatting.RED));
                        if (s.target != null) lore.add(Text.literal("Target: " + s.target));
                        lore.add(Text.literal("Lost: " + s.stake + " bronze"));
                        slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
                    }
                    slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                    if (!p.giveItemStack(slip)) p.dropItem(slip, false);
                }
            }
        }

        confirmedBets.clear();

        // Resolve parlay/prop bets with result slips
        if (!parlayBets.isEmpty()) {
            for (ParlayBet pb : new ArrayList<>(parlayBets)) {
                boolean allWin = true; double combined = 1.0; String failDesc = null;
                for (PropCondition leg : pb.getLegs()) {
                    boolean win = evaluateProp(server, leg, winnerName);
                    if (!win) {
                        allWin = false;
                        failDesc = switch (leg.getType()) {
                            case FIRST_FAINT -> "First Faint " + leg.getParam();
                            case TOTAL_FAINTS_OVER -> "Total Faints > " + leg.getParam();
                        };
                        break;
                    }
                    combined *= Math.max(1.0, leg.getOddsMultiplier() / 100.0);
                }
                if (allWin) {
                    int payout = (int)Math.floor(pb.getStakeBronze() * combined);
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(pb.getPlayerUuid());
                    if (player != null && payout > 0) {
                        giveCurrencyToPlayer(player, payout);
                        player.sendMessage(Text.literal("Prop/Parlay won! Payout: " + payout + " coins."), false);
                        if (com.example.config.FeatureFlags.isBetReceipts()) {
                            ItemStack slip = new ItemStack(Items.PAPER, 1);
                            slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Parlay Result Slip").formatted(net.minecraft.util.Formatting.AQUA));
                            List<Text> lore = new ArrayList<>();
                            lore.add(Text.literal("Result: WON").formatted(net.minecraft.util.Formatting.GREEN));
                            lore.add(Text.literal("Stake: " + pb.getStakeBronze() + " bronze"));
                            lore.add(Text.literal(String.format("Combined: %.2fx", combined)));
                            for (PropCondition leg : pb.getLegs()) {
                                String line = switch (leg.getType()) {
                                    case FIRST_FAINT -> "Leg: First Faint " + leg.getParam() + " @ " + (leg.getOddsMultiplier()/100.0) + "x";
                                    case TOTAL_FAINTS_OVER -> "Leg: Total Faints > " + leg.getParam() + " @ " + (leg.getOddsMultiplier()/100.0) + "x";
                                };
                                lore.add(Text.literal(line));
                            }
                            slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                            slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                            if (!player.giveItemStack(slip)) player.dropItem(slip, false);
                        }
                    }
                } else {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(pb.getPlayerUuid());
                    if (player != null && com.example.config.FeatureFlags.isBetReceipts()) {
                        ItemStack slip = new ItemStack(Items.PAPER, 1);
                        slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Parlay Result Slip").formatted(net.minecraft.util.Formatting.AQUA));
                        List<Text> lore = new ArrayList<>();
                        lore.add(Text.literal("Result: LOST").formatted(net.minecraft.util.Formatting.RED));
                        lore.add(Text.literal("Stake: " + pb.getStakeBronze() + " bronze"));
                        if (failDesc != null) lore.add(Text.literal("Failed leg: " + failDesc));
                        for (PropCondition leg : pb.getLegs()) {
                            String line = switch (leg.getType()) {
                                case FIRST_FAINT -> "Leg: First Faint " + leg.getParam() + " @ " + (leg.getOddsMultiplier()/100.0) + "x";
                                case TOTAL_FAINTS_OVER -> "Leg: Total Faints > " + leg.getParam() + " @ " + (leg.getOddsMultiplier()/100.0) + "x";
                            };
                            lore.add(Text.literal(line));
                        }
                        slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                        slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
                        if (!player.giveItemStack(slip)) player.dropItem(slip, false);
                    }
                }
            }
            parlayBets.clear();
        }
    }

    public synchronized void clearBets() {
        pendingBets.clear();
        confirmedBets.clear();
        parlayBets.clear();
        playerA = null;
        playerB = null;
        oddsA = 0;
        oddsB = 0;
        bettingOpen = false;
    }

    public boolean addParlayBetPrepaid(ServerPlayerEntity player, List<PropCondition> legs, int prepaidBronze) {
        if (prepaidBronze <= 0 || legs == null || legs.isEmpty() || legs.size() > 2) {
            player.sendMessage(Text.literal("Invalid parlay."), false);
            return false;
        }
        parlayBets.add(new ParlayBet(player.getUuid(), new ArrayList<>(legs), prepaidBronze));
        player.sendMessage(Text.literal("Parlay placed! Legs: " + legs.size()), false);
        if (com.example.config.FeatureFlags.isSoundCues()) {
            player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.2f);
        }
        if (com.example.config.FeatureFlags.isBetReceipts()) {
            ItemStack slip = new ItemStack(Items.PAPER, 1);
            slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Parlay Slip: " + legs.size() + " legs").formatted(net.minecraft.util.Formatting.AQUA));
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Stake: " + prepaidBronze + " bronze"));
            for (PropCondition leg : legs) {
                String line = switch (leg.getType()) {
                    case FIRST_FAINT -> "First Faint " + leg.getParam() + " @ " + (leg.getOddsMultiplier()/100.0) + "x";
                    case TOTAL_FAINTS_OVER -> "Total Faints > " + leg.getParam() + " @ " + (leg.getOddsMultiplier()/100.0) + "x";
                };
                lore.add(Text.literal(line));
            }
            slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
            slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (!player.giveItemStack(slip)) player.dropItem(slip, false);
        }
        return true;
    }

    private boolean evaluateProp(MinecraftServer server, PropCondition leg, String winnerName) {
        if (leg == null) return false;
        var rs = com.example.region.RegionService.INSTANCE;
        switch (leg.getType()) {
            case FIRST_FAINT -> {
                String expected = leg.getParam();
                String first = rs.getFirstFaintSide();
                if (first == null) return false;
                String aName = getPlayerA();
                String bName = getPlayerB();
                if (aName == null || bName == null) return false;
                String check = expected.equalsIgnoreCase("A") ? aName : bName;
                return first.equalsIgnoreCase(check);
            }
            case TOTAL_FAINTS_OVER -> {
                int thr;
                try { thr = Integer.parseInt(leg.getParam()); } catch (Exception e) { return false; }
                return rs.getTotalFaints() > thr;
            }
        }
        return false;
    }

    // Currency helpers
    public int countCurrency(ServerPlayerEntity player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isCurrency(stack)) total += toBronzeUnits(stack);
        }
        return total;
    }

    public int takeCurrencyFromPlayer(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return 0;
        int remaining = amount;
        int removedBronze = 0;
        // Iterate inventory and remove items by their bronze value, giving change if we overshoot.
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!isCurrency(stack)) continue;
            // Determine bronze value per single item of this stack
            ItemStack one = stack.copy();
            one.setCount(1);
            int perItem = toBronzeUnits(one);
            if (perItem <= 0) continue;
            // How many of this item do we need at most?
            int maxUsable = stack.getCount();
            // Number of items needed to cover remaining
            int needItems = Math.min(maxUsable, (int) Math.ceil(remaining / (double) perItem));
            if (needItems <= 0) continue;
            stack.decrement(needItems);
            int value = needItems * perItem;
            removedBronze += value;
            remaining = Math.max(0, amount - removedBronze);
        }
        // If we removed more bronze value than needed, return change
        if (removedBronze > amount) {
            giveCurrencyToPlayer(player, removedBronze - amount);
        }
        // If we couldn't cover full amount, return the bronze actually removed (caller may refund)
        return Math.min(amount, removedBronze);
    }

    public void giveCurrencyToPlayer(ServerPlayerEntity player, int bronzeAmount) {
        if (bronzeAmount <= 0) return;
        int gold = bronzeAmount / 10000;
        int rem = bronzeAmount % 10000;
        int silver = rem / 100;
        int bronze = rem % 100;

        giveStacks(player, goldCoin(), gold);
        giveStacks(player, silverCoin(), silver);
        giveStacks(player, bronzeCoin(), bronze);
    }

    public boolean isCurrency(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isIn(CURRENCY_TAG)) return true;
        return stack.isOf(bronzeCoin()) || stack.isOf(silverCoin()) || stack.isOf(goldCoin());
    }

    private void giveStacks(ServerPlayerEntity player, Item item, int totalCount) {
        while (totalCount > 0) {
            int give = Math.min(totalCount, 64);
            ItemStack stack = new ItemStack(item, give);
            if (!player.giveItemStack(stack)) player.dropItem(stack, false);
            totalCount -= give;
        }
    }

    public int toBronzeUnits(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id != null) {
            if (id.equals(GOLD_ID)) return stack.getCount() * 10000;
            if (id.equals(SILVER_ID)) return stack.getCount() * 100;
            if (id.equals(BRONZE_ID)) return stack.getCount();
        }
        if (stack.isOf(goldCoin())) return stack.getCount() * 10000;
        if (stack.isOf(silverCoin())) return stack.getCount() * 100;
        if (stack.isOf(bronzeCoin())) return stack.getCount();
        return stack.getCount();
    }

    public synchronized int getConfirmedAmountFor(UUID player) {
        return confirmedBets.stream().filter(b -> b.getPlayerUuid().equals(player)).mapToInt(Bet::getAmount).sum();
    }

    public synchronized String getConfirmedTargetFor(UUID player) {
        String target = null;
        for (Bet b : confirmedBets) {
            if (!b.getPlayerUuid().equals(player)) continue;
            if (target == null) target = b.getTargetName();
            else if (!target.equalsIgnoreCase(b.getTargetName())) return null;
        }
        return target;
    }

    public synchronized int refundConfirmedBetsFor(ServerPlayerEntity player) {
        if (!bettingOpen) return 0;
        int sum = 0;
        Iterator<Bet> it = confirmedBets.iterator();
        while (it.hasNext()) {
            Bet b = it.next();
            if (b.getPlayerUuid().equals(player.getUuid())) {
                sum += b.getAmount();
                it.remove();
            }
        }
        if (sum > 0) {
            giveCurrencyToPlayer(player, sum);
            player.sendMessage(Text.literal("Your bet was refunded: " + sum + " bronze units."), false);
        }
        return sum;
    }

    public boolean confirmPendingBetPrepaid(ServerPlayerEntity player, int prepaidBronze) {
        if (!isBettingOpen()) {
            player.sendMessage(Text.literal("Betting is currently closed."), false);
            return false;
        }
        Bet bet = pendingBets.remove(player.getUuid());
        if (bet == null) {
            player.sendMessage(Text.literal("No pending bet to confirm."), false);
            return false;
        }
        if (prepaidBronze <= 0) {
            player.sendMessage(Text.literal("Stake amount is zero."), false);
            return false;
        }
        Bet finalized = new Bet(bet.getPlayerUuid(), bet.getTargetName(), prepaidBronze, bet.getOddsMultiplier());
        confirmedBets.add(finalized);
        player.sendMessage(Text.literal("Bet confirmed! Wager: " + prepaidBronze + " bronze units."), false);
        if (com.example.config.FeatureFlags.isSoundCues()) {
            player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.2f);
        }
        if (com.example.config.FeatureFlags.isBetReceipts()) {
            ItemStack slip = new ItemStack(Items.PAPER, 1);
            slip.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Bet Slip: " + finalized.getTargetName()).formatted(net.minecraft.util.Formatting.AQUA));
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Wager: " + finalized.getAmount() + " bronze"));
            lore.add(Text.literal("Odds: " + (finalized.getOddsMultiplier()/100.0) + "x"));
            slip.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
            slip.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (!player.giveItemStack(slip)) player.dropItem(slip, false);
        }
        return true;
    }
}
