// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.mana;

import org.terasology.entitySystem.Component;

/**
 * Component for entities that can accumulate magical power (Mana).
 */
public class ManaComponent implements Component {

    public int maximum = 100;
    public int current;
    public int regenRate = 3;
}
