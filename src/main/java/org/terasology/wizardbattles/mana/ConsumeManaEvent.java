// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.wizardbattles.mana;

import org.terasology.entitySystem.event.Event;

public class ConsumeManaEvent implements Event {

    private int amount = 0;

    public ConsumeManaEvent(int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
