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
package org.terasology.wizardtowers;

//import org.terasology.combatSystem.weaponFeatures.OwnerSpecific;
//import org.terasology.combatSystem.weaponFeatures.components.LaunchEntityComponent;
//import org.terasology.combatSystem.weaponFeatures.events.LaunchEntityEvent;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.logic.inventory.ItemComponent;

@RegisterSystem
public class CastingSystem extends BaseComponentSystem {

    @ReceiveEvent(components = SpellComponent.class)
    public void cast(ActivateEvent event, EntityRef entity) {
        // Find the caster
        EntityRef caster = EntityRef.NULL;

        if (entity.hasComponent(ItemComponent.class)) {
//            caster = OwnerSpecific.getUltimateOwner(entity);
        }

        // if no owner of "entity" is present then "entity" becomes "caster". e.g. world generated
        // launchers or player implemented traps that don't have ItemComponent that cast the
        // spell.

        if (caster == EntityRef.NULL || caster == null) {
            caster = entity;
        }

        // Reduce the caster's mana by the cost of the spell
        ManaComponent casterMana = caster.getComponent(ManaComponent.class);
        if (casterMana != null) {
            // Was cast by a player or NPC, reduce their Mana
            SpellComponent spellComponent = entity.getComponent(SpellComponent.class);
            int current = casterMana.current;
            if (current >= spellComponent.manaCost) {
                casterMana.current -= spellComponent.manaCost;
                caster.saveComponent(casterMana);
                // Complete the spell cast
                entity.send(new CastEvent(event));
            }
        } // else was cast by trap or something else
        // TODO handle the case where a non-wizard entity (e.g. NPC) tries to use
        //  something like a spell scroll, maybe create a CasterComponent
    }

//    @ReceiveEvent(components = LaunchEntityComponent.class)
//    public void onFire(CastEvent event, EntityRef entity) {
//        entity.send(new LaunchEntityEvent(event.getDirection()));
//    }

}
