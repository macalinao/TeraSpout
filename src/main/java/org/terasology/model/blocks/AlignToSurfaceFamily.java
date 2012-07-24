package org.terasology.model.blocks;

import org.terasology.math.Side;
import org.terasology.teraspout.TeraBlock;

import java.util.EnumMap;

/**
 * @author Immortius <immortius@gmail.com>
 */
public class AlignToSurfaceFamily implements BlockFamily {
    String _name;
    EnumMap<Side, TeraBlock> _blocks = new EnumMap<Side, TeraBlock>(Side.class);
    TeraBlock _archetype;

    /**
     * @param name   The name for the block group.
     * @param blocks The set of blocks that make up the group. Front, Back, Left and Right must be provided - the rest is ignored.
     */
    public AlignToSurfaceFamily(String name, EnumMap<Side, TeraBlock> blocks) {
        _name = name;
        for (Side side : Side.values()) {
            TeraBlock block = blocks.get(side);
            if (block != null) {
                _blocks.put(side, block);
                block.withBlockFamily(this);
            }
        }
        if (_blocks.containsKey(Side.TOP)) {
            _archetype = _blocks.get(Side.TOP);
        } else {
            _archetype = _blocks.get(Side.FRONT);
        }
    }

    public String getTitle() {
        return _name;
    }

    public short getBlockIdFor(Side attachmentSide, Side direction) {
        TeraBlock block = getBlockFor(attachmentSide, direction);
        return (block != null) ? block.getId() : 0;
    }

    public TeraBlock getBlockFor(Side attachmentSide, Side direction) {
        return _blocks.get(attachmentSide);
    }

    public TeraBlock getArchetypeBlock() {
        return _archetype;
    }
}
