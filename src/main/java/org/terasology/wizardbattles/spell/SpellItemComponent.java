// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.spell;

import org.terasology.entitySystem.Component;

public class SpellItemComponent implements Component {

    public String spell;
    public int charges = -1; // -1 means unlimited
}
