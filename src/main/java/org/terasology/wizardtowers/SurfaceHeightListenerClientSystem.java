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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterSystem(RegisterMode.AUTHORITY)
public class SurfaceHeightListenerClientSystem extends BaseComponentSystem implements SurfaceHeightListenerClient {

    @In
    private BlockManager blockManager;

    @In
    private WorldProvider worldProvider;

    private static final Logger logger = LoggerFactory.getLogger(SurfaceHeightListenerClientSystem.class);

    private Map<Vector2i, SurfaceAndBiome> surfaceFacetMap = new HashMap<>();
    private Map<Vector2i, Integer> usageMap = new HashMap<>();
    private Map<Vector2i, Set<QuadUsages>> quadUsageMap = new HashMap<>();
    private final Object mutex = new Object();

    static List<Biome> ACCEPTABLE_BIOMES = Arrays.asList(CoreBiome.SNOW, CoreBiome.PLAINS, CoreBiome.MOUNTAINS);

    boolean print = false;

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
        print = false;
//        if (xzMin.x == -96 && xzMin.y == -64) {
//            print = true;
//            logger.info("Found {}, {}", region.min(), facet.getClass().getSimpleName());
//        }

        Set<QuadUsages> quadUsages = quadUsageMap.get(xzMin);
        if (quadUsages != null && quadUsages.size() == 4) {
            return;
        }
        if (!surfaceFacetMap.containsKey(xzMin) ) {
            if (print) {
                logger.info("Adding to map");
            }
            SurfaceAndBiome surfaceAndBiome = new SurfaceAndBiome();
            if (facet instanceof SurfaceHeightFacet) {
                surfaceAndBiome.surface = (SurfaceHeightFacet) facet;
            } else if (facet instanceof BiomeFacet) {
                surfaceAndBiome.biome = (BiomeFacet) facet;
            }
            surfaceFacetMap.put(xzMin, surfaceAndBiome);
        } else {
            if (print) {
                logger.info("Setting other facet");
            }
            SurfaceAndBiome surfaceAndBiome = surfaceFacetMap.get(xzMin);
            if (facet instanceof SurfaceHeightFacet) {
                surfaceAndBiome.surface = (SurfaceHeightFacet) facet;
            } else if (facet instanceof BiomeFacet) {
                surfaceAndBiome.biome = (BiomeFacet) facet;
            }
            if (print) {
                logger.info("SAB {}", surfaceAndBiome.toString());
            }
        }

