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
import org.terasology.entitySystem.entity.EntityStore;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.geom.BaseVector3i;
import org.terasology.math.geom.Vector3f;
import org.terasology.utilities.Assets;
import org.terasology.world.generation.EntityBuffer;
import org.terasology.world.generation.EntityProviderPlugin;
import org.terasology.world.generation.Region;
import org.terasology.world.generator.plugin.RegisterPlugin;

import java.util.Map;
import java.util.Optional;

@RegisterPlugin
public class TowerWizardEntityProvider implements EntityProviderPlugin {

    private static final Logger logger = LoggerFactory.getLogger(TowerWizardEntityProvider.class);

    @Override
    public void process(Region region, EntityBuffer buffer) {
        WizardTowerFacet facet = region.getFacet(WizardTowerFacet.class);
        if (facet != null && facet.getRelativeEntries().size() > 0) {
            for (Map.Entry<BaseVector3i, StructureGenerator> entry : facet.getWorldEntries().entrySet()) {
                BaseVector3i vector3i = entry.getKey();
                logger.info("Tower at {}", vector3i);

                EntityStore entityStore =
                        getEntityStoreForPrefab(region, vector3i, "WizardTowers:wizard", 0, 18, 0);
                if (entityStore != null) {
                        buffer.enqueue(entityStore);
                }
                entityStore =
                        getEntityStoreForPrefab(region, vector3i, "WizardTowers:skeleton", 1, 2, 1);
                if (entityStore != null) {
                    buffer.enqueue(entityStore);
                }
                entityStore =
                        getEntityStoreForPrefab(region, vector3i, "WizardTowers:skeleton", -1, 2, -1);
                if (entityStore != null) {
                    buffer.enqueue(entityStore);
                }
            }
        }
    }

    private EntityStore getEntityStoreForPrefab(Region region,
                                                BaseVector3i vector3i,
                                                String prefab,
                                                int xOffset,
                                                int yOffset,
                                                int zOffset) {
        Optional<Prefab> optionalPrefab = Assets.getPrefab(prefab);
        if (optionalPrefab.isPresent()) {
            EntityStore entityStore = new EntityStore(optionalPrefab.get());

            int x = vector3i.x() + xOffset;
            int y = vector3i.y() + yOffset;
            int z = vector3i.z() + zOffset;
            if (region.getRegion().encompasses(x, y, z)) {
                Vector3f pos3d = new Vector3f(x, y, z);
                logger.info("Adding {} at {}", prefab, pos3d);
                entityStore.addComponent(new LocationComponent(pos3d));
                return entityStore;
            } else {
                logger.warn("Region does not encompass prefab position {} {} {}", x, y, z);
            }
        } else {
            logger.warn("Failed to find prefab {}", prefab);
        }
        return null;
    }
}
