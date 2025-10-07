package com.example.config;

import com.google.gson.JsonObject;

/** Configuration for dynamic odds: enable flag, weights, clamps, margin. */
public class DynamicOddsConfig {
    private static boolean enabled = false;

    // Weights for components
    private static double wRating = 1.0; // weight for (avgRatingA - avgRatingB)
    private static double wBST = 0.4;    // weight for (avgBSTA - avgBSTB)
    private static double wType = 0.6;   // weight for (typeCoverageA - typeCoverageB)
    private static double wSpeed = 0.3;  // weight for (avgSpeedA - avgSpeedB)

    // Logistic scale
    private static double alpha = 0.01;

    // Odds clamps (x100), e.g., 110 => 1.10x to 300 => 3.00x
    private static int minOdds = 110;
    private static int maxOdds = 300;

    // House margin applied to fair odds (0.0 to 0.1 typical). 0.05 => 5%.
    private static double margin = 0.0;

    public static void loadFromConfig() {
        JsonObject obj = ConfigManager.load();
        if (obj.has("dynamicOdds")) {
            JsonObject d = obj.getAsJsonObject("dynamicOdds");
            try { if (d.has("enabled")) enabled = d.get("enabled").getAsBoolean(); } catch (Exception ignored) {}
            try { if (d.has("wRating")) wRating = d.get("wRating").getAsDouble(); } catch (Exception ignored) {}
            try { if (d.has("wBST")) wBST = d.get("wBST").getAsDouble(); } catch (Exception ignored) {}
            try { if (d.has("wType")) wType = d.get("wType").getAsDouble(); } catch (Exception ignored) {}
            try { if (d.has("wSpeed")) wSpeed = d.get("wSpeed").getAsDouble(); } catch (Exception ignored) {}
            try { if (d.has("alpha")) alpha = d.get("alpha").getAsDouble(); } catch (Exception ignored) {}
            try { if (d.has("minOdds")) minOdds = d.get("minOdds").getAsInt(); } catch (Exception ignored) {}
            try { if (d.has("maxOdds")) maxOdds = d.get("maxOdds").getAsInt(); } catch (Exception ignored) {}
            try { if (d.has("margin")) margin = d.get("margin").getAsDouble(); } catch (Exception ignored) {}
        }
    }

    public static void saveToConfig() {
        JsonObject obj = ConfigManager.load();
        JsonObject d = obj.has("dynamicOdds") && obj.get("dynamicOdds").isJsonObject() ? obj.getAsJsonObject("dynamicOdds") : new JsonObject();
        d.addProperty("enabled", enabled);
        d.addProperty("wRating", wRating);
        d.addProperty("wBST", wBST);
        d.addProperty("wType", wType);
        d.addProperty("wSpeed", wSpeed);
        d.addProperty("alpha", alpha);
        d.addProperty("minOdds", minOdds);
        d.addProperty("maxOdds", maxOdds);
        d.addProperty("margin", margin);
        obj.add("dynamicOdds", d);
        ConfigManager.save(obj);
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }
    public static double getwRating() { return wRating; }
    public static double getwBST() { return wBST; }
    public static double getwType() { return wType; }
    public static double getwSpeed() { return wSpeed; }
    public static double getAlpha() { return alpha; }
    public static int getMinOdds() { return minOdds; }
    public static int getMaxOdds() { return maxOdds; }
    public static double getMargin() { return margin; }
}
