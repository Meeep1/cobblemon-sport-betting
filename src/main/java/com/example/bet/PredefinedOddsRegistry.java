package com.example.bet;

import com.example.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

public class PredefinedOddsRegistry {
    public static final PredefinedOddsRegistry INSTANCE = new PredefinedOddsRegistry();

    public static class PairKey {
        public final String a;
        public final String b;
        public PairKey(String p1, String p2) {
            // Order-insensitive, store lowercase
            String s1 = p1.toLowerCase(Locale.ROOT);
            String s2 = p2.toLowerCase(Locale.ROOT);
            if (s1.compareTo(s2) <= 0) { a = s1; b = s2; } else { a = s2; b = s1; }
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true; if (!(o instanceof PairKey pk)) return false;
            return a.equals(pk.a) && b.equals(pk.b);
        }
        @Override public int hashCode() { return Objects.hash(a, b); }
        @Override public String toString() { return a + "|" + b; }
    }

    public static class PairOdds {
        public final int oddsA; // multiplier for nameA when presented as (nameA, nameB)
        public final int oddsB;
        public PairOdds(int a, int b) { this.oddsA = a; this.oddsB = b; }
    }

    private final Map<PairKey, PairOdds> map = new HashMap<>();
    // Team odds: keys are normalized team strings (sorted lowercase members joined by '+')
    private static class TeamPairKey {
        public final String aTeam; public final String bTeam;
        TeamPairKey(String a, String b) {
            // order-insensitive
            if (a.compareTo(b) <= 0) { aTeam = a; bTeam = b; } else { aTeam = b; bTeam = a; }
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true; if (!(o instanceof TeamPairKey tk)) return false;
            return aTeam.equals(tk.aTeam) && bTeam.equals(tk.bTeam);
        }
        @Override public int hashCode() { return Objects.hash(aTeam, bTeam); }
    }
    private final Map<TeamPairKey, PairOdds> teamMap = new HashMap<>();

    private PredefinedOddsRegistry() { load(); }

    public Optional<PairOdds> get(String nameA, String nameB) {
        return Optional.ofNullable(map.get(new PairKey(nameA, nameB)));
    }

    public void set(String nameA, int oddsA, String nameB, int oddsB) {
        map.put(new PairKey(nameA, nameB), new PairOdds(oddsA, oddsB));
        save();
    }

    public boolean remove(String nameA, String nameB) {
        boolean removed = map.remove(new PairKey(nameA, nameB)) != null;
        if (removed) save();
        return removed;
    }

