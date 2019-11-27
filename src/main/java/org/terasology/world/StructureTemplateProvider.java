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
package org.terasology.world;

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
import org.terasology.utilities.random.Random;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.chunks.CoreChunk;
import org.terasology.world.generation.*;
import org.terasology.world.generation.facets.SeaLevelFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;
import org.terasology.world.generator.plugin.RegisterPlugin;

import java.util.Optional;

/**
 * Determines where structures can be placed.  Will put structures at the surface.
 */
@RegisterPlugin
@Produces(StructureTemplateFacet.class)
@Requires({
        @Facet(value = SeaLevelFacet.class),
        @Facet(value = SurfaceHeightFacet.class),
        @Facet(value = BiomeFacet.class),
        @Facet(value = RoughnessFacet.class)
})
public class StructureTemplateProvider implements ConfigurableFacetProvider, FacetProviderPlugin {

    private static final Logger logger = LoggerFactory.getLogger(StructureTemplateProvider.class);

    private Noise densityNoiseGen;
    private Configuration configuration = new Configuration();
    StructureGenerator structureGenerator;

    public StructureTemplateProvider() {
        structureGenerator = new StructureGenerator() {
            @Override
            public void generate(BlockManager blockManager, CoreChunk view, Random rand, int posX, int posY, int posZ) {
                Optional<Prefab> prefabOptional = Assets.getPrefab("WizardTowers:tower");
                float noise = densityNoiseGen.noise(posX, posY, posZ);
                if (noise > 0.5f) {
                    if (prefabOptional.isPresent()) {
//                        logger.info("Found prefab");
                        Prefab prefab = prefabOptional.get();
                        SpawnBlockRegionsComponent spawnBlockRegions = prefab.getComponent(SpawnBlockRegionsComponent.class);
                        if (spawnBlockRegions != null) {
                            Vector3i vector3i = view.chunkToWorldPosition(posX, posY, posZ);
                            logger.info("Generating at {} (world center), {} {} {} {} (region relative center)",
                                    vector3i, view.getRegion(), posX, posY, posZ);
                            for (SpawnBlockRegionsComponent.RegionToFill regionToFill : spawnBlockRegions.regionsToFill) {
                                Block block = regionToFill.blockType;

                                Region3i region = regionToFill.region;
//                                logger.info("Region {}, block {}", region, block);
                                float noise2 = densityNoiseGen.noise(posX, posY, posZ);
//                                if (noise2 > 0.5f) {
//                                    logger.info("Region {}, block {}", region, block);
                                    for (Vector3i pos : region) {
                                        // todo translate region position to world position for setting block
                                        Vector3i regionFillPos = view.chunkToWorldPosition(pos);
                                        // my template x values are relatve to center by y and z are not
//                                        logger.info("Region fill pos (world) {}", regionFillPos);
                                        int relX = pos.x + posX;
                                        int relY = pos.y + posY;
                                        int relZ = posZ - 4 + pos.z;
//                                        logger.info("Region fill pos (chunk) {} {} {}", relX, relY, relZ);
//                                        logger.info("Block at {} {} {}: {}",
//                                          posX, posY, posZ, view.getBlock(posX, posY, posZ).getURI());
                                         view.setBlock(relX, relY, relZ, block);

                                    }
//                                }
                            }
                        }
                    } else {
                        logger.warn("Failed to find tower prefab");
                    }
                }

            }
        };
    }

    /**
     * @param configuration the default configuration to use
     */
    public StructureTemplateProvider(Configuration configuration) {
        this();
        this.configuration = configuration;
    }

    @Override
    public void setSeed(long seed) {
        densityNoiseGen = new WhiteNoise(seed);
    }

    @Override
    public void process(GeneratingRegion region) {
//        logger.info("Running procces for region {}", region.getRegion());

        // todo make sure a region only has one structure

        // todo see if we can determine a max size flat area
        SurfaceHeightFacet surface = region.getRegionFacet(SurfaceHeightFacet.class);
        BiomeFacet biome = region.getRegionFacet(BiomeFacet.class);

        StructureTemplateFacet facet =
                new StructureTemplateFacet(region.getRegion(), region.getBorderForFacet(StructureTemplateFacet.class));

        float minY = region.getRegion().minY();
        float maxY = region.getRegion().maxY();

        SeaLevelFacet seaLevelFacet = region.getRegionFacet(SeaLevelFacet.class);

        if (minY > seaLevelFacet.getSeaLevel() + 20f) {
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
//                if (densityNoiseGen.noise(region.getRegion().minX(), region.getRegion().minZ()) > 0.5f) {
//                    logger.info("Surface wholly within region {}, max {}, min {}, roughness {}",
//                            region.getRegion(), max, min, roughnessFacet.getMeanDeviation());
                    if (roughnessFacet.getMeanDeviation() < 1f) {
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
                            do {
                                // Scan this 8 x 8 patch
                                for (int x = startX, i = 0; x <= maxX && i < 8; ++i, ++x) {
                                    for (int z = startZ, j = 0; z <= maxZ && j < 8; ++z, ++j) {
                                        float height = surface.getWorld(x, z);
                                        if (height < minH) {
                                            minH = height;
                                        }
                                        if (height > maxH) {
                                            maxH = height;
                                        }
//                                logger.info("Height at {} {} = {}", x, z, height);
                                    }
                                }
                                float range = maxH - minH;
                                if (range < 2f) {
                                    foundCandidate = true;
                                    int centerX = startX + 4;
                                    int centerZ = startZ + 4;
                                    int height = TeraMath.floorToInt(minH) + 1;
                                    logger.info("Candidate at {} {} {} {} (max {}, min {}, range {})",
                                            centerX, height, centerZ, biome.getWorld(startX, startZ).getName(),
                                            maxH, minH, maxH - minH);

                                    facet.setWorld(centerX, height, centerZ, structureGenerator);
                                }
                                if (!foundCandidate) {
                                    startX += 8;
                                    startZ += 8;
                                }
                            } while (!foundCandidate && startX < maxX);
                        }
                    }
//                }
            }
        }

        region.setRegionFacet(StructureTemplateFacet.class, facet);
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
        return "Structures";
    }

    @Override
    public Component getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(Component configuration) {
        this.configuration = (Configuration) configuration;
    }

    public static class Configuration implements Component {
        @Range(min = 0, max = 1.0f, increment = 0.05f, precision = 2, description = "Define the overall structure density")
        public float density = 0.2f;
    }
}
