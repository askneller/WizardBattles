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
import org.terasology.core.world.generator.rasterizers.FloraRasterizer;
import org.terasology.core.world.generator.rasterizers.TreeRasterizer;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.math.ChunkMath;
import org.terasology.math.JomlUtil;
import org.terasology.math.Region3i;
import org.terasology.math.geom.BaseVector3i;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.CoreRegistry;
import org.terasology.structureTemplates.components.SpawnBlockRegionsComponent;
import org.terasology.utilities.random.FastRandom;
import org.terasology.utilities.random.Random;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.BlockRegion;
import org.terasology.world.block.BlockRegions;
import org.terasology.world.chunks.CoreChunk;
import org.terasology.world.generation.Region;
import org.terasology.world.generation.RequiresRasterizer;
import org.terasology.world.generation.WorldRasterizerPlugin;
import org.terasology.world.generation.facets.base.SparseFacet3D;
import org.terasology.world.generator.plugin.RegisterPlugin;

import java.util.Map;
import java.util.Objects;

@RegisterPlugin
@RequiresRasterizer({FloraRasterizer.class, TreeRasterizer.class})
public class WizardTowerRasterizer implements WorldRasterizerPlugin {

    private static final Logger logger = LoggerFactory.getLogger(WizardTowerRasterizer.class);

    private BlockManager blockManager;

    /**
     * Stores the prefab of the Igloo Structure Template.
     */
    Prefab structurePrefab;

    @Override
    public void initialize() {
        blockManager = CoreRegistry.get(BlockManager.class);
        structurePrefab = Objects.requireNonNull(CoreRegistry.get(PrefabManager.class)).getPrefab("WizardBattles:tower");
    }

    @Override
    public void generateChunk(CoreChunk chunk, Region chunkRegion) {
        // todo change to this code
//        WizardTowerFacet structureFacet = chunkRegion.getFacet(WizardTowerFacet.class);
//        SpawnBlockRegionsComponent spawnBlockRegionsComponent =
//                structurePrefab.getComponent(SpawnBlockRegionsComponent.class);
//
//        for (Map.Entry<BaseVector3i, WizardTower> entry : structureFacet.getWorldEntries().entrySet()) {
//            logger.info("CoreChunk region {}, chunkRegion region {}", chunk.getRegion(), chunkRegion.getRegion());
//            // Block positions are specified relative to the centre of the tower for X and Z,
//            // and relative to the bottom for Y
//            org.joml.Vector3i basePosition = new org.joml.Vector3i(JomlUtil.from(entry.getKey()));
//            // Fill blocks in the required regions.
//            for (SpawnBlockRegionsComponent.RegionToFill regionToFill : spawnBlockRegionsComponent.regionsToFill) {
//                Block block = regionToFill.blockType;
//                BlockRegion region = regionToFill.region;
//                for (org.joml.Vector3i pos : BlockRegions.iterable(region)) {
//                    // pos is the position vector relative to the origin block of the Structural Template
//                    pos.add(basePosition);
////                    if (chunkRegion.getRegion().encompasses(JomlUtil.from(pos))) {
//                        chunk.setBlock(ChunkMath.calcRelativeBlockPos(JomlUtil.from(pos)), block);
////                    }
//                }
//            }
//        }
        WizardTowerFacet facet = chunkRegion.getFacet(WizardTowerFacet.class);

        for (Map.Entry<BaseVector3i, StructureGenerator> entry : facet.getRelativeEntries().entrySet()) {
            BaseVector3i pos = entry.getKey();
            StructureGenerator generator = entry.getValue();
            int seed = relativeToWorld(facet, pos).hashCode();
            Random random = new FastRandom(seed);
            generator.generate(blockManager, chunk, random, pos.x(), pos.y(), pos.z());
        }
    }

    protected final Vector3i relativeToWorld(SparseFacet3D facet, BaseVector3i pos) {

        Region3i worldRegion = facet.getWorldRegion();
        Region3i relativeRegion = facet.getRelativeRegion();

        return new Vector3i(
                pos.x() - relativeRegion.minX() + worldRegion.minX(),
                pos.y() - relativeRegion.minY() + worldRegion.minY(),
                pos.z() - relativeRegion.minZ() + worldRegion.minZ());
    }
}
