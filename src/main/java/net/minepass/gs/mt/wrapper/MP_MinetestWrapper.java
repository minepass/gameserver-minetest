/*
 *  This file is part of MinePass, licensed under the MIT License (MIT).
 *
 *  Copyright (c) MinePass.net <http://www.minepass.net>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package net.minepass.gs.mt.wrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

import net.minepass.api.gameserver.MPAsciiArt;
import net.minepass.api.gameserver.MPConfigException;
import net.minepass.api.gameserver.MPStartupException;
import net.minepass.api.gameserver.embed.solidtx.TxLog;
import net.minepass.api.gameserver.embed.solidtx.TxStack;

/**
 * MinePass wrapper for Minetest.
 * <p>
 * This wrapper facilities the background syncing of MinePass authorization
 * data by creating secondary threads before launching the Minetest server.
 * <p>
 * The server binary will be autodetected if possible, but can be specified
 * manually via the --server option.
 * <p>
 * The world path should be given with the --world option.
 * <p>
 * Other command line parameters passed to the wrapper are forwarded to the
 * vanilla server at startup.
 */
public class MP_MinetestWrapper {

    static final String operatingSystem = System.getProperty("os.name").toLowerCase();
    static final String configFileName = "minepass.config";

    // Main
    // ------------------------------------------------------------------------------------------------------------- //

