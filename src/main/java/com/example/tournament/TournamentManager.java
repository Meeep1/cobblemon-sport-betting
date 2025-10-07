package com.example.tournament;

import net.minecraft.server.MinecraftServer;

public class TournamentManager {
    public static final TournamentManager INSTANCE = new TournamentManager();
    private Tournament active;

    public Tournament getActive() { return active; }

    public boolean create(String id) { this.active = new Tournament(id); return true; }
    public boolean start() { return active != null && active.start(); }
    public void announceState(MinecraftServer server) { /* no-op stub */ }
    public boolean add(String name) { return active != null && active.addEntrant(name); }
    public boolean remove(String name) { return active != null && active.removeEntrant(name); }
    public void reportWinnerAndAdvance(MinecraftServer server, String winner) { if (active != null) active.reportWinnerAndAdvance(winner); }
}
