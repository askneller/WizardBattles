///*
// * Copyright 2019 MovingBlocks
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.terasology.wizardbattles;
//
//import org.terasology.math.geom.Vector3f;
//import org.terasology.particles.ParticleData;
//import org.terasology.particles.ParticleDataMask;
//import org.terasology.particles.functions.affectors.AffectorFunction;
//import org.terasology.utilities.random.Random;
//
//import java.util.Map;
//
///**
// * From the Projectiles module
// */
//public class AttractorAffectorFunction extends AffectorFunction<AttractorAffectorComponent> {
//    static final float EPS = 1e-2f;
//
//    public AttractorAffectorFunction() {
//        super(AttractorAffectorComponent.class, ParticleDataMask.VELOCITY,
//                ParticleDataMask.ENERGY);
//    }
//
//    @Override
//    public void update(final AttractorAffectorComponent component,
//                       final ParticleData particleData,
//                       final Random random,
//                       final float delta
//    ) {
//        if (component.origin == null) return;
//
//        Vector3f particlePos = new Vector3f(particleData.position);
//        for (Map.Entry<Vector3f, Float> attractor : component.attractors.entrySet()) {
//            final Vector3f attractorOffset = attractor.getKey();
//            float strength = attractor.getValue();
//            Vector3f attractorPos = new Vector3f(attractorOffset).add(component.origin.getWorldPosition());
//            Vector3f displacementVector = attractorPos.sub(particlePos);
//            float displacementSquared = displacementVector.lengthSquared();
//            Vector3f acceleration = new Vector3f();
//
//            if (strength > 0) {
//                if (displacementSquared < EPS) {
//                    particleData.energy = 0;
//                    continue;
//                }
//                acceleration = new Vector3f(displacementVector).normalize().div(displacementSquared);
//            } else if (strength < 0) {
//                strength = -strength;
//                if (displacementSquared == 0) {
//                    displacementVector = new Vector3f(random.nextFloat(-.1f, .1f), random.nextFloat(-.1f, .1f), random.nextFloat(-.1f, .1f));
//                    displacementSquared = displacementVector.lengthSquared();
//                }
//                acceleration = new Vector3f(displacementVector).normalize().div(displacementSquared).invert();
//            }
//
//            acceleration.mul(strength);
//            particleData.velocity.add(
//                    acceleration.mul(delta)
//            );
//        }
//    }
//}
