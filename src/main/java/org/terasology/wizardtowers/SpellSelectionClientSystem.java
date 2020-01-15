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
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.registry.In;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.utilities.Assets;

import java.util.List;
import java.util.Optional;

@RegisterSystem(RegisterMode.CLIENT)
public class SpellSelectionClientSystem extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(SpellSelectionClientSystem.class);

    @In
    private LocalPlayer localPlayer;

    @In
    private EntityManager entityManager;

    @In
    private NUIManager nuiManager;

    @Override
    public void initialise() {
        logger.info("Init");
        nuiManager.getHUD().addHUDElement("WizardTowers:SpellSelection");
        nuiManager.getHUD().addHUDElement("WizardTowers:Casting");
    }

    @ReceiveEvent(components = {CharacterComponent.class}, netFilter = RegisterMode.CLIENT)
    public void onNextSpell(NextSpellButton event, EntityRef entity,
                           SpellSelectionComponent spellSelectionComponent, GrimoireComponent grimoireComponent) {
        List<String> knownSpells = grimoireComponent.knownSpells;
        String current = spellSelectionComponent.selected;
        String spell = null;
        if (current == null || current.length() == 0) {
            if (knownSpells.size() > 0) {
                spell = knownSpells.get(0);
            }
        } else {
            int currentIndex = knownSpells.indexOf(current);
            if (currentIndex == knownSpells.size() - 1) {
                spell = knownSpells.get(0);
            } else {
                spell = knownSpells.get(currentIndex + 1);
            }
        }
        spellSelectionComponent.selected = spell;

        localPlayer.getCharacterEntity().saveComponent(spellSelectionComponent);
        event.consume();
    }

    @ReceiveEvent(components = {CharacterComponent.class}, netFilter = RegisterMode.CLIENT)
    public void onPrevSpell(PrevSpellButton event, EntityRef entity,
                           SpellSelectionComponent spellSelectionComponent, GrimoireComponent grimoireComponent) {
        List<String> knownSpells = grimoireComponent.knownSpells;
        String current = spellSelectionComponent.selected;
        String spell = null;
        if (current == null || current.length() == 0) {
            if (knownSpells.size() > 0) {
                spell = knownSpells.get(knownSpells.size() - 1);
            }
        } else {
            int currentIndex = knownSpells.indexOf(current);
            if (currentIndex == 0) {
                spell = knownSpells.get(knownSpells.size() - 1);
            } else {
                spell = knownSpells.get(currentIndex - 1);
            }
        }
        spellSelectionComponent.selected = spell;

        localPlayer.getCharacterEntity().saveComponent(spellSelectionComponent);
        event.consume();
    }

    @ReceiveEvent(components = {CharacterComponent.class}, netFilter = RegisterMode.CLIENT)
    public void onCastSpellButton(CastSpellButton event, EntityRef entity, SpellSelectionComponent spellSelectionComponent) {
        String current = spellSelectionComponent.selected;
        if (current != null) {
            Optional<Prefab> optionalPrefab = Assets.getPrefab(current);
            if (optionalPrefab.isPresent()) {
                Prefab spellPrefab = optionalPrefab.get();
                if (ManaUtil.hasSufficient(spellPrefab, entity)) {
                    ManaUtil.sendConsumeEvent(entity, spellPrefab);
                    entity.send(new BeginCastingEvent());
                }
            }
        }
    }
}
