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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.commands.AsyncCommandHelper;
import com.sk89q.worldguard.bukkit.permission.RegionPermissionModel;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.util.DomainInputResolver;
import com.sk89q.worldguard.protection.util.DomainInputResolver.UserLocatorPolicy;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class MemberCommands extends RegionCommandsBase {

    private final WorldGuardPlugin plugin;

    public MemberCommands(WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Command(aliases = {"addmember", "addmember", "addmem", "am"},
            usage = "<id> <members...>",
            flags = "w",
            desc = "Add a member to a region",
            min = 2)
    public void addMember(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(plugin, world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        id = region.getId();

        // Check permissions
        if (!getPermissionModel(sender).mayAddMembers(region)) {
            throw new CommandPermissionsException();
        }

        ArrayList<UUID> membersAsUUID = region.getMembersAsUUID();

        Iterator<String> argPlayerNameIter = new NameFunctions().getPlayersNameIter(args);

        ArrayList<String> onlineNotMemberPlayers = new ArrayList<>();
        StringBuilder onlineNotMemberPlayersString = new StringBuilder();
        StringBuilder alreadyMemberPlayersString = new StringBuilder();
        StringBuilder offlinePlayersString = new StringBuilder();

        while (argPlayerNameIter.hasNext()) {
            String argPlayerName = argPlayerNameIter.next();
            UUID argPlayerUUID = Bukkit.getOfflinePlayer(argPlayerName).getUniqueId();
            if (sender == null || getPermissionModel(sender).mayAddOfflineMembers(region) || Bukkit.getOfflinePlayer(argPlayerUUID).isOnline()) {
                if (membersAsUUID.contains(argPlayerUUID)) {
                    alreadyMemberPlayersString.append(alreadyMemberPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
                } else {
                    onlineNotMemberPlayers.add(argPlayerName);
                    onlineNotMemberPlayersString.append(onlineNotMemberPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
                }
            } else {
                offlinePlayersString.append(offlinePlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
            }
        }

        // Resolve members asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                plugin.getProfileService(), onlineNotMemberPlayers.toArray(new String[0]));
        resolver.setLocatorPolicy(UserLocatorPolicy.UUID_ONLY);

        // Then add it to the members
        ListenableFuture<DefaultDomain> future = Futures.transform(
                plugin.getExecutorService().submit(resolver),
                resolver.createAddAllFunction(region.getMembers()));

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Adding members to the region '%s' on '%s'")
                .sendMessageAfterDelay("(Please wait... querying player names...)")
                .thenRespondWith((onlineNotMemberPlayersString.length() > 0 ? "Region '%s' updated with new members " +
                                onlineNotMemberPlayersString.toString() + "." : "Region '%s' members not changed.") +
                                (alreadyMemberPlayersString.length() > 0 ? "\n" + ChatColor.RED + "Players " + alreadyMemberPlayersString.toString() + " already in members." + ChatColor.RESET : "") +
                                (offlinePlayersString.length() > 0 ? "\n" + ChatColor.RED + "Players " + offlinePlayersString.toString() + " now offline." + ChatColor.RESET : ""),
                        "Failed to add new members");
    }

    @Command(aliases = {"addowner", "addowner", "ao"},
            usage = "<id> <owners...>",
            flags = "w",
            desc = "Add an owner to a region",
            min = 2)
    public void addOwner(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world

        Player player = null;
        LocalPlayer localPlayer = null;
        if (sender instanceof Player) {
            player = (Player) sender;
            localPlayer = plugin.wrapPlayer(player);
        }

        String id = args.getString(0);

        RegionManager manager = checkRegionManager(plugin, world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        id = region.getId();

        Boolean flag = region.getFlag(DefaultFlag.BUYABLE);
        DefaultDomain owners = region.getOwners();

        if (localPlayer != null) {
            if (flag != null && flag && owners != null && owners.size() == 0) {
                if (!plugin.hasPermission(player, "worldguard.region.unlimited")) {
                    int maxRegionCount = plugin.getGlobalStateManager().get(world).getMaxRegionCount(player);
                    if (maxRegionCount >= 0 && manager.getRegionCountOfPlayer(localPlayer)
                            >= maxRegionCount) {
                        throw new CommandException("You already own the maximum allowed amount of regions.");
                    }
                }
                plugin.checkPermission(sender, "worldguard.region.addowner.unclaimed." + id.toLowerCase());
            } else {
                // Check permissions
                if (!getPermissionModel(sender).mayAddOwners(region)) {
                    throw new CommandPermissionsException();
                }
            }
        }

        ArrayList<Player> ownersAsPlayers = region.getOwnersAsPlayer();

        Iterator<String> argPlayerNameIter = new NameFunctions().getPlayersNameIter(args);

        ArrayList<String> onlineNotOwnerPlayers = new ArrayList<>();
        StringBuilder onlineNotOwnerPlayersString = new StringBuilder();
        StringBuilder alreadyOwnerPlayersString = new StringBuilder();
        StringBuilder exceededOwnerPlayersString = new StringBuilder();
        StringBuilder offlinePlayersString = new StringBuilder();

        while (argPlayerNameIter.hasNext()) {
            String argPlayerName = argPlayerNameIter.next();
            Player argPlayer = Bukkit.getPlayer(argPlayerName);
            if (argPlayer == null) {
                argPlayer = Bukkit.getOfflinePlayer(argPlayerName).getPlayer();
            }

            if (argPlayer != null) {
                int maxArgPlayerRegionCount = plugin.getGlobalStateManager().get(world).getMaxRegionCount(argPlayer);
                if (sender == null || getPermissionModel(sender).mayAddOfflineOwners(region) || argPlayer.isOnline()) {
                    LocalPlayer wrappedPlayer = plugin.wrapPlayer(argPlayer);
                    if (ownersAsPlayers.contains(argPlayer)) {
                        alreadyOwnerPlayersString.append(alreadyOwnerPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");

                    } else if (sender != null && !getPermissionModel(sender).mayAddToOwnerAbovePlayerLimit(region) && !getPermissionModel(argPlayer).mayClaimRegionsUnbounded() &&
                            maxArgPlayerRegionCount >= 0 && wrappedPlayer != null && manager.getRegionCountOfPlayer(wrappedPlayer) >= maxArgPlayerRegionCount) {
                        exceededOwnerPlayersString.append(exceededOwnerPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");

                    } else {
                        onlineNotOwnerPlayers.add(argPlayerName);
                        onlineNotOwnerPlayersString.append(onlineNotOwnerPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
                    }
                }
            } else if (sender == null || getPermissionModel(sender).mayAddOfflineMembers(region)){
                onlineNotOwnerPlayers.add(argPlayerName);
                onlineNotOwnerPlayersString.append(onlineNotOwnerPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
            }  else {
                offlinePlayersString.append(offlinePlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
            }
        }

        // Resolve owners asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                plugin.getProfileService(), onlineNotOwnerPlayers.toArray(new String[0]));
        resolver.setLocatorPolicy(UserLocatorPolicy.UUID_ONLY);

        // Then add it to the owners
        ListenableFuture<DefaultDomain> future = Futures.transform(
                plugin.getExecutorService().submit(resolver),
                resolver.createAddAllFunction(region.getOwners()));

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Adding owners to the region '%s' on '%s'")
                .sendMessageAfterDelay("(Please wait... querying player names...)")
                .thenRespondWith((onlineNotOwnerPlayersString.length() > 0 ? "Region '%s' updated with new owners " +
                        onlineNotOwnerPlayersString.toString() + "." : "Region '%s' owners not changed.") +
                        (alreadyOwnerPlayersString.length() > 0 ? "\n" + ChatColor.RED + "Players " + alreadyOwnerPlayersString.toString() + " already in owners." + ChatColor.RESET : "") +
                        (exceededOwnerPlayersString.length() > 0 ? "\n" + ChatColor.RED + "Players " + exceededOwnerPlayersString.toString() + " has the maximum number of regions." + ChatColor.RESET : "") +
                        (offlinePlayersString.length() > 0 ? "\n" + ChatColor.RED + "Players " + offlinePlayersString.toString() + " now offline." + ChatColor.RESET : ""),
                        "Failed to add new owners");
    }

    @Command(aliases = {"removemember", "remmember", "removemem", "remmem", "rm"},
            usage = "<id> <owners...>",
            flags = "aw:",
            desc = "Remove an owner to a region",
            min = 1)
    public void removeMember(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(plugin, world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayRemoveMembers(region)) {
            throw new CommandPermissionsException();
        }

        ListenableFuture<?> future;

        ArrayList<UUID> membersAsUUID = region.getMembersAsUUID();

        Iterator<String> argPlayerNameIter = new NameFunctions().getPlayersNameIter(args);

        ArrayList<UUID> memberPlayersUUID = new ArrayList<>();
        StringBuilder memberPlayersString = new StringBuilder();
        StringBuilder notMemberPlayersString = new StringBuilder();
        while (argPlayerNameIter.hasNext()) {
            String argPlayerName = argPlayerNameIter.next();

            UUID argPlayerUUID = Bukkit.getOfflinePlayer(argPlayerName).getUniqueId();

            if (membersAsUUID.contains(argPlayerUUID)) {
                memberPlayersUUID.add(argPlayerUUID);
                memberPlayersString.append(memberPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
            } else {
                notMemberPlayersString.append(notMemberPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
            }
        }

        if (memberPlayersString.length() > 0) {
            memberPlayersString.insert(0, "Region '%s' members: ").append(" successfully removed.");
        } else {
            memberPlayersString = new StringBuilder("Region '%s' members not changed.");
        }
        // TODO: Беда с UUID в том что несуществующие никнеймы не могут разрешиться и вернуться. Можно ограничить либо только реальными
        //  игроками, либо использовать списки с никами и UUID+Name. Отключить NameOnly.
        if (args.hasFlag('a')) {
            if (membersAsUUID.size() > 0) {
                memberPlayersUUID.clear();
                memberPlayersUUID.addAll(membersAsUUID);
                memberPlayersString = new StringBuilder("All region members successfully removed.");
            } else {
                memberPlayersString = new StringBuilder("The region has no members.");
            }
        } else if (args.argsLength() < 2) {
            throw new CommandException("List some names to remove, or use -a to remove all.");
        }

        // Resolve members asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                plugin.getProfileService(), new NameFunctions().playerNamesFromUUIDs(memberPlayersUUID).toArray(new String[0]));

        // Then remove it from the members
        future = Futures.transform(
                plugin.getExecutorService().submit(resolver),
                resolver.createRemoveAllFunction(region.getMembers()));

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Removing members from the region '%s' on '%s'")
                .sendMessageAfterDelay("(Please wait... querying player names...)")
                .thenRespondWith(memberPlayersString.toString() +
                        (notMemberPlayersString.length() > 0 ? ChatColor.RED + "\n" + "Players: " + notMemberPlayersString +
                                " are not members of the region '" + id + "'." + ChatColor.RESET : ""),
                        "Failed to remove members");
    }

    @Command(aliases = {"removeowner", "remowner", "ro"},
            usage = "<id> <owners...>",
            flags = "caw:",
            desc = "Remove an owner to a region",
            min = 1)
    public void removeOwner(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        Player player;
        UUID playerUUID = null;
        if (sender instanceof Player) {
            player = (Player) sender;
            playerUUID = player.getUniqueId();
        }

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(plugin, world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        RegionPermissionModel permModel = getPermissionModel(sender);
        // Check permissions
        if (!getPermissionModel(sender).mayRemoveOwners(region)) {
            throw new CommandPermissionsException();
        }

        WorldConfiguration wcfg = plugin.getGlobalStateManager().get(world);

        ListenableFuture<?> future;

        ArrayList<UUID> ownersAsUUID = region.getOwnersAsUUID();

        Iterator<String> argPlayerNameIter = new NameFunctions().getPlayersNameIter(args);

        ArrayList<UUID> ownerPlayersUUID = new ArrayList<>();
        StringBuilder ownerPlayersString = new StringBuilder();
        StringBuilder notOwnerPlayersString = new StringBuilder();
        while (argPlayerNameIter.hasNext()) {
            String argPlayerName = argPlayerNameIter.next();

            UUID argPlayerUUID = Bukkit.getOfflinePlayer(argPlayerName).getUniqueId();

            if (ownersAsUUID.contains(argPlayerUUID)) {
                ownerPlayersUUID.add(argPlayerUUID);
                ownerPlayersString.append(ownerPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
            } else {
                notOwnerPlayersString.append(notOwnerPlayersString.length() > 0 ? ", " : "").append("[").append(argPlayerName).append("]");
            }
        }

        if (ownerPlayersString.length() > 0) {
            ownerPlayersString.insert(0, "Region '%s' owners: ").append(" successfully removed.");
        } else {
            ownerPlayersString = new StringBuilder("Region '%s' owners not changed.");
        }

        if (args.hasFlag('c') ||
                (ownersAsUUID.size() > 1 &&
                    ownerPlayersUUID.containsAll(ownersAsUUID)) &&
                    (!wcfg.permForRemoveLastOwner || permModel.mayRemoveLastOwner(region))) {
            if (sender != null && wcfg.permForRemoveLastOwner && !permModel.mayRemoveLastOwner(region)) {
                throw new CommandPermissionsException();
            }

            if (ownersAsUUID.size() > 0) {
                ownerPlayersUUID.clear();
                ownerPlayersUUID.addAll(ownersAsUUID);
                ownerPlayersString = new StringBuilder("All region owners successfully removed.");
            } else {
                ownerPlayersString = new StringBuilder("The region has no owners.");
            }

        } else {
            if (args.hasFlag('a') ||
                    (ownerPlayersUUID.containsAll(ownersAsUUID)) && !permModel.mayRemoveLastOwner(region)) {
                if (playerUUID != null && ownerPlayersUUID.size() == 1 && ownerPlayersUUID.contains(playerUUID) && !permModel.mayRemoveLastOwner(region)){
                    throw new CommandException("You can't remove yourself from owner without special permission!");
                }
                ownerPlayersUUID.clear();
                ownerPlayersUUID.addAll(ownersAsUUID);
                if (playerUUID != null && ownersAsUUID.contains(playerUUID)) {
                    ownerPlayersUUID.remove(playerUUID);
                    ownerPlayersString = new StringBuilder("Other region owners successfully deleted.");
                } else if (ownersAsUUID.size() > 0) {
                    ownerPlayersUUID.remove(0);
                    ownerPlayersString = new StringBuilder("All owners of the region, except the first, successfully removed.");
                }

            } else if (args.argsLength() < 2) {
                throw new CommandException("List some names to remove, or use -a to remove all owners except yourself.");
            }
        }

        // Resolve owners asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                plugin.getProfileService(), new NameFunctions().playerNamesFromUUIDs(ownerPlayersUUID).toArray(new String[0]));
        resolver.setLocatorPolicy(UserLocatorPolicy.UUID_AND_NAME);

        // Then remove it from the owners
        future = Futures.transform(
                plugin.getExecutorService().submit(resolver),
                resolver.createRemoveAllFunction(region.getOwners()));

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Removing owners from the region '%s' on '%s'")
                .sendMessageAfterDelay("(Please wait... querying player names...)")
                .thenRespondWith(ownerPlayersString.toString() +
                        (notOwnerPlayersString.length() > 0 ? ChatColor.RED + "\n" + "Players: " + notOwnerPlayersString + " are not owners of the region '" + id + "'." + ChatColor.RESET : ""),
                        "Failed to remove owners");
    }

    @Command(aliases = {"seize", "sr"},
            usage = "[id]",
            flags = "s",
            desc = "Add you to region without owners as owner",
            min = 0, max = 1)

    public void seizeRegion(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        Player player = plugin.checkPlayer(sender);

        // Lookup the existing region
        RegionManager manager = checkRegionManager(plugin, world);
        ProtectedRegion region;

        if (!(sender instanceof Player)) {
            throw new CommandException("Only players can use this command.");
        }

        if (args.argsLength() == 0) { // Get region from where the player is
            region = checkRegionStandingIn(manager, player, false);
        } else { // Get region from the ID
            region = checkExistingRegion(manager, args.getString(0), false);
        }

        if (args.hasFlag('s') && !(getPermissionModel(sender).maySelectRegionWithoutOwners() && region.getOwners().size() == 0)) {
            setPlayerSelection(player, region);
            return;
        }

        if (!getPermissionModel(sender).maySeizeRegion()) {
            throw new CommandPermissionsException();
        }

        // Check whether the player has created too many regions
        if (!getPermissionModel(sender).mayClaimRegionsUnbounded()) {
            WorldConfiguration wcfg = plugin.getGlobalStateManager().get(player.getWorld());
            int maxRegionCount = wcfg.getMaxRegionCount(player);
            if (maxRegionCount >= 0 && manager.getRegionCountOfPlayer(plugin.wrapPlayer(player)) >= maxRegionCount) {
                throw new CommandException("You own too many regions, delete one first to seize a new one.");
            }

            if (!getPermissionModel(sender).maySeizeAnyRegion()) {
                checkClaimingSize(wcfg, region, player);
            }
        }

        setPlayerSelection(player, region);

        // Resolve owners asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                plugin.getProfileService(), new String[] { player.getName() });
        resolver.setLocatorPolicy(UserLocatorPolicy.UUID_ONLY);

        // Then add it to the owners
        ListenableFuture<DefaultDomain> future = Futures.transform(
                plugin.getExecutorService().submit(resolver),
                resolver.createAddAllFunction(region.getOwners()));

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Attempt to seize the region '%s'")
                .sendMessageAfterDelay("(Please wait... querying player names...)")
                .thenRespondWith("Successful region seize",
                        "Failed to seize region");

    }
}
