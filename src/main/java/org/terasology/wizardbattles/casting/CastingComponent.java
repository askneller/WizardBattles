// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.casting;

import org.terasology.entitySystem.Component;

public class CastingComponent implements Component {
    /**
     * The time when casting was started as returned by Time.getGameTimeInMs
     */
    public long begunAt;

    /**
     * The number of ms required to cast the spell. Copied from SpellComponent
     */
    public long timeRequired;
}
