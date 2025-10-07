package com.example.bet;

import java.util.Objects;

/** A single prop condition that can be used as a standalone prop or as a parlay leg. */
public class PropCondition {
    public enum Type {
        FIRST_FAINT,         // param: "A" or "B" (maps to BetManager playerA/playerB)
        TOTAL_FAINTS_OVER    // param: integer threshold as string, e.g., "5"
    }

    private final Type type;
    private final String param; // stringly-typed to avoid extra boxing
    private final int oddsMultiplier; // x100, e.g., 200 = 2.0x

    public PropCondition(Type type, String param, int oddsMultiplier) {
        this.type = type;
        this.param = param;
        this.oddsMultiplier = oddsMultiplier;
    }

    public Type getType() { return type; }
    public String getParam() { return param; }
    public int getOddsMultiplier() { return oddsMultiplier; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PropCondition)) return false;
        PropCondition that = (PropCondition) o;
        return oddsMultiplier == that.oddsMultiplier && type == that.type && Objects.equals(param, that.param);
    }

    @Override
    public int hashCode() { return Objects.hash(type, param, oddsMultiplier); }
}
