// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.casting;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.registry.In;
import org.terasology.utilities.Assets;
import org.terasology.wizardbattles.spell.SpellCastEvent;
import org.terasology.wizardbattles.spell.SpellSelectionComponent;

import java.util.Optional;

@RegisterSystem(RegisterMode.CLIENT)
public class CastingClientSystem extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(CastingClientSystem.class);

    @In
    LocalPlayer localPlayer;

    @ReceiveEvent(netFilter = RegisterMode.CLIENT)
    public void onCompleteCasting(CompleteCastingEvent event, EntityRef entity) {
        entity.removeComponent(CastingComponent.class);
        SpellSelectionComponent spellSelectionComponent = entity.getComponent(SpellSelectionComponent.class);
        if (spellSelectionComponent.selected != null) {
            Optional<Prefab> prefabOptional = Assets.getPrefab(spellSelectionComponent.selected);
            prefabOptional.ifPresent(prefab -> {
                Vector3f pos = new Vector3f(localPlayer.getPosition().x, localPlayer.getPosition().y, localPlayer.getPosition().z);
                Vector3f vd = new Vector3f(localPlayer.getViewDirection().x, localPlayer.getViewDirection().y, localPlayer.getViewDirection().z);
                ActivateEvent activateEvent = new ActivateEvent(
                        null,
                        entity,
                        pos,
                        vd,
                        null,
                        null,
                        0);
                SpellCastEvent spellCastEvent = new SpellCastEvent(activateEvent, prefab);
                entity.send(spellCastEvent);
            });
        }
    }

}
