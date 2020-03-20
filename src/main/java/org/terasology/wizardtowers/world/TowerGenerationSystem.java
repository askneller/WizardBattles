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
import org.terasology.world.ChunkView;
import org.terasology.world.WorldComponent;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

@RegisterSystem(RegisterMode.AUTHORITY)
public class TowerGenerationSystem extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(TowerGenerationSystem.class);
    private static final Object lock = new Object();
    private static final String TOWER_GENERATION_ACTION_ID = "TowerGeneration";
    private static final long TOWER_GENERATION_INTERVAL = 10000L;
    private static final int MAX = 20;
    private static int NUM = 0;

    private static Queue<Vector3i> siteQueue = new LinkedList<>();
    private static Set<Vector3i> sitesBuilt = new HashSet<>();
    private static Set<Vector3i> sitesChecking = new HashSet<>();

    @In
    private WorldProvider worldProvider;

    @In
    private EntityManager entityManager;

    @In
    private DelayManager delayManager;

    // a site can be queued if it is not checking and has not been built.

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

    public static boolean hasSiteBeenBuilt(Vector3i site) {
        return sitesBuilt.contains(site);
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
            Vector3i site = getSite();
            if (site != null) {
                logger.info("Found site from queue {}", site);
                tryBuild(site.x, site.y, site.z);
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
                logger.info("Block at (world) {} {} {}, {}",
                        worldX, worldY, worldZ, worldViewAround.getBlock(worldX, worldY, worldZ));
                Optional<Prefab> prefabOptional = Assets.getPrefab("WizardTowers:tower");
                if (prefabOptional.isPresent()) {
                    Prefab prefab = prefabOptional.get();
                    SpawnBlockRegionsComponent spawnBlockRegions = prefab.getComponent(SpawnBlockRegionsComponent.class);
                    if (spawnBlockRegions != null) {
                        logger.debug("Generating at (world center), {} {} {}", worldX, worldY, worldZ);
                        for (SpawnBlockRegionsComponent.RegionToFill regionToFill : spawnBlockRegions.regionsToFill) {
                            Block block = regionToFill.blockType;

                            Region3i region = regionToFill.region;
                            // Block positions are specified relative to the centre of the tower for X and Z,
                            // and relative to the bottom for Y
                            for (Vector3i blockPos : region) {
                                int relX = blockPos.x + worldX;
                                int relY = blockPos.y + worldY;
                                int relZ = blockPos.z + worldZ;
                                if (++num < 5) {
                                    logger.debug("Setting block at world position {} {} {}", relX, relY, relZ);
                                }
                                worldViewAround.setBlock(relX, relY, relZ, block);
                            }
                        }
                    }
                }
            }
        } else {
            reclaimSiteNotRelevant(new Vector3i(worldX, worldY, worldZ));
        }
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
            if (++NUM < MAX) {
                logger.info("reclaimSiteNotRelevant {}", site);
            }
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
