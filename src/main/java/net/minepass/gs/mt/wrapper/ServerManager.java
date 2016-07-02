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

import net.minepass.api.gameserver.MPAsciiArt;
import net.minepass.api.gameserver.MPWorldServerDetails;
import net.minepass.api.gameserver.embed.solidtx.TxLog;
import net.minepass.api.gameserver.embed.solidtx.TxSync;
import net.minepass.gs.mt.wrapper.parsers.PlayerLoginEventParser;
import net.minepass.gs.mt.wrapper.parsers.PlayerLogoutEventParser;
import net.minepass.gs.mt.wrapper.parsers.ServerStartEventParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ServerManager reads log output from the vanilla server, aided by the
 * MinePass Mod, which are then used to trigger MinePass events.
 * <p>
 * MinePass data syncing and scheduled tasks are started as secondary threads
 * once the ServerManager verifies that startup is completed. They are later
 * stopped when the manager detects the server is shutting down.
 *
 * @see ConsoleManager
 */
public class ServerManager implements Runnable {

    private MP_MinetestWrapper wrapper;

    private TxLog logger;
    private Process serverProcess;
    private BufferedReader serverLogReader;

    private Thread syncThread;
    private Thread scheduledTasks;

    private LinkedList<EventParser> eventParsers;
    private HashMap<String, EventParser> eventParserHold;

    public ServerManager(MP_MinetestWrapper wrapper) {
        this.wrapper = wrapper;
        this.logger = wrapper.getLogger();

        this.eventParserHold = new HashMap<>();
        this.eventParsers = new LinkedList<>();
        initEventParsers();
    }

    public void setServerProcess(Process process) {
        this.serverProcess = process;
        InputStream is = process.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        this.serverLogReader = new BufferedReader(isr);
    }

    private static Pattern serverLogPattern = Pattern.compile(
            "^(?<ts>[0-9: -]+): (?<output>(?<level>[A-Z]+)(\\[(?<thread>[a-zA-Z]+)\\]): (?<msg>.*))$"
    );

    private final String THREAD = "thread";
    private final String LEVEL = "level";
    private final String MESSAGE = "msg";
    private final String OUTPUT = "output";

    private void initEventParsers() {
        eventParsers.add(new ServerStartEventParser(wrapper));
        eventParsers.add(new PlayerLoginEventParser(wrapper));
        eventParsers.add(new PlayerLogoutEventParser(wrapper));
    }

    @Override
    public void run() {
        EventParser.Status status;
        String logOutput;
        Matcher m;

        while ((logOutput = getNextLogEvent()) != null) {
            m = serverLogPattern.matcher(logOutput);

            if (m.find()) {
                switch (m.group(LEVEL)) {
                    case "ERROR":
                        logger.error(m.group(OUTPUT), this);
                        break;
                    default:
                        logger.info(m.group(OUTPUT), this);
                }
            } else {
                logger.info("*".concat(logOutput), this);
                continue;
            }

            if (eventParserHold.containsKey(m.group(THREAD))) {
                // This log entry should be applied to an existing event on a held thread.
                //
                status = eventParserHold.get(m.group(THREAD)).acceptLogMessage(m.group(MESSAGE), true);
                if (status == EventParser.Status.HANDLED) {
                    eventParserHold.remove(m.group(THREAD));
                }
            } else {
                // Search for applicable parser.
                //
                runParsers:
                for (EventParser p : eventParsers) {
                    if (!p.isEnabled())
                        continue;

                    if (!p.filterLevel(m.group(LEVEL)))
                        continue;

                    if (!p.filterThread(m.group(THREAD)))
                        continue;

                    status = p.acceptLogMessage(m.group(MESSAGE), false);

                    switch (status) {
                        case HANDLED:
                            break runParsers;
                        case HOLD:
                            eventParserHold.put(m.group(THREAD), p);
                            break runParsers;
                    }
                }
            }
        }
    }

    public void startMinePass() {
        if (getState().minepassStarted) {
            return;
        }
        getState().minepassStarted = true;

        // Start sync thread.
        this.syncThread = new Thread(new TxSync(wrapper.getMinepass(), 10));
        syncThread.setDaemon(false);  // ensure any disk writing finishes
        syncThread.start();

        // Send server config.
        sendServerCommand("#join_url", wrapper.getMinepass().getServer().join_url);
        sendServerCommand("#founder_name", wrapper.getMinepass().getServer().founder.name);

        // Start scheduled tasks.
        this.scheduledTasks = new Thread(new MTGameserverTasks(wrapper), "MinePass");
        scheduledTasks.setDaemon(true);
        scheduledTasks.start();

        // Output MinePass logo.
        for (String x : MPAsciiArt.getLogo("System Ready")) {
            logger.info(x, null);
        }

        // Build server details.
        MPWorldServerDetails details = new MPWorldServerDetails();
        details.plugin_type = "minetest-wrapper";
        details.plugin_version = wrapper.getWrapperVersion();
        details.game_realm = "os";
        details.game_version = getState().minetestVersion;
        details.game_version_raw = String.format(
                "Minetest %s",
                getState().minetestVersion
        );

        // Assemble plugin list.
        for (Map.Entry<String,String> p : getState().plugins.entrySet()) {
            details.addPlugin(p.getKey(), p.getValue().isEmpty() ? getState().minetestVersion : p.getValue(), p.getKey());
        }

        // Import whitelist (if needed).
        if (!wrapper.getMinepass().getServer().whitelist_imported) {
            File authFileBackup = new File(getState().minetestWorldPath.concat(File.separator).concat(MinePassMT.authBackupFilename));
            details.importWhitelist(authFileBackup);
        }

        // Send server details.
        wrapper.getMinepass().sendObject(details, null);
    }

    public void stopMinePass() {
        if (syncThread != null) {
            syncThread.interrupt();
        }
        if (scheduledTasks != null) {
            scheduledTasks.interrupt();
        }
        getState().minepassStarted = false;
    }

    public void tellPlayer(String name, String message) {
        sendServerCommand("/msg", name, message);
    }

    public void kickPlayer(String name, String message) {
        sendServerCommand("/kick", name, message);
    }

    private String getNextLogEvent() {
        String s;

        try {
            while (true) {
                if (serverLogReader != null && serverLogReader.ready()) {
                    s = serverLogReader.readLine();
                    break;
                }
                Thread.sleep(200);
            }
            return s;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void sendServerCommand(String command, String... params) {
        StringBuilder sb = new StringBuilder(command);
        for (String p : params) {
            sb.append(" ").append(p);
        }
        sendServerCommand(sb.toString());
    }

    private void sendServerCommand(String command) {
        wrapper.getConsoleManager().sendCommand(command);
    }

    private CommonState getState() {
        return wrapper.getState();
    }
}
