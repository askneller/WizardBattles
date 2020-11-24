/*
 * Copyright 2019 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.wizardbattles;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.prefab.Prefab;

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
