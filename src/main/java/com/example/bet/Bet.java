package com.example.bet;

import java.util.UUID;

/**
 * Represents a single player's bet on a battle participant at a specific odds multiplier.
 */
public class Bet {
    private final UUID playerUuid;
    private final String targetName; // The battler the player bet on (by name)
    private final int amount; // number of currency units wagered
    private final int oddsMultiplier; // e.g., 150 => 1.5x

    public Bet(UUID playerUuid, String targetName, int amount, int oddsMultiplier) {
        this.playerUuid = playerUuid;
        this.targetName = targetName;
        this.amount = amount;
        this.oddsMultiplier = oddsMultiplier;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getTargetName() { return targetName; }
    public int getAmount() { return amount; }
    public int getOddsMultiplier() { return oddsMultiplier; }
}
