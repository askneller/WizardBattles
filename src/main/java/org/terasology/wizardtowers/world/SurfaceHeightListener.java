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
import org.terasology.wizardtowers.SurfaceHeightListenerClient;
import org.terasology.world.generation.FacetProviderListener;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.WorldFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;
import org.terasology.world.generator.plugin.RegisterListener;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterListener // todo qualify with classes we are listening to
public class SurfaceHeightListener implements FacetProviderListener {

    private static final Logger logger = LoggerFactory.getLogger(SurfaceHeightListener.class);

    private static final Map<Class<? extends WorldFacet>, List<SurfaceHeightListenerClient>> CLIENTS = new HashMap<>();

    public static <F extends WorldFacet> void register(Class<F> facetClass, SurfaceHeightListenerClient client) {
        if (facetClass.equals(SurfaceHeightFacet.class)) {
            List<SurfaceHeightListenerClient> surfaceHeightListenerClients =
                    CLIENTS.computeIfAbsent(facetClass, k -> new LinkedList<>());
            surfaceHeightListenerClients.add(client);
        }
    }

    @Override
    public void initialize() {
        Set<Class<? extends WorldFacet>> classes = CLIENTS.keySet();
        classes.forEach(c -> logger.info("Initialising with client {}", c.getSimpleName()));
    }

    @Override
    public <F extends WorldFacet> void notify(GeneratingRegion region, F facet) {
        if (facet != null && region != null) {
            List<SurfaceHeightListenerClient> clients = CLIENTS.get(facet.getClass());
            if (clients != null && !clients.isEmpty()) {
                clients.forEach(c -> c.onListened(region.getRegion(), facet));
            }
        }
    }
}
