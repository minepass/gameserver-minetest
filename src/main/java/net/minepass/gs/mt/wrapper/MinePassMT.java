package net.minepass.gs.mt.wrapper;

import net.minepass.api.gameserver.MPConfigException;
import net.minepass.api.gameserver.MPPlayer;
import net.minepass.api.gameserver.MPStartupException;
import net.minepass.api.gameserver.MPWorldServer;
import net.minepass.api.gameserver.MinePass;
import net.minepass.api.gameserver.embed.solidtx.core.storage.StorageManager;
import net.minepass.api.gameserver.embed.solidtx.disk.FileStorageContainer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinePassMT extends MinePass {

    static public final String authFilename = "auth.txt";
    static public final String authShadowFilename = "auth.shadow.txt";
    static public final String authBackupFilename = "auth.import.txt";
    static public final String commandFilename = "command.txt";

    static public ArrayList<String> allPrivileges = new ArrayList<>(Arrays.asList(
            "server", "shout", "fly", "password", "bring", "kick", "teleport", "ban", "noclip", "interact", "fast",
            "home", "privs", "give", "protection_bypass", "rollback", "settime", "basic_privs"
    ));

    protected String worldPath;

    protected File authFile;
    protected File authShadowFile;

    protected File commandFile;
    protected Queue<String> commandQueue;

    public MinePassMT(MTConfig config) throws MPConfigException, MPStartupException {
        super(config);

        this.worldPath = variantConfig.get("worldpath");
        authFile = new File(worldPath.concat(File.separator).concat(authFilename));
        authShadowFile = new File(worldPath.concat(File.separator).concat(authShadowFilename));
        File authFileBackup = new File(worldPath.concat(File.separator).concat(authBackupFilename));

        if (!authFile.isFile()) {
            try {
                authFile.createNewFile();
            } catch (IOException e) {
                throw new MPConfigException("Could not create auth file.");
            }
        }
        if (!authFile.canWrite()) {
            throw new MPConfigException("Auth file is not writable.");
        }

        // Backup original whitelist.
        if (!getServer().whitelist_imported) {
            if (authFile.length() > 0 && !authFileBackup.exists()) {
                try {
                    Files.copy(Paths.get(authFile.getPath()), Paths.get(authFileBackup.getPath()));
                } catch (IOException e) {
                    throw new MPStartupException("Failed to backup auth file for import.", e);
                }
            }
        }

        // Update local auth.
        updateLocalAuth();

        // Initialize command file and queue.
        commandFile = new File(worldPath.concat(File.separator).concat(commandFilename));
        commandQueue = new ConcurrentLinkedQueue<>();
    }

    public void updateLocalAuth() {
        try {
            FileWriter fw = new FileWriter(authShadowFile, false);
            fw.write(getServerAuthTxt(getServer()));
            fw.flush();
            fw.close();
            Files.copy(Paths.get(authShadowFile.getPath()), Paths.get(authFile.getPath()), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public MPPlayer getPlayerByName(String playerName) {
        for (MPPlayer player : getServer().players) {
            if (player.name.equalsIgnoreCase(playerName)) {
                return player;
            }
        }

        return null;
    }

    @Override
    protected void setupStorage(StorageManager sm) {
        sm.addContainer(new FileStorageContainer(
                jsonAdapter,
                variantConfig.get("worldpath") + File.separator + "minepass_cache.json"
        ));
    }

    protected String getServerAuthTxt(MPWorldServer server) {
        StringBuffer s = new StringBuffer();
        for (MPPlayer p : server.players) {
            if (p.realm.equalsIgnoreCase("os")) {
                s.append(getPlayerAuthTxt(p)).append("\n");
            }
        }
        for (String name : server.bypass_players.keySet()) {
            s.append(getPlayerAuthTxt(name, server.bypass_players.get(name))).append("\n");
        }
        return s.toString();
    }

    protected String getPlayerAuthTxt(MPPlayer player) {
        Pattern privPattern = Pattern.compile("mt:(?<name>.+)");
        ArrayList<String> privileges = new ArrayList<>();

        Matcher m;
        for (String p : player.privileges) {
            m = privPattern.matcher(p);
            if (m.find()) {
                if (m.group("name").equals("all")) {
                    privileges.addAll(allPrivileges);
                } else {
                    privileges.add(m.group("name"));
                }
            }
        }

        return getPlayerAuthTxt(player.name, player.secret, privileges.toArray(new String[privileges.size()]));
    }

    protected String getPlayerAuthTxt(String name, String secret, String[] privileges) {
        return String.join(":", name, secret, String.join(",", privileges));
    }

    protected String getPlayerAuthTxt(String name, String identity) {
        return String.join(":", name, identity);
    }

}