    public static void main(String[] args) {
        String worldPath = null;
        String serverBinary = getDefaultServerBinary();
        boolean createWorld = false;

        ArrayList<String> serverArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--server":
                    if (i + 1 < args.length)
                        serverBinary = args[++i];
                    break;
                case "--world":
                    if (i + 1 < args.length)
                        worldPath = args[++i];
                    break;
                case "--ignore-missing-world":
                    createWorld = true;
                    break;
                default:
                    serverArgs.add(a);
            }
        }

        MP_MinetestWrapper wrapper = new MP_MinetestWrapper(serverBinary, worldPath, createWorld);

        // Initialize MinePass, and perform initial sync (if necessary).
        if (wrapper.initMinePass()) {
            // Launch server and console managers.
            wrapper.launchManagers();

            // Delay to make the wrapper initial log output more visible, and give the managers time to initialize.
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                return;
            }

            // Launch minetest server.
            wrapper.launchServer(serverArgs.toArray(new String[serverArgs.size()]));
            System.out.println("Minetest server exited.");
            wrapper.getServerManager().stopMinePass();
        }
    }

    protected static String getDefaultServerBinary() {
        if (operatingSystem.contains("mac")) {
            return "/Applications/minetest.app/Contents/MacOS/minetest";
        } else if (operatingSystem.contains("win")) {
            return "minetest.exe";
        } else {
            return "minetest";
        }
    }

    // Wrapper Instance
    // ------------------------------------------------------------------------------------------------------------- //

    private TxLog logger;
    private Boolean debug;
    private String wrapperVersion;
    private MinePassMT minepass;
    private ConsoleManager consoleManager;
    private ServerManager serverManager;
    private final CommonState state = new CommonState();

    public MP_MinetestWrapper(String minetestBinary, String worldPath, boolean createWorld) {
        this.wrapperVersion = properties.getProperty("version");
        TxLog.log(TxLog.Level.INFO, String.format("MinePass Wrapper (%s) for Minetest", wrapperVersion));
        TxLog.log(TxLog.Level.INFO, String.format("+ Server bin: %s", minetestBinary));
        TxLog.log(TxLog.Level.INFO, String.format("+ World path: %s", worldPath));

        if (minetestBinary == null) {
            throw new RuntimeException("Minetest binary not detected. Please use --server argument.");
        }
        File minetestBinaryFile = new File(minetestBinary);
        if (!minetestBinaryFile.exists() || !minetestBinaryFile.canExecute()) {
            throw new RuntimeException("Minetest binary does not exist, or is not executable.");
        }

        if (worldPath == null) {
            throw new RuntimeException("Minetest world path not given. Please use --world argument.");
        }
        File worldPathFile = new File(worldPath);
        if (!worldPathFile.isDirectory() || !worldPathFile.canWrite()) {
            throw new RuntimeException("World path does not exist, or is not writable.");
        }

        if (!createWorld) {
            File worldPathDataFile = new File(worldPath + File.separator + "world.mt");
            if (!worldPathDataFile.exists()) {
                throw new RuntimeException("World path does not contain existing Minetest world.");
            }
        }

        loadConfig(worldPath);

        getState().minetestBinary = minetestBinary;
        getState().minetestWorldPath = worldPath;
    }

    /**
     * Ensure MinePass has a valid configuration and perform an initial sync if needed.
     * <p>
     * An initial sync is only required following software updates, otherwise MinePass
     * data is transmitted asynchronously and the server is unaffected by any possible
     * network conditions.
     */
    private boolean initMinePass() {
        try {
            debug = config.getProperty("debug_enabled", "false").equals("true");

            if (debug) {
                TxStack.debug = true;
            }

            MTConfig mtc = new MTConfig(getState().minetestWorldPath);
            mtc.variant = "MTWrapper ".concat(wrapperVersion);
            mtc.api_host = config.getProperty("setup_api_host");
            mtc.server_uuid = config.getProperty("setup_server_id");
            mtc.server_secret = config.getProperty("setup_server_secret");

            /**
             * The MinePass network stack is built upon SolidTX, an MIT licensed project
             * developed in collaboration with BinaryBabel OSS.
             *
             * The source code for the MinePass game server stack is available at:
             *   https://github.com/minepass/gameserver-core
             *
             * The source code and documentation for SolidTX is available at:
             *   https://github.com/org-binbab/solid-tx
             *
             */
            this.minepass = new MinePassMT(mtc);
            this.logger = minepass.log;
            minepass.setContext(this);

            logger.info("MinePass Core Version: " + minepass.getVersion(), null);
            logger.info("MinePass API Endpoint: " + mtc.api_host, null);
            logger.info("MinePass World Server UUID: " + minepass.getServerUUID(), null);
        } catch (MPConfigException e) {
            e.printStackTrace();
            for (String x : MPAsciiArt.getNotice("Configuration Update Required")) {
                TxLog.log(TxLog.Level.INFO, x);
            }
            TxLog.log(TxLog.Level.WARN, "Run the server configuration wizard at http://minepass.net");
            TxLog.log(TxLog.Level.WARN, "Then paste the configuration into minepass.config (in your world directory)");
            return false;
        } catch (MPStartupException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Launch the primary wrapper managers in secondary threads.
     *
     * @see ConsoleManager
     * @see ServerManager
     */
    private void launchManagers() {
        if (minepass == null) {
            return;
        }

        this.consoleManager = new ConsoleManager(this);
        Thread consoleThread = new Thread(consoleManager, "MPConsole");
        consoleThread.setDaemon(true);
        consoleThread.start();

        this.serverManager = new ServerManager(this);
        Thread controlThread = new Thread(serverManager, "MinePass");
        controlThread.setDaemon(true);
        controlThread.start();
    }

    /**
     * Launch the vanilla server with the provided args.
     *
     * @param args
     */
    private void launchServer(String[] args) {
        if (minepass == null) {
            return;
        }

        ArrayList<String> argList = new ArrayList<>();
        argList.add(getState().minetestBinary);
        argList.add("--server");
        argList.add("--world");
        argList.add(getState().minetestWorldPath);

        Collections.addAll(argList, args);

        try {
            ProcessBuilder pb = new ProcessBuilder(argList);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            getServerManager().setServerProcess(p);
            p.waitFor();
        } catch (IOException e) {
            throw new RuntimeException("Failed to invoke server startup", e);
        } catch (InterruptedException e) {
            // Ignore.
        }
    }

    public TxLog getLogger() {
        return logger;
    }

    public MinePassMT getMinepass() {
        return minepass;
    }

    public ConsoleManager getConsoleManager() {
        return consoleManager;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public CommonState getState() {
        return state;
    }

    public String getWrapperVersion() {
        return wrapperVersion;
    }

    public boolean getDebug() {
        return debug;
    }

    // Configuration
    // ------------------------------------------------------------------------------------------------------------- //

    public Properties config;

    private void loadConfig(String worldPath) {
        File configFile = new File(worldPath + File.separator + configFileName);
        InputStream configFileInput;

        try {
            configFileInput = new FileInputStream(configFile);
        } catch (FileNotFoundException e) {
            // Use default config file from jar resource.
            configFileInput = MP_MinetestWrapper.class.getResourceAsStream("/config.properties");
        }

        config = new Properties();
        try {
            config.load(configFileInput);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read properties file", e);
        } finally {
            closeQuietly(configFileInput);
        }

        // Save default config (if needed).
        if (!configFile.exists()) {
            try (OutputStream configFileOutput = new FileOutputStream(configFile)) {
                config.store(configFileOutput, "MinePass Configuration");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static final Properties properties;

    static {
        InputStream propertiesInput = MP_MinetestWrapper.class.getResourceAsStream("/wrapper.properties");

        properties = new Properties();
        try {
            properties.load(propertiesInput);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read properties file", e);
        } finally {
            closeQuietly(propertiesInput);
        }
    }

    // IO Helpers
    // ------------------------------------------------------------------------------------------------------------- //

    /**
     * Close InputStream without extra try/catch.
     *
     * @param input the input to close
     */
    static public void closeQuietly(InputStream input) {
        try {
            if (input != null) {
                input.close();
            }
        } catch (IOException ioe) {
            // Ignore.
        }
    }

    /**
     * Close OutputStream without extra try/catch.
     *
     * @param output the output to close
     */
    static public void closeQuietly(OutputStream output) {
        try {
            if (output != null) {
                output.close();
            }
        } catch (IOException ioe) {
            // Ignore.
        }
    }
}
