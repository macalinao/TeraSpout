/*
 * Copyright 2012 Benjamin Glatzel <benjamin.glatzel@me.com>
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

package org.terasology.events;

import org.terasology.entitySystem.AbstractEvent;
import org.terasology.math.Vector3i;
import org.terasology.teraspout.TeraBlock;

/**
 * Event when a block has changed
 *
 * @author Immortius
 */
public class BlockChangedEvent extends AbstractEvent {
    private TeraBlock oldType;
    private TeraBlock newType;
    private Vector3i blockPosition;

    public BlockChangedEvent(Vector3i pos, TeraBlock newType, TeraBlock oldType) {
        this.blockPosition = new Vector3i(pos);
        this.oldType = oldType;
        this.newType = newType;
    }

    public Vector3i getBlockPosition() {
        return blockPosition;
    }

    public TeraBlock getOldType() {
        return oldType;
    }

    public TeraBlock getNewType() {
        return newType;
    }
}
