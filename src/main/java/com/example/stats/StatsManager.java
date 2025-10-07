package com.example.stats;

import com.example.config.ConfigManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks player total wins, current/longest win streaks, and head-to-head stats. Persists to config.
 */
public class StatsManager {
    public static final StatsManager INSTANCE = new StatsManager();

    public static class PlayerStats {
        public int totalWins;
        public int currentStreak;
        public int longestStreak;
    }

    public static class HeadToHead {
        public String left;  // normalized order key left name
        public String right; // normalized order key right name
        public int winsLeft;
        public int winsRight;
    }

    private final Map<String, PlayerStats> players = new HashMap<>();
    private final Map<String, HeadToHead> h2h = new HashMap<>(); // key: left + "|" + right (alphabetical order)

    private StatsManager() {
        load();
    }

    private static String normName(String name) {
        return name == null ? null : name.trim();
    }

    private static String pairKey(String a, String b) {
        String n1 = a.compareToIgnoreCase(b) <= 0 ? a : b;
        String n2 = a.compareToIgnoreCase(b) <= 0 ? b : a;
        return n1 + "|" + n2;
    }

    private synchronized PlayerStats ps(String name) {
        name = normName(name);
        return players.computeIfAbsent(name, k -> new PlayerStats());
    }

    private synchronized HeadToHead h2h(String a, String b) {
        a = normName(a); b = normName(b);
        String key = pairKey(a, b);
        HeadToHead hh = this.h2h.get(key);
        if (hh == null) {
            hh = new HeadToHead();
            if (a.compareToIgnoreCase(b) <= 0) { hh.left = a; hh.right = b; }
            else { hh.left = b; hh.right = a; }
            this.h2h.put(key, hh);
        }
        return hh;
    }

    public synchronized void recordResult(String winner, String loser) {
        if (winner == null || loser == null) return;
        winner = normName(winner); loser = normName(loser);
        if (winner.isEmpty() || loser.isEmpty() || winner.equalsIgnoreCase(loser)) return;
        // Player stats
        PlayerStats w = ps(winner); PlayerStats l = ps(loser);
        w.totalWins += 1;
        w.currentStreak += 1;
        if (w.currentStreak > w.longestStreak) w.longestStreak = w.currentStreak;
        l.currentStreak = 0;
        // Head-to-head
        HeadToHead hh = h2h(winner, loser);
        if (winner.equalsIgnoreCase(hh.left)) hh.winsLeft += 1; else hh.winsRight += 1;
        save();
    }

    public synchronized Optional<PlayerStats> getPlayer(String name) {
        name = normName(name);
        PlayerStats s = players.get(name);
        return Optional.ofNullable(s);
    }

    public synchronized Optional<HeadToHead> getHeadToHead(String a, String b) {
        if (a == null || b == null) return Optional.empty();
        HeadToHead hh = h2h.get(pairKey(normName(a), normName(b)));
        return Optional.ofNullable(hh);
    }

    public synchronized List<Map.Entry<String, PlayerStats>> topWins(int n) {
        return players.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().totalWins, e1.getValue().totalWins))
                .limit(Math.max(0, n)).collect(Collectors.toList());
    }

    public synchronized List<Map.Entry<String, PlayerStats>> topStreaks(int n) {
        return players.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().currentStreak, e1.getValue().currentStreak))
                .limit(Math.max(0, n)).collect(Collectors.toList());
    }

    public synchronized void resetAll() {
        players.clear();
        h2h.clear();
        save();
    }

    private void load() {
        JsonObject root = ConfigManager.load();
        if (!root.has("stats")) return;
        try {
            JsonObject s = root.getAsJsonObject("stats");
            if (s.has("players")) {
                JsonObject p = s.getAsJsonObject("players");
                for (String name : p.keySet()) {
                    JsonObject o = p.getAsJsonObject(name);
                    PlayerStats ps = new PlayerStats();
                    if (o.has("totalWins")) ps.totalWins = o.get("totalWins").getAsInt();
                    if (o.has("currentStreak")) ps.currentStreak = o.get("currentStreak").getAsInt();
                    if (o.has("longestStreak")) ps.longestStreak = o.get("longestStreak").getAsInt();
                    players.put(name, ps);
                }
            }
            if (s.has("h2h")) {
                JsonObject h = s.getAsJsonObject("h2h");
                for (String key : h.keySet()) {
                    JsonObject o = h.getAsJsonObject(key);
                    HeadToHead hh = new HeadToHead();
                    hh.left = o.get("left").getAsString();
                    hh.right = o.get("right").getAsString();
                    hh.winsLeft = o.get("winsLeft").getAsInt();
                    hh.winsRight = o.get("winsRight").getAsInt();
                    this.h2h.put(key, hh);
                }
            }
        } catch (Exception ignored) {}
    }

    private synchronized void save() {
        JsonObject root = ConfigManager.load();
        JsonObject s = new JsonObject();
        // players
        JsonObject p = new JsonObject();
        for (Map.Entry<String, PlayerStats> e : players.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("totalWins", e.getValue().totalWins);
            o.addProperty("currentStreak", e.getValue().currentStreak);
            o.addProperty("longestStreak", e.getValue().longestStreak);
            p.add(e.getKey(), o);
        }
        s.add("players", p);
        // h2h
        JsonObject h = new JsonObject();
        for (Map.Entry<String, HeadToHead> e : h2h.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("left", e.getValue().left);
            o.addProperty("right", e.getValue().right);
            o.addProperty("winsLeft", e.getValue().winsLeft);
            o.addProperty("winsRight", e.getValue().winsRight);
            h.add(e.getKey(), o);
        }
        s.add("h2h", h);
        root.add("stats", s);
        ConfigManager.save(root);
    }
}
