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
