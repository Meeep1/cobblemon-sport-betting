package com.example.config;

import com.google.gson.JsonObject;

public class FeatureFlags {
    private static boolean betReceipts = true;
    private static boolean soundCues = true;
    private static boolean selfBetRestrict = true;

    public static void loadFromConfig() {
        JsonObject obj = ConfigManager.load();
        if (obj.has("betReceipts")) betReceipts = obj.get("betReceipts").getAsBoolean();
        if (obj.has("soundCues")) soundCues = obj.get("soundCues").getAsBoolean();
        if (obj.has("selfBetRestrict")) selfBetRestrict = obj.get("selfBetRestrict").getAsBoolean();
    }

    public static void saveToConfig() {
        JsonObject obj = ConfigManager.load();
        obj.addProperty("betReceipts", betReceipts);
        obj.addProperty("soundCues", soundCues);
        obj.addProperty("selfBetRestrict", selfBetRestrict);
        ConfigManager.save(obj);
    }

    public static boolean isBetReceipts() { return betReceipts; }
    public static void setBetReceipts(boolean v) { betReceipts = v; }

    public static boolean isSoundCues() { return soundCues; }
    public static void setSoundCues(boolean v) { soundCues = v; }

    public static boolean isSelfBetRestrict() { return selfBetRestrict; }
    public static void setSelfBetRestrict(boolean v) { selfBetRestrict = v; }
}
