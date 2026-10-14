package dev.jujutsunpc.game;

import java.util.*;

/** Deliberately limited to v51 procedure classes confirmed present in the supplied JAR. */
public final class AbilityCatalog {
    public record Move(String key, String title, String procedure, int cost, int cooldownTicks,
                       double optimalMinRange, double optimalMaxRange) {}
    /** Source animation names from JujutsuCraft v51's player_animation JSON resources. */
    public static String originalAnimation(String moveKey) {
        return switch (moveKey) {
            case "red" -> "red";
            case "blue" -> "right_arm_front";
            case "purple" -> "both_arm_front";
            case "dismantle", "cleave" -> "punch_right";
            default -> "";
        };
    }
    public record Technique(String key, String title, List<Move> moves) {}

    public static final Technique NONE = new Technique("none", "No technique", List.of());
    public static final Technique LIMITLESS = new Technique("limitless", "Limitless", List.of(
        new Move("blue", "Blue", "TechniqueBlueProcedure", 35, 90, 3, 17),
        new Move("red", "Red", "TechniqueRedProcedure", 40, 100, 2, 17),
        new Move("purple", "Hollow Purple", "HollowPurpleProcedure", 100, 260, 7, 29)));
    public static final Technique SHRINE = new Technique("shrine", "Shrine", List.of(
        new Move("dismantle", "Dismantle", "DismantleProcedure", 25, 65, 2, 19),
        new Move("cleave", "Cleave", "CleaveProcedure", 20, 55, 0, 4)));

    public static final List<Technique> ALL = List.of(NONE, LIMITLESS, SHRINE);
    private static final Map<String, Technique> BY_KEY;
    static {
        Map<String, Technique> map = new LinkedHashMap<>();
        for (Technique t : ALL) map.put(t.key(), t);
        BY_KEY = Collections.unmodifiableMap(map);
    }
    public static Technique technique(String id) { return BY_KEY.getOrDefault(id, NONE); }
    public static Optional<Move> move(String techniqueId, String moveId) {
        return technique(techniqueId).moves().stream().filter(m -> m.key().equals(moveId)).findFirst();
    }
    private AbilityCatalog() {}
}
