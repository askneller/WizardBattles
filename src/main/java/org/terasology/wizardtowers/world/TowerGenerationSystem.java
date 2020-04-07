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
package org.terasology.wizardtowers.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.delay.DelayManager;
import org.terasology.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.math.Region3i;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.structureTemplates.components.SpawnBlockRegionsComponent;
import org.terasology.utilities.Assets;
import org.terasology.wizardtowers.SurfaceHeightListenerClientSystem;
import org.terasology.world.ChunkView;
import org.terasology.world.WorldComponent;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

@RegisterSystem(RegisterMode.AUTHORITY)
public class TowerGenerationSystem extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(TowerGenerationSystem.class);
    private static final Object lock = new Object();
    private static final String TOWER_GENERATION_ACTION_ID = "TowerGeneration";
    private static final long TOWER_GENERATION_INTERVAL = 10000L;
    private static final int MAX = 20;
    private static int NUM = 0;

    private static Queue<Vector3i> siteQueue = new LinkedList<>();
    private static Queue<SurfaceHeightListenerClientSystem.PotentialSite> siteQueue2 = new LinkedList<>();
    private static List<SurfaceHeightListenerClientSystem.PotentialSite> siteList = new LinkedList<>();
    private static Set<Vector3i> sitesBuilt = new HashSet<>();
    private static Set<Vector3i> sitesChecking = new HashSet<>();
    private static Set<Vector3i> sitesAdded = new HashSet<>();

    @In
    private WorldProvider worldProvider;

    @In
    private EntityManager entityManager;

    @In
    private DelayManager delayManager;

    // todo: multiple generator threads adding same site
    public static void addSite(Vector3i site) {
        if (site != null) {
            synchronized (lock) {
                if (++NUM < MAX) {
                    logger.info("Trying to add site {}", site);
                }
                if (!hasSiteBeenBuilt(site) && !isSiteChecking(site)) {
                    logger.info("Adding site {}", site);
                    siteQueue.add(site);
                }
            }
        }
    }

    public static void addSite(SurfaceHeightListenerClientSystem.PotentialSite site) {
        if (site != null) {
            Vector3i location = site.location();
            synchronized (lock) {
                if (!hasSiteBeenAdded(location)) {
                    logger.info("Adding site at location {}", location);
                    sitesAdded.add(location);
                    siteList.add(site);
                }
            }
        }
    }

    public static boolean hasSiteBeenBuilt(Vector3i site) {
        return sitesBuilt.contains(site);
    }

    public static boolean hasSiteBeenAdded(Vector3i site) {
        return sitesAdded.contains(site);
    }

    public static boolean isSiteChecking(Vector3i site) {
        return sitesChecking.contains(site);
    }

    public void postBegin() {
        boolean processedOnce = false;
        for (EntityRef entity : entityManager.getEntitiesWith(WorldComponent.class)) {
            if (!processedOnce) {
                delayManager.addPeriodicAction(entity, TOWER_GENERATION_ACTION_ID, TOWER_GENERATION_INTERVAL, TOWER_GENERATION_INTERVAL);
                processedOnce = true;
            } else {
                logger.warn("More than one entity with WorldComponent found");
            }
        }
    }

    @ReceiveEvent
    public void onPeriodicActionTriggered(PeriodicActionTriggeredEvent event, EntityRef unusedEntity) {
        if (event.getActionId().equals(TOWER_GENERATION_ACTION_ID)) {
            if (!siteList.isEmpty()) {
                SurfaceHeightListenerClientSystem.PotentialSite site2 = getSite2();
                if (site2 != null) {
                    logger.info("Found site from queue {}", site2.location());
                    tryBuild(site2);
                }
            }
        }
    }

    private Vector3i getSite() {
        synchronized (lock) {
            Vector3i site = siteQueue.poll();
            // If another thread is checking this site or has built it, then discard it
            if (++NUM < MAX) {
                logger.info("Site from queue {}", site);
            }
            if (site != null && !isSiteChecking(site) && !hasSiteBeenBuilt(site)) {
                logger.info("Checking Site {}", site);
                sitesChecking.add(site);
                return site;
            }
            logger.info("Not valid {}", site);
        }
        return null;
    }

    private SurfaceHeightListenerClientSystem.PotentialSite getSite2() {
        synchronized (lock) {
            List<SurfaceHeightListenerClientSystem.PotentialSite> collect = siteList.stream()
                    .sorted((s1, s2) -> s2.location().y - s1.location().y)
                    .collect(Collectors.toList());
            if (!collect.isEmpty()) {
                logger.info("highest {}", collect.get(0).location());
                logger.info("lowest {}", collect.get(collect.size() - 1).location());
                // If another thread is checking this site or has built it, then discard it
                if (++NUM < MAX) {
                    logger.info("potentialSite from queue {}", collect.get(0).location());
                }
                siteList.clear();
                    return collect.get(0);
            }
        }
        return null;
    }

    private void tryBuild(SurfaceHeightListenerClientSystem.PotentialSite potentialSite) {
        if (!potentialSite.isBiomesMatch()) {
            logger.info("Biomes do not match! Ignoring");
        } else {
            tryBuild(potentialSite.location());
        }
    }

    private void tryBuild(Vector3i location) {
        tryBuild(location.x, location.y, location.z);
    }

    private void tryBuild(int worldX, int worldY, int worldZ) {
//        Block block = worldProvider.getBlock(x, y, z);
        Vector3i pos = new Vector3i(worldX, worldY, worldZ);
        if (worldProvider.isBlockRelevant(pos)) {

            logger.info("Block at (world) {} {} {} relevant", worldX, worldY, worldZ);
            Vector3i chunkPos = convertToChunkPosition(pos);
            logger.info("Chuck pos min {}", chunkPos);
            ChunkView worldViewAround = worldProvider.getWorldViewAround(chunkPos);
            logger.info("worldViewAround {}", worldViewAround);
            if (worldViewAround != null) {
                int num = 0;
                Block blockAtLocation = worldViewAround.getBlock(worldX, worldY, worldZ);
                logger.info("Block at (world) {} {} {}, {}",
                        worldX, worldY, worldZ, blockAtLocation);
                checkAroundBase(worldViewAround, worldX, worldY, worldZ, blockAtLocation);
                Optional<Prefab> prefabOptional = Assets.getPrefab("WizardTowers:tower");
                if (prefabOptional.isPresent()) {
                    Prefab prefab = prefabOptional.get();
                    SpawnBlockRegionsComponent spawnBlockRegions = prefab.getComponent(SpawnBlockRegionsComponent.class);
                    if (spawnBlockRegions != null) {
                        logger.info("Generating at (world center), {} {} {}", worldX, worldY, worldZ);
                        setSiteBuilding(pos);
                        for (SpawnBlockRegionsComponent.RegionToFill regionToFill : spawnBlockRegions.regionsToFill) {
                            Block block = regionToFill.blockType;

                            Region3i region = regionToFill.region;
                            // Block positions are specified relative to the centre of the tower for X and Z,
                            // and relative to the bottom for Y
                            for (Vector3i blockPos : region) {
                                int relX = blockPos.x + worldX;
                                int relY = blockPos.y + worldY;
                                int relZ = blockPos.z + worldZ;
                                if (worldProvider.isBlockRelevant(relX, relY, relZ)) {
                                    if (++num < 5) {
                                        logger.info("Setting block at world position {} {} {}", relX, relY, relZ);
                                    }
                                    worldProvider.setBlock(new Vector3i(relX, relY, relZ), block);
                                } else {
                                    logger.warn("Block at world position {} {} {} no longer relevant while constructing tower at {} {} {}",
                                            relX, relY, relZ, worldX, worldY, worldZ);
                                }
                            }
                        }
                    }
                }
            } else {
                logger.info("worldViewAround is null");
            }
        } else {
            reclaimSiteNotRelevant(new Vector3i(worldX, worldY, worldZ));
        }
    }

    private void checkAroundBase(ChunkView worldViewAround, int worldX, int worldY, int worldZ, Block blockAtLocation) {
        // Start by checking 1 around
        int below = 0;
        boolean foundBase = false;
        do {
            int distance = 1;
            boolean allSolid = true;
            do {
//                if (below < 2 && distance >= 5) {
//                    logger.info("\n\nCould fill in base\n");
//                }
                allSolid = fillAroundBaseAtDistance(worldViewAround, worldX, worldY, worldZ, distance, below, blockAtLocation);
//                logger.info("All solid at distance {}, below {} = {}", distance, below, allSolid);
                distance++;
            } while (allSolid && distance < 4);
//            if (!allSolid) {
//                below++;
//            } else {
//                foundBase = true;
//                logger.info("Found base at below {}, dist {}", below, distance);
//            }
            below++;
        } while (below <= 4);
        logger.info("Ended. Found {}, below {}", foundBase, below);
    }

    // Return true if all blocks are solid at distance and below
    private boolean checkAroundBaseAtDistance(ChunkView worldViewAround,
                                              int worldX,
                                              int worldY,
                                              int worldZ,
                                              int distance,
                                              int below) {
        int x = worldX - distance;
        int y = worldY - below;
        int z = worldZ + distance;
        logger.info("Starting at {} {} {}", x, y, z);
        // Check "top", positive x direction
        for (; x < worldX + (distance + 1); ++x) {
            Block block = worldViewAround.getBlock(x, y, z);
            if (block.isPenetrable() && worldProvider.isBlockRelevant(x, y, z)) {
                logger.info("Non-solid at {} {}: {}", x, z, block.getURI());
                return false;
            }
        }
        // Check "right side", negative z direction
        --x; // reverse final increment
        logger.info("Next start at {} {} {}", x, y, z);
        for (; z > worldZ - (distance + 1); --z) {
            Block block = worldViewAround.getBlock(x, y, z);
            if (block.isPenetrable() && worldProvider.isBlockRelevant(x, y, z)) {
                logger.info("Non-solid at {} {}: {}", x, z, block.getURI());
                return false;
            }
        }
        // Check "bottom", negative x direction
        ++z; // reverse final dec
        logger.info("Next start at {} {} {}", x, y, z);
        for (; x > worldX - (distance + 1); --x) {
            Block block = worldViewAround.getBlock(x, y, z);
            if (block.isPenetrable() && worldProvider.isBlockRelevant(x, y, z)) {
                logger.info("Non-solid at {} {}: {}", x, z, block.getURI());
                return false;
            }
        }
        // Check "left side", positive z direction
        ++x; // reverse final increment
        logger.info("Next start at {} {} {}", x, y, z);
        for (; z < worldZ + (distance + 1); ++z) {
            Block block = worldViewAround.getBlock(x, y, z);
            if (block.isPenetrable() && worldProvider.isBlockRelevant(x, y, z)) {
                logger.info("Non-solid at {} {}: {}", x, z, block.getURI());
                return false;
            }
        }
        return true;
    }

    private boolean fillAroundBaseAtDistance(ChunkView worldViewAround,
                                             int worldX,
                                             int worldY,
                                             int worldZ,
                                             int distance,
                                             int below,
                                             Block baseBlock) {
        // todo is not setting the blocks
        int x = worldX - distance;
        int y = worldY - below;
        int z = worldZ + distance;
        logger.info("Starting at {} {} {}", x, y, z);
        // Check "top", positive x direction
        for (; x < worldX + (distance + 1); ++x) {
            Block block = worldViewAround.getBlock(x, y, z);
            if (block.isPenetrable() && worldProvider.isBlockRelevant(x, y, z)) {
                logger.info("Non-solid at {} {}: {}. Setting to {}", x, z, block.getURI(), baseBlock);
                worldProvider.setBlock(new Vector3i(x, y, x), baseBlock);
            }
        }
        // Check "right side", negative z direction
        --x; // reverse final increment
        logger.info("Next start at {} {} {}", x, y, z);
        for (; z > worldZ - (distance + 1); --z) {
            Block block = worldViewAround.getBlock(x, y, z);
            if (block.isPenetrable() && worldProvider.isBlockRelevant(x, y, z)) {
                logger.info("Non-solid at {} {}: {}. Setting to {}", x, z, block.getURI(), baseBlock);
                worldProvider.setBlock(new Vector3i(x, y, x), baseBlock);
            }
        }
        // Check "bottom", negative x direction
        ++z; // reverse final dec
        logger.info("Next start at {} {} {}", x, y, z);
        for (; x > worldX - (distance + 1); --x) {
            Block block = worldViewAround.getBlock(x, y, z);
            if (block.isPenetrable() && worldProvider.isBlockRelevant(x, y, z)) {
                logger.info("Non-solid at {} {}: {}. Setting to {}", x, z, block.getURI(), baseBlock);
                worldProvider.setBlock(new Vector3i(x, y, x), baseBlock);
            }
        }
        // Check "left side", positive z direction
        ++x; // reverse final increment
        logger.info("Next start at {} {} {}", x, y, z);
        for (; z < worldZ + (distance + 1); ++z) {
            Block block = worldViewAround.getBlock(x, y, z);
            if (block.isPenetrable() && worldProvider.isBlockRelevant(x, y, z)) {
                logger.info("Non-solid at {} {}: {}. Setting to {}", x, z, block.getURI(), baseBlock);
                worldProvider.setBlock(new Vector3i(x, y, x), baseBlock);
            }
        }
        return true;
    }

    private void setSiteBuilding(Vector3i site) {
        synchronized (lock) {
            if (++NUM < MAX) {
                logger.info("setSiteBuilding {}", site);
            }
            sitesBuilt.add(site);
            sitesChecking.remove(site);
        }
    }

    private void reclaimSiteNotRelevant(Vector3i site) {
        synchronized (lock) {
            logger.info("reclaimSiteNotRelevant {}", site);
            sitesChecking.remove(site);
            addSite(site);
        }
    }

    // Chunk position has to reference the "centre" of the chunk
    private Vector3i convertToChunkPosition(Vector3i pos) {
        return convertToChunkPosition(pos.x, pos.y, pos.z);
    }

    private Vector3i convertToChunkPosition(int x, int y, int z) {
        // TODO use constants for the sizes
        return new Vector3i(
                convertAxisPosition(x, 32), convertAxisPosition(y, 64), convertAxisPosition(z, 32));
    }

    private int convertAxisPosition(int v, int size) {
        int chunkV = v / size;
        if (v < 0) {
            chunkV -= 1;
        }
        return chunkV;
    }
}
