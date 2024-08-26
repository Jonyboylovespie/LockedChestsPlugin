package jonyboylovespie.lockedchestplugin;
import org.bukkit.plugin.java.JavaPlugin;

public class LockedChestsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ExplosionListener(), this);
        getServer().getPluginManager().registerEvents(new ChestOwnershipListener(this), this);
        getServer().getPluginManager().registerEvents(new AnvilRenameListener(), this);
    }
}