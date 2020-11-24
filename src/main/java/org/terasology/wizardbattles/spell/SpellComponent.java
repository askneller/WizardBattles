// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.spell;

import org.terasology.entitySystem.Component;

public class SpellComponent implements Component {

    public int manaCost;
    public long castingTimeMs;
}
