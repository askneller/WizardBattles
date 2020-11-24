// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.mana;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.Time;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.registry.In;
import org.terasology.rendering.nui.NUIManager;

/**
 * Handles client-side functionality for Hunger features.
 */
@RegisterSystem(RegisterMode.CLIENT)
public class ManaClientSystem extends BaseComponentSystem {
    /**
     * The logger for debugging to the log files.
     */
    private static final Logger logger = LoggerFactory.getLogger(ManaClientSystem.class);

    @In
    private NUIManager nuiManager;

    @In
    private Time time;

    /**
     * Adds the hunger bar to the player's HUD.
     */
    @Override
    public void preBegin() {
        nuiManager.getHUD().addHUDElement("WizardBattles:Mana");
    }
}
