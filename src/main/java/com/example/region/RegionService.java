package com.example.region;

import com.example.bet.BetManager;
import com.example.bet.PredefinedOddsRegistry;
import com.example.config.ConfigManager;
import com.example.integration.CobblemonIntegration;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class RegionService implements ServerTickEvents.EndTick {
    public static final RegionService INSTANCE = new RegionService();

    private boolean autoOpenEnabled = false;
    private Region areaA;
    private Region areaB;
    private Region arena;

    // Bossbar for odds/winner
    private ServerBossBar oddsBar;

    // Track previous occupants for entry notifications
    private final HashSet<UUID> lastInA = new HashSet<>();
    private final HashSet<UUID> lastInB = new HashSet<>();

    // Winner announcement state
    private String lastWinnerName = null;
    private int winnerTicksRemaining = 0; // ticks to continue showing winner bar
    private int rainbowIndex = 0;
    private int winnerDelayTicks = 0; // during this delay, players inside start zones don't see the winner bar
    // Cooldown before a new match can auto-open after a winner is announced
    private int matchCooldownTicks = 0; // when > 0, auto-open is disabled and odds won't reappear
    private static final int DEFAULT_MATCH_COOLDOWN_TICKS = 100; // ~5 seconds
    private static final BossBar.Color[] RAINBOW = new BossBar.Color[] {
        BossBar.Color.PINK, BossBar.Color.RED, BossBar.Color.YELLOW, BossBar.Color.GREEN, BossBar.Color.BLUE, BossBar.Color.PURPLE, BossBar.Color.WHITE
    };

    // Fireworks config
    private Identifier fireworksDimension = null;
    private BlockPos fireworksPos = null;

    // Cache server reference for use by external integrations
    private volatile MinecraftServer lastServer;

    // Live battle state for bossbar display
    private boolean liveBattleActive = false;
    private String liveA = null; // display label (may be team formatted)
    private String liveB = null;
    private java.util.Set<String> liveTeamA = new java.util.HashSet<>();
    private java.util.Set<String> liveTeamB = new java.util.HashSet<>();
    private int liveRemainingA = -1;
    private int liveRemainingB = -1;

    // Props context tracking
    private String firstFaintSide = null; // equals liveA or liveB when set
    private int totalFaints = 0;

    // Auto-open stabilization: wait briefly after both sides present to allow teammates to step in
    private int autoOpenTimer = -1; // -1 = inactive; when >=0 counts down to open
    private static final int AUTO_OPEN_STABILIZE_TICKS = 40; // ~2 seconds

    private RegionService() {
        loadFromConfig();
        ServerTickEvents.END_SERVER_TICK.register(this);
    }

    public boolean isAutoOpenEnabled() { return autoOpenEnabled; }
    public void setAutoOpenEnabled(boolean v) { autoOpenEnabled = v; saveToConfig(); }
    public Region getAreaA() { return areaA; }
    public Region getAreaB() { return areaB; }
    public Region getArena() { return arena; }

    public void setAreaA(Region r) { this.areaA = r; saveToConfig(); }
    public void setAreaB(Region r) { this.areaB = r; saveToConfig(); }
    public void setArena(Region r) { this.arena = r; saveToConfig(); }

    public void setFireworks(Identifier dim, BlockPos pos) {
        this.fireworksDimension = dim;
        this.fireworksPos = pos;
        saveToConfig();
    }

    private void loadFromConfig() {
        JsonObject obj = ConfigManager.load();
        if (obj.has("autoOpen")) autoOpenEnabled = obj.get("autoOpen").getAsBoolean();
        if (obj.has("areaA")) areaA = parseRegion(obj.getAsJsonObject("areaA"));
        if (obj.has("areaB")) areaB = parseRegion(obj.getAsJsonObject("areaB"));
        if (obj.has("arena")) arena = parseRegion(obj.getAsJsonObject("arena"));
        if (obj.has("fireworks") && obj.get("fireworks").isJsonObject()) {
            JsonObject fw = obj.getAsJsonObject("fireworks");
            try {
                this.fireworksDimension = Identifier.of(fw.get("dimension").getAsString());
                JsonObject p = fw.getAsJsonObject("pos");
                this.fireworksPos = new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
            } catch (Exception ignored) { }
        }
    }

    private void saveToConfig() {
        JsonObject obj = ConfigManager.load();
        obj.addProperty("autoOpen", autoOpenEnabled);
        if (areaA != null) obj.add("areaA", regionToJson(areaA)); else obj.remove("areaA");
        if (areaB != null) obj.add("areaB", regionToJson(areaB)); else obj.remove("areaB");
        if (arena != null) obj.add("arena", regionToJson(arena)); else obj.remove("arena");
        if (fireworksDimension != null && fireworksPos != null) {
            JsonObject fw = new JsonObject();
            fw.addProperty("dimension", fireworksDimension.toString());
            JsonObject p = new JsonObject();
            p.addProperty("x", fireworksPos.getX());
            p.addProperty("y", fireworksPos.getY());
            p.addProperty("z", fireworksPos.getZ());
            fw.add("pos", p);
            obj.add("fireworks", fw);
        } else { obj.remove("fireworks"); }
        ConfigManager.save(obj);
    }

    private JsonObject regionToJson(Region r) {
        JsonObject o = new JsonObject();
        o.addProperty("dimension", r.dimension.toString());
        JsonObject min = new JsonObject();
        min.addProperty("x", r.min.getX()); min.addProperty("y", r.min.getY()); min.addProperty("z", r.min.getZ());
        JsonObject max = new JsonObject();
        max.addProperty("x", r.max.getX()); max.addProperty("y", r.max.getY()); max.addProperty("z", r.max.getZ());
        o.add("min", min); o.add("max", max);
        return o;
    }

    private Region parseRegion(JsonObject o) {
        try {
            Identifier dim = Identifier.of(o.get("dimension").getAsString());
            JsonObject min = o.getAsJsonObject("min");
            JsonObject max = o.getAsJsonObject("max");
            BlockPos a = new BlockPos(min.get("x").getAsInt(), min.get("y").getAsInt(), min.get("z").getAsInt());
            BlockPos b = new BlockPos(max.get("x").getAsInt(), max.get("y").getAsInt(), max.get("z").getAsInt());
            return new Region(dim, a, b);
        } catch (Exception e) { return null; }
    }

    @Override
    public void onEndTick(MinecraftServer server) {
        // cache server each tick
        this.lastServer = server;
        // decrement cooldown if active
        if (matchCooldownTicks > 0) matchCooldownTicks--;

        boolean canAutoOpen = autoOpenEnabled && areaA != null && areaB != null && Objects.equals(areaA.dimension, areaB.dimension) && matchCooldownTicks <= 0;
        if (canAutoOpen) {
            RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, areaA.dimension);
            ServerWorld world = server.getWorld(dimKey);
            if (world != null) {
                // find occupants
                List<ServerPlayerEntity> inA = world.getPlayers(p -> areaA.contains(p.getBlockPos()));
                List<ServerPlayerEntity> inB = world.getPlayers(p -> areaB.contains(p.getBlockPos()));

                // Entry messages for new occupants
                HashSet<UUID> currA = inA.stream().map(ServerPlayerEntity::getUuid).collect(Collectors.toCollection(HashSet::new));
                HashSet<UUID> currB = inB.stream().map(ServerPlayerEntity::getUuid).collect(Collectors.toCollection(HashSet::new));
                for (ServerPlayerEntity p : inA) {
                    if (!lastInA.contains(p.getUuid())) {
                        p.sendMessage(Text.literal("You entered Area A").formatted(Formatting.GREEN), true);
                    }
                }
                for (ServerPlayerEntity p : inB) {
                    if (!lastInB.contains(p.getUuid())) {
                        p.sendMessage(Text.literal("You entered Area B").formatted(Formatting.RED), true);
                    }
                }
                lastInA.clear(); lastInA.addAll(currA);
                lastInB.clear(); lastInB.addAll(currB);

                if (!BetManager.INSTANCE.isBettingOpen()) {
                    // Stabilization window: start timer when both sides present; reset if either side becomes empty
                    if (inA.size() >= 1 && inB.size() >= 1) {
                        if (autoOpenTimer < 0) autoOpenTimer = AUTO_OPEN_STABILIZE_TICKS;
                        else if (autoOpenTimer > 0) autoOpenTimer--;
                    } else {
                        autoOpenTimer = -1;
                    }

                    if (autoOpenTimer == 0) {
                        // Build team names
                        java.util.LinkedHashSet<String> namesA = inA.stream().map(p -> p.getGameProfile().getName()).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                        java.util.LinkedHashSet<String> namesB = inB.stream().map(p -> p.getGameProfile().getName()).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                        String dispA = String.join(" & ", namesA);
                        String dispB = String.join(" & ", namesB);

                        // Choose odds: dynamic (if enabled) -> predefined team -> predefined pair (if singles) -> default 1.0x
                        boolean usedOdds = false;
                        if (com.example.config.DynamicOddsConfig.isEnabled()) {
                            var dyn = com.example.stats.DynamicOddsService.INSTANCE.compute(namesA, namesB);
                            if (dyn != null) {
                                BetManager.INSTANCE.setOdds(dispA, dyn.oddsA, dispB, dyn.oddsB);
                                usedOdds = true;
                            }
                        }
                        if (!usedOdds && (namesA.size() > 1 || namesB.size() > 1)) {
                            var teamOdds = PredefinedOddsRegistry.INSTANCE.getTeam(namesA, namesB);
                            if (teamOdds.isPresent()) {
                                BetManager.INSTANCE.setOdds(dispA, teamOdds.get().oddsA, dispB, teamOdds.get().oddsB);
                                usedOdds = true;
                            }
                        }
                        if (!usedOdds) {
                            if (namesA.size() == 1 && namesB.size() == 1) {
                                String aName = namesA.iterator().next();
                                String bName = namesB.iterator().next();
                                PredefinedOddsRegistry.INSTANCE.get(aName, bName).ifPresentOrElse(pair -> {
                                    BetManager.INSTANCE.setOdds(dispA, pair.oddsA, dispB, pair.oddsB);
                                }, () -> {
                                    BetManager.INSTANCE.setOdds(dispA, 100, dispB, 100);
                                });
                            } else {
                                // default team odds
                                BetManager.INSTANCE.setOdds(dispA, 100, dispB, 100);
                            }
                        }

                        // Open team betting mode and announce. Do NOT start live mode here;
                        // let Cobblemon hooks transition to live when the battle actually begins.
                        BetManager.INSTANCE.openBettingForBattleTeams(dispA, namesA, dispB, namesB);
                        announceBetsOpen(server, dispA, dispB);
                        autoOpenTimer = -1;
                    }
                }
            }
        }
        // Always keep the bossbar refreshed (odds or winner) if an arena exists
        updateBossbar(server);
    }

    private void announceBetsOpen(MinecraftServer server, String a, String b) {
        if (arena == null) return;
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, arena.dimension);
        ServerWorld w = server.getWorld(dimKey);
        if (w == null) return;
        Text title = Text.literal("Bets Open: " + a + " vs " + b).formatted(Formatting.GOLD);
        for (ServerPlayerEntity p : w.getPlayers(player -> arena.contains(player.getBlockPos()))) {
            p.sendMessage(title, true); // action bar
            if (com.example.config.FeatureFlags.isSoundCues()) p.playSound(net.minecraft.sound.SoundEvents.UI_TOAST_IN, 1.0f, 1.2f);
        }
    }

    private void updateBossbar(MinecraftServer server) {
        if (arena == null) return;
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, arena.dimension);
        ServerWorld w = server.getWorld(dimKey);
        if (w == null) return;

        // If we're in cooldown, suppress odds bar entirely
        if (matchCooldownTicks > 0 && !liveBattleActive) {
            if (oddsBar != null) {
                for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) oddsBar.removePlayer(pl);
                oddsBar = null;
            }
            return;
        }

        if (liveBattleActive) {
            // Show live status: A vs B — A: x left | B: y left
            String a = liveA != null ? liveA : (BetManager.INSTANCE.getPlayerA() != null ? BetManager.INSTANCE.getPlayerA() : "A");
            String b = liveB != null ? liveB : (BetManager.INSTANCE.getPlayerB() != null ? BetManager.INSTANCE.getPlayerB() : "B");
            String leftA = liveRemainingA >= 0 ? ("" + liveRemainingA) : "?";
            String leftB = liveRemainingB >= 0 ? ("" + liveRemainingB) : "?";
            String text = String.format("%s vs %s — %s left: %s | %s left: %s", a, b, a, leftA, b, leftB);
            if (oddsBar == null) {
                oddsBar = new ServerBossBar(Text.literal(text), BossBar.Color.BLUE, BossBar.Style.NOTCHED_20);
                oddsBar.setPercent(1.0f);
            } else {
                oddsBar.setName(Text.literal(text));
                oddsBar.setColor(BossBar.Color.BLUE);
            }
            List<ServerPlayerEntity> viewers = w.getPlayers(p -> arena.contains(p.getBlockPos()));
            if (oddsBar != null) {
                for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) oddsBar.removePlayer(pl);
                for (ServerPlayerEntity pl : viewers) oddsBar.addPlayer(pl);
            }
            return;
        }

        if (BetManager.INSTANCE.isBettingOpen()) {
            String a = BetManager.INSTANCE.getPlayerA();
            String b = BetManager.INSTANCE.getPlayerB();
            int oa = Math.max(BetManager.INSTANCE.getOddsA(), 1);
            int ob = Math.max(BetManager.INSTANCE.getOddsB(), 1);
            String text = String.format("Odds: %s %.2fx | %s %.2fx", a != null ? a : "A", oa/100.0, b != null ? b : "B", ob/100.0);
            if (oddsBar == null) {
                oddsBar = new ServerBossBar(Text.literal(text), BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);
                oddsBar.setPercent(1.0f);
            } else {
                oddsBar.setName(Text.literal(text));
            }
            // add viewers in arena
            List<ServerPlayerEntity> viewers = w.getPlayers(p -> arena.contains(p.getBlockPos()));
            if (oddsBar != null) {
                for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) oddsBar.removePlayer(pl);
                for (ServerPlayerEntity pl : viewers) oddsBar.addPlayer(pl);
            }
        } else {
            // If there is a recent winner, show rainbow winner bar for a bit
            if (winnerTicksRemaining > 0 && lastWinnerName != null) {
                String text = "Winner: " + lastWinnerName;
                if (oddsBar == null) {
                    oddsBar = new ServerBossBar(Text.literal(text), RAINBOW[rainbowIndex % RAINBOW.length], BossBar.Style.NOTCHED_10);
                    oddsBar.setPercent(1.0f);
                } else {
                    oddsBar.setName(Text.literal(text));
                    oddsBar.setColor(RAINBOW[rainbowIndex % RAINBOW.length]);
                }
                rainbowIndex++;
                winnerTicksRemaining--;
                // Apply delay filter: hide for players standing in Area A or B for the first few ticks
                final boolean delayActive = winnerDelayTicks > 0;
                if (winnerDelayTicks > 0) winnerDelayTicks--;
                List<ServerPlayerEntity> viewers = w.getPlayers(p -> {
                    boolean inArena = arena.contains(p.getBlockPos());
                    if (!inArena) return false;
                    if (!delayActive) return true;
                    // hide for players who are still in start zones A/B during delay
                    boolean inA = areaA != null && areaA.contains(p.getBlockPos());
                    boolean inB = areaB != null && areaB.contains(p.getBlockPos());
                    return !(inA || inB);
                });
                if (oddsBar != null) {
                    for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) oddsBar.removePlayer(pl);
                    for (ServerPlayerEntity pl : viewers) oddsBar.addPlayer(pl);
                }
            } else {
                if (oddsBar != null) {
                    for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) oddsBar.removePlayer(pl);
                    oddsBar = null;
                }
            }
        }
    }

    // API from integration to control live bossbar
    public void onBattleLiveStart(String a, String b) {
        this.liveBattleActive = true;
        this.liveA = a;
        this.liveB = b;
        this.liveRemainingA = -1;
        this.liveRemainingB = -1;
        this.firstFaintSide = null;
        this.totalFaints = 0;
        // Close betting if somehow still open
        BetManager.INSTANCE.closeBetting();
    }

    public void onBattleLiveStartTeams(String displayA, java.util.Set<String> namesA, String displayB, java.util.Set<String> namesB) {
        this.liveBattleActive = true;
        this.liveA = displayA;
        this.liveB = displayB;
        this.liveTeamA.clear(); if (namesA != null) this.liveTeamA.addAll(namesA);
        this.liveTeamB.clear(); if (namesB != null) this.liveTeamB.addAll(namesB);
        this.liveRemainingA = -1;
        this.liveRemainingB = -1;
        this.firstFaintSide = null;
        this.totalFaints = 0;
        BetManager.INSTANCE.closeBetting();
    }

    public void onBattleLiveEnd() {
        this.liveBattleActive = false;
        this.liveA = null;
        this.liveB = null;
        this.liveTeamA.clear();
        this.liveTeamB.clear();
        this.liveRemainingA = -1;
        this.liveRemainingB = -1;
    }

    public void setLiveRemaining(String sideName, int remaining) {
        if (sideName == null) return;
        if (liveA != null && sideName.equalsIgnoreCase(liveA)) this.liveRemainingA = remaining;
        else if (liveB != null && sideName.equalsIgnoreCase(liveB)) this.liveRemainingB = remaining;
        else {
            // try team membership match
            if (liveTeamA.stream().anyMatch(n -> n.equalsIgnoreCase(sideName))) this.liveRemainingA = remaining;
            if (liveTeamB.stream().anyMatch(n -> n.equalsIgnoreCase(sideName))) this.liveRemainingB = remaining;
        }
    }

    public void recordFaint(String sideName) {
        if (sideName == null) return;
        this.totalFaints++;
        if (this.firstFaintSide == null) this.firstFaintSide = sideName;
    }

    public String getFirstFaintSide() { return firstFaintSide; }
    public int getTotalFaints() { return totalFaints; }

    // Debug/testing helpers
    public int getLiveRemaining(String sideName) {
        if (sideName == null) return -1;
        if (liveA != null && sideName.equalsIgnoreCase(liveA)) return this.liveRemainingA;
        if (liveB != null && sideName.equalsIgnoreCase(liveB)) return this.liveRemainingB;
        return -1;
    }

    public void adjustLiveRemaining(String sideName, int delta) {
        if (sideName == null) return;
        int curr = getLiveRemaining(sideName);
        if (curr < 0) curr = 6; // default starting team size
        int next = Math.max(0, Math.min(6, curr + delta));
        setLiveRemaining(sideName, next);
    }

    public void announceWinner(MinecraftServer server, String winnerName) {
        this.lastWinnerName = winnerName;
        this.winnerTicksRemaining = 200; // ~10 seconds
        this.rainbowIndex = 0;
        this.winnerDelayTicks = 40; // ~2 seconds of delay for start zones
        this.matchCooldownTicks = DEFAULT_MATCH_COOLDOWN_TICKS; // block immediate auto-open after win
        // Also send actionbar to arena occupants
        if (arena != null) {
            RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, arena.dimension);
            ServerWorld w = server.getWorld(dimKey);
            if (w != null) {
                Text msg = Text.literal("Winner: " + winnerName + "!").formatted(Formatting.GOLD);
                for (ServerPlayerEntity p : w.getPlayers(pl -> arena.contains(pl.getBlockPos()))) {
                    p.sendMessage(msg, true);
                    if (com.example.config.FeatureFlags.isSoundCues()) p.playSound(net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
        }
        // Launch a celebratory firework if configured
        spawnFirework(server);
    }

    private void spawnFirework(MinecraftServer server) {
        if (fireworksDimension == null || fireworksPos == null) return;
        ServerWorld fw = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, fireworksDimension));
        if (fw == null) return;
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET, 1);
    // Make sure it actually explodes: set fireworks component with 1 explosion and some colors
    FireworkExplosionComponent explosion = new FireworkExplosionComponent(
        FireworkExplosionComponent.Type.LARGE_BALL,
        new IntArrayList(new int[]{0xFF0000, 0xFFFF00}), // red, yellow
        new IntArrayList(new int[]{0xFFFFFF}),           // fade to white
        true,  // trail
        true   // twinkle
    );
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(2, java.util.List.of(explosion))); // flight 2
        FireworkRocketEntity entity = new FireworkRocketEntity(fw, fireworksPos.getX() + 0.5, fireworksPos.getY() + 0.1, fireworksPos.getZ() + 0.5, rocket);
        fw.spawnEntity(entity);
    }

    // Debug helper: trigger a firework without a winner event
    public void triggerFireworkForDebug() {
        MinecraftServer server = getServer();
        if (server != null) spawnFirework(server);
    }

    public MinecraftServer getServer() {
        return this.lastServer;
    }
}
