package org.terasology.teraspout;

import java.util.WeakHashMap;

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
	private WeakHashMap<SpoutChunk, TeraChunk> chunks = new WeakHashMap<SpoutChunk, TeraChunk>();
	private final TerasologyEngine engine;

	public TeraSpout(TerasologyEngine engine) {
		this.engine = engine;
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
		return null;
	}
}
