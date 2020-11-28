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

import org.terasology.core.world.generator.facets.BiomeFacet;
import org.terasology.entitySystem.Component;
import org.terasology.nui.properties.Range;
import org.terasology.utilities.procedural.Noise;
import org.terasology.utilities.procedural.WhiteNoise;
import org.terasology.world.generation.Border3D;
import org.terasology.world.generation.ConfigurableFacetProvider;
import org.terasology.world.generation.Facet;
import org.terasology.world.generation.FacetProviderPlugin;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.Produces;
import org.terasology.world.generation.Requires;
import org.terasology.world.generation.facets.ElevationFacet;
import org.terasology.world.generation.facets.SeaLevelFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;
import org.terasology.world.generation.facets.SurfacesFacet;
import org.terasology.world.generator.plugin.RegisterPlugin;

/**
 * Determines where structures can be placed.  Will put structures at the surface.
 */
@RegisterPlugin
@Produces(WizardTowerFacet.class)
@Requires({
        @Facet(value = SeaLevelFacet.class),
        @Facet(value = ElevationFacet.class),
        @Facet(value = BiomeFacet.class)
})
public class WizardTowerProvider implements ConfigurableFacetProvider, FacetProviderPlugin {

    private Noise densityNoiseGen;
    private Configuration configuration = new Configuration();

    public WizardTowerProvider() {
    }

    /**
     * @param configuration the default configuration to use
     */
    public WizardTowerProvider(Configuration configuration) {
        this();
        this.configuration = configuration;
    }

    @Override
    public void setSeed(long seed) {
        densityNoiseGen = new WhiteNoise(seed);
    }

    @Override
    public void process(GeneratingRegion region) {
        Border3D borderForFacet = region.getBorderForFacet(WizardTowerFacet.class);
        // TODO: extending the border doesn't seem to prevent ArrayIndexOutOfBoundsExceptions
        WizardTowerFacet facet =
                new WizardTowerFacet(region.getRegion(), borderForFacet.extendBy(0, 19, 6));

        new WizardTowerLocationFinder(region)
                .populateFacet(facet);

        region.setRegionFacet(WizardTowerFacet.class, facet);
    }

    @Override
    public String getConfigurationName() {
        return "WizardBattles";
    }

    @Override
    public Component getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(Component configuration) {
        this.configuration = (Configuration) configuration;
    }

    // TODO use the config probabilities
    public static class Configuration implements Component {
        @Range(min = 0, max = 1.0f, increment = 0.05f, precision = 2, description = "Define the overall structure density")
        public float density = 0.2f;
    }
}
