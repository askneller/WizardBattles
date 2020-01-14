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

import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.nui.databinding.Binding;
import org.terasology.rendering.nui.databinding.ReadOnlyBinding;
import org.terasology.rendering.nui.layers.hud.CoreHudWidget;
import org.terasology.rendering.nui.widgets.UILoadBar;


public class CastingWindow extends CoreHudWidget {

    @Override
    public void initialise() {
        UILoadBar castingProgress = find("castingProgress", UILoadBar.class);
        castingProgress.bindVisible(new ReadOnlyBinding<Boolean>() {
            @Override
            public Boolean get() {
                EntityRef character = CoreRegistry.get(LocalPlayer.class).getCharacterEntity();
                return character != null && character.hasComponent(CastingComponent.class);
            }
        });
        castingProgress.bindValue(
                new Binding<Float>() {
                    @Override
                    public Float get() {
                        EntityRef character = CoreRegistry.get(LocalPlayer.class).getCharacterEntity();
                        if (character == null || !character.hasComponent(CastingComponent.class)) {
                            return 0.0f;
                        }

                        CastingComponent casting = character.getComponent(CastingComponent.class);
                        long gameTime = CoreRegistry.get(Time.class).getGameTimeInMs();
                        return ((float) (gameTime - casting.begunAt) / casting.timeRequired);
                    }

                    @Override
                    public void set(Float value) {
                    }
                });
    }
}
