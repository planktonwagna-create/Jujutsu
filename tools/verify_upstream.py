#!/usr/bin/env python3
"""Inspect user-provided JARs without executing mod code or extracting class files."""
import pathlib
import zipfile
import json
import subprocess
import sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
SOURCE=pathlib.Path("/mnt/data") if pathlib.Path("/mnt/data").exists() else ROOT.parent.parent
jars={
    'jujutsucraft': SOURCE/'JujutsuCraft-ver51-forge-1.20.1.jar',
    'easy_npc': SOURCE/'easy_npc-forge-1.20.1-7.15.0.jar',
    'customnpcs': SOURCE/'CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260711.jar'
}
# The ZIP is commonly extracted elsewhere; also allow explicit JAR directory argument.
if len(sys.argv)>1:
    SOURCE=pathlib.Path(sys.argv[1]).resolve()
    jars={k:SOURCE / p.name for k,p in jars.items()}
failed=False
required_classes={
    'jujutsucraft':['net/mcreator/jujutsucraft/procedures/TechniqueRedProcedure.class',
                    'net/mcreator/jujutsucraft/procedures/HollowPurpleProcedure.class',
                    'net/mcreator/jujutsucraft/procedures/DismantleProcedure.class',
                    'net/mcreator/jujutsucraft/init/JujutsucraftModMobEffects.class'],
    'easy_npc':['de/markusbordihn/easynpc/entity/easynpc/EasyNPC.class'],
    'customnpcs':['noppes/npcs/entity/EntityNPCInterface.class']
}
for name, path in jars.items():
    if not path.is_file():
        print('MISSING',path)
        failed=True
        continue
    with zipfile.ZipFile(path) as z:
        contents=set(z.namelist())
        for cls in required_classes[name]:
            if cls not in contents:
                print('MISSING CLASS',name,cls)
                failed=True
        if name=='jujutsucraft':
            for animation in ['red','domain_expansion1','simple_domain1']:
                filename=f'assets/jujutsucraft/player_animation/{animation}.json'
                assert filename in contents
                payload=json.loads(z.read(filename))
                assert 'animations' in payload
            n_loot=len([n for n in contents if n.startswith('data/jujutsucraft/loot_tables/') and n.endswith('.json')])
            overridden=len(list((ROOT/'src/main/resources/data/jujutsucraft/loot_tables').rglob('*.json')))
            if n_loot!=overridden:
                print('LOOT TABLE OVERRIDE MISMATCH',n_loot,overridden)
                failed=True
            else: print('PASS: all',n_loot,'JJK mod-native loot tables overridden')
    print('PASS' if not failed else 'CHECK',name,'JAR content inspection')
print('NOTE: JAR inspection does not compile or execute the addon.')
raise SystemExit(1 if failed else 0)
