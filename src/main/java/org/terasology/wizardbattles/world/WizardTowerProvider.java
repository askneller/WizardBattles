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
package org.terasology.wizardbattles.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.biomesAPI.Biome;
import org.terasology.core.world.CoreBiome;
import org.terasology.core.world.generator.facets.BiomeFacet;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Rect2i;
import org.terasology.structureTemplates.components.SpawnBlockRegionsComponent;
import org.terasology.utilities.Assets;
import org.terasology.utilities.procedural.Noise;
import org.terasology.utilities.procedural.WhiteNoise;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockRegion;
import org.terasology.world.block.BlockRegions;
import org.terasology.world.generation.Border3D;
import org.terasology.world.generation.Facet;
import org.terasology.world.generation.FacetBorder;
import org.terasology.world.generation.FacetProviderPlugin;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.Produces;
import org.terasology.world.generation.Requires;
import org.terasology.world.generation.facets.ElevationFacet;
import org.terasology.world.generation.facets.SeaLevelFacet;
import org.terasology.world.generation.facets.SurfacesFacet;
import org.terasology.world.generator.plugin.RegisterPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Determines where structures can be placed.  Will put structures at the surface.
 */
@RegisterPlugin
@Produces(WizardTowerFacet.class)
@Requires({
        @Facet(value = SeaLevelFacet.class),
        @Facet(value = ElevationFacet.class, border = @FacetBorder(sides = 16)),
        @Facet(value = BiomeFacet.class, border = @FacetBorder(sides = 16)),
        @Facet(value = SurfacesFacet.class, border = @FacetBorder(sides = 5))
})
public class WizardTowerProvider implements FacetProviderPlugin {

    private static final Logger logger = LoggerFactory.getLogger(WizardTowerProvider.class);

    private Noise noise;

    public WizardTowerProvider() {
    }

    StructureGenerator structureGenerator = (blockManager, view, rand, posX, posY, posZ) -> {
        Optional<Prefab> prefabOptional = Assets.getPrefab("WizardBattles:tower");
        if (prefabOptional.isPresent()) {
            Prefab prefab = prefabOptional.get();
            SpawnBlockRegionsComponent spawnBlockRegions = prefab.getComponent(SpawnBlockRegionsComponent.class);
            if (spawnBlockRegions != null) {
                for (SpawnBlockRegionsComponent.RegionToFill regionToFill : spawnBlockRegions.regionsToFill) {
                    Block block = regionToFill.blockType;

                    BlockRegion region = regionToFill.region;
                    // Block positions are specified relative to the centre of the tower for X and Z,
                    // and relative to the bottom for Y
                    for (org.joml.Vector3i pos : BlockRegions.iterable(region)) {
                        // These positions are relative to the corner of the chunk, as per view.setBlock()
                        int relX = pos.x + posX;
                        int relY = pos.y + posY;
                        int relZ = pos.z + posZ;
                        // encompasses uses world position
                        int worldX = view.getRegion().minX() + relX;
                        int worldY = view.getRegion().minY() + relY;
                        int worldZ = view.getRegion().minZ() + relZ;
                        if (view.getRegion().encompasses(worldX, worldY, worldZ)) {
                                view.setBlock(relX, relY, relZ, block);
                        }
                    }
                }
            }
        }
    };

    @Override
    public void setSeed(long seed) {
        noise = new WhiteNoise(seed);
    }

    @Override
    public void process(GeneratingRegion region) {
        Border3D border = region.getBorderForFacet(WizardTowerFacet.class)
                .extendBy(WizardTower.TOP, WizardTower.BOTTOM, WizardTower.SIDES);

        WizardTowerFacet facet = new WizardTowerFacet(region.getRegion(), border);
                Candidate candidate = findCandidate(region);

        if (candidate != null) {
            if (facet.getWorldRegion().encompasses(candidate.x, candidate.y, candidate.z)
                    && noise.noise(candidate.x, candidate.z) > 0.5) {
                logger.info("Generating at {} {} {}", candidate.x, candidate.y, candidate.z);
                facet.setWorld(candidate.x, candidate.y, candidate.z, structureGenerator);
            }
        }
        region.setRegionFacet(WizardTowerFacet.class, facet);
    }

