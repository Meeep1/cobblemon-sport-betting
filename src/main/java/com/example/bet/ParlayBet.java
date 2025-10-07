package com.example.bet;

import java.util.List;
import java.util.UUID;

/** A parlay bet: up to 2 legs (PropConditions) with a single stake. */
public class ParlayBet {
    private final UUID playerUuid;
    private final List<PropCondition> legs; // size 1 or 2 for now
    private final int stakeBronze;

    public ParlayBet(UUID playerUuid, List<PropCondition> legs, int stakeBronze) {
        this.playerUuid = playerUuid;
        this.legs = legs;
        this.stakeBronze = stakeBronze;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public List<PropCondition> getLegs() { return legs; }
    public int getStakeBronze() { return stakeBronze; }
}
