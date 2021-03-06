// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.casting;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.combatSystem.physics.events.CombatImpulseEvent;
import org.terasology.combatSystem.weaponFeatures.components.AttackerComponent;
import org.terasology.combatSystem.weaponFeatures.components.LaunchEntityComponent;
import org.terasology.combatSystem.weaponFeatures.events.ReduceAmmoEvent;
import org.terasology.engine.Time;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.characters.GazeMountPointComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.JomlUtil;
import org.terasology.math.geom.BaseVector3f;
import org.terasology.math.geom.Quat4f;
import org.terasology.math.geom.Vector3f;
import org.terasology.physics.CollisionGroup;
import org.terasology.physics.StandardCollisionGroup;
import org.terasology.physics.components.TriggerComponent;
import org.terasology.physics.components.shapes.BoxShapeComponent;
import org.terasology.registry.In;
import org.terasology.rendering.logic.MeshComponent;
import org.terasology.utilities.Assets;
import org.terasology.wizardbattles.LaunchEvent;
import org.terasology.wizardbattles.spell.SpellCastEvent;
import org.terasology.wizardbattles.spell.SpellComponent;
import org.terasology.wizardbattles.spell.SpellSelectionComponent;

import java.util.Optional;

@RegisterSystem
public class CastingSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final Logger logger = LoggerFactory.getLogger(CastingSystem.class);

    @In
    private EntityManager entityManager;

    @In
    private Time time;

    @ReceiveEvent
    public void spellCast(SpellCastEvent event, EntityRef entity) {
        Prefab spellPrefab = event.getSpellPrefab();
        Iterable<Component> components = spellPrefab.iterateComponents();
        components.forEach(component -> {
            if (component instanceof LaunchEntityComponent) {
                launchEntity(event.getDirection(), entity, (LaunchEntityComponent) component);
            }
        });
    }

    @ReceiveEvent
    public void onBeginCasting(BeginCastingEvent event, EntityRef entity) {
        CastingComponent castingComponent = entity.getComponent(CastingComponent.class);
        if (castingComponent == null) {
            // Not already casting
            SpellSelectionComponent spellSelectionComponent = entity.getComponent(SpellSelectionComponent.class);
            if (spellSelectionComponent.selected != null) {
                Optional<Prefab> prefabOptional = Assets.getPrefab(spellSelectionComponent.selected);
                final CastingComponent casting = new CastingComponent();
                prefabOptional.ifPresent(prefab -> {
                    SpellComponent spellComponent = prefab.getComponent(SpellComponent.class);
                    if (spellComponent != null) {
                        if (spellComponent.castingTimeMs > 0) {
                            casting.begunAt = time.getGameTimeInMs();
                            casting.timeRequired = spellComponent.castingTimeMs;
                            entity.saveComponent(casting);
                        } else if (spellComponent.castingTimeMs == 0) {
                            entity.send(new CompleteCastingEvent());
                        }
                    }
                });
            }
        }
    }

    @Override
    public void update(float delta) {
        Iterable<EntityRef> entitiesWith = entityManager.getEntitiesWith(CastingComponent.class);
        for (EntityRef caster : entitiesWith) {
            CastingComponent castingComponent = caster.getComponent(CastingComponent.class);
            long now = time.getGameTimeInMs();
            if (castingComponent.begunAt + castingComponent.timeRequired <= now) {
                caster.send(new CompleteCastingEvent());
            }
        }
    }

    /**
     * This code is almost a direct copy from {@link org.terasology.combatSystem.weaponFeatures.systems.LaunchEntitySystem}.
     * @param direction
     * @param entity
     * @param launchEntity
     */
    private void launchEntity(Vector3f direction,
                              EntityRef entity,
                              LaunchEntityComponent launchEntity) {

        // Launch cooldown is handled by the SpellItemComponent's cooldown
        EntityRef player = EntityRef.NULL; // Note the entity passed in is already the ultimate entity
        // e.g. player, trap, launcher etc.


        player = entity;

        EntityRef entityToLaunch = EntityRef.NULL;
        // creates an entity with specified prefab for eg. an arrow prefab
        if (launchEntity.launchEntityPrefab != null) {
            entityToLaunch = entityManager.create(launchEntity.launchEntityPrefab);
        }

        if (entityToLaunch != EntityRef.NULL) {
            LocationComponent location = entityToLaunch.getComponent(LocationComponent.class);

            // adds the entity as the shooter for the arrow. It will be the launcher itself.
            entityToLaunch.addOrSaveComponent(new AttackerComponent(player)); // attacker is the player

            LocationComponent shooterLoc = player.getComponent(LocationComponent.class);

            if (shooterLoc == null) {
                return;
            }

            if (entityToLaunch.hasComponent(MeshComponent.class)) {
                MeshComponent mesh = entityToLaunch.getComponent(MeshComponent.class);
                BoxShapeComponent box = new BoxShapeComponent();
                box.extents = JomlUtil.from(mesh.mesh.getAABB().getExtents().scale(2.0f));
                entityToLaunch.addOrSaveComponent(box);
            }

            // rotates the entity to face in the direction of pointer
            Vector3f initialDir = location.getWorldDirection();
            Vector3f finalDir = new Vector3f(direction);
            finalDir.normalize();
            location.setWorldRotation(Quat4f.shortestArcQuat(initialDir, finalDir));

            // sets the scale of the entity
            location.setWorldScale(0.5f);

            // sets the location of entity to current player's location with an offset
            GazeMountPointComponent gaze = player.getComponent(GazeMountPointComponent.class);
            if (gaze != null) {
                BaseVector3f baseVector3f =
                        new org.terasology.math.geom.Vector3f(gaze.translate.x, gaze.translate.y, gaze.translate.z);
                location.setWorldPosition(shooterLoc.getWorldPosition().add(baseVector3f).add(finalDir.scale(0.3f)));
            } else {
                location.setWorldPosition(shooterLoc.getWorldPosition());
            }

            entityToLaunch.saveComponent(location);

            if (!entityToLaunch.hasComponent(TriggerComponent.class)) {
                TriggerComponent trigger = new TriggerComponent();
                trigger.collisionGroup = StandardCollisionGroup.ALL;
                trigger.detectGroups = Lists.<CollisionGroup>newArrayList(StandardCollisionGroup.DEFAULT, StandardCollisionGroup.WORLD, StandardCollisionGroup.CHARACTER, StandardCollisionGroup.SENSOR);
                entityToLaunch.addOrSaveComponent(trigger);
            }

            // applies impulse to the entity
            Vector3f impulse = finalDir;
            impulse.normalize();
            impulse.mul(launchEntity.impulse);

            entityToLaunch.send(new CombatImpulseEvent(impulse));
            entityToLaunch.send(new LaunchEvent(direction));
            entity.send(new ReduceAmmoEvent());
        }
    }
}
