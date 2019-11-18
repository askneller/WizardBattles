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
import org.terasology.caves.CaveFacet;
import org.terasology.entitySystem.Component;
import org.terasology.math.Region3i;
import org.terasology.rendering.nui.properties.Range;
import org.terasology.utilities.procedural.Noise;
import org.terasology.world.generation.*;
import org.terasology.world.generator.plugin.RegisterPlugin;

@RegisterPlugin
@Produces(CaveFloorFacet.class)
@Requires(@Facet(CaveFacet.class))
public class CaveFloorProvider implements ConfigurableFacetProvider, FacetProviderPlugin {
    private static final Logger logger = LoggerFactory.getLogger(CaveFloorProvider.class);
    private Noise densityNoiseGen;
    private boolean hasPrinted = false;

    private CaveFloorConfiguration configuration = new CaveFloorConfiguration();

    public static final float NO_CAVE = -999999999.0f;

    // nothing calls Region.getFacet(CaveFloorFacet.class) and so this method is never called
    // todo: but SurfaceHeightProvider is never rasterised?? Look into that
    @Override
    public void process(GeneratingRegion region) {
        // If no region calls region.getFacet(<the provided facet class>) then this method 'process' will never be called
        CaveFacet caveFacet = region.getRegionFacet(CaveFacet.class);
        CaveFloorFacet floorFacet =
                new CaveFloorFacet(region.getRegion(), region.getBorderForFacet(CaveFloorFacet.class));

        Region3i worldRegion = caveFacet.getWorldRegion();
        for (int x = worldRegion.minX(); x <= worldRegion.maxX(); ++x) {
            for (int z = worldRegion.minZ(); z <= worldRegion.maxZ(); ++z) {
                // The first false we encounter may be the ceiling, so the floor is indicated by the first false
                // after the we encounter a true
                boolean foundTrue = false;
                int y;
                for (y = worldRegion.maxY(); y >= worldRegion.minY(); --y) {
                    boolean cave = caveFacet.getWorld(x, y, z);
                    if (!foundTrue && cave) {
                        foundTrue = true;
                    } else if (foundTrue && !cave) {
//                        logger.info("Found floor at {} {} {}", x, y + 1, z);
                        floorFacet.setWorld(x, z, y + 1);
                        break;
                    }
                }
                if (y < worldRegion.minY()) {
                    // Didn't find any cave
                    floorFacet.setWorld(x, z, NO_CAVE);
                }
            }
        }
//        print(caveFacet, floorFacet);
        region.setRegionFacet(CaveFloorFacet.class, floorFacet);
    }

    private void print(CaveFacet facet, CaveFloorFacet floorFacet) {
        boolean[] values = facet.getInternal();
        boolean shouldPrint = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                shouldPrint = true;
//                logger.info("Will print");
                break;
            }
        }
        if (shouldPrint) {
//            logger.info("Cave region: {}", facet.getWorldRegion());
            Region3i worldRegion = facet.getWorldRegion();
            if (worldRegion.minX() > -5 && worldRegion.minX() <= 32 && worldRegion.minZ() > -5 && worldRegion.minZ() <= 32) {
                if (!hasPrinted) {
                    hasPrinted = true;
                    printAll(facet, floorFacet);
                }
            }
        }
    }

    private void printAll(CaveFacet facet, CaveFloorFacet floorFacet) {
        Region3i worldRegion = facet.getWorldRegion();
        for (int x = worldRegion.minX(); x <= worldRegion.maxX(); ++x) {
            for (int z = worldRegion.minZ(); z <= worldRegion.maxZ(); ++z) {
                boolean foundTrue = false;
                if (checkForCave(x, z, facet)) {
                    for (int y = worldRegion.maxY(); y >= worldRegion.minY(); --y) {
                        boolean cave = facet.getWorld(x, y, z);
                        if (cave) {
                            foundTrue = true;
                        }
                        logger.info("Value at {} {} {}: {}", x, y, z, cave);
                        if (foundTrue && !cave) {
                            break;
                        }
                    }
                    float floor = floorFacet.getWorld(x, z);
                    logger.info("Floor at {} {}: {}", x, z, Math.abs(floor - NO_CAVE) < 1.0f ? "None" : floor);
                }
            }
        }
    }

    private boolean checkForCave(int x, int z, CaveFacet facet) {
        Region3i worldRegion = facet.getWorldRegion();
        for (int y = worldRegion.maxY(); y >= worldRegion.minY(); --y) {
            if (facet.getWorld(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getConfigurationName() {
        return "Cave Floor";
    }

    @Override
    public Component getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(Component configuration) {
        this.configuration = (CaveFloorConfiguration) configuration;
    }

    private static class CaveFloorConfiguration implements Component {
        @Range(min = 0, max = 1.0f, increment = 0.05f, precision = 2, description = "Blah")
        private float density = 0.15f;

    }

}
