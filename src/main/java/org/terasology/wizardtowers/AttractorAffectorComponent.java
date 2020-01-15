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

import org.terasology.entitySystem.Component;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.geom.Vector3f;
import org.terasology.network.Replicate;

import java.util.HashMap;
import java.util.Map;

/**
 * From the Projectiles module
 */
public class AttractorAffectorComponent implements Component {

    @Replicate
    public Map<Vector3f, Float> attractors;

    @Replicate
    public LocationComponent origin;

    public AttractorAffectorComponent() {
        this.attractors = new HashMap<>();
    }

}
