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

package org.terasology.logic.world;

import java.util.logging.Logger;

import org.spout.api.geo.cuboid.Chunk;
import org.spout.api.material.BlockMaterial;
import org.spout.api.material.MaterialRegistry;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.math.Vector3i;
import org.terasology.teraspout.TeraBlock;
import org.terasology.teraspout.TeraChunk;
import org.terasology.teraspout.TeraSpout;

/**
 * @author Immortius
 */
public class WorldView {
    private static Logger logger = Logger.getLogger(WorldView.class.getName());

    private Vector3i offset;
    private Region3i chunkRegion;
    private Region3i blockRegion;
    private TeraChunk[] chunks;

    private Vector3i chunkPower;
    private Vector3i chunkSize;
    private Vector3i chunkFilterSize;

    public static WorldView createLocalView(Vector3i pos, ChunkProvider chunkProvider) {
        Region3i region = Region3i.createFromCenterExtents(pos, new Vector3i(1, 0, 1));
        return createWorldView(region, Vector3i.one(), chunkProvider);
    }

    public static WorldView createSubview(Vector3i pos, int extent, ChunkProvider chunkProvider) {
        Region3i region = TeraMath.getChunkRegionAroundBlockPos(pos, extent);
        return createWorldView(region, new Vector3i(-region.min().x, 0, -region.min().z), chunkProvider);
    }

    public static WorldView createWorldView(Region3i region, Vector3i offset, ChunkProvider chunkProvider) {
        TeraChunk[] chunks = new TeraChunk[region.size().x * region.size().z];
        for (Vector3i chunkPos : region) {
            TeraChunk chunk = chunkProvider.getChunk(chunkPos);
            if (chunk == null) {
                return null;
            }
            int index = (chunkPos.x - region.min().x) + region.size().x * (chunkPos.z - region.min().z);
            chunks[index] = chunk;
        }
        return new WorldView(chunks, region, offset);
    }

    public WorldView(TeraChunk[] chunks, Region3i chunkRegion, Vector3i offset) {
        this.chunkRegion = chunkRegion;
        this.chunks = chunks;
        this.offset = offset;
        setChunkSize(new Vector3i(Chunk.BLOCKS.SIZE, Chunk.BLOCKS.SIZE, Chunk.BLOCKS.SIZE));
    }

    public Region3i getChunkRegion() {
        return chunkRegion;
    }

    public TeraBlock getBlock(float x, float y, float z) {
        return getBlock(TeraMath.floorToInt(x + 0.5f), TeraMath.floorToInt(y + 0.5f), TeraMath.floorToInt(z + 0.5f));
    }

    public TeraBlock getBlock(Vector3i pos) {
        return getBlock(pos.x, pos.y, pos.z);
    }

    // TODO: Review
    public TeraBlock getBlock(int blockX, int blockY, int blockZ) {
        if (!blockRegion.encompasses(blockX, blockY, blockZ)) {
            return TeraSpout.getInstance().getBlock((BlockMaterial) MaterialRegistry.get(0));
        }

        int chunkIndex = relChunkIndex(blockX, blockY, blockZ);
        return chunks[chunkIndex].getBlock(TeraMath.calcBlockPos(blockX, blockY, blockZ, chunkFilterSize));
    }

    public byte getSunlight(float x, float y, float z) {
        return getSunlight(TeraMath.floorToInt(x + 0.5f), TeraMath.floorToInt(y + 0.5f), TeraMath.floorToInt(z + 0.5f));
    }

    public byte getSunlight(Vector3i pos) {
        return getSunlight(pos.x, pos.y, pos.z);
    }

    public byte getLight(float x, float y, float z) {
        return getLight(TeraMath.floorToInt(x + 0.5f), TeraMath.floorToInt(y + 0.5f), TeraMath.floorToInt(z + 0.5f));
    }

    public byte getLight(Vector3i pos) {
        return getLight(pos.x, pos.y, pos.z);
    }

