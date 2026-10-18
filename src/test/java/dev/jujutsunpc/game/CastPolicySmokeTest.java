package dev.jujutsunpc.game;
import dev.jujutsunpc.game.CastPolicy.Mode;

public final class CastPolicySmokeTest {
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
    public static void main(String[] args) {
        check(!CastPolicy.canCast(Mode.DISABLED,true,0,0),"disabled manual");
        check(!CastPolicy.canCast(Mode.AUTOMATIC,true,0,0),"auto-only cannot manual");
        check(!CastPolicy.canCast(Mode.TRIGGERED,true,30,60),"manual obeys cooldown");
        check(CastPolicy.canCast(Mode.TRIGGERED,true,60,60),"manual ready");
        check(!CastPolicy.canCast(Mode.BOTH,false,50,60),"auto obeys cooldown");
        check(CastPolicy.canCast(Mode.BOTH,false,60,60),"auto ready");
        check(!CastPolicy.canCast(Mode.TRIGGERED,false,60,0),"triggered not auto");
        check(CastPolicy.secondsToTicks(15)==300L,"seconds to ticks");
        check(AbilityCatalog.move("limitless","blue").isPresent(),"blue available");
        check(AbilityCatalog.move("shrine","blue").isEmpty(),"move tied to technique");
        check(AbilityCatalog.ALL.size() >= 45,"many expanded techniques");
        check(AbilityCatalog.move("limitless","attack").isPresent(),"universal melee");
        check(AbilityCatalog.move("shrine","barrage").isPresent(),"universal barrage");
        check(AbilityCatalog.supportsCharge(AbilityCatalog.move("limitless","blue").orElseThrow()),"native hold check for blue");
        check(AbilityCatalog.supportsCrouch(AbilityCatalog.move("shrine","dismantle").orElseThrow()),"native sneak check");
        System.out.println("PASS: cooldown-only policy checks");
    }
}
