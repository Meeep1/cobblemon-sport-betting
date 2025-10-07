package com.example.stats;

import java.util.*;

/** Tracks last-known species for players at battle start and computes simple aggregates. */
public class RosterTracker {
    public static final RosterTracker INSTANCE = new RosterTracker();

    // name -> list of species ids from last start (lowercase)
    private final Map<String, List<String>> lastRoster = new HashMap<>();
    // name -> cached aggregates
    private final Map<String, Aggregates> lastAgg = new HashMap<>();

    public static class Aggregates {
        public double avgBST;     // average base stat total
        public double avgSpeed;   // average base speed
        public double typeCoverage; // rough coverage vs opponent (computed later in context)
    }

    private static String key(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    public synchronized void setRoster(String playerName, List<String> speciesIds) {
        lastRoster.put(key(playerName), new ArrayList<>(speciesIds));
        // compute aggregates with coarse tables
        Aggregates a = new Aggregates();
        if (!speciesIds.isEmpty()) {
            double sumBST = 0.0, sumSpeed = 0.0; int n = 0;
            for (String id : speciesIds) {
                SpeciesInfo info = SpeciesInfo.get(id);
                sumBST += info.bst; sumSpeed += info.baseSpeed; n++;
            }
            a.avgBST = n > 0 ? sumBST / n : 0.0;
            a.avgSpeed = n > 0 ? sumSpeed / n : 0.0;
        }
        lastAgg.put(key(playerName), a);
    }

    public synchronized Aggregates getAggregates(String playerName) {
        return lastAgg.getOrDefault(key(playerName), new Aggregates());
    }

    public synchronized List<String> getRoster(String playerName) {
        return lastRoster.getOrDefault(key(playerName), java.util.Collections.emptyList());
    }

    // Minimal static species table; extend as needed or replace with Cobblemon registry reflection.
    static class SpeciesInfo {
        final int bst; final int baseSpeed; final String type1; final String type2; // types by string ids
        SpeciesInfo(int bst, int baseSpeed, String t1, String t2) { this.bst = bst; this.baseSpeed = baseSpeed; this.type1 = t1; this.type2 = t2; }

        private static final Map<String, SpeciesInfo> TABLE = new HashMap<>();
        static {
            // a tiny default set; admins can expand later if we add config loading
            TABLE.put("pikachu", new SpeciesInfo(320, 90, "electric", null));
            TABLE.put("charizard", new SpeciesInfo(534, 100, "fire", "flying"));
            TABLE.put("gengar", new SpeciesInfo(500, 110, "ghost", "poison"));
            TABLE.put("dragonite", new SpeciesInfo(600, 80, "dragon", "flying"));
            TABLE.put("garchomp", new SpeciesInfo(600, 102, "dragon", "ground"));
            TABLE.put("blissey", new SpeciesInfo(540, 55, "normal", null));
            // ... add more commonly used species as needed
        }
        static SpeciesInfo get(String id) { return TABLE.getOrDefault(id == null ? "" : id.toLowerCase(Locale.ROOT), new SpeciesInfo(500, 80, null, null)); }
    }

    // Simple type chart coverage: count how many opposing types are hit super effectively by any own type.
    public static double coverageScore(Collection<String> mySpecies, Collection<String> oppSpecies) {
        Set<String> oppTypes = new HashSet<>();
        for (String id : oppSpecies) {
            SpeciesInfo si = SpeciesInfo.get(id);
            if (si.type1 != null) oppTypes.add(si.type1);
            if (si.type2 != null) oppTypes.add(si.type2);
        }
        Set<String> myTypes = new HashSet<>();
        for (String id : mySpecies) {
            SpeciesInfo si = SpeciesInfo.get(id);
            if (si.type1 != null) myTypes.add(si.type1);
            if (si.type2 != null) myTypes.add(si.type2);
        }
        int hit = 0, total = Math.max(1, oppTypes.size());
        for (String t : oppTypes) {
            boolean covered = false;
            for (String mt : myTypes) { if (TypeChart.isSuperEffective(mt, t)) { covered = true; break; } }
            if (covered) hit++;
        }
        return hit / (double) total;
    }

    static class TypeChart {
        // Very minimal effectiveness set. Extend as needed.
        private static boolean isSE(String atk, String def) {
            return switch ((atk + ">" + def)) {
                case "electric>water", "electric>flying",
                     "fire>grass", "fire>ice", "fire>bug", "fire>steel",
                     "water>fire", "water>ground", "water>rock",
                     "grass>water", "grass>ground", "grass>rock",
                     "ice>grass", "ice>ground", "ice>flying", "ice>dragon",
                     "fighting>normal", "fighting>ice", "fighting>rock", "fighting>dark", "fighting>steel",
                     "ground>fire", "ground>electric", "ground>poison", "ground>rock", "ground>steel",
                     "flying>grass", "flying>fighting", "flying>bug",
                     "psychic>fighting", "psychic>poison",
                     "bug>grass", "bug>psychic", "bug>dark",
                     "rock>fire", "rock>ice", "rock>flying", "rock>bug",
                     "ghost>ghost", "ghost>psychic",
                     "dragon>dragon",
                     "dark>ghost", "dark>psychic",
                     "steel>ice", "steel>rock", "steel>fairy",
                     "fairy>fighting", "fairy>dragon", "fairy>dark" -> true;
                default -> false;
            };
        }
        public static boolean isSuperEffective(String atk, String def) { if (atk == null || def == null) return false; return isSE(atk, def); }
    }
}
