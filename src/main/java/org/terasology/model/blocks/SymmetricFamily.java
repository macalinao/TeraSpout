package org.terasology.model.blocks;

import org.terasology.math.Side;
import org.terasology.teraspout.TeraBlock;

/**
 * The standard block group consisting of a single symmetrical block that doesn't need rotations
 *
 * @author Immortius <immortius@gmail.com>
 */
public class SymmetricFamily implements BlockFamily {

    TeraBlock block;

    public SymmetricFamily(TeraBlock block) {
        this.block = block;
        block.withBlockFamily(this);
    }

    public String getTitle() {
        return block.getTitle();
    }

    public short getBlockIdFor(Side attachmentSide, Side direction) {
        return block.getId();
    }

    public TeraBlock getBlockFor(Side attachmentSide, Side direction) {
        return block;
    }

    public TeraBlock getArchetypeBlock() {
        return block;
    }
}