    public List<String> listStrings(int limit) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<PairKey, PairOdds> e : map.entrySet()) {
            out.add(e.getKey().a + " vs " + e.getKey().b + ": " + (e.getValue().oddsA/100.0) + "x / " + (e.getValue().oddsB/100.0) + "x");
            if (limit > 0 && out.size() >= limit) break;
        }
        Collections.sort(out);
        return out;
    }

    private void load() {
        JsonObject cfg = ConfigManager.load();
        if (cfg.has("predefinedOdds") && cfg.get("predefinedOdds").isJsonArray()) {
            JsonArray arr = cfg.getAsJsonArray("predefinedOdds");
            map.clear();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("a") || !o.has("b") || !o.has("oddsA") || !o.has("oddsB")) continue;
                String a = o.get("a").getAsString();
                String b = o.get("b").getAsString();
                int oa = o.get("oddsA").getAsInt();
                int ob = o.get("oddsB").getAsInt();
                map.put(new PairKey(a, b), new PairOdds(oa, ob));
            }
        }
        if (cfg.has("predefinedTeamOdds") && cfg.get("predefinedTeamOdds").isJsonArray()) {
            JsonArray arr = cfg.getAsJsonArray("predefinedTeamOdds");
            teamMap.clear();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("teamA") || !o.has("teamB") || !o.has("oddsA") || !o.has("oddsB")) continue;
                try {
                    java.util.Set<String> teamA = jsonArrayToNameSet(o.getAsJsonArray("teamA"));
                    java.util.Set<String> teamB = jsonArrayToNameSet(o.getAsJsonArray("teamB"));
                    String keyA = normalizeTeamKey(teamA);
                    String keyB = normalizeTeamKey(teamB);
                    int oa = o.get("oddsA").getAsInt();
                    int ob = o.get("oddsB").getAsInt();
                    teamMap.put(new TeamPairKey(keyA, keyB), new PairOdds(oa, ob));
                } catch (Exception ignored) {}
            }
        }
    }

    private void save() {
        JsonObject cfg = ConfigManager.load();
        JsonArray arr = new JsonArray();
        for (Map.Entry<PairKey, PairOdds> e : map.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("a", e.getKey().a);
            o.addProperty("b", e.getKey().b);
            o.addProperty("oddsA", e.getValue().oddsA);
            o.addProperty("oddsB", e.getValue().oddsB);
            arr.add(o);
        }
        cfg.add("predefinedOdds", arr);
        JsonArray teamArr = new JsonArray();
        for (Map.Entry<TeamPairKey, PairOdds> e : teamMap.entrySet()) {
            JsonObject o = new JsonObject();
            // split team keys back to arrays
            JsonArray aMembers = new JsonArray();
            for (String s : e.getKey().aTeam.split("\\+")) aMembers.add(s);
            JsonArray bMembers = new JsonArray();
            for (String s : e.getKey().bTeam.split("\\+")) bMembers.add(s);
            o.add("teamA", aMembers);
            o.add("teamB", bMembers);
            o.addProperty("oddsA", e.getValue().oddsA);
            o.addProperty("oddsB", e.getValue().oddsB);
            teamArr.add(o);
        }
        cfg.add("predefinedTeamOdds", teamArr);
        ConfigManager.save(cfg);
    }

    public Optional<PairOdds> getTeam(java.util.Set<String> teamA, java.util.Set<String> teamB) {
        if (teamA == null || teamA.isEmpty() || teamB == null || teamB.isEmpty()) return Optional.empty();
        String aKey = normalizeTeamKey(teamA);
        String bKey = normalizeTeamKey(teamB);
        return Optional.ofNullable(teamMap.get(new TeamPairKey(aKey, bKey)));
    }

    public void setTeam(java.util.Set<String> teamA, int oddsA, java.util.Set<String> teamB, int oddsB) {
        String aKey = normalizeTeamKey(teamA);
        String bKey = normalizeTeamKey(teamB);
        teamMap.put(new TeamPairKey(aKey, bKey), new PairOdds(oddsA, oddsB));
        save();
    }

    public boolean removeTeam(java.util.Set<String> teamA, java.util.Set<String> teamB) {
        String aKey = normalizeTeamKey(teamA);
        String bKey = normalizeTeamKey(teamB);
        boolean removed = teamMap.remove(new TeamPairKey(aKey, bKey)) != null;
        if (removed) save();
        return removed;
    }

    private static String normalizeTeamKey(java.util.Set<String> team) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : team) if (s != null) list.add(s.toLowerCase(java.util.Locale.ROOT));
        java.util.Collections.sort(list);
        return String.join("+", list);
    }

    private static java.util.Set<String> jsonArrayToNameSet(JsonArray arr) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (JsonElement e : arr) {
            try { out.add(e.getAsString()); } catch (Exception ignored) {}
        }
        return out;
    }

    public java.util.List<String> listTeamStrings(int limit) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<TeamPairKey, PairOdds> e : teamMap.entrySet()) {
            String aDisp = String.join(" & ", e.getKey().aTeam.split("\\+"));
            String bDisp = String.join(" & ", e.getKey().bTeam.split("\\+"));
            out.add(aDisp + " vs " + bDisp + ": " + (e.getValue().oddsA/100.0) + "x / " + (e.getValue().oddsB/100.0) + "x");
            if (limit > 0 && out.size() >= limit) break;
        }
        java.util.Collections.sort(out);
        return out;
    }
}
