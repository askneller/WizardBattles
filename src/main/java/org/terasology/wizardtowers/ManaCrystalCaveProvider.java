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

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.caves.CaveFacet;
import org.terasology.caves.CaveFacetProvider;
import org.terasology.core.world.generator.facetProviders.PositionFilters;
import org.terasology.entitySystem.Component;
import org.terasology.math.Region3i;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
import org.terasology.rendering.nui.properties.Range;
import org.terasology.utilities.procedural.Noise;
import org.terasology.utilities.procedural.WhiteNoise;
import org.terasology.world.generation.*;
import org.terasology.world.generator.plugin.RegisterPlugin;

import java.util.List;


@RegisterPlugin
@Produces(ManaCrystalFacet.class)
@Requires(@Facet(CaveFacet.class))
public class ManaCrystalCaveProvider implements ConfigurableFacetProvider, FacetProviderPlugin {
    private static final Logger logger = LoggerFactory.getLogger(ManaCrystalCaveProvider.class);
    private Noise densityNoiseGen;
    private boolean hasPrinted = false;

    private ManaCrystalDensityConfiguration configuration = new ManaCrystalDensityConfiguration();

    @Override
    public void setSeed(long seed) {
        densityNoiseGen = new WhiteNoise(seed + 1);
    }

    @Override
    public void process(GeneratingRegion region) {
        CaveFacet caveFacet = region.getRegionFacet(CaveFacet.class);
        ManaCrystalFacet facet =
                new ManaCrystalFacet(region.getRegion(), region.getBorderForFacet(ManaCrystalFacet.class));

        List<Predicate<Vector3i>> filters = Lists.newArrayList();

        filters.add(PositionFilters.probability(densityNoiseGen, configuration.density));
        print(caveFacet);
    }

    private void print(CaveFacet facet) {
        boolean[] values = facet.getInternal();
        boolean shouldPrint = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                shouldPrint = true;
                logger.info("Will print");
                break;
            }
        }
        if (shouldPrint) {
            logger.info("Cave region: {}", facet.getWorldRegion());
            Region3i worldRegion = facet.getWorldRegion();
            if (worldRegion.minX() > -5 && worldRegion.minX() <= 32 && worldRegion.minZ() > -5 && worldRegion.minZ() <= 32) {
                if (!hasPrinted) {
                    hasPrinted = true;
                    printAll(facet);
                }
            }
        }
    }

    private void printAll(CaveFacet facet) {
        Region3i worldRegion = facet.getWorldRegion();
        for (int x = worldRegion.minX(); x <= worldRegion.maxX(); ++x) {
            for (int z = worldRegion.minZ(); z <= worldRegion.maxZ(); ++z) {
                for (int y = worldRegion.maxY(); y >= worldRegion.minY(); --y) {
                    logger.info("Value at {} {} {}: {}", x, y, z, facet.getWorld(x, y, z));
                }
            }
        }
    }

    @Override
    public String getConfigurationName() {
        return "Mana Crystals";
    }

    @Override
    public Component getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(Component configuration) {
        this.configuration = (ManaCrystalDensityConfiguration) configuration;
    }

    private static class ManaCrystalDensityConfiguration implements Component {
        @Range(min = 0, max = 1.0f, increment = 0.05f, precision = 2, description = "Define the overall amount of mana crystals")
        private float density = 0.15f;

    }

}
