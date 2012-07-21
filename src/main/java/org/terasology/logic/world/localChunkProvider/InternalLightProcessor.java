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

package org.terasology.logic.world.localChunkProvider;

import org.terasology.math.Side;
import org.terasology.model.blocks.Block;
import org.terasology.teraspout.TeraChunk;

/**
 * @author Immortius
 */
public class InternalLightProcessor {

    public static void generateInternalLighting(TeraChunk chunk) {
        int top = TeraChunk.SIZE_Y - 1;

        short[] tops = new short[TeraChunk.SIZE_X * TeraChunk.SIZE_Z];
        short[] partLit = new short[TeraChunk.SIZE_X * TeraChunk.SIZE_Z];

        // Tunnel light down
        for (int x = 0; x < TeraChunk.SIZE_X; x++) {
            for (int z = 0; z < TeraChunk.SIZE_Z; z++) {
                int y = top;
                for (; y >= 0; y--) {
                    Block block = chunk.getBlock(x,y,z);
                    if (block.isTranslucent() && !block.isLiquid()) {
                        chunk.setSunlight(x, y, z, TeraChunk.MAX_LIGHT);
                    } else {
                        break;
                    }
                }
                tops[x + TeraChunk.SIZE_X * z] = (short) y;
            }
        }

        for (int x = 0; x < TeraChunk.SIZE_X; x++) {
            for (int z = 0; z < TeraChunk.SIZE_Z; z++) {
                if (tops[x + TeraChunk.SIZE_X * z] < top) {
                    spreadSunlightInternal(chunk, x, tops[x + TeraChunk.SIZE_X * z] + 1, z);
                }
                for (int y = top; y >= 0; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    if (y > tops[x + TeraChunk.SIZE_X * z] && ((x > 0 && tops[(x - 1) + TeraChunk.SIZE_X * z] >= y) ||
                            (x < TeraChunk.SIZE_X - 1 && tops[(x + 1) + TeraChunk.SIZE_X * z] >= y) ||
                            (z > 0 && tops[x + TeraChunk.SIZE_X * (z - 1)] >= y) ||
                            (z < TeraChunk.SIZE_Z - 1 && tops[x + TeraChunk.SIZE_X * (z + 1)] >= y))) {
                        spreadSunlightInternal(chunk, x, y, z);
                    }
                    if (block.getLuminance() > 0) {
                        chunk.setLight(x, y, z, block.getLuminance());
                        spreadLightInternal(chunk, x, y, z);
                    }
                }
            }
        }
    }

    private static void spreadLightInternal(TeraChunk chunk, int x, int y, int z) {
        byte lightValue = chunk.getLight(x, y, z);
        if (lightValue <= 1) return;

        // TODO: use custom bounds checked iterator for this
        for (Side adjDir : Side.values()) {
            int adjX = x + adjDir.getVector3i().x;
            int adjY = y + adjDir.getVector3i().y;
            int adjZ = z + adjDir.getVector3i().z;
            if (chunk.isInBounds(adjX, adjY, adjZ)) {
                byte adjLightValue = chunk.getLight(adjX, adjY, adjZ);

                if (adjLightValue < lightValue - 1 && chunk.getBlock(adjX, y, adjZ).isTranslucent()) {
                    chunk.setLight(adjX, adjY, adjZ, (byte) (lightValue - 1));
                    spreadLightInternal(chunk, adjX, adjY, adjZ);
                }
            }
        }
    }

    private static void spreadSunlightInternal(TeraChunk chunk, int x, int y, int z) {
        byte lightValue = chunk.getSunlight(x, y, z);

        if (y > 0 && chunk.getSunlight(x, y - 1, z) < lightValue - 1 && chunk.getBlock(x, y - 1, z).isTranslucent()) {
            chunk.setSunlight(x, y - 1, z, (byte) (lightValue - 1));
            spreadSunlightInternal(chunk, x, y - 1, z);
        }

        if (y < TeraChunk.SIZE_Y && lightValue < TeraChunk.MAX_LIGHT && chunk.getSunlight(x, y + 1, z) < lightValue - 1 && chunk.getBlock(x, y + 1, z).isTranslucent()) {
            chunk.setSunlight(x, y + 1, z, (byte) (lightValue - 1));
            spreadSunlightInternal(chunk, x, y + 1, z);
        }

        if (lightValue <= 1) return;

        for (Side adjDir : Side.horizontalSides()) {
            int adjX = x + adjDir.getVector3i().x;
            int adjZ = z + adjDir.getVector3i().z;

            if (chunk.isInBounds(adjX, y, adjZ)) {
                byte adjLightValue = chunk.getSunlight(adjX, y, adjZ);
                if (adjLightValue < lightValue - 1 && chunk.getBlock(adjX, y, adjZ).isTranslucent()) {
                    chunk.setSunlight(adjX, y, adjZ, (byte) (lightValue - 1));
                    spreadSunlightInternal(chunk, adjX, y, adjZ);
                }
            }
        }
    }
}