    private Candidate findCandidate(GeneratingRegion region) {
        if (Math.abs(region.getRegion().minX()) < 256 || Math.abs(region.getRegion().minZ()) < 256) {
            return null;
        }

        Border3D border = region.getBorderForFacet(WizardTowerFacet.class)
                .extendBy(WizardTower.TOP, WizardTower.BOTTOM, WizardTower.SIDES);
        WizardTowerFacet facet = new WizardTowerFacet(region.getRegion(), border);

        ElevationFacet elevationFacet = region.getRegionFacet(ElevationFacet.class);

        Rect2i elevationRegion = elevationFacet.getWorldRegion();
        int elevRegionMinX = elevationRegion.minX();
        int elevRegionMinZ = elevationRegion.minY();
        int elevRegionMaxX = elevationRegion.maxX();
        int elevRegionMaxZ = elevationRegion.maxY();
        int regionMinX = region.getRegion().minX();
        int regionMinZ = region.getRegion().minZ();
        int regionMaxX = region.getRegion().maxX();
        int regionMaxZ = region.getRegion().maxZ();
        int centerX = TeraMath.floorToInt(region.getRegion().center().x);
        int centerZ = TeraMath.floorToInt(region.getRegion().center().z);

        float elevationSW = elevationFacet.getWorld(elevRegionMinX, elevRegionMinZ);
        float elevationW = elevationFacet.getWorld(centerX, elevRegionMinZ);
        float elevationNW = elevationFacet.getWorld(elevRegionMaxX, elevRegionMinZ);
        float elevationRMin = elevationFacet.getWorld(regionMinX, regionMinZ);
        float elevationRxXnZ = elevationFacet.getWorld(regionMaxX, regionMinZ);
        float elevationS = elevationFacet.getWorld(elevRegionMinX, centerZ);
        float elevationCenter = elevationFacet.getWorld(centerX, centerZ);
        float elevationN = elevationFacet.getWorld(elevRegionMaxX, centerZ);
        float elevationRnXxZ = elevationFacet.getWorld(regionMinX, regionMaxZ);
        float elevationRMax = elevationFacet.getWorld(regionMaxX, regionMaxZ);
        float elevationSE = elevationFacet.getWorld(elevRegionMinX, elevRegionMaxZ);
        float elevationE = elevationFacet.getWorld(centerX, elevRegionMaxZ);
        float elevationNE = elevationFacet.getWorld(elevRegionMaxX, elevRegionMaxZ);

        List<Float> regionElevations = Arrays.asList(elevationRMin, elevationRxXnZ, elevationCenter,
                elevationRnXxZ, elevationRMax);

        boolean allUnder = regionElevations.stream().allMatch(aFloat -> aFloat < region.getRegion().minY());
        boolean allAbove = regionElevations.stream().allMatch(aFloat -> aFloat > region.getRegion().maxY());

        if (allAbove || allUnder) {
            return null;
        }

        // find the highest ground, if it is flat, generate a tower given a certain probability
        float highest = -10000f;
        int highestX = 0;
        int highestZ = 0;
        for (int x = regionMinX; x <= regionMaxX; ++x) {
            for (int z = regionMinZ; z <= regionMaxZ; ++z) {
                float elevation = elevationFacet.getWorld(x, z);
                if (elevation > highest) {
                    highest = elevation;
                    highestX = x;
                    highestZ = z;
                }
            }
        }
        final float finalHighest = highest;
        List<Float> borderElevations = Arrays.asList(elevationSW, elevationW, elevationNW, elevationS,
                elevationN, elevationSE, elevationE, elevationNE);
        boolean bordersLowerThanHighest = borderElevations.stream().allMatch(be -> be < finalHighest);
        if (!bordersLowerThanHighest) {
            return null; // still has to be higher than neighbours
        }

        outer:
        for (int x = 1; x <= 3; ++x) {
            for (int z = 1; z <= 3; ++z) {
                // corners
                float sw = elevationFacet.getWorld(highestX - x, highestZ - z);
                float nw = elevationFacet.getWorld(highestX + x, highestZ - z);
                float se = elevationFacet.getWorld(highestX - x, highestZ + z);
                float ne = elevationFacet.getWorld(highestX + x, highestZ + z);
                if (Math.abs((highest - sw)) > 1.5 || Math.abs((highest - nw)) > 1.5
                        || Math.abs((highest - se)) > 1.5 || Math.abs((highest - ne)) > 1.5) {
                    break outer;
                }
            }
        }

        SurfacesFacet surfacesFacet = region.getRegionFacet(SurfacesFacet.class);
        BiomeFacet biomeFacet = region.getRegionFacet(BiomeFacet.class);
        List<Candidate> candidates = new ArrayList<>();
        for (int x = regionMinX; x <= regionMaxX; ++x) {
            for (int z = regionMinZ; z <= regionMaxZ; ++z) {
                Optional<Float> primarySurface = surfacesFacet.getPrimarySurface(elevationFacet, x, z);
                if (primarySurface.isPresent()) {
                    int surface = TeraMath.floorToInt(primarySurface.get());
                    Optional<Float> sw = surfacesFacet.getPrimarySurface(elevationFacet, x - 3, z - 3);
                    Optional<Float> nw = surfacesFacet.getPrimarySurface(elevationFacet, x + 3, z - 3);
                    Optional<Float> se = surfacesFacet.getPrimarySurface(elevationFacet, x - 3, z + 3);
                    Optional<Float> ne = surfacesFacet.getPrimarySurface(elevationFacet, x + 3, z + 3);
                    List<Optional<Float>> optionals = Arrays.asList(sw, nw, se, ne);
                    boolean present = optionals.stream().allMatch(Optional::isPresent);
                    if (present) {
                        boolean surroundingBlocksApproxSameHeight =
                                optionals.stream().map(Optional::get).allMatch(f -> {
                                    int i = TeraMath.floorToInt(f);
                                    return Math.abs(surface - i) <= 1;
                                });
                        Biome biome = biomeFacet.getWorld(x, z);
                        if (surroundingBlocksApproxSameHeight && correctBiome(biome)) {
                            Candidate c = new Candidate();
                            c.x = x;
                            c.y = surface;
                            c.z = z;
                            c.height = primarySurface.get();
                            candidates.add(c);
                        }
                    }
                }
            }
        }

//        if (candidates.size() > 0 && num < 5) {
//            logger.info("======================================");
//            logger.info("Region {}", region.getRegion());
//            logger.info("WizardTowerFacet border top {}, sides {}, bottom {}", border.getTop(), border.getSides(), border.getBottom());
//            logger.info("WizardTowerFacet region {}", facet.getWorldRegion());
//
//            logger.info("ElevationFacet region {}", elevationFacet.getWorldRegion());
//
//            logger.info("Elevation at SW({}, {}) is {}", elevRegionMinX, elevRegionMinZ, elevationSW);
//            logger.info("Elevation at W({}, {}) is {}", centerX, elevRegionMinZ, elevationW);
//            logger.info("Elevation at NW({}, {}) is {}", elevRegionMaxX, elevRegionMinZ, elevationNW);
//            logger.info("Elevation at RMin({}, {}) is {}", regionMinX, regionMinZ, elevationRMin);
//            logger.info("Elevation at RxXnZ({}, {}) is {}", regionMaxX, regionMinZ, elevationRxXnZ);
//            logger.info("Elevation at S({}, {}) is {}", elevRegionMinX, centerZ, elevationS);
//            logger.info("Elevation at Center({}, {}) is {}", centerX, centerZ, elevationCenter);
//            logger.info("Elevation at N({}, {}) is {}", elevRegionMaxX, centerZ, elevationN);
//            logger.info("Elevation at RnXxZ({}, {}) is {}", regionMinX, regionMaxZ, elevationRnXxZ);
//            logger.info("Elevation at RMax({}, {}) is {}", regionMaxX, regionMaxZ, elevationRMax);
//            logger.info("Elevation at SE({}, {}) is {}", elevRegionMinX, elevRegionMaxZ, elevationSE);
//            logger.info("Elevation at E({}, {}) is {}", centerX, elevRegionMaxZ, elevationE);
//            logger.info("Elevation at NE({}, {}) is {}", elevRegionMaxX, elevRegionMaxZ, elevationNE);
//
//            logger.info("Num candidates {}", candidates.size());
//
//        }
        if (candidates.size() > 0) {
            candidates.sort(Comparator.comparingDouble(Candidate::getHeight));

            Candidate candidate;
            for (int i = candidates.size() - 1; i >= 0; i--) {
                candidate = candidates.get(i);
                if (candidate.x > regionMinX + 7 && candidate.x < regionMaxX - 7 && candidate.z > regionMinZ + 7
                        && candidate.z < regionMaxZ - 7 && candidate.y < region.getRegion().maxY() - 25) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean correctBiome(Biome biome) {
        return biome.equals(CoreBiome.MOUNTAINS) || biome.equals(CoreBiome.PLAINS) || biome.equals(CoreBiome.SNOW);
    }

    public static class Candidate {
        public int x;
        public int y;
        public int z;
        public float height;

        public float getHeight() {
            return height;
        }
    }
}
