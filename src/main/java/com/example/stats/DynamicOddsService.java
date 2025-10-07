package com.example.stats;

import com.example.config.DynamicOddsConfig;

import java.util.LinkedHashSet;

/** Combines ratings and roster aggregates into a probability and returns odds (x100). */
public class DynamicOddsService {
    public static final DynamicOddsService INSTANCE = new DynamicOddsService();

    public static class OddsPair { public final int oddsA; public final int oddsB; public OddsPair(int a,int b){oddsA=a;oddsB=b;} }

    public OddsPair compute(java.util.Set<String> teamA, java.util.Set<String> teamB) {
        if (teamA == null || teamB == null || teamA.isEmpty() || teamB.isEmpty()) return null;

        // Ratings features
        double avgA = teamA.stream().mapToDouble(RatingsService.INSTANCE::getEffectiveRating).average().orElse(1500.0);
        double avgB = teamB.stream().mapToDouble(RatingsService.INSTANCE::getEffectiveRating).average().orElse(1500.0);
        double fRating = avgA - avgB;

        // Roster aggregates: average BST and Speed
        double bstA = teamA.stream().mapToDouble(n -> RosterTracker.INSTANCE.getAggregates(n).avgBST).average().orElse(500.0);
        double bstB = teamB.stream().mapToDouble(n -> RosterTracker.INSTANCE.getAggregates(n).avgBST).average().orElse(500.0);
        double fBST = bstA - bstB;
        double spA = teamA.stream().mapToDouble(n -> RosterTracker.INSTANCE.getAggregates(n).avgSpeed).average().orElse(80.0);
        double spB = teamB.stream().mapToDouble(n -> RosterTracker.INSTANCE.getAggregates(n).avgSpeed).average().orElse(80.0);
        double fSpeed = spA - spB;

        // Type coverage: use last-rosters if present
        java.util.List<String> aSpecies = new java.util.ArrayList<>();
        java.util.List<String> bSpecies = new java.util.ArrayList<>();
        for (String n : teamA) aSpecies.addAll(RosterTracker.INSTANCE.getRoster(n));
        for (String n : teamB) bSpecies.addAll(RosterTracker.INSTANCE.getRoster(n));
        double covA = RosterTracker.coverageScore(aSpecies, bSpecies);
        double covB = RosterTracker.coverageScore(bSpecies, aSpecies);
        double fType = covA - covB;

        double scoreDiff = DynamicOddsConfig.getwRating() * fRating
                + DynamicOddsConfig.getwBST() * fBST
                + DynamicOddsConfig.getwType() * fType
                + DynamicOddsConfig.getwSpeed() * fSpeed;

        double alpha = DynamicOddsConfig.getAlpha();
        double pA = 1.0 / (1.0 + Math.exp(-alpha * scoreDiff));
        pA = Math.max(0.01, Math.min(0.99, pA));

        // Convert to fair odds
        double fairA = 1.0 / pA;
        double fairB = 1.0 / (1.0 - pA);

        // Apply margin (overround) by scaling towards 1.0
        double m = DynamicOddsConfig.getMargin();
        if (m > 0) {
            fairA = 1.0 + (fairA - 1.0) * (1.0 - m);
            fairB = 1.0 + (fairB - 1.0) * (1.0 - m);
        }

        int min = DynamicOddsConfig.getMinOdds();
        int max = DynamicOddsConfig.getMaxOdds();
        int oa = clamp((int) Math.round(fairA * 100.0), min, max);
        int ob = clamp((int) Math.round(fairB * 100.0), min, max);
        return new OddsPair(oa, ob);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
