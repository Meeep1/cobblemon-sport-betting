package com.example.integration;

import com.example.bet.BetManager;
import com.example.region.Region;
import com.example.region.RegionService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import java.util.UUID;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Cobblemon integration with defensive reflection. Only hooks if Cobblemon is present.
 * Filters events so that we only react when the battle occurs inside the configured Arena region.
 */
public class CobblemonIntegration {
    private static boolean hooked = false;
    private static java.util.UUID currentBattleId = null;
    private static final boolean ALLOW_PVE_TRAINER_BATTLES = true; // enable NPC trainer integration

    // Called from our code when auto-opening based on regions
    public static void onBattleStart(String playerA, String playerB) {
        BetManager.INSTANCE.openBettingForBattle(playerA, playerB);
        BetManager.INSTANCE.closeBetting();
        RegionService.INSTANCE.onBattleLiveStart(playerA, playerB);
    }

    public static void onBattleStartTeams(String displayA, java.util.Set<String> namesA, String displayB, java.util.Set<String> namesB) {
        BetManager.INSTANCE.openBettingForBattleTeams(displayA, namesA, displayB, namesB);
        BetManager.INSTANCE.closeBetting();
        RegionService.INSTANCE.onBattleLiveStartTeams(displayA, namesA, displayB, namesB);
        // Attempt to record last-known rosters per player by reflecting species from actors (best-effort)
        try {
            // We can't access the battle object here directly; roster recording is attempted in handleBattleStartedPost.
            // This call path is invoked by RegionService auto-open only when Cobblemon isn't driving the event.
        } catch (Throwable ignored) {}
    }

    public static void onBattleEnd(MinecraftServer server, String winnerName) {
        BetManager.INSTANCE.resolveBets(server, winnerName);
        // End live mode so the winner bossbar can display immediately (affects debug/manual resolves)
        RegionService.INSTANCE.onBattleLiveEnd();
        RegionService.INSTANCE.announceWinner(server, winnerName);
    }

