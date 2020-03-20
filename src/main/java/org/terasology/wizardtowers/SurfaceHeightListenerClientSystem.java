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
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Vector2i;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.registry.Share;
import org.terasology.wizardtowers.world.SurfaceHeightListener;
import org.terasology.wizardtowers.world.TowerGenerationSystem;
import org.terasology.world.ChunkView;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BlockManager;
import org.terasology.world.generation.WorldFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

// todo: something like RegisterListenerClient, it should register itself with the listener, not the listener getting the client
//@Share({SurfaceHeightListenerClient.class})
@RegisterSystem(RegisterMode.AUTHORITY)
public class SurfaceHeightListenerClientSystem extends BaseComponentSystem implements SurfaceHeightListenerClient {

    @In
    private BlockManager blockManager;

    @In
    private WorldProvider worldProvider;

    private static final Logger logger = LoggerFactory.getLogger(SurfaceHeightListenerClientSystem.class);

    private Map<Vector2i, SurfaceHeightFacet> surfaceFacetMap = new HashMap<>();
    private Map<Vector2i, Integer> usageMap = new HashMap<>();
    private final Object mutex = new Object();

    private int max = 15;
    private int count;
    private boolean checked = false;
    private boolean print = false;

    public void initialise() {
        logger.info("Initializing {} {}", blockManager, worldProvider);
        count = 0;
        SurfaceHeightListener.register(SurfaceHeightFacet.class, this);
    }

    @Override
    public <T extends WorldFacet> void onListened(Region3i region, T facet) {
        // this happens in the generator thread, doing the build should be in another thread
        if (facet instanceof SurfaceHeightFacet) { // todo: use class when registering listener
            Vector3i min = region.min();
            Vector2i xzMin = new Vector2i(min.x, min.z);
            if (!surfaceFacetMap.containsKey(xzMin)) {
                    surfaceFacetMap.put(xzMin, (SurfaceHeightFacet) facet);
                    QuadArea quadArea = checkForQuad(xzMin, region.size(), surfaceFacetMap.get(xzMin));
                    if (quadArea != null) {
                        recordUsage(quadArea);
                        List<PotentialSite> sitesFromCentre = findSitesFromCentre(quadArea);
                        if (!sitesFromCentre.isEmpty()) {
                            // todo: once we have found potential sites, add the sites to a list and receive on chunk loaded
                            // todo: events, and see if any item in the list is in that chunk.
                            // todo: Can use a org.terasology.utilities.concurrency.TaskMaster to add the blocks
                            logger.info("Found {} potential sites", sitesFromCentre.size());
                            if (sitesFromCentre.size() > 1) {
                                sitesFromCentre.sort((a, b) -> TeraMath.floorToInt(b.centreHeight - a.centreHeight));
                                PotentialSite highest = sitesFromCentre.get(0);
                                logger.info("Highest {} {} {}", highest.x, highest.z, highest.centreHeight);
                                PotentialSite lowest = sitesFromCentre.get(sitesFromCentre.size() - 1);
                                logger.info("Lowest {} {} {}", lowest.x, lowest.z, lowest.centreHeight);
//                                build(highest.x, TeraMath.floorToInt(highest.centreHeight), highest.z);
                                TowerGenerationSystem.addSite(new Vector3i(
                                        highest.x, TeraMath.floorToInt(highest.centreHeight), highest.z));
                            } else if (sitesFromCentre.size() == 1) {
                                PotentialSite highest = sitesFromCentre.get(0);
                                logger.info("Only {} {} {}", highest.x, highest.z, highest.centreHeight);
//                                build(highest.x, TeraMath.floorToInt(highest.centreHeight), highest.z);
                                TowerGenerationSystem.addSite(new Vector3i(
                                        highest.x, TeraMath.floorToInt(highest.centreHeight), highest.z));
                            }
                        }
                    }
            }
        }
    }

    private void build(int x, int y, int z) {
//        Block block = worldProvider.getBlock(x, y, z);
        Vector3i pos = new Vector3i(x, y, z);
        if (worldProvider.isBlockRelevant(pos)) {
            logger.info("Block at {} {} {} relevant", x, y, z);
            ChunkView worldViewAround = worldProvider.getWorldViewAround(pos);
            if (worldViewAround != null) {
                logger.info("Block at {} {} {}, {}", x, y, z, worldViewAround.getBlock(x, y, z));
            }
        }
//        Optional<Prefab> prefabOptional = Assets.getPrefab("WizardTowers:tower");
//        if (prefabOptional.isPresent()) {
//            Prefab prefab = prefabOptional.get();
//            SpawnBlockRegionsComponent spawnBlockRegions = prefab.getComponent(SpawnBlockRegionsComponent.class);
//            if (spawnBlockRegions != null) {
//                Vector3i vector3i = view.chunkToWorldPosition(posX, posY, posZ);
//                logger.debug("Generating at {} (world center), {} {} {} {} (region relative center)",
//                        vector3i, view.getRegion(), posX, posY, posZ);
//                for (SpawnBlockRegionsComponent.RegionToFill regionToFill : spawnBlockRegions.regionsToFill) {
//                    Block block = regionToFill.blockType;
//
//                    Region3i region = regionToFill.region;
//                    // Block positions are specified relative to the centre of the tower for X and Z,
//                    // and relative to the bottom for Y
//                    for (Vector3i pos : region) {
//                        int relX = pos.x + posX;
//                        int relY = pos.y + posY;
//                        int relZ = pos.z + posZ;
//                        view.setBlock(relX, relY, relZ, block);
//                    }
//                }
//            }
//        }
    }

