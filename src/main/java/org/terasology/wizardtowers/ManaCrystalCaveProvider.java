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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.caves.CaveFloorFacet;
import org.terasology.entitySystem.Component;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.rendering.nui.properties.Range;
import org.terasology.utilities.procedural.Noise;
import org.terasology.utilities.procedural.WhiteNoise;
import org.terasology.world.generation.ConfigurableFacetProvider;
import org.terasology.world.generation.Facet;
import org.terasology.world.generation.FacetProviderPlugin;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.Produces;
import org.terasology.world.generation.Requires;
import org.terasology.world.generation.facets.SurfaceHeightFacet;
import org.terasology.world.generator.plugin.RegisterPlugin;


@RegisterPlugin
@Produces(ManaCrystalFacet.class)
@Requires({@Facet(CaveFloorFacet.class), @Facet(value = SurfaceHeightFacet.class)})
public class ManaCrystalCaveProvider implements ConfigurableFacetProvider, FacetProviderPlugin {

    private static final Logger logger = LoggerFactory.getLogger(ManaCrystalCaveProvider.class);
    private Noise densityNoiseGen;

    private ManaCrystalDensityConfiguration configuration = new ManaCrystalDensityConfiguration();

    @Override
    public void setSeed(long seed) {
        densityNoiseGen = new WhiteNoise(seed + 1);
    }

    @Override
    public void process(GeneratingRegion region) {
        CaveFloorFacet floorFacet = region.getRegionFacet(CaveFloorFacet.class);
        ManaCrystalFacet facet =
                new ManaCrystalFacet(region.getRegion(), region.getBorderForFacet(ManaCrystalFacet.class));

        Region3i worldRegion = facet.getWorldRegion();
        int minY = worldRegion.minY();
        int maxY = worldRegion.maxY();

        for (int z = worldRegion.minZ(); z <= worldRegion.maxZ(); z++) {
            for (int x = worldRegion.minX(); x <= worldRegion.maxX(); x++) {
                float caveFloorHeight = floorFacet.getWorld(x, z);
                int caveFloorInt = TeraMath.floorToInt(caveFloorHeight);

                // if the surface is in range
                if (hasCave(caveFloorHeight) && caveFloorInt >= minY && caveFloorInt <= maxY) {
                    // Does it meet depth requirements
                    SurfaceHeightFacet surfaceHeightFacet = region.getRegionFacet(SurfaceHeightFacet.class);
                    float s = surfaceHeightFacet.getWorld(x, z);
                    int surface = TeraMath.floorToInt(s);
                    boolean isDeepEnough = caveFloorInt < (float) surface - configuration.minDepth;
                    if (isDeepEnough && Math.abs(densityNoiseGen.noise(x, z)) < configuration.density) {
                        facet.setWorld(x, caveFloorInt + 1, z, ManaCrystalType.DEFAULT);
                    }
                }
            }
        }
        region.setRegionFacet(ManaCrystalFacet.class, facet);
    }

    private boolean hasCave(float caveFloor) {
        return !Float.isNaN(caveFloor);
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
        @Range(min = 0, max = 1.0f, increment = 0.01f, precision = 2, description = "Define the overall amount of mana crystals")
        private float density = 0.01f;

        @Range(min = 0, max = 250f, increment = 1f, precision = 0, description = "The minimum distance below the surface")
        private float minDepth = 30f;
    }

}
