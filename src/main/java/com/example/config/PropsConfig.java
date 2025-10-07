package com.example.config;

import com.google.gson.JsonObject;

/** Configurable props and multipliers (x100). */
public class PropsConfig {
    private static int firstFaintMultiplier = 180; // 1.8x
    private static int totalFaintsOverThreshold = 5;
    private static int totalFaintsOverMultiplier = 150; // 1.5x

    public static void loadFromConfig() {
        JsonObject obj = ConfigManager.load();
        if (obj.has("firstFaintMultiplier")) firstFaintMultiplier = obj.get("firstFaintMultiplier").getAsInt();
        if (obj.has("totalFaintsOverThreshold")) totalFaintsOverThreshold = obj.get("totalFaintsOverThreshold").getAsInt();
        if (obj.has("totalFaintsOverMultiplier")) totalFaintsOverMultiplier = obj.get("totalFaintsOverMultiplier").getAsInt();
    }

    public static void saveToConfig() {
        JsonObject obj = ConfigManager.load();
        obj.addProperty("firstFaintMultiplier", firstFaintMultiplier);
        obj.addProperty("totalFaintsOverThreshold", totalFaintsOverThreshold);
        obj.addProperty("totalFaintsOverMultiplier", totalFaintsOverMultiplier);
        ConfigManager.save(obj);
    }

    public static int getFirstFaintMultiplier() { return firstFaintMultiplier; }
    public static void setFirstFaintMultiplier(int v) { firstFaintMultiplier = v; }

    public static int getTotalFaintsOverThreshold() { return totalFaintsOverThreshold; }
    public static void setTotalFaintsOverThreshold(int v) { totalFaintsOverThreshold = v; }

    public static int getTotalFaintsOverMultiplier() { return totalFaintsOverMultiplier; }
    public static void setTotalFaintsOverMultiplier(int v) { totalFaintsOverMultiplier = v; }
}
