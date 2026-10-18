package dev.jujutsunpc.sound;

/** Populated only by server messages; never by client-local config preferences. */
public final class ClientHitSoundState {
    private static volatile boolean npc=true;
    private static volatile boolean player=true;
    public static void sync(boolean forNpc,boolean forPlayer){
        npc=forNpc;
        player=forPlayer;
    }
    public static boolean npcEnabled(){return npc;}
    public static boolean playerEnabled(){return player;}
    private ClientHitSoundState(){}
}
