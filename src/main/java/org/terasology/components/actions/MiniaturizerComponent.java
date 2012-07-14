package org.terasology.components.actions;

import javax.vecmath.Vector3f;

import org.terasology.entitySystem.Component;
import org.terasology.logic.world.MiniatureChunk;
import org.terasology.rendering.primitives.ChunkMesh;
import org.terasology.rendering.world.BlockGrid;

/**
 * @author Benjamin Glatzel <benjamin.glatzel@me.com>
 */
public class MiniaturizerComponent implements Component {

    public static final float SCALE = 1f / 32f;

    public ChunkMesh chunkMesh;
    public float orientation;
    public Vector3f renderPosition;
    public MiniatureChunk miniatureChunk;
    public BlockGrid blockGrid = new BlockGrid();

    public void reset() {
        if (chunkMesh != null) {
            chunkMesh.dispose();
            chunkMesh = null;
        }

        orientation = 0;
        renderPosition = null;
        miniatureChunk = null;
    }

}
