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

package net.minepass.gs.mt.wrapper.parsers;

import net.minepass.api.gameserver.MPPlayer;
import net.minepass.gs.mt.wrapper.EventParser;
import net.minepass.gs.mt.wrapper.MP_MinetestWrapper;

import java.util.UUID;
import java.util.regex.Matcher;

public class PlayerLoginEventParser extends EventParser {

    public PlayerLoginEventParser(MP_MinetestWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected String getPatternString(String version) {
        return "(?<name>[^ ]+).*joins game";
    }

    @Override
    protected Status run(Matcher m) {
        try {
            // In case we're going to kick the player, give time for the login to complete
            // so that we avoid a Broken Pipe message to the client.
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            // Ignore.
        }

        String playerLoginName = m.group("name");
        wrapper.getLogger().debug("Player login event: ".concat(playerLoginName), this);
        MPPlayer player = wrapper.getMinepass().getPlayerByName(playerLoginName);

        UUID playerUUID = null;
        if (player != null) {
            playerUUID = UUID.fromString(player.getId().toString());
        } else {
            playerUUID = UUID.randomUUID();
        }

        // Since UUIDs require data store search, cache mapping bi-directionally.
        getState().playerAuthNames.put(playerUUID, playerLoginName);
        getState().playerAuthUUIDs.put(playerLoginName, playerUUID);

        // Update current players.
        getState().currentPlayers.put(playerLoginName, playerUUID);

        return Status.HANDLED;
    }

    @Override
    protected boolean isEnabled() {
        return getState().minepassStarted;
    }
}
