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
package org.terasology.wizardtowers.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.biomesAPI.Biome;
import org.terasology.core.world.CoreBiome;
import org.terasology.core.world.generator.facets.BiomeFacet;
import org.terasology.core.world.generator.facets.RoughnessFacet;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Vector3i;
import org.terasology.rendering.nui.properties.Range;
import org.terasology.structureTemplates.components.SpawnBlockRegionsComponent;
import org.terasology.utilities.Assets;
import org.terasology.utilities.procedural.Noise;
import org.terasology.utilities.procedural.WhiteNoise;
import org.terasology.world.block.Block;
import org.terasology.world.generation.ConfigurableFacetProvider;
import org.terasology.world.generation.Facet;
import org.terasology.world.generation.FacetProviderPlugin;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.Produces;
import org.terasology.world.generation.Requires;
import org.terasology.world.generation.facets.SeaLevelFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;
import org.terasology.world.generator.plugin.RegisterPlugin;

import java.util.Optional;

/**
 * Determines where structures can be placed.  Will put structures at the surface.
 */
@RegisterPlugin
@Produces(WizardTowerFacet.class)
@Requires({
        @Facet(value = SeaLevelFacet.class),
        @Facet(value = SurfaceHeightFacet.class),
        @Facet(value = BiomeFacet.class),
        @Facet(value = RoughnessFacet.class)
})
public class WizardTowerProvider implements ConfigurableFacetProvider, FacetProviderPlugin {

    private static final Logger logger = LoggerFactory.getLogger(WizardTowerProvider.class);

    private Noise densityNoiseGen;
    private Configuration configuration = new Configuration();
    StructureGenerator structureGenerator;
    private int size = 14;

    public WizardTowerProvider() {
        structureGenerator = (blockManager, view, rand, posX, posY, posZ) -> {
            Optional<Prefab> prefabOptional = Assets.getPrefab("WizardTowers:tower");
            if (prefabOptional.isPresent()) {
                Prefab prefab = prefabOptional.get();
                SpawnBlockRegionsComponent spawnBlockRegions = prefab.getComponent(SpawnBlockRegionsComponent.class);
                if (spawnBlockRegions != null) {
                    Vector3i vector3i = view.chunkToWorldPosition(posX, posY, posZ);
                    logger.debug("Generating at {} (world center), {} {} {} {} (region relative center)",
                            vector3i, view.getRegion(), posX, posY, posZ);
                    for (SpawnBlockRegionsComponent.RegionToFill regionToFill : spawnBlockRegions.regionsToFill) {
                        Block block = regionToFill.blockType;

                        Region3i region = regionToFill.region;
                        // Block positions are specified relative to the centre of the tower for X and Z,
                        // and relative to the bottom for Y
                        for (Vector3i pos : region) {
                            int relX = pos.x + posX;
                            int relY = pos.y + posY;
                            int relZ = pos.z + posZ;
                            view.setBlock(relX, relY, relZ, block);
                        }
                    }
                }
            }
        };
    }

    /**
     * @param configuration the default configuration to use
     */
    public WizardTowerProvider(Configuration configuration) {
        this();
        this.configuration = configuration;
    }

    @Override
    public void setSeed(long seed) {
        densityNoiseGen = new WhiteNoise(seed);
    }

