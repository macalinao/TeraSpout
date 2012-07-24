package org.terasology.teraspout;

import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TShortObjectHashMap;

import java.util.WeakHashMap;

import org.spout.api.Spout;
import org.spout.api.material.BlockMaterial;
import org.spout.api.material.MaterialRegistry;
import org.spout.engine.world.SpoutChunk;
import org.terasology.game.TerasologyEngine;

/**
 * TeraSpout. Links server-side objects with client concepts.
 * 
 * @author simplyianm
 * 
 *         TODO!!!
 * 
 */
public class TeraSpout {
	private static TeraSpout _instance;

	private WeakHashMap<SpoutChunk, TeraChunk> chunks = new WeakHashMap<SpoutChunk, TeraChunk>();
	private TeraBlock[] blocks = new TeraBlock[1 << 16]; // store the blocks by
															// id

	private final TerasologyEngine engine;

	public TeraSpout(TerasologyEngine engine) {
		_instance = this;
		this.engine = engine;
	}

	public static TeraSpout getInstance() {
		return _instance;
	}

	/**
	 * Gets the TeraChunk of a SpoutChunk.
	 * 
	 * @param chunk
	 * @return
	 */
	public TeraChunk getChunk(SpoutChunk chunk) {
		TeraChunk t = chunks.get(chunk);
		if (t == null) {
			t = loadChunk(chunk);
		}
		return t;
	}

	private TeraChunk loadChunk(SpoutChunk chunk) {
		TeraChunk tc = new TeraChunk(chunk);
		return tc;
	}

	public TeraBlock getBlock(short mat) {
		TeraBlock b = blocks[mat];
		if (b == null) {
			blocks[mat] = b = loadBlock(mat);
		}
		return b;
	}

	public TeraBlock getBlock(BlockMaterial mat) {
		return getBlock(mat.getId());
	}

	private TeraBlock loadBlock(short mat) {
		return new TeraBlock((BlockMaterial) MaterialRegistry.get(mat));
	}
}
