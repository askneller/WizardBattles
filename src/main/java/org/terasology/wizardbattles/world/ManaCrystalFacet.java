// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.world;

import org.terasology.math.Region3i;
import org.terasology.world.generation.Border3D;
import org.terasology.world.generation.facets.base.SparseObjectFacet3D;

/**
 * Stores where mana crystals can be placed
 */
public class ManaCrystalFacet extends SparseObjectFacet3D<ManaCrystalType> {

    public ManaCrystalFacet(Region3i targetRegion, Border3D border) {
        super(targetRegion, border);
    }
}