    /**
     * Attempt to subscribe to Cobblemon battle start/end events via reflection to avoid compile-time deps.
     * Should be called during mod init. If anything fails, we silently skip hooking.
     */
    public static void tryHookCobblemon() {
        if (hooked) return;
        try {
            // We target Cobblemon 1.6.1 which exposes CobblemonEvents with Kotlin observables.
            // We'll use reflection and dynamic proxies for kotlin.jvm.functions.Function1 to subscribe.
            ClassLoader cl = CobblemonIntegration.class.getClassLoader();
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents", true, cl);
            Object startedPostObservable = eventsClass.getField("BATTLE_STARTED_POST").get(null);
            Object victoryObservable = eventsClass.getField("BATTLE_VICTORY").get(null);

            Class<?> priorityClass = Class.forName("com.cobblemon.mod.common.api.Priority", true, cl);
            Object priorityNormal = Enum.valueOf((Class<Enum>) priorityClass, "NORMAL");

            Class<?> function1 = Class.forName("kotlin.jvm.functions.Function1", true, cl);
            Class<?> unitClass = Class.forName("kotlin.Unit", true, cl);
            Object unitInstance = unitClass.getField("INSTANCE").get(null);

            // Subscribe to BATTLE_STARTED_POST
            Object startFn = java.lang.reflect.Proxy.newProxyInstance(
                function1.getClassLoader(),
                new Class[]{function1},
                (proxy, method, args) -> {
                    if ("invoke".equals(method.getName())) {
                        try {
                            Object event = args[0];
                            handleBattleStartedPost(event);
                        } catch (Throwable ignored) {}
                        return unitInstance;
                    }
                    return null;
                }
            );
            Method subscribeMethod = startedPostObservable.getClass().getMethod("subscribe", priorityClass, function1);
            subscribeMethod.invoke(startedPostObservable, priorityNormal, startFn);

            // Subscribe to BATTLE_VICTORY
            Object victoryFn = java.lang.reflect.Proxy.newProxyInstance(
                function1.getClassLoader(),
                new Class[]{function1},
                (proxy, method, args) -> {
                    if ("invoke".equals(method.getName())) {
                        try {
                            Object event = args[0];
                            handleBattleVictory(event);
                        } catch (Throwable ignored) {}
                        return unitInstance;
                    }
                    return null;
                }
            );
            Method subscribeMethod2 = victoryObservable.getClass().getMethod("subscribe", priorityClass, function1);
            subscribeMethod2.invoke(victoryObservable, priorityNormal, victoryFn);

            // Subscribe to BATTLE_FLED to clear state and avoid false winner displays
            Object fledObservable = eventsClass.getField("BATTLE_FLED").get(null);
            Object fledFn = java.lang.reflect.Proxy.newProxyInstance(
                function1.getClassLoader(),
                new Class[]{function1},
                (proxy, method, args) -> {
                    if ("invoke".equals(method.getName())) {
                        try {
                            Object event = args[0];
                            handleBattleFled(event);
                        } catch (Throwable ignored) {}
                        return unitInstance;
                    }
                    return null;
                }
            );
            Method subscribeMethod3 = fledObservable.getClass().getMethod("subscribe", priorityClass, function1);
            subscribeMethod3.invoke(fledObservable, priorityNormal, fledFn);

            // Subscribe to BATTLE_FAINTED for live remaining updates
            Object faintObservable = eventsClass.getField("BATTLE_FAINTED").get(null);
            Object faintFn = java.lang.reflect.Proxy.newProxyInstance(
                function1.getClassLoader(),
                new Class[]{function1},
                (proxy, method, args) -> {
                    if ("invoke".equals(method.getName())) {
                        try { handleBattleFainted(args[0]); } catch (Throwable ignored) {}
                        return unitInstance;
                    }
                    return null;
                }
            );
            Method subscribeMethod4 = faintObservable.getClass().getMethod("subscribe", priorityClass, function1);
            subscribeMethod4.invoke(faintObservable, priorityNormal, faintFn);

            hooked = true;
            com.example.ExampleMod.LOGGER.info("Cobblemon integration: subscribed to BATTLE_STARTED_POST and BATTLE_VICTORY.");
        } catch (Throwable t) {
            // Cobblemon not present or API mismatch; ignore and operate without automatic hooks.
            com.example.ExampleMod.LOGGER.info("Cobblemon integration not hooked: " + t.getClass().getSimpleName());
        }
    }