    public byte getSunlight(int blockX, int blockY, int blockZ) {
        if (!blockRegion.encompasses(blockX, blockY, blockZ)) {
            return 0;
        }

        int chunkIndex = relChunkIndex(blockX, blockY, blockZ);
        return chunks[chunkIndex].getSunlight(TeraMath.calcBlockPos(blockX, blockY, blockZ, chunkFilterSize));
    }

    public byte getLight(int blockX, int blockY, int blockZ) {
        if (!blockRegion.encompasses(blockX, blockY, blockZ)) {
            return 0;
        }

        int chunkIndex = relChunkIndex(blockX, blockY, blockZ);
        return chunks[chunkIndex].getLight(TeraMath.calcBlockPos(blockX, blockY, blockZ, chunkFilterSize));
    }

    public void setLight(Vector3i pos, byte light) {
        setLight(pos.x, pos.y, pos.z, light);
    }

    public void setSunlight(Vector3i pos, byte light) {
        setSunlight(pos.x, pos.y, pos.z, light);
    }

    public void setSunlight(int blockX, int blockY, int blockZ, byte light) {
        if (blockRegion.encompasses(blockX, blockY, blockZ)) {
            int chunkIndex = relChunkIndex(blockX, blockY, blockZ);
            chunks[chunkIndex].setSunlight(TeraMath.calcBlockPos(blockX, blockY, blockZ, chunkFilterSize), light);
        }
    }

    public void setLight(int blockX, int blockY, int blockZ, byte light) {
        if (blockRegion.encompasses(blockX, blockY, blockZ)) {
            int chunkIndex = relChunkIndex(blockX, blockY, blockZ);
            chunks[chunkIndex].setLight(TeraMath.calcBlockPos(blockX, blockY, blockZ, chunkFilterSize), light);
        }
    }

    public void setDirtyAround(Vector3i blockPos) {
        for (Vector3i pos : TeraMath.getChunkRegionAroundBlockPos(blockPos, 1)) {
            chunks[pos.x + offset.x + chunkRegion.size().x * (pos.z + offset.z)].setDirty(true);
        }
    }

    public void setDirtyAround(Region3i blockRegion) {
        Vector3i minPos = new Vector3i(blockRegion.min());
        minPos.sub(1, 0, 1);
        Vector3i maxPos = new Vector3i(blockRegion.max());
        maxPos.add(1, 0, 1);

        Vector3i minChunk = TeraMath.calcChunkPos(minPos, chunkPower);
        Vector3i maxChunk = TeraMath.calcChunkPos(maxPos, chunkPower);

        for (Vector3i pos : Region3i.createFromMinMax(minChunk, maxChunk)) {
            chunks[pos.x + offset.x + chunkRegion.size().x * (pos.z + offset.z)].setDirty(true);
        }
    }

    public void lock() {
        for (TeraChunk chunk : chunks) {
            chunk.lock();
        }
    }

    public void unlock() {
        for (TeraChunk chunk : chunks) {
            chunk.unlock();
        }
    }

    public boolean isValidView() {
        for (TeraChunk chunk : chunks) {
            if (chunk.isDisposed()) {
                return false;
            }
        }
        return true;
    }

    int relChunkIndex(int x, int y, int z) {
        return TeraMath.calcChunkPosX(x, chunkPower.x) + offset.x + chunkRegion.size().x * (TeraMath.calcChunkPosZ(z, chunkPower.z) + offset.z);
    }

    public void setChunkSize(Vector3i chunkSize) {
        this.chunkSize = chunkSize;
        this.chunkFilterSize = new Vector3i(TeraMath.ceilPowerOfTwo(chunkSize.x) - 1, 0, TeraMath.ceilPowerOfTwo(chunkSize.z) - 1);
        this.chunkPower = new Vector3i(TeraMath.sizeOfPower(chunkSize.x), 0, TeraMath.sizeOfPower(chunkSize.z));

        Vector3i blockMin = new Vector3i();
        blockMin.sub(offset);
        blockMin.mult(chunkSize.x, 0, chunkSize.z);
        Vector3i blockSize = chunkRegion.size();
        blockSize.mult(chunkSize.x, chunkSize.y, chunkSize.z);
        this.blockRegion = Region3i.createFromMinAndSize(blockMin, blockSize);
    }
}
