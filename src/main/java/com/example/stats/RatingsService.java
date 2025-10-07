package com.example.stats;

import com.example.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Simple ELO ratings with shrinkage for small samples. Names are normalized lowercase. */
public class RatingsService {
    public static final RatingsService INSTANCE = new RatingsService();

    private final Map<String, Double> ratings = new HashMap<>();
    private final Map<String, Integer> games = new HashMap<>();
    private double defaultRating = 1500.0;
    private int shrinkN = 10; // prior weight in games
    private int kFactor = 24; // update magnitude

    private RatingsService() { load(); }

    private static String key(String name) { return name == null ? "" : name.toLowerCase(Locale.ROOT); }

    public synchronized double getRating(String name) {
        return ratings.getOrDefault(key(name), defaultRating);
    }

    public synchronized void setRating(String name, double value) {
        ratings.put(key(name), value);
        save();
    }

    public synchronized int getGames(String name) { return games.getOrDefault(key(name), 0); }

    public synchronized void recordGame(String name) { games.put(key(name), getGames(name) + 1); }

    public synchronized double getEffectiveRating(String name) {
        int n = getGames(name);
        double r = getRating(name);
        return (defaultRating * shrinkN + r * n) / (shrinkN + n);
    }

    /** Update ratings for players on teamA vs teamB, given probability and actual outcome. */
    public synchronized void updateTeams(java.util.Set<String> teamA, java.util.Set<String> teamB, boolean aWon) {
        if (teamA == null || teamB == null || teamA.isEmpty() || teamB.isEmpty()) return;
        double avgA = teamA.stream().mapToDouble(this::getEffectiveRating).average().orElse(defaultRating);
        double avgB = teamB.stream().mapToDouble(this::getEffectiveRating).average().orElse(defaultRating);
        double expectedA = 1.0 / (1.0 + Math.pow(10.0, -(avgA - avgB) / 400.0));
        double scoreA = aWon ? 1.0 : 0.0;
        double deltaA = kFactor * (scoreA - expectedA);
        // apply delta to each member (simple approach)
        for (String n : teamA) setRating(n, getRating(n) + deltaA);
        for (String n : teamB) setRating(n, getRating(n) - deltaA);
        // record games count
        for (String n : teamA) recordGame(n);
        for (String n : teamB) recordGame(n);
        save();
    }

    private void load() {
        JsonObject obj = ConfigManager.load();
        if (obj.has("ratings") && obj.get("ratings").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("ratings");
            ratings.clear(); games.clear();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                try {
                    String name = o.get("name").getAsString();
                    double r = o.get("rating").getAsDouble();
                    int g = o.has("games") ? o.get("games").getAsInt() : 0;
                    ratings.put(key(name), r); games.put(key(name), g);
                } catch (Exception ignored) {}
            }
        }
    }

    private void save() {
        JsonObject obj = ConfigManager.load();
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, Double> e : ratings.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.getKey());
            o.addProperty("rating", e.getValue());
            o.addProperty("games", games.getOrDefault(e.getKey(), 0));
            arr.add(o);
        }
        obj.add("ratings", arr);
        ConfigManager.save(obj);
    }
}
