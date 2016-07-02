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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CommonState {

    public String minetestBinary = null;
    public String minetestVersion = null;
    public String minetestWorldPath = null;
    public boolean minepassStarted = false;

    /**
     * Store player UUIDs as presented by authenticator events.
     */
    public final ConcurrentHashMap<String,UUID> playerAuthUUIDs = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<UUID,String> playerAuthNames = new ConcurrentHashMap<>();

    /**
     * Current players per the login/logout events.
     * This is stored in [name]=uuid format since most log events use player names.
     */
    public final ConcurrentHashMap<String,UUID> currentPlayers = new ConcurrentHashMap<>();

    /**
     * Store and collate plugins. [Name=Version]
     */
    public final ConcurrentHashMap<String,String> plugins = new ConcurrentHashMap<>();

}
