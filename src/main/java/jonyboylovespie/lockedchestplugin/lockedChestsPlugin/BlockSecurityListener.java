package jonyboylovespie.lockedchestplugin.lockedChestsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class BlockSecurityListener implements Listener, CommandExecutor
{
    private static final String BYPASS_PERMISSION = "lockedchests.admin.bypass";

    private JavaPlugin plugin;
    private NamespacedKey ownerKey;
    private NamespacedKey trustedKey;

    public BlockSecurityListener(JavaPlugin plugin)
    {
        this.plugin = plugin;
        ownerKey = new NamespacedKey(plugin, "owner");
        trustedKey = new NamespacedKey(plugin, "trusted");
        this.plugin.getCommand("trust").setExecutor(this);
        this.plugin.getCommand("lockchest").setExecutor(this);
        this.plugin.getCommand("lockinspect").setExecutor(this);
    }

    // Data management methods

    private void removeOwnership(TileState state)
    {
        state.getPersistentDataContainer().remove(ownerKey);
        state.getPersistentDataContainer().remove(trustedKey);
        state.update();
    }

    private void setOwnership(Player owner, TileState state)
    {
        setOwnership(owner.getUniqueId().toString(), state);
    }

    private void setOwnership(String owner, TileState state)
    {
        state.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner);
        state.update();
    }

    private String getOwner(TileState state)
    {
        return state.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
    }

    private void addTrustedPlayer(UUID trustedPlayer, TileState state)
    {
        UuidSetDataType type = new UuidSetDataType();
        Set<UUID> trusted = getTrustedPlayers(state);
        if (trusted == null)
        {
            trusted = new HashSet<>();
        }
        trusted.add(trustedPlayer);
        state.getPersistentDataContainer().set(trustedKey, type, trusted);
        state.update();
    }

    private Set<UUID> getTrustedPlayers(TileState state)
    {
        UuidSetDataType type = new UuidSetDataType();
        var container = state.getPersistentDataContainer();
        try
        {
            return container.get(trustedKey, type);
        }
        catch (ClassCastException | IllegalArgumentException ex)
        {
            container.remove(trustedKey);
            state.update();
            return new HashSet<>();
        }
    }

    private boolean canPlayerOpen(Player player, TileState state)
    {
        return isPlayerOwner(player.getUniqueId(), state) || isPlayerTrusted(player.getUniqueId(), state);
    }

    private boolean hasBypassPermission(Player player)
    {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    private boolean isPlayerTrusted(UUID uuid, TileState state)
    {
        Set<UUID> trustedPlayers = getTrustedPlayers(state);
        if (trustedPlayers == null) return false;
        return getTrustedPlayers(state).contains(uuid);
    }

    private boolean isPlayerOwner(UUID playerUUID, TileState state)
    {
        String owner = getOwner(state);
        if (owner == null) return false;
        UUID ownerUUID = UUID.fromString(owner);
        return ownerUUID.equals(playerUUID);
    }

    // Chest Locking/Unlocking

    private void handleLocking(Player player, TileState state)
    {
        String owner = getOwner(state);
        if (owner == null)
        {
            handleAddLock(player, state);
            return;
        }
        handleRemoveLock(player, state);
    }

    private void handleAddLock(Player player, TileState state)
    {
        String ownerUUID = getOwner(state);
        if (ownerUUID == null)
        {
            if (state.getType() == Material.CHEST && ((Chest) state).getInventory().getHolder() instanceof DoubleChest doubleChest)
            {
                setOwnership(player, (Chest) doubleChest.getLeftSide());
                setOwnership(player, (Chest) doubleChest.getRightSide());
            }
            else
            {
                setOwnership(player, state);
            }
            player.sendMessage(ChatColor.GREEN + "Locking " + getContainerType(state).toLowerCase() + ".");
            return;
        }
        if (!isPlayerOwner(player.getUniqueId(), state))
        {
            player.sendMessage(ChatColor.RED + "You cannot lock this " + getContainerType(state).toLowerCase() + ", it is locked by " + Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID)).getName());
            return;
        }
        player.sendMessage(ChatColor.RED + "This " + getContainerType(state).toLowerCase() + " is already locked by you.");
    }

    private void handleRemoveLock(Player player, TileState state)
    {
        String ownerUUID = getOwner(state);
        if (ownerUUID == null)
        {
            player.sendMessage(ChatColor.RED + "This " + getContainerType(state).toLowerCase() + " is not locked.");
            return;
        }
        if (!isPlayerOwner(player.getUniqueId(), state) && !hasBypassPermission(player))
        {
            player.sendMessage(ChatColor.RED + "You cannot unlock this " + getContainerType(state).toLowerCase() + ", it is locked by " + Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID)).getName());
            return;
        }
        if (state.getType() == Material.CHEST && ((Chest) state).getInventory().getHolder() instanceof DoubleChest doubleChest)
        {
            removeOwnership((Chest) doubleChest.getLeftSide());
            removeOwnership((Chest) doubleChest.getRightSide());
        }
        else
        {
            removeOwnership(state);
        }
        player.sendMessage(ChatColor.GREEN + "Unlocking " + getContainerType(state).toLowerCase() + ".");
    }

    // Block Checking methods

    public static boolean isChest(Block block)
    {
        return block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST || block.getType() == Material.BARREL;
    }

    private String getContainerType(TileState state)
    {
        if (state.getType() == Material.CHEST)
        {
            return "Chest";
        }
        else if (state.getType() == Material.TRAPPED_CHEST)
        {
            return "Trapped Chest";
        }
        else if (state.getType() == Material.BARREL)
        {
            return "Barrel";
        }
        return "Unknown";
    }

    // Event handling methods

    @EventHandler
    public void onBlockPhysicsEvent(BlockPhysicsEvent event)
    {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;
        Chest chest = (Chest) block.getState();
        if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest)
        {
            String owner = getOwner(chest);
            if (owner == null) return;
            Chest newChest = (Chest) (chest.equals(doubleChest.getRightSide()) ? doubleChest.getLeftSide() : doubleChest.getRightSide());
            setOwnership(owner, newChest);
            Set<UUID> trustedPlayers = getTrustedPlayers(chest);
            if (trustedPlayers == null || trustedPlayers.isEmpty()) return;
            trustedPlayers.forEach(trustedPlayer -> addTrustedPlayer(trustedPlayer, newChest));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event)
    {
        Block block = event.getBlock();
        if (!isChest(block)) return;
        TileState state = (TileState) block.getState();
        String owner = getOwner(state);
        if (owner == null) return;
        Player player = event.getPlayer();
        UUID ownerUUID = UUID.fromString(owner);
        if (!ownerUUID.equals(player.getUniqueId()))
        {
            player.sendMessage(ChatColor.RED + "You cannot break this " + getContainerType(state).toLowerCase() + ", it is locked by " + Bukkit.getOfflinePlayer(ownerUUID).getName());
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event)
    {
        Location location = event.getSource().getLocation();
        if (location == null) return;
        Block sourceBlock = location.getBlock();
        if (!isChest(sourceBlock)) return;
        TileState chest = (TileState) sourceBlock.getState();
        if (getOwner(chest) != null)
        {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isChest(block)) return;
        Player player = event.getPlayer();
        TileState state = (TileState) block.getState();
        handleChestInteraction(player, state, event);
    }

    private void handleChestInteraction(Player player, TileState state, PlayerInteractEvent event)
    {
        String owner = getOwner(state);
        if (isUsingKey(player))
        {
            handleLocking(player, state);
            return;
        }
        if (owner == null) return;
        if (canPlayerOpen(player, state)) return;
        UUID ownerUUID = UUID.fromString(owner);
        player.sendMessage(ChatColor.RED + "You cannot open this " + getContainerType(state).toLowerCase() + ", it is locked by " + Bukkit.getOfflinePlayer(ownerUUID).getName());
        event.setCancelled(true);
    }

    // Command handling methods

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player)) return false;
        if (command.getName().equalsIgnoreCase("trust")) return handleTrustCommand(player, args);
        if (command.getName().equalsIgnoreCase("lockchest")) return handleLockChestCommand(player, args);
        if (command.getName().equalsIgnoreCase("lockinspect")) return handleLockInspectCommand(player, args);
        return false;
    }

    private boolean handleTrustCommand(Player player, String[] args)
    {
        if (args.length != 1) return false;
        String playerName = args[0];
        Player trustedPlayer = Bukkit.getPlayer(playerName);
        UUID trustedPlayerUUID = null;
        if (trustedPlayer == null)
        {
            try
            {
                UUID playerUUID = UUIDFetcher.getUUID(playerName);
                if (playerUUID == null)
                {
                    player.sendMessage(ChatColor.RED + "Player " + playerName + " does not exist.");
                    return false;
                }
                trustedPlayerUUID = playerUUID;
            }
            catch (Exception e)
            {
                player.sendMessage(ChatColor.RED + "Player " + playerName + " does not exist.");
                return false;
            }
        }
        else
        {
            trustedPlayerUUID = trustedPlayer.getUniqueId();
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null || !isChest(block))
        {
            player.sendMessage(ChatColor.RED + "You must be looking at a container.");
            return true;
        }
        TileState state = (TileState) block.getState();
        String owner = getOwner(state);
        if (owner == null)
        {
            player.sendMessage(ChatColor.RED + "This " + getContainerType(state).toLowerCase() + " is not locked.");
            return true;
        }
        if (!isPlayerOwner(player.getUniqueId(), state))
        {
            player.sendMessage(ChatColor.RED + "You cannot trust players to this " + getContainerType(state).toLowerCase() + ", it is locked by " + Bukkit.getOfflinePlayer(UUID.fromString(owner)).getName());
            return true;
        }
        if (isPlayerOwner(trustedPlayerUUID, state))
        {
            player.sendMessage(ChatColor.RED + "You cannot trust yourself to your own " + getContainerType(state).toLowerCase() + ".");
            return true;
        }
        if (isPlayerTrusted(trustedPlayerUUID, state))
        {
            player.sendMessage(ChatColor.RED + playerName + " is already trusted to this " + getContainerType(state).toLowerCase() + ".");
            return true;
        }
        if (state.getType() == Material.CHEST && ((Chest) state).getInventory().getHolder() instanceof DoubleChest doubleChest)
        {
            addTrustedPlayer(trustedPlayerUUID, (Chest) doubleChest.getLeftSide());
            addTrustedPlayer(trustedPlayerUUID, (Chest) doubleChest.getRightSide());
        }
        else
        {
            addTrustedPlayer(trustedPlayerUUID, state);
        }
        player.sendMessage(ChatColor.GREEN + "Trusted " + playerName + " to this " + getContainerType(state).toLowerCase() + ".");
        return true;
    }

    private boolean handleLockChestCommand(Player player, String[] args)
    {
        if (args.length != 1) return false;
        String action = args[0].toLowerCase();
        if (!action.equals("add") && !action.equals("remove"))
        {
            player.sendMessage(ChatColor.RED + "Must proceed /lockchest with add or remove.");
            return false;
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null || !isChest(block))
        {
            player.sendMessage(ChatColor.RED + "You must be looking at a container.");
            return true;
        }
        TileState state = (TileState) block.getState();
        if (action.equals("add"))
        {
            handleAddLock(player, state);
            return true;
        }
        handleRemoveLock(player, state);
        return true;
    }

    private boolean handleLockInspectCommand(Player player, String[] args)
    {
        if (args.length != 0) return false;
        Block block = player.getTargetBlockExact(5);
        if (block == null || !isChest(block))
        {
            player.sendMessage(ChatColor.RED + "You must be looking at a container.");
            return true;
        }
        TileState state = (TileState) block.getState();
        String owner = getOwner(state);
        if (owner == null)
        {
            player.sendMessage(ChatColor.RED + "This " + getContainerType(state).toLowerCase() + " is not locked.");
            return true;
        }
        Location chestLocation = state.getLocation();
        player.sendMessage(ChatColor.BLUE + "Container info of container at " + chestLocation.getX() + ", " + chestLocation.getY() + ", " + chestLocation.getZ() + " " + chestLocation.getWorld().getName() + ":");
        player.sendMessage(ChatColor.YELLOW + "Owner:");
        player.sendMessage(Bukkit.getOfflinePlayer(UUID.fromString(owner)).getName());
        player.sendMessage(ChatColor.YELLOW + "Trusted players:");
        Set<UUID> trustedPlayers = getTrustedPlayers(state);
        if (trustedPlayers == null || trustedPlayers.isEmpty())
        {
            player.sendMessage("No trusted players.");
            return true;
        }
        for (UUID trustedPlayer : trustedPlayers)
        {
            player.sendMessage(Bukkit.getOfflinePlayer(trustedPlayer).getName());
        }
        return true;
    }

    // Key methods

    private boolean isUsingKey(Player player)
    {
        return player.isSneaking() && playerHasKey(player);
    }

    private boolean playerHasKey(Player player)
    {
        return isKeyItem(player.getInventory().getItemInMainHand()) || isKeyItem(player.getInventory().getItemInOffHand());
    }

    private boolean isKeyItem(ItemStack item)
    {
        if (item == null) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().equalsIgnoreCase("Key") && (item.getType() == Material.IRON_NUGGET || item.getType() == Material.GOLD_NUGGET);
    }
}