    @Override
    public void process(GeneratingRegion region) {
        // todo make sure a region only has one structure
        // todo expand border so that the tower doesn't exceed the height of the region
        // todo see if we can determine a max size flat area
        // todo the surface doesn't need to be wholly within the region, just enough for a large enough flat patch
        SurfaceHeightFacet surface = region.getRegionFacet(SurfaceHeightFacet.class);
        BiomeFacet biome = region.getRegionFacet(BiomeFacet.class);

        WizardTowerFacet facet =
                new WizardTowerFacet(region.getRegion(), region.getBorderForFacet(WizardTowerFacet.class));

        float minY = region.getRegion().minY();
        float maxY = region.getRegion().maxY();

        SeaLevelFacet seaLevelFacet = region.getRegionFacet(SeaLevelFacet.class);

        if (minY > seaLevelFacet.getSeaLevel()) {
            // Determine if the surface lies wholly within the region's Y range
            boolean surfaceWithinRegion = true;
            float max = -100000.0f;
            float min = 10000000.0f;
            for (float s : surface.getInternal()) {
                if (s < min) {
                    min = s;
                } else if (s > max) {
                    max = s;
                }

                if (s < minY || s > maxY) {
                    surfaceWithinRegion = false;
                    break;
                }
            }

            if (surfaceWithinRegion) {
                RoughnessFacet roughnessFacet = region.getRegionFacet(RoughnessFacet.class);
                if (roughnessFacet.getMeanDeviation() < 1.5f) {
                    int minX = region.getRegion().minX();
                    int minZ = region.getRegion().minZ();
                    int maxX = region.getRegion().maxX();
                    int maxZ = region.getRegion().maxZ();
                    boolean biomeCheck = isCorrectBiome(region.getRegion(), biome);
                    if (biomeCheck) {
                        int startX = minX;
                        int startZ = minZ;
                        float minH = 100000f;
                        float maxH = -100000f;
                        boolean foundCandidate = false;
                        // Attempt to find a (size + 1) by (size + 1) patch where the height doesn't vary too much
                        do {
                            // Scan this (size +1) x (size +1) patch
                            for (int x = startX, i = 0; x <= maxX && i <= size; ++i, ++x) {
                                for (int z = startZ, j = 0; z <= maxZ && j <= size; ++z, ++j) {
                                    float height = surface.getWorld(x, z);
                                    if (height < minH) {
                                        minH = height;
                                    }
                                    if (height > maxH) {
                                        maxH = height;
                                    }
                                }
                            }
                            float range = maxH - minH;
                            if (range < 2f) { // Is the height variation of the blocks within a small range
                                foundCandidate = true;
                                int centerX = startX + (size / 2);
                                int centerZ = startZ + (size / 2);
                                int height = TeraMath.floorToInt(minH) + 1;
                                logger.debug("Candidate at {} {} {}, starting from {} {} {}, biome {}\n(max {}, min {}, range {})",
                                        centerX, height, centerZ, startX, height, startZ,
                                        biome.getWorld(startX, startZ).getName(), maxH, minH, maxH - minH);

                                facet.setWorld(centerX, height, centerZ, structureGenerator);
                            }
                            if (!foundCandidate) {
                                startX += size;
                                startZ += size;
                            }
                        } while (!foundCandidate && startX < maxX);
                    }
                }
            }
        }

        region.setRegionFacet(WizardTowerFacet.class, facet);
    }

    private boolean isCorrectBiome(Region3i region3i, BiomeFacet biomeFacet) {
        int minX = region3i.minX();
        int minZ = region3i.minZ();
        int maxX = region3i.maxX();
        int maxZ = region3i.maxZ();
        Biome biomeMin = biomeFacet.getWorld(minX, minZ);
        Biome biomeMax = biomeFacet.getWorld(maxX, maxZ);
        return (biomeMin.equals(CoreBiome.MOUNTAINS) && biomeMax.equals(CoreBiome.MOUNTAINS))
                || (biomeMin.equals(CoreBiome.SNOW) && biomeMax.equals(CoreBiome.SNOW))
                || (biomeMin.equals(CoreBiome.PLAINS) && biomeMax.equals(CoreBiome.PLAINS));
    }

    @Override
    public String getConfigurationName() {
        return "WizardTowers";
    }

    @Override
    public Component getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(Component configuration) {
        this.configuration = (Configuration) configuration;
    }

    // TODO use the config probabilities
    public static class Configuration implements Component {
        @Range(min = 0, max = 1.0f, increment = 0.05f, precision = 2, description = "Define the overall structure density")
        public float density = 0.2f;
    }
}
