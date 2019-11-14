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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.terasology.math.geom.BaseVector3i;
import org.terasology.registry.CoreRegistry;
import org.terasology.utilities.procedural.WhiteNoise;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.chunks.CoreChunk;
import org.terasology.world.generation.Region;
import org.terasology.world.generation.WorldRasterizerPlugin;
import org.terasology.world.generator.plugin.RegisterPlugin;

import java.util.List;
import java.util.Map;

@RegisterPlugin
public class ManaCrystalRasterizer implements WorldRasterizerPlugin {
    private final Map<ManaCrystalType, List<Block>> typeListEnumMap = Maps.newEnumMap(ManaCrystalType.class);

    private BlockManager blockManager;

    @Override
    public void initialize() {
        blockManager = CoreRegistry.get(BlockManager.class);

        typeListEnumMap.put(ManaCrystalType.DEFAULT, ImmutableList.<Block>of(
                blockManager.getBlockFamily("WizardTowers:ManaCrystal").getArchetypeBlock()));
    }

    @Override
    public void generateChunk(CoreChunk chunk, Region chunkRegion) {
        ManaCrystalFacet facet = chunkRegion.getFacet(ManaCrystalFacet.class);
        Block air = blockManager.getBlock(BlockManager.AIR_ID);

        WhiteNoise noise = new WhiteNoise(chunk.getPosition().hashCode());

        Map<BaseVector3i, ManaCrystalType> entries = facet.getRelativeEntries();
        for (BaseVector3i pos : entries.keySet()) {

            // check if some other rasterizer has already placed something here
            if (chunk.getBlock(pos).equals(air)) {

                ManaCrystalType type = entries.get(pos);
                List<Block> list = typeListEnumMap.get(type);
                int blockIdx = Math.abs(noise.intNoise(pos.getX(), pos.getY(), pos.getZ())) % list.size();
                Block block = list.get(blockIdx);
                chunk.setBlock(pos, block);
            }
        }
    }
}