        if (quadUsages == null) {
            quadUsages = new HashSet<>();
        }
        QuadArea quadArea = checkForQuad(xzMin, region.size(), quadUsages);
        if (quadArea != null && quadArea.hasAllFacets()) {
//            seeIfInterested(quadArea);
            recordUsage(quadArea);
//            if (!quadArea.allCorrectBiomes()) {
//                if (print) {
//                    logger.info("Not all correct biomes {}", quadArea);
//                }
//                return;
//            }
            if (print) {
                logger.info("Finding sites in: {}", quadArea);
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

    private void seeIfInterested(QuadArea quadArea) {
        AreaFacet areaFacet = quadArea.downLeft;
        if (areaFacet.area.x == -96 && areaFacet.area.y == -64) {
            logger.info("It's down left");
            print = true;
        }
        areaFacet = quadArea.downRight;
        if (areaFacet.area.x == -96 && areaFacet.area.y == -64) {
            logger.info("It's down right");
            print = true;
        }
        areaFacet = quadArea.upLeft;
        if (areaFacet.area.x == -96 && areaFacet.area.y == -64) {
            logger.info("It's up left");
            print = true;
        }
        areaFacet = quadArea.upRight;
        if (areaFacet.area.x == -96 && areaFacet.area.y == -64) {
            logger.info("It's up right");
            print = true;
        }
    }

    private QuadArea checkForQuad(Vector2i latest, Vector3i size, Set<QuadUsages> quadUsages) {
        Vector2i upRight = new Vector2i(latest.x + size.x, latest.y + size.z);
        Vector2i upLeft = new Vector2i(latest.x - size.x, latest.y + size.z);
        Vector2i right = new Vector2i(latest.x + size.x, latest.y);
        Vector2i left = new Vector2i(latest.x - size.x, latest.y);
        Vector2i up = new Vector2i(latest.x, latest.y + size.z);
        Vector2i down = new Vector2i(latest.x, latest.y - size.z);
        Vector2i downRight = new Vector2i(latest.x + size.x, latest.y - size.z);
        Vector2i downLeft = new Vector2i(latest.x - size.x, latest.y - size.z);

//        if (print) {
//            logger.info("UR {} UL {} R {} L {}", upRight, upLeft, right, left);
//            logger.info("U {} D {} DR {} DL {}", up, down, downRight, downLeft);
//        }

        QuadArea quadArea = null;
        if (surfaceFacetMap.containsKey(up) // As downLeft
                && surfaceFacetMap.containsKey(upRight)
                && surfaceFacetMap.containsKey(right)
                && !hasBeenUsed(latest, QuadUsages.DOWN_LEFT)) {
            quadArea = getQuadArea(up, upRight, latest, right);
//            if (print) {
//                logger.info("As downLeft");
//            }
        } else if (surfaceFacetMap.containsKey(up) // As downRight
                && surfaceFacetMap.containsKey(upLeft)
                && surfaceFacetMap.containsKey(left)
                && !hasBeenUsed(latest, QuadUsages.DOWN_RIGHT)) {
            quadArea = getQuadArea(upLeft, up, left, latest);
//            if (print) {
//                logger.info("As downRight");
//            }
        } else if (surfaceFacetMap.containsKey(down) // As upRight
                && surfaceFacetMap.containsKey(downLeft)
                && surfaceFacetMap.containsKey(left)
                && !hasBeenUsed(latest, QuadUsages.UP_RIGHT)) {
            quadArea = getQuadArea(left, latest, downLeft, down);
//            if (print) {
//                logger.info("As upRight");
//            }
        } else if (surfaceFacetMap.containsKey(down) // As upLeft
                && surfaceFacetMap.containsKey(downRight)
                && surfaceFacetMap.containsKey(right)
                && !hasBeenUsed(latest, QuadUsages.UP_LEFT)) {
            quadArea = getQuadArea(latest, right, down, downRight);
//            if (print) {
//                logger.info("As upLeft");
//            }
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

    private boolean hasBeenUsed(Vector2i key, QuadUsages usage) {
        synchronized (mutex) {
            Set<QuadUsages> usages = quadUsageMap.getOrDefault(key, new HashSet<>());
            return usages.contains(usage);
        }
    }

    private void recordUsage(QuadArea quadArea) {
        synchronized (mutex) {
            Vector2i key = quadArea.upLeft.area;
            Integer usages = usageMap.getOrDefault(key, 0) + 1;
            Set<QuadUsages> quadUsages = quadUsageMap.getOrDefault(key, new HashSet<>());
            quadUsages.add(QuadUsages.UP_LEFT);
//            usageMap.put(key, usages);
            quadUsageMap.put(key, quadUsages);
            if (usages == 4) {
                surfaceFacetMap.remove(key);
            }
        }
        synchronized (mutex) {
            Vector2i key = quadArea.upRight.area;
            Integer usages = usageMap.getOrDefault(key, 0) + 1;
            Set<QuadUsages> quadUsages = quadUsageMap.getOrDefault(key, new HashSet<>());
            quadUsages.add(QuadUsages.UP_RIGHT);
//            usageMap.put(key, usages);
            quadUsageMap.put(key, quadUsages);
            if (usages == 4) {
                surfaceFacetMap.remove(key);
            }
        }
        synchronized (mutex) {
            Vector2i key = quadArea.downLeft.area;
            Integer usages = usageMap.getOrDefault(key, 0) + 1;
            Set<QuadUsages> quadUsages = quadUsageMap.getOrDefault(key, new HashSet<>());
            quadUsages.add(QuadUsages.DOWN_LEFT);
//            usageMap.put(key, usages);
            quadUsageMap.put(key, quadUsages);
            if (usages == 4) {
                surfaceFacetMap.remove(key);
            }
        }
        synchronized (mutex) {
            Vector2i key = quadArea.downRight.area;
            Integer usages = usageMap.getOrDefault(key, 0) + 1;
            Set<QuadUsages> quadUsages = quadUsageMap.getOrDefault(key, new HashSet<>());
            quadUsages.add(QuadUsages.DOWN_RIGHT);
//            usageMap.put(key, usages);
            quadUsageMap.put(key, quadUsages);
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

//                if (isInteresting(worldX, worldY)) {
//                    logger.info("INTERESTING {} {}, min {} {}", worldX, worldY, quadArea.minX(), quadArea.minY());
//                }

                float centre = quadArea.getSurfaceHeight(x, y);
                if (centre < 75.0f) {
                    continue;
                }
                float dl = quadArea.getSurfaceHeight(x - half, y - half);
                float ul = quadArea.getSurfaceHeight(x - half, y + (half - 1));
                float ur = quadArea.getSurfaceHeight(x + (half - 1), y + (half - 1));
                float dr = quadArea.getSurfaceHeight(x + (half - 1), y - half);
                Biome dlBiome = quadArea.getBiome(x - half, y - half);
                Biome ulBiome = quadArea.getBiome(x - half, y + (half - 1));
                Biome urBiome = quadArea.getBiome(x + (half - 1), y + (half - 1));
                Biome drBiome = quadArea.getBiome(x + (half - 1), y - half);
                float l = quadArea.getSurfaceHeight(x - half, y);
                float up = quadArea.getSurfaceHeight(x, y + (half - 1));
                float r = quadArea.getSurfaceHeight(x + (half - 1), y);
                float dn = quadArea.getSurfaceHeight(x, y - half);
                float margin = 3.0f;
                boolean lowerThanCentreByMargin = allLowerThanCentreByMargin(margin, centre, dl, ul, ur, dr, l, r, up, dn);
                int numberLower = numberLowerThanCentreByMargin(0.1f, centre, dl, ul, ur, dr, l, r, up, dn);
                int numberLower3 = numberLowerThanCentreByMargin(3.0f, centre, dl, ul, ur, dr, l, r, up, dn);
                int numberLower5 = numberLowerThanCentreByMargin(5.0f, centre, dl, ul, ur, dr, l, r, up, dn);
                int numberLower7 = numberLowerThanCentreByMargin(7.0f, centre, dl, ul, ur, dr, l, r, up, dn);

//                if (isInteresting(worldX, worldY)) {
//                    logger.info("INTERESTING");
//                    logger.info("At {} {} h {}", worldX, worldY, centre);
//                    logger.info("Corners dl {}, ul {}, ur {}, dr {}", dl, ul, ur, dr);
//                    logger.info("Midway l {}, up {}, r {}, dn {}", l, up, r, dn);
//                    logger.info("lowerByMargin {}", lowerThanCentreByMargin);
//                    logger.info("number lower {}", numberLower);
//                    logger.info("lowerByMargin 3 {}", numberLower3);
//                    logger.info("lowerByMargin 5 {}", numberLower5);
//                    logger.info("lowerByMargin 7 {}", numberLower7);
//                    logger.info("INTERESTING ###############");
//                }

                if (numberLower == 8 && numberLower3 >= 6) {
//                    if (isInteresting(worldX, worldY)) {
//                        logger.info("INTERESTING");
//                        logger.info("At {} {} h {}", worldX, worldY, centre);
//                        logger.info("Corners dl {}, ul {}, ur {}, dr {}", dl, ul, ur, dr);
//                        logger.info("Midway l {}, up {}, r {}, dn {}", l, up, r, dn);
//                        logger.info("lowerByMargin {}", lowerThanCentreByMargin);
//                        logger.info("number lower {}", numberLower);
//                        logger.info("lowerByMargin 3 {}", numberLower3);
//                        logger.info("lowerByMargin 5 {}", numberLower5);
//                        logger.info("lowerByMargin 7 {}", numberLower7);
//                        logger.info("INTERESTING ###############");
//                    }

                    PotentialSite potentialSite = new PotentialSite(worldX, worldY, centre);
                    potentialSite.setAtSixteen(dl, l, ul, up, ur, r, dr, dn);
                    potentialSite.setAvgAtSixteen(avg(dl, l, ul, up, ur, r, dr, dn));
                    potentialSite.lowerBy3 = numberLower3;
                    potentialSite.lowerBy5 = numberLower5;
                    potentialSite.lowerBy7 = numberLower7;
                    potentialSite.biomesMatch = checkAllBiomes(dlBiome, ulBiome, drBiome, urBiome);
                    sites.add(potentialSite);

//                        logger.info("Not all biomes match {}, {} {} {} {}", potentialSite.location(),
//                                dlBiome, ulBiome, drBiome, urBiome);

                }
            }
        }
        return sites;
    }

    private boolean checkAllBiomes(Biome... biomes) {
            return Arrays.stream(biomes).allMatch(biome -> ACCEPTABLE_BIOMES.contains(biome));
    }

    private boolean allLowerThanCentreByMargin(float margin, float centre, float... values) {
        for (int i = 0; i < values.length; ++i) {
            if (centre - margin < values[i]) {
                return  false;
            }
        }
        return true;
    }

    private int numberLowerThanCentreByMargin(float margin, float centre, float... values) {
        int lower = 0;
        for (int i = 0; i < values.length; ++i) {
            if (centre - margin > values[i]) {
                ++lower;
            }
        }
        return lower;
    }

    private boolean isInteresting(int x, int y) {
        return TestUtils.isInRange(x, -92, 5) && TestUtils.isInRange(y, -56, 5);
    }


    public static enum QuadUsages {
        UP_LEFT, UP_RIGHT, DOWN_RIGHT, DOWN_LEFT
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

        float getSurfaceHeight(int x, int y) {
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

        Biome getBiome(int x, int y) {
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
            return areaFacet.surfaceAndBiome.biome.getWorld(worldX, worldY);
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
        int lowerBy3;
        int lowerBy5;
        int lowerBy7;
        boolean biomesMatch;

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

        public boolean isBiomesMatch() {
            return biomesMatch;
        }
    }

    public static class SurfaceAndBiome {
        SurfaceHeightFacet surface;
        BiomeFacet biome;

        @Override
        public String toString() {
            return "SurfaceAndBiome{" +
                    "surface=" + surface.getClass().getSimpleName() +
                    ", biome=" + biome.getClass().getSimpleName() +
                    '}';
        }
    }
}
