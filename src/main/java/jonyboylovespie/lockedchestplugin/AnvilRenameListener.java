package jonyboylovespie.lockedchestplugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AnvilRenameListener implements Listener {

    @EventHandler
    public void onAnvilRename(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack result = event.getResult();
        if (result == null) return;
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;
        if (result.getType() == Material.IRON_NUGGET || result.getType() == Material.GOLD_NUGGET) {
            if (meta.getDisplayName().equalsIgnoreCase("Key")) {
                meta.setCustomModelData(1335948828);
            }
            else {
                meta.setCustomModelData(null);
            }
            result.setItemMeta(meta);
            inventory.setItem(2, result);
        }
    }
}
