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
package org.terasology.rendering.world;

import java.util.Set;

import org.spout.api.geo.World;
import org.terasology.game.CoreRegistry;
import org.terasology.game.TerasologyEngine;
import org.terasology.logic.manager.Config;
import org.terasology.logic.world.WorldProvider;
import org.terasology.logic.world.WorldView;
import org.terasology.rendering.primitives.ChunkMesh;
import org.terasology.rendering.primitives.ChunkTessellator;
import org.terasology.teraspout.TeraChunk;

import com.google.common.collect.Sets;

/**
 * Provides the mechanism for updating and generating chunks.
 *
 * @author Benjamin Glatzel <benjamin.glatzel@me.com>
 */
public final class ChunkUpdateManager {

    public enum UPDATE_TYPE {
        DEFAULT, PLAYER_TRIGGERED
    }

    /* CONST */
    private static final int MAX_THREADS = Config.getInstance().getMaxThreads();

    /* CHUNK UPDATES */
    private static final Set<TeraChunk> currentlyProcessedChunks = Sets.newHashSet();

    private final ChunkTessellator tessellator;
    private final World worldProvider;

    public ChunkUpdateManager(ChunkTessellator tessellator, World worldProvider) {
        this.tessellator = tessellator;
        this.worldProvider = worldProvider;
    }

    /**
     * Updates the given chunk using a new thread from the thread pool. If the maximum amount of chunk updates
     * is reached, the chunk update is ignored. Chunk updates can be forced though.
     *
     * @param chunk The chunk to update
     * @param type  The chunk update type
     * @return True if a chunk update was executed
     */
    // TODO: Review this system
    public boolean queueChunkUpdate(TeraChunk chunk, final UPDATE_TYPE type) {

        if (!currentlyProcessedChunks.contains(chunk) && (currentlyProcessedChunks.size() < MAX_THREADS || type != UPDATE_TYPE.DEFAULT)) {
            executeChunkUpdate(chunk);
            return true;
        }

        return false;
    }

    private void executeChunkUpdate(final TeraChunk c) {
        currentlyProcessedChunks.add(c);

        // Create a new thread and start processing
        Runnable r = new Runnable() {
            @Override
            public void run() {
                ChunkMesh[] newMeshes = new ChunkMesh[WorldRenderer.VERTICAL_SEGMENTS];
//                WorldView worldView = worldProvider.getWorldViewAround(c.getPos());
                WorldView worldView = null; // TODO use chunkmodel somehow
                if (worldView != null) {
                    c.setDirty(false);
                    for (int seg = 0; seg < WorldRenderer.VERTICAL_SEGMENTS; seg++) {
                        newMeshes[seg] = tessellator.generateMesh(worldView, c.getPos(), TeraChunk.SIZE_Y / WorldRenderer.VERTICAL_SEGMENTS, seg * (TeraChunk.SIZE_Y / WorldRenderer.VERTICAL_SEGMENTS));
                    }

                    c.setPendingMesh(newMeshes);

                }
                currentlyProcessedChunks.remove(c);
            }
        };

        CoreRegistry.get(TerasologyEngine.class).submitTask("Chunk Update", r);
    }

}
