// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.mana;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.nui.databinding.Binding;
import org.terasology.nui.databinding.ReadOnlyBinding;
import org.terasology.nui.widgets.UIText;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.nui.layers.hud.CoreHudWidget;


public class ManaWindow extends CoreHudWidget {

    @Override
    public void initialise() {
        UIText uiText = find("mana", UIText.class);
        uiText.bindVisible(new ReadOnlyBinding<Boolean>() {
            @Override
            public Boolean get() {
                EntityRef character = CoreRegistry.get(LocalPlayer.class).getCharacterEntity();
                return character != null && character.hasComponent(ManaComponent.class);
            }
        });
        uiText.bindText(
                new Binding<String>() {
                    @Override
                    public String get() {
                        EntityRef character = CoreRegistry.get(LocalPlayer.class).getCharacterEntity();
                        if (character == null || !character.hasComponent(ManaComponent.class)) {
                            return "";
                        }

                        ManaComponent mana = character.getComponent(ManaComponent.class);
                        return mana.current + " / " + mana.maximum;
                    }

                    @Override
                    public void set(String value) {
                    }
                });
    }
}
