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

import net.minepass.gs.mt.wrapper.EventParser;
import net.minepass.gs.mt.wrapper.MP_MinetestWrapper;

import java.util.regex.Matcher;

public class PlayerLogoutEventParser extends EventParser {

    public PlayerLogoutEventParser(MP_MinetestWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected String getPatternString(String version) {
        return "(?<name>[^ ]+).*leaves game";
    }

    @Override
    protected Status run(Matcher m) {
        String playerLogoutName = m.group("name");
        wrapper.getLogger().debug("Player logout event: ".concat(playerLogoutName), this);
        getState().currentPlayers.remove(playerLogoutName);
        return Status.HANDLED;
    }

    @Override
    protected boolean isEnabled() {
        return getState().minepassStarted;
    }
}
