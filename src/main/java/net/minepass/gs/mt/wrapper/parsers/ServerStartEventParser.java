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
import java.util.regex.Pattern;

public class ServerStartEventParser extends EventParser {

    public ServerStartEventParser(MP_MinetestWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected String getPatternString(String version) {
        return "MinePass plugin v(?<version>[0-9]+(\\.[0-9]+)+)";
    }

    @Override
    protected Status run(Matcher m) {
        if (versionCompare(wrapper.getWrapperVersion(), m.group("version")) != 0) {
            wrapper.getLogger().error(String.format(
                    "Mod/Wrapper version mismatch: %s <> %s",
                    wrapper.getWrapperVersion(),
                    m.group("version")
            ), this);
            wrapper.getLogger().error("Please update the minepass mod in your game folder.", this);
        }
        return Status.HOLD;
    }

    private static Pattern serverInfoPattern = Pattern.compile("Server: version=(?<version>[^,]+),");
    private static Pattern serverModPattern = Pattern.compile("\\[Mod\\] (?<name>.+)$");
    private static Pattern serverLoadedPattern = Pattern.compile("MinePass plugin loaded");

    @Override
    protected boolean continuedInput(String logInput) {
        Matcher m;

        // Server version.
        m = serverInfoPattern.matcher(logInput);
        if (m.find()) {
            getState().minetestVersion = m.group("version");
            return true;
        }

        // Mods.
        m = serverModPattern.matcher(logInput);
        if (m.find()) {
            getState().plugins.put(m.group("name"), "");
            return true;
        }

        // Finalize.
        m = serverLoadedPattern.matcher(logInput);
        if (m.find()) {
            getServerManager().startMinePass();
            return false;
        }

        return true;
    }

    @Override
    protected boolean isEnabled() {
        return !getState().minepassStarted;
    }
}
