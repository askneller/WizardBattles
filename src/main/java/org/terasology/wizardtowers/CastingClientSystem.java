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

import java.util.Optional;

@RegisterSystem(RegisterMode.CLIENT)
public class CastingClientSystem extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(CastingClientSystem.class);

    @In
    LocalPlayer localPlayer;

    @ReceiveEvent(netFilter = RegisterMode.CLIENT)
    public void onCompleteCasting(CompleteCastingEvent event, EntityRef entity) {
        logger.info("Received CompleteCastingEvent on entity\n{} {}", event, entity.toString());
        entity.removeComponent(CastingComponent.class);
        SpellSelectionComponent spellSelectionComponent = entity.getComponent(SpellSelectionComponent.class);
        if (spellSelectionComponent.selected != null) {
            Optional<Prefab> prefabOptional = Assets.getPrefab(spellSelectionComponent.selected);
            prefabOptional.ifPresent(prefab -> {
                ActivateEvent activateEvent = new ActivateEvent(
                        null,
                        entity,
                        localPlayer.getPosition(),
                        localPlayer.getViewDirection(),
                        null,
                        null,
                        0);
                SpellCastEvent spellCastEvent = new SpellCastEvent(activateEvent, prefab);
                entity.send(spellCastEvent);
            });
        }
    }

}
