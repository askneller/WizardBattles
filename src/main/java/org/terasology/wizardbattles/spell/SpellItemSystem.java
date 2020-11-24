// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.spell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.combatSystem.weaponFeatures.OwnerSpecific;
import org.terasology.combatSystem.weaponFeatures.components.LaunchEntityComponent;
import org.terasology.combatSystem.weaponFeatures.events.LaunchEntityEvent;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.utilities.Assets;
import org.terasology.wizardbattles.casting.CastEvent;

import java.util.Optional;

@RegisterSystem
public class SpellItemSystem extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(SpellItemSystem.class);

    @ReceiveEvent(components = SpellItemComponent.class)
    public void cast(ActivateEvent event, EntityRef entity) {
        SpellItemComponent spellItemComponent = entity.getComponent(SpellItemComponent.class);
        String spellPrefabName = spellItemComponent.spell;
        if (spellPrefabName == null) {
            logger.error("Improperly configured SpellItemComponent: no spell prefab specified");
            return;
        }
        Optional<Prefab> prefabOptional = Assets.getPrefab(spellPrefabName);
        if (!prefabOptional.isPresent()) {
            logger.error("Improperly configured SpellItemComponent: spell prefab does not exist");
            return;
        } else {
            Prefab spellPrefab = prefabOptional.get();
            castSpell(event, spellPrefab, entity);
        }

        // todo will copy the launch entity system code and implement my own that doesn't require an entity with
        // todo a launch entity component, i will set the caster as the "ultimate" owner e.g. player, trap, spell block
        // e.g. player has wand -> player uses wand -> spellItemSystem gets activate event ->
        // system sends cast event to player with item's spell prefab -> casting system (maybe spell effect system)
        // gets event (maybe requires a spellSource component, indicating that this entity can be a source of
        // spells?) -> casting system does what's required based on spell prefab and its components (e.g. launch
        // entity, summon monster, teleport player etc.)
//        if (spellItemComponent.charges > 0) {
//            spellItemComponent.charges--;
//            entity.saveComponent(spellItemComponent);
//            entity.send(new CastEvent(event));
//        } else if (spellItemComponent.charges == -1) {
//            // Unlimited charges
//            entity.send(new CastEvent(event));
//        }
    }

    private void castSpell(ActivateEvent event, Prefab spellPrefab, EntityRef itemEntity) {
        EntityRef ultimateOwner = OwnerSpecific.getUltimateOwner(itemEntity);
        if (ultimateOwner != null) {
            ultimateOwner.send(new SpellCastEvent(event, spellPrefab));
        } else {
            logger.error("Failed to find owner of SpellItem");
        }
    }

    @ReceiveEvent(components = LaunchEntityComponent.class)
    public void onFire(CastEvent event, EntityRef entity) {
        entity.send(new LaunchEntityEvent(event.getDirection()));
    }

}
