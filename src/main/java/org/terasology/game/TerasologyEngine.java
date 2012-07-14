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

import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.GL_NORMALIZE;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glViewport;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.lwjgl.LWJGLException;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GLContext;
import org.spout.api.gamestate.GameState;
import org.spout.engine.Arguments;
import org.spout.engine.SpoutClient;
import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.asset.loaders.GLSLShaderLoader;
import org.terasology.asset.loaders.MaterialLoader;
import org.terasology.asset.loaders.ObjMeshLoader;
import org.terasology.asset.loaders.OggSoundLoader;
import org.terasology.asset.loaders.OggStreamingSoundLoader;
import org.terasology.asset.loaders.PNGTextureLoader;
import org.terasology.asset.sources.ClasspathSource;
import org.terasology.logic.manager.AssetManager;
import org.terasology.logic.manager.AudioManager;
import org.terasology.logic.manager.Config;
import org.terasology.logic.manager.FontManager;
import org.terasology.logic.manager.GroovyManager;
import org.terasology.logic.manager.PathManager;
import org.terasology.logic.manager.ShaderManager;
import org.terasology.logic.manager.VertexBufferObjectManager;
import org.terasology.model.blocks.management.BlockManager;
import org.terasology.model.blocks.management.BlockManifestor;
import org.terasology.model.shapes.BlockShapeManager;
import org.terasology.performanceMonitor.PerformanceMonitor;

import com.google.common.collect.Lists;

/**
 * @author Immortius
 */
public class TerasologyEngine extends SpoutClient {

    private Logger logger = Logger.getLogger(getClass().getName());

    private Deque<GameState> stateStack = new ArrayDeque<GameState>();
    private boolean initialised;
    private boolean running;
    private boolean disposed;
    private List<StateChangeFunction> pendingStateChanges = Lists.newArrayList();

    private GameState state;
    private Timer timer;
    private final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

    public TerasologyEngine() {
    	super();
    }

    @Override
    public void init(Arguments args) {
    	super.init(args);
    }
    
    @Override
    public void start(boolean checkWorlds) {
    	super.start(checkWorlds);
    	scheduler.startRenderThread();
    }
    
    @Override
    public void initRenderer() {
        if (initialised) {
            return;
        }
        initLogger();
        logger.log(Level.INFO, "Initializing TeraSpout...");

        initDisplay();
        initOpenGL();
        initOpenAL();
        initControls();
        initManagers();
        initTimer(); // Dependant on LWJGL
        initialised = true;
    }

    private void initLogger() {
        if (LWJGLUtil.DEBUG) {
            System.setOut(new PrintStream(System.out) {
                @Override
                public void print(final String message) {
                    Logger.getLogger("").info(message);
                }
            });
            System.setErr(new PrintStream(System.err) {
                @Override
                public void print(final String message) {
                    Logger.getLogger("").severe(message);
                }
            });
        }
        File dirPath = PathManager.getInstance().getLogPath();

        if (!dirPath.exists()) {
            if (!dirPath.mkdirs()) {
                return;
            }
        }

        try {
            FileHandler fh = new FileHandler(new File(dirPath, "Terasology.log").getAbsolutePath(), true);
            fh.setLevel(Level.INFO);
            fh.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(fh);
        } catch (IOException ex) {
            logger.log(Level.WARNING, ex.toString(), ex);
        }
    }

    public void run(GameState initialState) {
        if (!initialised) {
            throw new IllegalStateException("Game not initialized to run a GameState!");
        }
        changeState(initialState);
        running = true;
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        CoreRegistry.put(TerasologyEngine.class, this);

        cleanup();
    }

