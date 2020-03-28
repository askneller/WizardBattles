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
import org.terasology.core.world.generator.facets.BiomeFacet;
import org.terasology.wizardtowers.SurfaceHeightListenerClient;
import org.terasology.world.generation.Facet;
import org.terasology.world.generation.FacetProviderListener;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.WorldFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;
import org.terasology.world.generator.plugin.RegisterFacetListener;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterFacetListener({@Facet(SurfaceHeightFacet.class), @Facet(BiomeFacet.class)})
public class SurfaceHeightProviderListener implements FacetProviderListener {

    private static final Logger logger = LoggerFactory.getLogger(SurfaceHeightProviderListener.class);

    private static final Map<Class<? extends WorldFacet>, List<SurfaceHeightListenerClient>> CLIENTS = new HashMap<>();

    private boolean initialised = false;

    public static <F extends WorldFacet> void register(Class<F> facetClass, SurfaceHeightListenerClient client) {
        logger.info("Registering client. Facet {}, Client class {}",
                facetClass.getSimpleName(), client.getClass().getSimpleName());
        List<SurfaceHeightListenerClient> listenersForFacet = CLIENTS.get(facetClass);
        logger.info("listenersForFacet: {}", listenersForFacet);
        if (listenersForFacet == null) {
            listenersForFacet = new LinkedList<>();
            logger.info("Adding new listener list for {}", facetClass);
            CLIENTS.put(facetClass, listenersForFacet);
        }
        logger.info("Adding listener {}", client.getClass().getSimpleName());
        listenersForFacet.add(client);
        logger.info("CLIENTS key length {}", CLIENTS.keySet().size());
    }

    @Override
    public boolean isInitialised() {
        return initialised;
    }

    @Override
    public void initialize() {
        if (!isInitialised()) {
            logger.info("INITIALISING");
            initialised = true;
            Set<Class<? extends WorldFacet>> classes = CLIENTS.keySet();
            Set<Map.Entry<Class<? extends WorldFacet>, List<SurfaceHeightListenerClient>>> entries = CLIENTS.entrySet();
            entries.forEach(classListEntry -> {
                logger.info("Class entry key {}", classListEntry.getKey().getSimpleName());
                List<SurfaceHeightListenerClient> value = classListEntry.getValue();
                for (SurfaceHeightListenerClient client : value) {
                    logger.info("Client {}", client.getClass().getSimpleName());
                }
            });
        }
    }

    @Override
    public <F extends WorldFacet> void notify(GeneratingRegion region, F facet) {
        if (facet != null && region != null) {
            List<SurfaceHeightListenerClient> clients = CLIENTS.get(facet.getClass());
            if (clients != null && !clients.isEmpty()) {
                clients.forEach(c -> {
                    c.onListened(region.getRegion(), facet);
                });
            }
        }
    }
}
