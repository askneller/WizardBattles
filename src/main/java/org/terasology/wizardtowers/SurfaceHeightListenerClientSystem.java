/*
 * Copyright 2020 MovingBlocks
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
import org.terasology.biomesAPI.Biome;
import org.terasology.core.world.CoreBiome;
import org.terasology.core.world.generator.facets.BiomeFacet;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Vector2i;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.wizardtowers.world.SurfaceHeightProviderListener;
import org.terasology.wizardtowers.world.TowerGenerationSystem;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BlockManager;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.generation.WorldFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@RegisterSystem(RegisterMode.AUTHORITY)
public class SurfaceHeightListenerClientSystem extends BaseComponentSystem implements SurfaceHeightListenerClient {

    @In
    private BlockManager blockManager;

    @In
    private WorldProvider worldProvider;

    private static final Logger logger = LoggerFactory.getLogger(SurfaceHeightListenerClientSystem.class);

    private Map<Vector2i, SurfaceAndBiome> surfaceFacetMap = new HashMap<>();
    private Map<Vector2i, Integer> usageMap = new HashMap<>();
    private final Object mutex = new Object();

    static List<Biome> ACCEPTABLE_BIOMES = Arrays.asList(CoreBiome.SNOW, CoreBiome.PLAINS, CoreBiome.MOUNTAINS);

    public void initialise() {
        logger.info("Initializing {} {}", blockManager, worldProvider);
        SurfaceHeightProviderListener.register(SurfaceHeightFacet.class, this);
        SurfaceHeightProviderListener.register(BiomeFacet.class, this);
    }

    // todo: handle positions being very close but not exactly the same, e.g. (84, 76, 24) and (84, 76, 26)
    @Override
    public <T extends WorldFacet> void onListened(Region3i region, T facet) {
        // this happens in the generator thread, doing the build should be in another thread
        Vector3i min = region.min();
        Vector2i xzMin = new Vector2i(min.x, min.z);
        if (!surfaceFacetMap.containsKey(xzMin)) {
            SurfaceAndBiome surfaceAndBiome = new SurfaceAndBiome();
            if (facet instanceof SurfaceHeightFacet) {
                surfaceAndBiome.surface = (SurfaceHeightFacet) facet;
            } else if (facet instanceof BiomeFacet) {
                surfaceAndBiome.biome = (BiomeFacet) facet;
            }
            surfaceFacetMap.put(xzMin, surfaceAndBiome);
        } else {
            SurfaceAndBiome surfaceAndBiome = surfaceFacetMap.get(xzMin);
            if (facet instanceof SurfaceHeightFacet) {
                surfaceAndBiome.surface = (SurfaceHeightFacet) facet;
            } else if (facet instanceof BiomeFacet) {
                surfaceAndBiome.biome = (BiomeFacet) facet;
            }
        }

        QuadArea quadArea = checkForQuad(xzMin, region.size());
        if (quadArea != null && quadArea.hasAllFacets()) {
            recordUsage(quadArea);
            if (!quadArea.allCorrectBiomes()) {
                return;
            }
            List<PotentialSite> sitesFromCentre = findSitesFromCentre(quadArea);
            if (!sitesFromCentre.isEmpty()) {
                if (sitesFromCentre.size() > 1) {
                    sitesFromCentre.sort((a, b) -> TeraMath.floorToInt(b.centreHeight - a.centreHeight));
                    PotentialSite highest = sitesFromCentre.get(0);
                    PotentialSite lowest = sitesFromCentre.get(sitesFromCentre.size() - 1);
                    TowerGenerationSystem.addSite(highest);
                } else if (sitesFromCentre.size() == 1) {
                    PotentialSite highest = sitesFromCentre.get(0);
                    TowerGenerationSystem.addSite(highest);
                }
            }
        }
    }

    private QuadArea checkForQuad(Vector2i latest, Vector3i size) {
        Vector2i upRight = new Vector2i(latest.x + size.x, latest.y + size.z);
        Vector2i upLeft = new Vector2i(latest.x - size.x, latest.y + size.z);
        Vector2i right = new Vector2i(latest.x + size.x, latest.y);
        Vector2i left = new Vector2i(latest.x - size.x, latest.y);
        Vector2i up = new Vector2i(latest.x, latest.y + size.z);
        Vector2i down = new Vector2i(latest.x, latest.y - size.z);
        Vector2i downRight = new Vector2i(latest.x + size.x, latest.y - size.z);
        Vector2i downLeft = new Vector2i(latest.x - size.x, latest.y - size.z);

        QuadArea quadArea = null;
        if (surfaceFacetMap.containsKey(up) // As downLeft
                && surfaceFacetMap.containsKey(upRight)
                && surfaceFacetMap.containsKey(right)) {
            quadArea = getQuadArea(up, upRight, latest, right);
        } else if (surfaceFacetMap.containsKey(up) // As downRight
                && surfaceFacetMap.containsKey(upLeft)
                && surfaceFacetMap.containsKey(left)) {
            quadArea = getQuadArea(upLeft, up, left, latest);
        } else if (surfaceFacetMap.containsKey(down) // As upRight
                && surfaceFacetMap.containsKey(downLeft)
                && surfaceFacetMap.containsKey(left)) {
            quadArea = getQuadArea(left, latest, downLeft, down);
        } else if (surfaceFacetMap.containsKey(down) // As upLeft
                && surfaceFacetMap.containsKey(downRight)
                && surfaceFacetMap.containsKey(right)) {
            quadArea = getQuadArea(latest, right, down, downRight);
        }
        return quadArea;
    }

    private QuadArea getQuadArea(Vector2i upLeft, Vector2i upRight, Vector2i downLeft, Vector2i downRight) {
        return new QuadArea(
                new AreaFacet(upLeft, surfaceFacetMap.get(upLeft)),
                new AreaFacet(upRight, surfaceFacetMap.get(upRight)),
                new AreaFacet(downLeft, surfaceFacetMap.get(downLeft)),
                new AreaFacet(downRight, surfaceFacetMap.get(downRight))
        );
    }

    private void recordUsage(QuadArea quadArea) {
        synchronized (mutex) {
            Vector2i key = quadArea.upLeft.area;
            Integer usages = usageMap.getOrDefault(key, 0) + 1;
            usageMap.put(key, usages);
            if (usages == 4) {
                surfaceFacetMap.remove(key);
            }
        }
        synchronized (mutex) {
            Vector2i key = quadArea.upRight.area;
            Integer usages = usageMap.getOrDefault(key, 0) + 1;
            usageMap.put(key, usages);
            if (usages == 4) {
                surfaceFacetMap.remove(key);
            }
        }
        synchronized (mutex) {
            Vector2i key = quadArea.downLeft.area;
            Integer usages = usageMap.getOrDefault(key, 0) + 1;
            usageMap.put(key, usages);
            if (usages == 4) {
                surfaceFacetMap.remove(key);
            }
        }
        synchronized (mutex) {
            Vector2i key = quadArea.downRight.area;
            Integer usages = usageMap.getOrDefault(key, 0) + 1;
            usageMap.put(key, usages);
            if (usages == 4) {
                surfaceFacetMap.remove(key);
            }
        }
    }

    private boolean allWithinHeight(float margin, float... values) {
        for (int i = 0; i < values.length - 1; ++i) {
            for (int j = i + 1; j < values.length; ++j) {
                if (Math.abs(values[i] - values[j]) > margin) {
                    return  false;
                }
            }
        }
        return true;
    }

    private float avg(float... values) {
        float avg = 0.0f;
        for (float val : values) {
                avg += val;
        }
        return avg / values.length;
    }

    private List<PotentialSite> findSitesFromCentre(QuadArea quadArea) {
        int size = 16;
        int half = size / 2;
        int shift = 2;
        int maxX = quadArea.sizeX();
        int maxY = quadArea.sizeY();
        List<PotentialSite> sites = new LinkedList<>();
        for (int x = half; x < maxX - (half - 1); x += shift) {
            for (int y = half; y < maxY - (half - 1); y += shift) {
                // x and y specify the centre
                int worldX = x + quadArea.minX();
                int worldY = y + quadArea.minY();

                float centre = quadArea.get(x, y);
                if (centre < 75.0f) {
                    continue;
                }
                float dl = quadArea.get(x - half, y - half);
                float ul = quadArea.get(x - half, y + (half - 1));
                float ur = quadArea.get(x + (half - 1), y + (half - 1));
                float dr = quadArea.get(x + (half - 1), y - half);
                float l = quadArea.get(x - half, y);
                float up = quadArea.get(x, y + (half - 1));
                float r = quadArea.get(x + (half - 1), y);
                float dn = quadArea.get(x, y - half);
                float margin = 3.0f;
                boolean lowerThanCentreByMargin = allLowerThanCentreByMargin(margin, centre, dl, ul, ur, dr, l, r, up, dn);

                if (lowerThanCentreByMargin) {
                    PotentialSite potentialSite = new PotentialSite(worldX, worldY, centre);
                    potentialSite.setAtSixteen(dl, l, ul, up, ur, r, dr, dn);
                    potentialSite.setAvgAtSixteen(avg(dl, l, ul, up, ur, r, dr, dn));
                    sites.add(potentialSite);
                }
            }
        }
        return sites;
    }

    private boolean allLowerThanCentreByMargin(float margin, float centre, float... values) {
        for (int i = 0; i < values.length - 1; ++i) {
            if (centre - margin < values[i]) {
                return  false;
            }
        }
        return true;
    }



    public static class QuadArea {
        int SIZE = 32;

        AreaFacet upLeft;
        AreaFacet upRight;
        AreaFacet downLeft;
        AreaFacet downRight;
        boolean print = false;

        QuadArea(AreaFacet upLeft,
                        AreaFacet upRight,
                        AreaFacet downLeft,
                        AreaFacet downRight) {
            this.upLeft = upLeft;
            this.upRight = upRight;
            this.downLeft = downLeft;
            this.downRight = downRight;
        }

        int sizeX() {
            return SIZE * 2;
        }

        int sizeY() {
            return SIZE * 2;
        }

        Vector2i min() {
            return downLeft.area;
        }

        int minX() {
            return min().x;
        }

        int minY() {
            return min().y;
        }

        int relXToWorld(int x) {
            return min().x + x;
        }

        int relYToWorld(int y) {
            return min().y + y;
        }

        float get(int x, int y) {
            boolean lowerX = x < SIZE;
            boolean lowerY = y < SIZE;
            AreaFacet areaFacet;

            if (lowerX && lowerY) {
                areaFacet = downLeft;
            } else if (lowerX) { // lowerY false
                areaFacet = upLeft;
            } else if (lowerY) { // lowerX false
                areaFacet = downRight;
            } else {
                areaFacet = upRight;
            }
            int relX = x % SIZE;
            int relY = y % SIZE;
            int worldX = areaFacet.area.x + relX;
            int worldY = areaFacet.area.y + relY;
            return areaFacet.surfaceAndBiome.surface.getWorld(worldX, worldY);
        }

        float getRelative(int x, int z) {
            boolean lowerHalfX = x < SIZE;
            boolean lowerHalfY = z < SIZE;

            if (lowerHalfX && lowerHalfY) {
                return upLeft.surfaceAndBiome.surface.get(x, z);
            } else if (lowerHalfX) {
                // We know lowerHalfY must be false
                return downLeft.surfaceAndBiome.surface.get(x, z);
            } else if (lowerHalfY) {
                // We know lowerHalfX is false
                return upRight.surfaceAndBiome.surface.get(x, z);
            } else {
                return downRight.surfaceAndBiome.surface.get(x, z);
            }
        }

        public boolean hasAllFacets() {
            return areaHasFacets(upLeft) && areaHasFacets(upRight)
                    && areaHasFacets(downLeft) && areaHasFacets(downRight);
        }

        private boolean areaHasFacets(AreaFacet areaFacet) {
            return areaFacet.surfaceAndBiome.surface != null && areaFacet.surfaceAndBiome.biome != null;
        }

        public boolean allCorrectBiomes() {
            return upLeft.isCorrectBiome() && upRight.isCorrectBiome()
                    && downRight.isCorrectBiome() && downLeft.isCorrectBiome();
        }

        @Override
        public String toString() {
            return "QuadArea{" +
                    "upLeft=" + upLeft +
                    ", upRight=" + upRight +
                    ", downLeft=" + downLeft +
                    ", downRight=" + downRight +
                    '}';
        }
    }

    public static class AreaFacet {
        Vector2i area;
        SurfaceAndBiome surfaceAndBiome;

        public AreaFacet(Vector2i area, SurfaceAndBiome surfaceAndBiome) {
            this.area = area;
            this.surfaceAndBiome = surfaceAndBiome;
        }

        public boolean isCorrectBiome() {
            Biome minBiome = surfaceAndBiome.biome.get(0, 0);
            Biome maxBiome = surfaceAndBiome.biome.get(ChunkConstants.SIZE_X - 1, ChunkConstants.SIZE_Z);
            return ACCEPTABLE_BIOMES.contains(minBiome) && ACCEPTABLE_BIOMES.contains(maxBiome);
        }

        @Override
        public String toString() {
            return "AreaFacet{" +
                    "area=" + area +
                    '}';
        }
    }

    public static class PotentialSite {
        int x;
        int z;
        float centreHeight;
        float[] heightsAtSixteen;
        float avgAtSixteen;

        public PotentialSite(int x, int z, float centreHeight) {
            this.x = x;
            this.z = z;
            this.centreHeight = centreHeight;
        }

        public void setAtSixteen(float dl, float l, float ul, float up, float ur, float rt, float dr, float dn) {
            heightsAtSixteen = new float[8];
            heightsAtSixteen[0] = dl;
            heightsAtSixteen[1] = l;
            heightsAtSixteen[2] = ul;
            heightsAtSixteen[3] = up;
            heightsAtSixteen[4] = ur;
            heightsAtSixteen[5] = rt;
            heightsAtSixteen[6] = dr;
            heightsAtSixteen[7] = dn;
        }

        public void setAvgAtSixteen(float avgAtSixteen) {
            this.avgAtSixteen = avgAtSixteen;
        }

        public Vector3i location() {
            return new Vector3i(x, TeraMath.floorToInt(centreHeight), z);
        }
    }

    public static class SurfaceAndBiome {
        SurfaceHeightFacet surface;
        BiomeFacet biome;
    }
}
