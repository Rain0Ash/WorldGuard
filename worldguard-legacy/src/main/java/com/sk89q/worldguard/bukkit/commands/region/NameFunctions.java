/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit.commands.region;

import com.sk89q.minecraft.util.commands.CommandContext;
import org.bukkit.Bukkit;

import java.util.*;

public class NameFunctions {

    public ArrayList<String> getPlayersNameList(CommandContext args) {
        return new ArrayList<>(new LinkedHashSet<>(Arrays.asList(args.getParsedPaddedSlice(1, 0))));
    }

    public ArrayList<String> getPlayersNameList(CommandContext args, Boolean sorted) {
        ArrayList<String> argPlayerNameList = getPlayersNameList(args);
        if (sorted) {
            Collections.sort(argPlayerNameList, new Comparator<String>() {
                @Override
                public int compare(String firstString, String secondString) {
                    return firstString.compareToIgnoreCase(secondString);
                }
            });
        }
        return argPlayerNameList;
    }

    public Iterator<String> getPlayersNameIter(CommandContext args) {
        return getPlayersNameList(args).iterator();
    }

    public Iterator<String> getPlayersNameIter(CommandContext args, Boolean sorted) {
        return getPlayersNameList(args, sorted).iterator();
    }

    public String playerNameFromUUID(UUID playerUUID) {
        return Bukkit.getOfflinePlayer(playerUUID).getName();
    }

    public ArrayList<String> playerNamesFromUUIDs(Iterable<UUID> playersUUID) {
        ArrayList<String> playerNames = new ArrayList<>();
        for (UUID uuid : playersUUID) {
            String playerName = playerNameFromUUID(uuid);
            if (playerName != null) playerNames.add(playerName);
        }
        return playerNames;
    }
}
