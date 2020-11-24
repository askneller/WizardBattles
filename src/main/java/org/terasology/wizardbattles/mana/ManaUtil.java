// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.mana;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.wizardbattles.spell.SpellComponent;

public class ManaUtil {

    public static boolean hasSufficient(Prefab prefab, EntityRef entity) {
        SpellComponent spellComponent = prefab.getComponent(SpellComponent.class);
        return spellComponent != null && hasSufficient(spellComponent.manaCost, entity);
    }

    public static boolean hasSufficient(int required, EntityRef entity) {
        ManaComponent manaComponent = entity.getComponent(ManaComponent.class);
        return manaComponent != null && manaComponent.current > required;
    }

    public static void sendConsumeEvent(EntityRef entity, Prefab prefab) {
        SpellComponent spellComponent = prefab.getComponent(SpellComponent.class);
        entity.send(new ConsumeManaEvent(spellComponent.manaCost));
    }
}