    // Handlers for specific Cobblemon events, using reflection to read properties safely
    private static void handleBattleStartedPost(Object event) {
        try {
            Region arena = RegionService.INSTANCE.getArena();
            if (arena == null) return;

            Method getBattle = event.getClass().getMethod("getBattle");
            Object battle = getBattle.invoke(event);
            if (battle == null) return;

            try {
                Method getBattleId = battle.getClass().getMethod("getBattleId");
                Object idObj = getBattleId.invoke(battle);
                if (idObj instanceof java.util.UUID bid) currentBattleId = bid;
            } catch (Throwable ignored) {}

            // Get players in battle
            Method getPlayers = battle.getClass().getMethod("getPlayers");
            Object playersObj = getPlayers.invoke(battle);
            if (!(playersObj instanceof java.util.List<?> players)) return;

            // Build team sets by iterating actors and reading playerUUIDs membership
            java.util.Set<String> namesA = new java.util.LinkedHashSet<>();
            java.util.Set<String> namesB = new java.util.LinkedHashSet<>();
            String aName = null;
            String bName = null;

            if (players.size() >= 2) {
                // Dimension + arena containment checks for at least one player on each side
                Object p1Obj = players.get(0);
                Object p2Obj = players.get(1);
                if (!(p1Obj instanceof ServerPlayerEntity p1) || !(p2Obj instanceof ServerPlayerEntity p2)) return;
                Identifier arenaDim = arena.dimension;
                Identifier p1Dim = ((ServerWorld) p1.getWorld()).getRegistryKey().getValue();
                Identifier p2Dim = ((ServerWorld) p2.getWorld()).getRegistryKey().getValue();
                if (!arenaDim.equals(p1Dim) || !arenaDim.equals(p2Dim)) return;
                if (!arena.contains(p1.getBlockPos()) || !arena.contains(p2.getBlockPos())) return;

                // Derive teams from actors order: find two first distinct actors with players and map their members
                try {
                    Method getActors = battle.getClass().getMethod("getActors");
                    Object actorsObj = getActors.invoke(battle);
                    if (actorsObj instanceof Iterable<?> actors) {
                        java.util.List<Object> actorList = new java.util.ArrayList<>();
                        for (Object act : actors) actorList.add(act);
                        if (actorList.size() >= 2) {
                            Object actA = actorList.get(0);
                            Object actB = actorList.get(1);
                            // collect names for A
                            namesA.addAll(resolveActorPlayerNames(actA));
                            namesB.addAll(resolveActorPlayerNames(actB));
                        }
                    }
                } catch (Throwable ignored) {}

                // Fallback to single names from first two players
                if (namesA.isEmpty()) namesA.add(p1.getGameProfile().getName());
                if (namesB.isEmpty()) namesB.add(p2.getGameProfile().getName());
                aName = String.join(" & ", namesA);
                bName = String.join(" & ", namesB);
            } else if (ALLOW_PVE_TRAINER_BATTLES && players.size() == 1) {
                Object p1Obj = players.get(0);
                if (!(p1Obj instanceof ServerPlayerEntity p1)) return;

                // Check player in arena
                Identifier arenaDim = arena.dimension;
                Identifier p1Dim = ((ServerWorld) p1.getWorld()).getRegistryKey().getValue();
                if (!arenaDim.equals(p1Dim) || !arena.contains(p1.getBlockPos())) return;

                // Find a non-player actor name
                try {
                    Method getActors = battle.getClass().getMethod("getActors");
                    Object actorsObj = getActors.invoke(battle);
                    if (actorsObj instanceof Iterable<?> actors) {
                        for (Object act : actors) {
                            try {
                                Method getPlayerUUIDs = act.getClass().getMethod("getPlayerUUIDs");
                                Object uuidsObj = getPlayerUUIDs.invoke(act);
                                boolean isPlayerActor = false;
                                if (uuidsObj instanceof Iterable<?> it) {
                                    var iterator = it.iterator();
                                    if (iterator != null && iterator.hasNext()) isPlayerActor = true;
                                }
                                if (!isPlayerActor) {
                                    // Use actor display name
                                    String trainerName = null;
                                    try {
                                        Method getName = act.getClass().getMethod("getName");
                                        Object text = getName.invoke(act);
                                        if (text != null) {
                                            Method getString = text.getClass().getMethod("getString");
                                            Object s = getString.invoke(text);
                                            if (s != null) trainerName = s.toString();
                                        }
                                    } catch (Throwable ignored2) {}
                                    if (trainerName != null && !trainerName.isBlank()) {
                                        namesA.add(p1.getGameProfile().getName());
                                        aName = String.join(" & ", namesA);
                                        namesB.add(trainerName);
                                        bName = String.join(" & ", namesB);
                                        break;
                                    }
                                }
                            } catch (Throwable ignored2) {}
                        }
                    }
                } catch (Throwable ignored3) {}
                if (aName == null || bName == null) return; // couldn't resolve trainer name
            } else {
                return; // not a supported battle type
            }

            if (aName == null || bName == null) return;

            // Start live mode (teams)
            if (!namesA.isEmpty() || !namesB.isEmpty()) onBattleStartTeams(aName, namesA, bName, namesB);
            else onBattleStart(aName, bName);

            // Initialize remaining counts at start by counting alive Pokemon per actor
            try {
                // Obtain a server instance via any player in the battle
                MinecraftServer server = null;
                if (players.size() >= 1 && players.get(0) instanceof ServerPlayerEntity anyP) server = anyP.getServer();

                Method getActors = battle.getClass().getMethod("getActors");
                Object actorsObj = getActors.invoke(battle);
                if (actorsObj instanceof Iterable<?> actors) {
                    Integer countA = null, countB = null;
                    // Also attempt to capture roster species per named player
                    java.util.Map<String, java.util.List<String>> speciesByName = new java.util.HashMap<>();
                    for (Object act : actors) {
                        // Count alive mons
                        int alive = 0;
                        try {
                            Method getPokemonList = act.getClass().getMethod("getPokemonList");
                            Object listObj = getPokemonList.invoke(act);
                            if (listObj instanceof java.util.List<?> list) {
                                for (Object bp : list) {
                                    Method getGone = bp.getClass().getMethod("getGone");
                                    Object gone = getGone.invoke(bp);
                                    if (!(gone instanceof Boolean) || !((Boolean) gone)) alive++;
                                    // Capture species id when possible
                                    try {
                                        Method getSpecies = bp.getClass().getMethod("getSpecies");
                                        Object speciesObj = getSpecies.invoke(bp);
                                        String sid = null;
                                        if (speciesObj != null) {
                                            try {
                                                Method getName = speciesObj.getClass().getMethod("getName");
                                                Object nm = getName.invoke(speciesObj);
                                                if (nm != null) sid = nm.toString();
                                            } catch (Throwable ignoredSpec) {}
                                        }
                                        if (sid != null) {
                                            speciesByName.computeIfAbsent("__last__", k -> new java.util.ArrayList<>()).add(sid);
                                        }
                                    } catch (Throwable ignoredSp) {}
                                }
                            }
                        } catch (Throwable ignored2) {}

                        // Resolve actor name
                        String name = null;
                        try {
                            Method getPlayerUUIDs = act.getClass().getMethod("getPlayerUUIDs");
                            Object uuidsObj = getPlayerUUIDs.invoke(act);
                            if (uuidsObj instanceof Iterable<?> it) {
                                var iterator = it.iterator();
                                if (iterator != null && iterator.hasNext()) {
                                    Object u = iterator.next();
                                    if (u instanceof java.util.UUID uuid) {
                                        if (server != null) {
                                            var sp = server.getPlayerManager().getPlayer(uuid);
                                            if (sp != null) name = sp.getGameProfile().getName();
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored3) {}
                        if (name == null) {
                            try {
                                Method getName = act.getClass().getMethod("getName");
                                Object text = getName.invoke(act);
                                if (text != null) {
                                    Method getString = text.getClass().getMethod("getString");
                                    Object s = getString.invoke(text);
                                    if (s != null) name = s.toString();
                                }
                            } catch (Throwable ignored4) {}
                        }

                        if (name != null) {
                            if (name.equalsIgnoreCase(aName)) countA = alive;
                            else if (name.equalsIgnoreCase(bName)) countB = alive;
                        }
                    }
                    if (countA != null) RegionService.INSTANCE.setLiveRemaining(aName, countA);
                    if (countB != null) RegionService.INSTANCE.setLiveRemaining(bName, countB);

                    // Record rosters for each named player using the captured list (best-effort; per actor not separated here)
                    try {
                        if (!namesA.isEmpty()) {
                            for (String n : namesA) {
                                com.example.stats.RosterTracker.INSTANCE.setRoster(n, speciesByName.getOrDefault("__last__", java.util.Collections.emptyList()));
                            }
                        }
                        if (!namesB.isEmpty()) {
                            for (String n : namesB) {
                                com.example.stats.RosterTracker.INSTANCE.setRoster(n, speciesByName.getOrDefault("__last__", java.util.Collections.emptyList()));
                            }
                        }
                    } catch (Throwable ignoredRoster) {}
                }
            } catch (Throwable ignoredInit) {}
        } catch (Throwable ignored) { }
    }

    private static void handleBattleVictory(Object event) {
        try {
            Region arena = RegionService.INSTANCE.getArena();
            if (arena == null) return;

            Method getBattle = event.getClass().getMethod("getBattle");
            Object battle = getBattle.invoke(event);
            if (battle == null) return;

            // Only handle victories for our tracked battle
            try {
                Method getBattleId = battle.getClass().getMethod("getBattleId");
                Object idObj = getBattleId.invoke(battle);
                if (!(idObj instanceof java.util.UUID bid) || currentBattleId == null || !currentBattleId.equals(bid)) return;
            } catch (Throwable ignored) { return; }

            Method getWinners = event.getClass().getMethod("getWinners");
            Object winnersObj = getWinners.invoke(event);
            if (!(winnersObj instanceof java.util.List<?> winners) || winners.isEmpty()) return;

            Method getPlayers = battle.getClass().getMethod("getPlayers");
            Object playersObj = getPlayers.invoke(battle);
            if (!(playersObj instanceof java.util.List<?> players) || players.isEmpty()) return;

            Object anyPlayerObj = players.get(0);
            if (!(anyPlayerObj instanceof ServerPlayerEntity anyPlayer)) return;
            MinecraftServer server = anyPlayer.getServer();
            if (server == null) return;

            // Align winner to stored A/B where possible
            Object winnerActor = winners.get(0);
            ServerPlayerEntity p1 = null, p2 = null;
            if (players.size() >= 1 && players.get(0) instanceof ServerPlayerEntity) p1 = (ServerPlayerEntity) players.get(0);
            if (players.size() >= 2 && players.get(1) instanceof ServerPlayerEntity) p2 = (ServerPlayerEntity) players.get(1);
            String aStored = BetManager.INSTANCE.getPlayerA();
            String bStored = BetManager.INSTANCE.getPlayerB();
            if (aStored == null || bStored == null) return;

            if (p1 != null && p2 != null) {
                String p1Name = p1.getGameProfile().getName();
                String p2Name = p2.getGameProfile().getName();
                if (p1Name != null && p2Name != null) {
                    boolean p1IsA = p1Name.equalsIgnoreCase(aStored);
                    boolean p2IsB = p2Name.equalsIgnoreCase(bStored);
                    if (!(p1IsA && p2IsB)) {
                        boolean p1IsB = p1Name.equalsIgnoreCase(bStored);
                        boolean p2IsA = p2Name.equalsIgnoreCase(aStored);
                        if (p1IsB && p2IsA) { ServerPlayerEntity tmp = p1; p1 = p2; p2 = tmp; }
                    }
                }
            }

            String winnerName = null;
            try {
                Method getPlayerUUIDs = winnerActor.getClass().getMethod("getPlayerUUIDs");
                Object uuidsObj = getPlayerUUIDs.invoke(winnerActor);
                if (uuidsObj instanceof Iterable<?> it) {
                    for (Object u : it) {
                        if (u instanceof java.util.UUID uuid) {
                            if (p1 != null && uuid.equals(p1.getUuid())) { winnerName = aStored != null ? aStored : p1.getGameProfile().getName(); break; }
                            if (p2 != null && uuid.equals(p2.getUuid())) { winnerName = bStored != null ? bStored : p2.getGameProfile().getName(); break; }
                            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
                            if (sp != null) { winnerName = sp.getGameProfile().getName(); break; }
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {}

            if (winnerName == null) {
                try {
                    Method getName = winnerActor.getClass().getMethod("getName");
                    Object text = getName.invoke(winnerActor);
                    if (text != null) {
                        Method getString = text.getClass().getMethod("getString");
                        Object s = getString.invoke(text);
                        if (s != null) winnerName = s.toString();
                    }
                } catch (Throwable ignored) {}
            }

            if (winnerName != null) {
                onBattleEnd(server, winnerName);
                currentBattleId = null;
                RegionService.INSTANCE.onBattleLiveEnd();
                // Update ratings based on team membership
                try {
                    java.util.Set<String> teamA = com.example.bet.BetManager.INSTANCE.getTeamA();
                    java.util.Set<String> teamB = com.example.bet.BetManager.INSTANCE.getTeamB();
                    boolean aWon = winnerName.equalsIgnoreCase(com.example.bet.BetManager.INSTANCE.getPlayerA());
                    com.example.stats.RatingsService.INSTANCE.updateTeams(teamA, teamB, aWon);
                } catch (Throwable ignoredRating) {}
            }
        } catch (Throwable ignored) { }
    }

    private static java.util.Set<String> resolveActorPlayerNames(Object actor) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        try {
            Method getPlayerUUIDs = actor.getClass().getMethod("getPlayerUUIDs");
            Object uuidsObj = getPlayerUUIDs.invoke(actor);
            MinecraftServer server = RegionService.INSTANCE.getServer();
            if (uuidsObj instanceof Iterable<?> it) {
                for (Object u : it) {
                    if (u instanceof java.util.UUID uuid) {
                        if (server != null) {
                            var sp = server.getPlayerManager().getPlayer(uuid);
                            if (sp != null && sp.getGameProfile() != null && sp.getGameProfile().getName() != null) {
                                names.add(sp.getGameProfile().getName());
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return names;
    }

    private static void handleBattleFled(Object event) {
        try {
            Method getBattle = event.getClass().getMethod("getBattle");
            Object battle = getBattle.invoke(event);
            if (battle == null) return;
            try {
                Method getBattleId = battle.getClass().getMethod("getBattleId");
                Object idObj = getBattleId.invoke(battle);
                if (idObj instanceof UUID bid) {
                    if (currentBattleId != null && currentBattleId.equals(bid)) {
                        com.example.ExampleMod.LOGGER.info("Cobblemon: battle fled; clearing tracked battle.");
                        currentBattleId = null;
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static void handleBattleFainted(Object event) {
        try {
            // Ignore if not tracking a live battle or bets aren’t tied to current battle
            if (currentBattleId == null) return;
            Method getBattle = event.getClass().getMethod("getBattle");
            Object battle = getBattle.invoke(event);
            if (battle == null) return;
            try {
                Method getBattleId = battle.getClass().getMethod("getBattleId");
                Object idObj = getBattleId.invoke(battle);
                if (!(idObj instanceof UUID bid) || !bid.equals(currentBattleId)) return;
            } catch (Throwable ignored) { return; }

            // Identify which actor’s Pokemon fainted and map to A or B by player names
            Method getKilled = event.getClass().getMethod("getKilled");
            Object killed = getKilled.invoke(event);
            if (killed == null) return;
            Method getActor = killed.getClass().getMethod("getActor");
            Object actor = getActor.invoke(killed);
            if (actor == null) return;

            String aStored = com.example.bet.BetManager.INSTANCE.getPlayerA();
            String bStored = com.example.bet.BetManager.INSTANCE.getPlayerB();
            if (aStored == null || bStored == null) return;

            // Try to resolve actor to a player UUID and then to a name
            String sideName = null;
            try {
                Method getPlayerUUIDs = actor.getClass().getMethod("getPlayerUUIDs");
                Object uuidsObj = getPlayerUUIDs.invoke(actor);
                if (uuidsObj instanceof Iterable<?> it) {
                    for (Object u : it) {
                        if (u instanceof UUID uuid) {
                            // If UUID matches either side, set sideName
                            // We don’t have direct UUIDs for A/B stored; compare via server player names
                            MinecraftServer server = RegionService.INSTANCE.getServer();
                            if (server != null) {
                                var sp = server.getPlayerManager().getPlayer(uuid);
                                if (sp != null) {
                                    String name = sp.getGameProfile().getName();
                                    if (name != null) {
                                        if (name.equalsIgnoreCase(aStored)) sideName = aStored;
                                        else if (name.equalsIgnoreCase(bStored)) sideName = bStored;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {}

            if (sideName == null) {
                // Fallback: use actor.getName().getString()
                try {
                    Method getName = actor.getClass().getMethod("getName");
                    Object text = getName.invoke(actor);
                    if (text != null) {
                        Method getString = text.getClass().getMethod("getString");
                        Object s = getString.invoke(text);
                        if (s != null) {
                            String nm = s.toString();
                            if (nm.equalsIgnoreCase(aStored)) sideName = aStored;
                            else if (nm.equalsIgnoreCase(bStored)) sideName = bStored;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (sideName != null) {
                // Decrement remaining for that side if we have a current value; initialize to 6 if unknown
                // We keep count in RegionService; get current and set current-1
                // Simple approach: if unknown (-1), set opponent to 6 and this side to 5
                RegionService.INSTANCE.setLiveRemaining(sideName, -999); // signal we need to update based on current
                // Record faint for props context
                RegionService.INSTANCE.recordFaint(sideName);
                // We don’t have direct team size here; use heuristic: start at 6 if unset, then decrement
                // To implement the decrement properly, query current via reflection counts per side:
                try {
                    // Count non-gone BattlePokemon for each actor
                    Method getActors = battle.getClass().getMethod("getActors");
                    Object actorsObj = getActors.invoke(battle);
                    int countA = -1, countB = -1;
                    if (actorsObj instanceof Iterable<?> actors) {
                        for (Object act : actors) {
                            Method getPokemonList = act.getClass().getMethod("getPokemonList");
                            Object listObj = getPokemonList.invoke(act);
                            int alive = 0;
                            if (listObj instanceof java.util.List<?> list) {
                                for (Object bp : list) {
                                    Method getGone = bp.getClass().getMethod("getGone");
                                    Object gone = getGone.invoke(bp);
                                    if (!(gone instanceof Boolean) || !((Boolean) gone)) alive++;
                                }
                            }
                            String name = null;
                            try {
                                Method getPlayerUUIDs = act.getClass().getMethod("getPlayerUUIDs");
                                Object uuidsObj = getPlayerUUIDs.invoke(act);
                                if (uuidsObj instanceof Iterable<?> it) {
                                    var iterator = it.iterator();
                                    if (iterator != null && iterator.hasNext()) {
                                        Object u = iterator.next();
                                        if (u instanceof UUID uuid) {
                                            MinecraftServer server = RegionService.INSTANCE.getServer();
                                            if (server != null) {
                                                var sp = server.getPlayerManager().getPlayer(uuid);
                                                if (sp != null) name = sp.getGameProfile().getName();
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignored2) {}
                            if (name == null) {
                                try {
                                    Method getName = act.getClass().getMethod("getName");
                                    Object text = getName.invoke(act);
                                    if (text != null) {
                                        Method getString = text.getClass().getMethod("getString");
                                        Object s = getString.invoke(text);
                                        if (s != null) name = s.toString();
                                    }
                                } catch (Throwable ignored3) {}
                            }
                            if (name != null) {
                                if (name.equalsIgnoreCase(aStored)) countA = alive;
                                else if (name.equalsIgnoreCase(bStored)) countB = alive;
                            }
                        }
                    }
                    if (countA >= 0) RegionService.INSTANCE.setLiveRemaining(aStored, countA);
                    if (countB >= 0) RegionService.INSTANCE.setLiveRemaining(bStored, countB);
                } catch (Throwable ignored2) {}
            }
        } catch (Throwable ignored) {
        }
    }
}
