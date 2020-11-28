// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.spell;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.Event;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.JomlUtil;
import org.terasology.math.geom.Vector3f;

public class SpellCastEvent implements Event {
    private EntityRef instigator;
    private EntityRef target;
    private Vector3f origin;
    private Vector3f direction;
    private Vector3f hitPosition;
    private Vector3f hitNormal;
    private int activationId;
    private Prefab spellPrefab;

    public SpellCastEvent() {

    }

    public SpellCastEvent(ActivateEvent info, Prefab prefab) {
        instigator = info.getInstigator();
        target = info.getTarget();
        origin = JomlUtil.from(info.getOrigin());
        direction = JomlUtil.from(info.getDirection());
        hitPosition = JomlUtil.from(info.getHitPosition());
        hitNormal = JomlUtil.from(info.getHitNormal());
        activationId = info.getActivationId();
        spellPrefab = prefab;
    }
    
    public EntityRef getInstigator() {
        return instigator;
    }

    public EntityRef getTarget() {
        return target;
    }

    public Vector3f getOrigin() {
        return origin;
    }

    public Vector3f getDirection() {
        return direction;
    }

    public Vector3f getHitPosition() {
        return hitPosition;
    }

    public Vector3f getHitNormal() {
        return hitNormal;
    }

    public int getActivationId() {
        return activationId;
    }

    public Prefab getSpellPrefab() {
        return spellPrefab;
    }

    public Vector3f getTargetLocation() {
        LocationComponent loc = target.getComponent(LocationComponent.class);
        if (loc != null) {
            return loc.getWorldPosition();
        }
        return null;
    }

    public Vector3f getInstigatorLocation() {
        LocationComponent loc = instigator.getComponent(LocationComponent.class);
        if (loc != null) {
            return loc.getWorldPosition();
        }
        return new Vector3f();
    }

    @Override
    public String toString() {
        return "SpellCastEvent{" +
                "instigator=" + instigator +
                ", target=" + target +
                ", origin=" + origin +
                ", direction=" + direction +
                ", hitPosition=" + hitPosition +
                ", hitNormal=" + hitNormal +
                ", activationId=" + activationId +
                ", spellPrefab=" + spellPrefab +
                '}';
    }
}
