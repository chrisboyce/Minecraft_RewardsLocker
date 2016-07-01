package com.bukkit.Turbowaffle.RewardsLocker;



import org.bukkit.block.Chest;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.Inventory;

public class RewardsLockerPlayerListener extends PlayerListener {
	
	public static RewardsLocker plugin;
	
	public RewardsLockerPlayerListener(RewardsLocker instance) {
		plugin = instance;
	}


	public void onPlayerJoin(PlayerJoinEvent event){
		if(plugin.database.playerHasItemsInChest(event.getPlayer()) || 
				plugin.database.playerHasUndeliveredItems(event.getPlayer())){
			plugin.notifyPlayer(event.getPlayer(), "You have items waiting in your Rewards Chest!");
		}
	}
	
	public void onPlayerInteract(PlayerInteractEvent event){
		if(event.getClickedBlock().getTypeId() == 54){
			if(plugin.database.isValidChestLocation(event.getPlayer().getLocation())){
				if(plugin.database.playerOwnsChest(event.getPlayer(), event.getClickedBlock())){
					Chest chest = (Chest) event.getClickedBlock().getState();
					Inventory chestInventory = chest.getInventory();
					plugin.database.deliverItems(event.getPlayer(), chestInventory);
				}
			}
		}
	}
}
