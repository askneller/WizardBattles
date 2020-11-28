// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.spell;

import org.terasology.entitySystem.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <em>Grimoire</em> is an old term for a "book of spells". This component represents all of the spells that the
 * entity (player or NPC) has learned.
 */
public class GrimoireComponent implements Component {

    /**
     * A list of all spells that this entity knows. A list of spell prefabs.
     */
    public List<String> knownSpells = new ArrayList<>();
}
