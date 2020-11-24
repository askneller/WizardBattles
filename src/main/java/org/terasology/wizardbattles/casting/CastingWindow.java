// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.casting;

import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.nui.databinding.Binding;
import org.terasology.nui.databinding.ReadOnlyBinding;
import org.terasology.nui.widgets.UILoadBar;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.nui.layers.hud.CoreHudWidget;


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