    @Override
    public void stop() {
        running = false;
            disposed = true;
            initialised = false;
            AudioManager.getInstance().destroy();
            Mouse.destroy();
            Keyboard.destroy();
            Display.destroy();
            super.stop();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public void changeState(GameState newState) {
        if (running) {
            pendingStateChanges.add(new ChangeState(newState));
        } else {
            doPurgeStates();
            doPushState(newState);
        }
    }

    public void pushState(GameState newState) {
        if (running) {
            pendingStateChanges.add(new PushState(newState));
        } else {
            doPushState(newState);
        }
    }

    public void popState() {
        if (running) {
            pendingStateChanges.add(new PopState());
        } else {
            doPopState();
        }
    }

    public void submitTask(final String name, final Runnable task) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                PerformanceMonitor.startThread(name);
                try {
                    task.run();
                } catch (RejectedExecutionException e) {
                    logger.log(Level.SEVERE, "Thread submitted after shutdown requested: " + name);
                } finally {
                    PerformanceMonitor.endThread(name);
                }
            }
        });
    }

    public int getActiveTaskCount() {
        return threadPool.getActiveCount();
    }

    private void addLibraryPath(File libPath) {
        try {
            String envPath = System.getProperty("java.library.path");
            if (envPath == null || envPath.isEmpty()) {
                System.setProperty("java.library.path", libPath.getAbsolutePath());
            } else {
                System.setProperty("java.library.path", envPath + File.pathSeparator + libPath.getAbsolutePath());
            }

            final Field usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");
            usrPathsField.setAccessible(true);

            List<String> paths = new ArrayList<String>(Arrays.asList((String[]) usrPathsField.get(null)));

            if (paths.contains(libPath)) {
                return;
            }

            paths.add(0, libPath.getAbsolutePath());  // Add to beginning, to override system libraries

            usrPathsField.set(null, paths.toArray(new String[paths.size()]));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't link static libraries. " + e.toString(), e);
            System.exit(1);
        }
    }

    private void initOpenAL() {
        // TODO: Put in registry
        AudioManager.getInstance().initialize();
    }

    private void initDisplay() {
        try {
            if (Config.getInstance().isFullscreen()) {
                Display.setDisplayMode(Display.getDesktopDisplayMode());
                Display.setFullscreen(true);
            } else {
                Display.setDisplayMode(Config.getInstance().getDisplayMode());
                Display.setResizable(true);
            }

            Display.setTitle("Terasology" + " | " + "Pre Alpha");
            Display.create(Config.getInstance().getPixelFormat());
        } catch (LWJGLException e) {
            logger.log(Level.SEVERE, "Can not initialize graphics device.", e);
            System.exit(1);
        }
    }

    private void initOpenGL() {
        checkOpenGL();
        resizeViewport();
        initOpenGLParams();
    }

    private void checkOpenGL() {
        boolean canRunGame = GLContext.getCapabilities().OpenGL20
                & GLContext.getCapabilities().OpenGL11
                & GLContext.getCapabilities().OpenGL12
                & GLContext.getCapabilities().OpenGL14
                & GLContext.getCapabilities().OpenGL15;

        if (!canRunGame) {
            logger.log(Level.SEVERE, "Your GPU driver is not supporting the mandatory versions of OpenGL. Considered updating your GPU drivers?");
            System.exit(1);
        }

    }

    private void resizeViewport() {
        glViewport(0, 0, Display.getWidth(), Display.getHeight());
    }

    public void initOpenGLParams() {
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_NORMALIZE);
        glDepthFunc(GL_LEQUAL);
    }

    private void initControls() {
        try {
            Keyboard.create();
            Keyboard.enableRepeatEvents(true);
            Mouse.create();
            Mouse.setGrabbed(true);
        } catch (LWJGLException e) {
            logger.log(Level.SEVERE, "Could not initialize controls.", e);
            System.exit(1);
        }
    }

    private void initManagers() {
        CoreRegistry.put(GroovyManager.class, new GroovyManager());
        AssetManager.getInstance().register(AssetType.MESH, "obj", new ObjMeshLoader());
        AssetManager.getInstance().register(AssetType.MUSIC, "ogg", new OggStreamingSoundLoader());
        AssetManager.getInstance().register(AssetType.SOUND, "ogg", new OggSoundLoader());
        AssetManager.getInstance().register(AssetType.TEXTURE, "png", new PNGTextureLoader());
        AssetManager.getInstance().register(AssetType.SHADER, "glsl", new GLSLShaderLoader());
        AssetManager.getInstance().register(AssetType.MATERIAL, "mat", new MaterialLoader());
        AssetManager.getInstance().addAssetSource(new ClasspathSource("engine", getClass().getProtectionDomain().getCodeSource(), "org/terasology/data"));
        // TODO: Shouldn't be setting up the block/block shape managers here (do on transition to StateSinglePlayer)
        BlockShapeManager.getInstance().reload();
        BlockManifestor manifestor = new BlockManifestor(BlockManager.getInstance());

        try {
            manifestor.loadConfig();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load block definitions", e);
            System.exit(-1);
        }

        for (AssetUri uri : AssetManager.list(AssetType.SHADER)) {
            AssetManager.load(uri);
        }

        for (AssetUri uri : AssetManager.list(AssetType.MATERIAL)) {
            AssetManager.load(uri);
        }

        // TODO: This has to occur after the BlockManager has been created, so that texture:engine:terrain exists. Fix this.
        ShaderManager.getInstance();
        VertexBufferObjectManager.getInstance();
        FontManager.getInstance();

    }

    private void initTimer() {
        timer = new Timer();
        CoreRegistry.put(Timer.class, timer);
    }

    private void cleanup() {
        logger.log(Level.INFO, "Shutting down Terasology...");
        Config.getInstance().saveConfig(new File(PathManager.getInstance().getWorldPath(), "last.cfg"));
        doPurgeStates();
        terminateThreads();
    }

    private void terminateThreads() {
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, e.toString(), e);
        }
    }

    @Override
    public void render(float dt) {
        // Only process rendering and updating once a second
        // TODO: Add debug config setting to run even if display inactive
        if (!Display.isActive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, e.toString(), e);
            }
            
            Display.processMessages();
            return;
        }

        processStateChanges();
        state = stateStack.peek();

        if (state == null) {
            stop();
            return;
        }

        timer.tick();

        PerformanceMonitor.startActivity("Render");
        state.onRender(timer.getDelta());
        Display.update();
        Display.sync(60);
        PerformanceMonitor.endActivity();

        PerformanceMonitor.startActivity("Audio");
        AudioManager.getInstance().update();
        PerformanceMonitor.endActivity();

        PerformanceMonitor.rollCycle();
        PerformanceMonitor.startActivity("Other");

        if (Display.wasResized())
            resizeViewport();
    }

    private void processStateChanges() {
        for (StateChangeFunction func : pendingStateChanges) {
            func.enact();
        }
        pendingStateChanges.clear();
    }

    private void doPurgeStates() {
        while (!stateStack.isEmpty()) {
            doPopState();
        }
    }

    private void doPopState() {
        GameState oldState = stateStack.pop();
        oldState.unloadResources();
        if (!stateStack.isEmpty()) {
            stateStack.peek().loadResources();
        }
    }

    private void doPushState(GameState newState) {
        if (!stateStack.isEmpty()) {
            stateStack.peek().unloadResources();
        }
        stateStack.push(newState);
        newState.initialize();
        newState.loadResources();
    }

    private interface StateChangeFunction {
        void enact();
    }

    private class ChangeState implements StateChangeFunction {
        public GameState newState;

        public ChangeState(GameState newState) {
            this.newState = newState;
        }

        @Override
        public void enact() {
            doPurgeStates();
            doPushState(newState);
        }
    }

    private class PushState implements StateChangeFunction {
        public GameState newState;

        public PushState(GameState newState) {
            this.newState = newState;
        }

        @Override
        public void enact() {
            doPushState(newState);
        }
    }

    private class PopState implements StateChangeFunction {

        public PopState() {
        }

        @Override
        public void enact() {
            doPopState();
		}
	}
}
