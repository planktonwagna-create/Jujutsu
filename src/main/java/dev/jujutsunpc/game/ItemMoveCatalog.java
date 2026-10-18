package dev.jujutsunpc.game;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/**
 * JujutsuCraft v51 item classes inspected for native click/swing abilities.
 * Only items with an original skill procedure expose the conditional tab.
 */
public final class ItemMoveCatalog {
    private static final Map<String,List<AbilityCatalog.Move>> ITEMS;
    private static AbilityCatalog.Move m(String id,String title,String procedure) {
        return new AbilityCatalog.Move("item_"+id,title,procedure+"Procedure",0,100,0,18,-1);
    }
    static{
        LinkedHashMap<String,List<AbilityCatalog.Move>> map=new LinkedHashMap<>();
        map.put("weapon_nanami",List.of(m("nanami","Nanami Ratio Strike","WeaponNanamiRightClickedInAir2")));
        map.put("sword_okkotsu_yuta",List.of(m("copy","Copy Technique (item)","CopiedCursedTechniqueRightclicked")));
        map.put("supreme_martial_solution",List.of(m("kamutoke","Kamutoke Discharge","SupremeMartialSolutionRightClickedInAir")));
        map.put("gavel_big",List.of(m("gavel","Gavel Special","GavelRightclicked")));
        map.put("gavel",List.of(m("gavel","Gavel Special","GavelRightclicked")));
        map.put("gavel_long",List.of(m("gavel","Gavel Special","GavelRightclicked")));
        map.put("executioners_sword",List.of(m("gavel","Executioner's Sword Skill","GavelRightclicked")));
        map.put("broom",List.of(m("broom","Broom Flight","BroomRightClick")));
        map.put("dragon_bone",List.of(m("dragon_bone","Dragon Bone Charge","DragonBoneRightclicked")));
        map.put("nyoi_staff",List.of(m("nyoi","Nyoi Staff Skill","NyoiStaffRightclickedOnBlock")));
        map.put("harisen",List.of(m("harisen","Harisen Strike","HarisenEntitySwingsItem")));
        ITEMS=Collections.unmodifiableMap(map);
    }
    public static List<AbilityCatalog.Move> equipped(LivingEntity entity) {
        ResourceLocation name=ForgeRegistries.ITEMS.getKey(entity.getMainHandItem().getItem());
        if(name==null || !"jujutsucraft".equals(name.getNamespace()))return List.of();
        return ITEMS.getOrDefault(name.getPath(),List.of());
    }
    public static String key(LivingEntity entity,AbilityCatalog.Move move){
        ResourceLocation name=ForgeRegistries.ITEMS.getKey(entity.getMainHandItem().getItem());
        return name==null?move.key():name+":"+move.key();
    }
    public static boolean available(LivingEntity npc,AbilityCatalog.Move move) {
        return equipped(npc).stream().anyMatch(m->m.key().equals(move.key())
                && m.procedure().equals(move.procedure()));
    }
    private ItemMoveCatalog(){}
}
