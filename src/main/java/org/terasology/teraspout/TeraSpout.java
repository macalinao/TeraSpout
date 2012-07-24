package org.terasology.teraspout;

import java.util.HashMap;
import java.util.WeakHashMap;

import org.spout.api.material.BlockMaterial;
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
	private HashMap<BlockMaterial, TeraBlock> blocks = new HashMap<BlockMaterial, TeraBlock>();
	
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
	
	public TeraBlock getBlock(BlockMaterial mat) {
		TeraBlock b = blocks.get(mat);
		if (b == null) {
			b = loadBlock(mat);
		}
		return b;
	}
	
	private TeraBlock loadBlock(BlockMaterial mat) {
		return null; // todo
	}
}
