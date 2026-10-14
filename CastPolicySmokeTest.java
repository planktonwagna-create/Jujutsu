package dev.jujutsunpc.game;
import dev.jujutsunpc.game.CastPolicy.Mode;

public final class CastPolicySmokeTest {
    static void check(boolean success, String name) {
        if (!success) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        check(!CastPolicy.canCast(Mode.DISABLED,true,999,0,999,0),"disabled manual");
        check(!CastPolicy.canCast(Mode.AUTOMATIC,true,999,0,999,0),"auto-only cannot manual");
        check(CastPolicy.canCast(Mode.TRIGGERED,true,0,99999,0,999),"manual ignores cooldown and energy");
        check(!CastPolicy.canCast(Mode.BOTH,false,0,35,50,0),"auto honors energy");
        check(!CastPolicy.canCast(Mode.BOTH,false,99,35,40,60),"auto honors cooldown");
        check(CastPolicy.canCast(Mode.BOTH,false,99,35,61,60),"auto ready");
        check(AbilityCatalog.move("limitless","blue").isPresent(),"blue catalog");
        check(AbilityCatalog.move("shrine","blue").isEmpty(),"can't grant second technique moves");
        System.out.println("PASS: 8 combat policy / move catalog checks");
    }
}
