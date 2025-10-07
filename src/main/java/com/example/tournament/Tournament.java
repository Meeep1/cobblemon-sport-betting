package com.example.tournament;

import java.util.ArrayList;
import java.util.List;

public class Tournament {
    public static class Match {
        public final String a;
        public final String b;
        public String winner = null;
        public int round = 1;
        public Match(String a, String b) { this.a = a; this.b = b; }
    }

    public enum State { CREATED, STARTED, FINISHED }

    private final String id;
    private State state = State.CREATED;
    private final List<String> entrants = new ArrayList<>();
    private final List<Match> matches = new ArrayList<>();
    private int currentIndex = -1;

    public Tournament(String id) { this.id = id == null ? "untitled" : id; }

    public String getId() { return id; }
    public State getState() { return state; }
    public List<Match> getMatches() { return matches; }
    public Match getCurrentMatch() { if (currentIndex >= 0 && currentIndex < matches.size()) return matches.get(currentIndex); return null; }

    public boolean addEntrant(String name) { if (name == null || name.isBlank() || state != State.CREATED) return false; if (entrants.contains(name)) return false; entrants.add(name); return true; }
    public boolean removeEntrant(String name) { if (state != State.CREATED) return false; return entrants.remove(name); }

    public boolean start() {
        if (state != State.CREATED) return false;
        matches.clear();
        for (int i = 0; i < entrants.size(); i += 2) {
            String a = entrants.get(i);
            String b = (i + 1 < entrants.size()) ? entrants.get(i + 1) : "BYE";
            Match m = new Match(a, b); m.round = 1; matches.add(m);
        }
        currentIndex = matches.isEmpty() ? -1 : 0;
        state = matches.isEmpty() ? State.FINISHED : State.STARTED;
        return true;
    }

    public boolean reportWinnerAndAdvance(String winner) {
        if (state != State.STARTED || getCurrentMatch() == null) return false;
        getCurrentMatch().winner = winner;
        currentIndex++;
        if (currentIndex >= matches.size()) { state = State.FINISHED; currentIndex = matches.size() - 1; }
        return true;
    }
}
