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
package org.terasology.game;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.spout.api.Spout;
import org.spout.engine.SpoutClient;
import org.terasology.game.modes.StateMainMenu;
import org.terasology.logic.manager.PathManager;

import com.beust.jcommander.JCommander;

/**
 * The heart and soul of Terasology.
 * <p/>
 * TODO: Create a function returns the number of generated worlds
 * 
 * @author Benjamin Glatzel <benjamin.glatzel@me.com>
 * @author Kireev Anton <adeon.k87@gmail.com>
 */
public final class Terasology {
	private static TerasologyEngine engine;

	private Terasology() {
	}

	public static void main(String[] args) {
		try {
			boolean inJar = false;

			try {
				CodeSource cs = SpoutClient.class.getProtectionDomain()
						.getCodeSource();
				inJar = cs.getLocation().toURI().getPath().endsWith(".jar");
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
	
			if (inJar) {
				unpackLwjgl();
			}
			
			engine = new TerasologyEngine();
	
			PathManager.getInstance().determineRootPath(true);
			
			Spout.setEngine(engine);
			Spout.getFilesystem().init();
			new JCommander(engine, args);
			engine.init(args);
			engine.start();
		
//			engine.init();
//			engine.run(new StateMainMenu());
//			engine.dispose();
		} catch (Throwable t) {
			Logger.getLogger(Terasology.class.getName()).log(Level.SEVERE,
					"Uncaught Exception", t);
		}
		System.exit(0);
	}	
	
	private static void unpackLwjgl() {
		String[] files = null;
		String osPath = "";

		if(SystemUtils.IS_OS_WINDOWS) {
			files = new String[] {
					"jinput-dx8_64.dll",
					"jinput-dx8.dll",
					"jinput-raw_64.dll",
					"jinput-raw.dll",
					"jinput-wintab.dll",
					"lwjgl.dll",
					"lwjgl64.dll",
					"OpenAL32.dll",
					"OpenAL64.dll"
			};
			osPath = "windows/";
		} else if (SystemUtils.IS_OS_MAC) {
			files = new String[] {
					"libjinput-osx.jnilib",
					"liblwjgl.jnilib",
					"openal.dylib",
			};
			osPath = "mac/";
		} else if(SystemUtils.IS_OS_LINUX) {
			files = new String[] {
					"liblwjgl.so",
					"liblwjgl64.so",
					"libopenal.so",
					"libopenal64.so",
					"libjinput-linux.so",
					"libjinput-linux64.so"
			};
			osPath = "linux/";
		} else {
			Spout.getEngine().getLogger().log(Level.SEVERE, "Error loading natives of operating system type: " + SystemUtils.OS_NAME);
			return;
		}

		File cacheDir = new File(System.getProperty("user.dir"), "natives/" + osPath);
		cacheDir.mkdirs();
		for (String f : files) {
			File outFile = new File(cacheDir, f);
			if (!outFile.exists()) {
				try {
					FileUtils.copyInputStreamToFile(SpoutClient.class.getResourceAsStream("/" + f), outFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		String nativePath = cacheDir.getAbsolutePath();
		System.setProperty("org.lwjgl.librarypath", nativePath);
		System.setProperty("net.java.games.input.librarypath", nativePath);
	}

}