    private boolean isRegion(Region3i region3i) {
        return region3i.minX() <= -125 && region3i.maxX() >= -125 && region3i.minZ() <= 110 && region3i.maxZ() >= 110;
    }

    private boolean isQuad(QuadArea quadArea) {
        return quadArea.minX() <= -125 && quadArea.minX() + quadArea.sizeX() >= -125
                && quadArea.minY() <= 110 && quadArea.minY() + quadArea.sizeY() >= 110;
    }

    private QuadArea checkForQuad(Vector2i latest, Vector3i size, SurfaceHeightFacet facet) {
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

    private void findSitesFromEdge(QuadArea quadArea) {
        int size = 16;
        int shift = 4;
        // starting downLeft
        int maxX = quadArea.sizeX();
        int maxY = quadArea.sizeY();
        if (quadArea.print) {
            logger.info("%%%%%%%%% Quad {} {}", quadArea.minX(), quadArea.minY());
        }
        for (int x = 0; x < maxX; x += shift) {
            for (int y = 0; y < maxY; y += shift) {
                // x and y specify the lower left corner of the area
                if (quadArea.print) {
                    logger.info("*****************************************");
                    logger.info("Checking at (relative) {} {}, world {} {}", x, y, quadArea.minX() + x, quadArea.minY() + y);
                }
                float dl = quadArea.get(x, y);
                float ul = quadArea.get(x, y + (size - 1));
                float ur = quadArea.get(x + (size - 1), y + (size - 1));
                float dr = quadArea.get(x + (size - 1), y);
                float l = quadArea.get(x, y + (size / 2 - 1));
                float up = quadArea.get(x + (size / 2 - 1), y + (size - 1));
                float r = quadArea.get(x + (size - 1), y + (size / 2 - 1));
                float dn = quadArea.get(x + (size / 2 - 1), y);
                float margin = 5.0f;
                boolean withinMargin = allWithinHeight(margin, dl, ul, ur, dr, l, r, up, dn);
                float avg = avg(dl, ul, ur, dr, l, r, up, dn);

                if (quadArea.print) {
                    logger.info("Corners dl {}, ul {}, ur {}, dr {}", dl, ul, ur, dr);
                    logger.info("Midway l {}, up {}, r {}, dn {}", l, up, r, dn);
                    logger.info("In height margin {} avg {} mid {} at {} {}", withinMargin, avg,
                            quadArea.get(x + (size / 2 - 1), y + (size / 2 - 1)),
                            quadArea.relXToWorld(x + (size / 2 - 1)),
                            quadArea.relYToWorld(y + (size / 2 - 1)));
                    logger.info("*****************************************");
                }

                // todo add if (print) and do full logging
                if (withinMargin) {
                    float mid = quadArea.get(x + (size / 2 - 1), y + (size / 2 - 1));
                    if (Math.abs(avg - mid) > 3.0f) {
                        if (++count < max && quadArea.print) {
                            logger.info("FOUND!!");
                            logger.info("Corners dl {}, ul {}, ur {}, dr {}", dl, ul, ur, dr);
                            logger.info("Midway l {}, up {}, r {}, dn {}", l, up, r, dn);
                            logger.info("In height margin {} avg {} mid {}", margin, avg, mid);
                            logger.info("FOUND END ###############");
                        }
                    }
                }
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
//                    if (++count < max) {
//                        logger.info("FOUND!!");
//                        logger.info("At {} {} h {}", worldX, worldY, centre);
//                        logger.info("Corners dl {}, ul {}, ur {}, dr {}", dl, ul, ur, dr);
//                        logger.info("Midway l {}, up {}, r {}, dn {}", l, up, r, dn);
//                        logger.info("FOUND END ###############");
//                    }
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
//            logger.info("GET {} {}", x, y);
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
//            logger.info("Facet is {}", areaFacet);
            int relX = x % SIZE;
            int relY = y % SIZE;
            int worldX = areaFacet.area.x + relX;
            int worldY = areaFacet.area.y + relY;
//            logger.info("Final world {} {}", worldX, worldY);
            return areaFacet.facet.getWorld(worldX, worldY);
        }

        float getRelative(int x, int z) {
            boolean lowerHalfX = x < SIZE;
            boolean lowerHalfY = z < SIZE;

            if (lowerHalfX && lowerHalfY) {
                return upLeft.facet.get(x, z);
            } else if (lowerHalfX) {
                // We know lowerHalfY must be false
                return downLeft.facet.get(x, z);
            } else if (lowerHalfY) {
                // We know lowerHalfX is false
                return upRight.facet.get(x, z);
            } else {
                return downRight.facet.get(x, z);
            }
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
        SurfaceHeightFacet facet;

        public AreaFacet(Vector2i area, SurfaceHeightFacet facet) {
            this.area = area;
            this.facet = facet;
        }

        @Override
        public String toString() {
            return "AreaFacet{" +
                    "area=" + area +
                    ", facet=" + facet.getWorldRegion() +
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

    }
}
