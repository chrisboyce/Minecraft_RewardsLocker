package com.bukkit.Turbowaffle.RewardsLocker;

import java.sql.ResultSet;
import java.util.logging.Logger;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;

import org.bukkit.configuration.file.FileConfiguration;

public class RewardsLocker extends org.bukkit.plugin.java.JavaPlugin {

	private static Logger logger = Logger.getLogger("Minecraft");
	private static String loggerPrefix = "[RewardsLocker]";
	public RewardsDatabase database;
	private final RewardsLockerPlayerListener playerListener = new RewardsLockerPlayerListener(this);
	

	public static void log(String message){
		logger.info(loggerPrefix + " " + message);
		
	}
	
	private void initConfig(){
		FileConfiguration config = getConfig();
		
		if(!config.isString("mysql.host")){
			config.set("mysql.host", "localhost");
		}
		if(!config.isString("mysql.db")){
			config.set("mysql.db", "minecraft");
		}
		if(!config.isString("mysql.user")){
			config.set("mysql.user", "root");
		}
		if(!config.isString("mysql.pass")){
			config.set("mysql.pass", "");
		}
		if(!config.isString("mysql.table_prefix")){
			config.set("mysql.table_prefix", "rl_");
		}
		if(!config.isInt("mysql.port")){
			config.set("mysql.port", 3306);
		}

		saveConfig();
	}
	public void onEnable() {
		
		initConfig();
		FileConfiguration config = getConfig();
		
		database = new RewardsDatabase(
				this,
				config.getString("mysql.host"),
				config.getString("mysql.db"),
				config.getString("mysql.user"),
				config.getString("mysql.pass"),
				config.getInt("mysql.port"),
				config.getString("mysql.table_prefix")
		);
		
		database.setupTables();
		getServer().getScheduler().scheduleSyncRepeatingTask(this,
				new NotifyNewRewards(this), 0, 20 * 60 * 5);
		
		
		System.out.println("Enabling RewardsLocker");
		PluginManager pm = this.getServer().getPluginManager();
		
		pm.registerEvent(Event.Type.PLAYER_JOIN,playerListener,Event.Priority.Normal,this);
		pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener,Event.Priority.Normal,this);
	}
	
	public void onDisable() {
	}
	
	public void notifyPlayer(String playerName, String message){
		Player player = getServer().getPlayer(playerName);
		if(player != null){
			player.sendMessage("§a[Rewards] " + message);
			getServer().broadcastMessage("§a" + message);
		}
	}
	
	public void notifyPlayer(Player player, String message){
		if(player != null){
			player.sendMessage("§a[Rewards] " + message);
		}
	}
	
	public void notifyServer(String message){
		getServer().broadcastMessage("§a[Rewards]" + message);
	}
	public void notifyServerAboutPlayer(String playerName, String message){
		Player player = getServer().getPlayer(playerName);
		if(player != null){
			getServer().broadcastMessage("§a[Rewards]" + message);
		}
	}
	
	/**
	 * Checks for new rewards and notifies players
	 * 
	 */
	public class NotifyNewRewards implements Runnable {
		
		private RewardsLocker plugin;

		public NotifyNewRewards(RewardsLocker plugin){
			this.plugin = plugin;
	
		}

		public void run() {
			try{
				ResultSet notifications = plugin.database.getNewRewardNotifications();
				if(notifications != null){
					while(notifications.next()){
						notifyServerAboutPlayer(notifications.getString("player"),notifications.getString("player") + " got reward \"" + 
								notifications.getString("desc") + "\""); 
						plugin.database.setNotified(notifications.getInt("id"));
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	




	public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
		String commandName = command.getName().toLowerCase();
		if (!(sender instanceof Player)){
			System.out.println("Got command from server, doing nothing");
			return false;
		}
		Player player = (Player) sender;
		database.isValidChestLocation(player.getLocation());
		
		if(commandName.equalsIgnoreCase("rl_claim")){
			database.claimChest(player);
		}
		return true;
	}
}
