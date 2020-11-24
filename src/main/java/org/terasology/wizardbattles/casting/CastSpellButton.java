// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.wizardbattles.casting;

import org.terasology.input.ActivateMode;
import org.terasology.input.BindButtonEvent;
import org.terasology.input.DefaultBinding;
import org.terasology.input.InputType;
import org.terasology.input.Keyboard;
import org.terasology.input.RegisterBindButton;

/**
 */
@RegisterBindButton(id = "castSelectedSpell",
        description = "${WizardBattles:menu#cast-selected-spell}", mode = ActivateMode.PRESS, category = "inventory")
@DefaultBinding(type = InputType.KEY, id = Keyboard.KeyId.F)
public class CastSpellButton extends BindButtonEvent {
}
