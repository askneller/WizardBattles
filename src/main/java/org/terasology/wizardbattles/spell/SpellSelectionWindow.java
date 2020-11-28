// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.spell;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.logic.common.DisplayNameComponent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.nui.databinding.Binding;
import org.terasology.nui.databinding.ReadOnlyBinding;
import org.terasology.nui.widgets.UIText;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.nui.layers.hud.CoreHudWidget;
import org.terasology.utilities.Assets;

import java.util.Optional;


public class SpellSelectionWindow extends CoreHudWidget {

    @Override
    public void initialise() {
        UIText uiText = find("spellSelection", UIText.class);
        uiText.bindVisible(new ReadOnlyBinding<Boolean>() {
            @Override
            public Boolean get() {
                EntityRef character = CoreRegistry.get(LocalPlayer.class).getCharacterEntity();
                return character != null && character.hasComponent(GrimoireComponent.class);
            }
        });
        uiText.bindText(
                new Binding<String>() {
                    @Override
                    public String get() {
                        EntityRef character = CoreRegistry.get(LocalPlayer.class).getCharacterEntity();
                        if (character == null || !character.hasComponent(GrimoireComponent.class)) {
                            return "";
                        }

                        SpellSelectionComponent spellSelectionComponent =
                                character.getComponent(SpellSelectionComponent.class);
                        String displayName = "None";
                        if (spellSelectionComponent != null && spellSelectionComponent.selected != null
                                && spellSelectionComponent.selected.length() > 0) {
                            Optional<Prefab> optionalPrefab = Assets.getPrefab(spellSelectionComponent.selected);
                            if (optionalPrefab.isPresent()) {
                                Prefab prefab = optionalPrefab.get();
                                DisplayNameComponent displayNameComponent = prefab.getComponent(DisplayNameComponent.class);
                                if (displayNameComponent != null) {
                                    displayName = displayNameComponent.name;
                                }
                            }
                        }
                        return displayName;
                    }

                    @Override
                    public void set(String value) {
                    }
                });
    }
}
