package com.bukkit.Turbowaffle.RewardsLocker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Permission;
import com.griefcraft.model.Protection;
import com.griefcraft.scripting.event.LWCProtectionRegistrationPostEvent;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class RewardsDatabase {

	private String tablePrefix = "rl_";
	private Connection con;
	private RewardsLocker plugin = null;
	
	public RewardsDatabase(RewardsLocker plugin,String host,String db, String user, String pass, int port, String tablePrefix){
		this.plugin = plugin;
		this.tablePrefix = tablePrefix;
		try {
			RewardsLocker.log("Loading MySQL driver");
			Class.forName("com.mysql.jdbc.Driver");
			String url =
				"jdbc:mysql://" + host + ":" + port + "/" + db;

			System.out.println("Connect to RewardsLocker DB on [" + url + "]");
			 con =
			 DriverManager.getConnection(
			 //url,"turbo_minecraft", "du2rebU2");
					 url,user, pass);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public ResultSet getChestRowForPlayer(String player){
		String query = "SELECT * FROM " + tablePrefix + "chests WHERE player_id = '" + player + "'";
		try {
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(query);
			return rs;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
	/**
	 * Gets a ResultSet of new notifications
	 * 
	 * @return
	 */
	public ResultSet getNewRewardNotifications(){
		String query = "SELECT " +
	tablePrefix + "rewards.id as id, " +
	tablePrefix + "rewards.`player_id` as player, " +
	tablePrefix + "events.short_desc as `desc` " +
"FROM " +
tablePrefix + "rewards," + tablePrefix + "events " +
"WHERE " +
tablePrefix + "rewards.`event_id` = " + tablePrefix + "events.id && " +
tablePrefix + "rewards.`notified_date` IS NULL limit 5";
		System.out.println(query);
		try {
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(query);
			return rs;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
		
	public boolean playerHasUndeliveredItems(Player player){

		Statement stmt;
		try {
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * " +
					 "from " + tablePrefix + "rewards WHERE player_id = '" + player.getName() + "' &&" +
					 		"delivered_date IS NULL");
			while(rs.next()){
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean playerOwnsChest(Player player, Block chestBlock){
		Location chestLocation = chestBlock.getLocation();

		try {

			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * FROM " + tablePrefix + "chests " +
					"WHERE chest_x = "  + chestLocation.getBlockX() +
		           " && chest_y = "  + chestLocation.getBlockY() + " && chest_z = "  +
		           chestLocation.getBlockZ() + " && player_id = '"  + player.getName()  + "'");

			while(rs.next()){
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public void deliverItems(Player player, Inventory chestInventory){
		
		int[] emptyInventorySlots = new int[27];
		int emptyInventoryCount = 0;
		
		ItemStack[] items = chestInventory.getContents();
		for(int i = 0; i < 27; i++){
			if(items[i] == null || items[i].getTypeId() == 0){
				//System.out.println("Found an open slot at index " + i);
				emptyInventoryCount++;
				emptyInventorySlots[emptyInventoryCount-1] = i;
				
			}
		}
		
		try {
			Statement stmt = con.createStatement();
			Statement updateStatement;
			ResultSet rs = stmt.executeQuery("SELECT * " +
					 "from " + tablePrefix + "rewards WHERE player_id = '" + player.getName() + "' && " +
					 "delivered_date IS NULL");
			while(rs.next()){
				if(emptyInventoryCount > 0){
					System.out.println("Delivering " + rs.getInt("item_id") + " x " + rs.getInt("item_count") + 
					" to slot " + emptyInventorySlots[emptyInventoryCount-1] + ", player " + player.getName());
					items[emptyInventorySlots[emptyInventoryCount - 1]] = new ItemStack(rs.getInt("item_id"),rs.getInt("item_count"));
					emptyInventoryCount--;
					updateStatement = con.createStatement();
					updateStatement.executeUpdate("UPDATE " + tablePrefix + "rewards set delivered_date = NOW() WHERE id = " + 
							rs.getInt("id"));
				}
			}
			chestInventory.setContents(items);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void setNotified(int rewardID){

		String updateQuery = "UPDATE " + tablePrefix + "rewards SET notified_date = NOW() WHERE id = ?";
		try {
			PreparedStatement updateStatement = con.prepareStatement(updateQuery);
			updateStatement.setInt(1, rewardID);
			updateStatement.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public boolean playerHasItemsInChest(Player player){
		try {
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * " +
					 "from " + tablePrefix + "chests WHERE player_id = '" + player.getName() + "'");
			while(rs.next()){
				Block block = player.getWorld().getBlockAt(new Location(player.getWorld(),
						rs.getInt("chest_x"),rs.getInt("chest_y"),rs.getInt("chest_z")));
				
				if(block.getTypeId() == 54){
					Chest chest = (Chest) block.getState();
					Inventory chestInventory = chest.getInventory();
					ItemStack[] items = chestInventory.getContents();
					for(int i = 0; i < 27; i++){
						if(items[i] == null || items[i].getTypeId() == 0){
							//this slot is empty
						} else {
							return true;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isValidChestLocation(Location location){
		WorldGuardPlugin wg = getWorldGuard();
		RegionManager rm = wg.getRegionManager(location.getWorld());
		ApplicableRegionSet regions = rm.getApplicableRegions(location);
		for(ProtectedRegion region: regions){
			if(region.getId().contains("_rewards")){
				return true;
			}
		}
		RewardsLocker.log("Location [" + location.toString() + "] is not a valid rewards chest location");
		return false;
	}
	public WorldGuardPlugin getWorldGuard() {
        Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
     
        if (wg == null || !(wg instanceof WorldGuardPlugin)) {
            return null;
        }
     
        return (WorldGuardPlugin) wg;
    }
	public LWCPlugin getLWCPlugin(){
		Plugin lwc = plugin.getServer().getPluginManager().getPlugin("LWC");
		if (lwc == null || !(lwc instanceof LWCPlugin)) {
			RewardsLocker.log("LWC Not found!");
            return null;
        }
		return (LWCPlugin) lwc;
	}
	public void claimChest(Player player){
		Location currentPlayerLocation = player.getLocation();
		Location chestLocation = currentPlayerLocation;
		chestLocation.setY(chestLocation.getY()-1);
		ResultSet rs;
		
		try{
			ResultSet chestRow = getChestRowForPlayer(player.getName());
			
				if(chestRow.next()){
					int x,y,z;
					
					x = chestRow.getInt("chest_x");
					y = chestRow.getInt("chest_y");
					z = chestRow.getInt("chest_z");
					player.sendMessage("You already have a chest! It is at " + x + "," + y + "," + z);
					return;
				}
				
				Statement stmt = con.createStatement();
				String query = "SELECT * " +
				 "from " + tablePrefix + "chests WHERE chest_x = " + chestLocation.getBlockX() +
				 " && chest_y = " + chestLocation.getBlockY() + " && chest_z = " + 
				 chestLocation.getBlockZ();
				RewardsLocker.log(query);
				rs = stmt.executeQuery(query);
				while(rs.next()){
					player.sendMessage("Someone has already claimed this chest!");
					return;
				}
				Block block = player.getWorld().getBlockAt(chestLocation);
				if(!isValidChestLocation(chestLocation)){
					player.sendMessage("You can only claim chests in the Rewards Hall!");
					return;
				}
				if(block.getTypeId() != 54){
					player.sendMessage("You must be on top of the chest!");
					return;
				}
				
				String insertQuery = "INSERT INTO " + tablePrefix + "chests set player_id = '" + 
				player.getName() + "',chest_x = " + block.getX() + ", chest_y = " +
				block.getY() + ", chest_z = " + block.getZ();
				RewardsLocker.log(insertQuery);
				stmt.executeUpdate(insertQuery);
				
				Location signLocation = chestLocation;
				signLocation.setY(signLocation.getY() + 1);
				Block signBlock = player.getWorld().getBlockAt(signLocation);
				signBlock.setTypeId(68);
				Location wallLocation = signLocation;
				wallLocation.setX(wallLocation.getX()-1);
				if(player.getWorld().getBlockAt(wallLocation).getTypeId() != 0){
					signBlock.setData((byte) 0x5);
				}
				wallLocation.setX(wallLocation.getX()+2);
				if(player.getWorld().getBlockAt(wallLocation).getTypeId() != 0){
					signBlock.setData((byte) 0x4);
				}
				wallLocation.setX(wallLocation.getX()-1);
				wallLocation.setZ(wallLocation.getZ()-1);
				if(player.getWorld().getBlockAt(wallLocation).getTypeId() != 0){
					signBlock.setData((byte) 0x3);
				
				}
				wallLocation.setZ(wallLocation.getZ()+2);
				if(player.getWorld().getBlockAt(wallLocation).getTypeId() != 0){
					signBlock.setData((byte) 0x2);
					
				}
				
				Sign sign = (Sign) signBlock.getState();
				sign.setLine(0,"[Rewards]");
				sign.setLine(1,player.getName());
		
				plugin.notifyPlayer(player.getName(), "Chest claimed!");
				
				if(!getLWCPlugin().getLWC().isProtectable(block)){
					RewardsLocker.log("Chest block isn't protectable!");
					return;
				}
				
				Protection protection = getLWCPlugin().getLWC().getPhysicalDatabase().registerProtection(
						block.getTypeId(), 
						Protection.Type.PRIVATE, 
						block.getWorld().getName(), 
						player.getName(), 
						"", 
						block.getX(), 
						block.getY(), 
						block.getZ());
				
				if (protection != null) {
					getLWCPlugin().getLWC().getModuleLoader().dispatchEvent(new LWCProtectionRegistrationPostEvent(protection));
	            }
				

		
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	public void setupTables(){
		String chestTableQuery = "CREATE TABLE IF NOT EXISTS `" + tablePrefix + "chests` (\n" + 
		  "`id` int(10) NOT NULL AUTO_INCREMENT,\n" + 
		  "`player_id` varchar(32) DEFAULT '0',\n" + 
		  "`chest_x` int(11) DEFAULT '0',\n" + 
		 " `chest_y` int(11) DEFAULT '0',\n" + 
		  "`chest_z` int(11) DEFAULT '0',\n" + 
		  "`claim_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP,\n" + 
		  "`world` char(16) DEFAULT 'world',\n" + 
		  "PRIMARY KEY (`id`),\n" + 
		 " KEY `player_id` (`player_id`)\n" + 
		") ENGINE=MyISAM DEFAULT CHARSET=latin1\n";
		String eventTableQuery = "CREATE TABLE IF NOT EXISTS `" + tablePrefix + "events` (\n" + 
		  "`id` int(10) NOT NULL AUTO_INCREMENT,\n" + 
		  "`short_desc` varchar(32) DEFAULT '0',\n" + 
		  "`long_desc` text,\n" + 
		  "`default_item_id` int(11) DEFAULT '0',\n" + 
		  "`default_item_count` int(11) DEFAULT '0',\n" + 
		  "`public` int(1) DEFAULT '0',\n" + 
		  "`count` int(10) DEFAULT '0',\n" + 
		  "`quantifier` varchar(50) DEFAULT NULL,\n" + 
		  "PRIMARY KEY (`id`)\n" + 
		") ENGINE=MyISAM DEFAULT CHARSET=latin1";
		String rewardsTableQuery = "CREATE TABLE IF NOT EXISTS `" + tablePrefix + "rewards` (\n" + 
		  "`id` int(10) NOT NULL AUTO_INCREMENT,\n" + 
		  "`player_id` varchar(32) DEFAULT '0',\n" + 
		  "`event_id` int(11) DEFAULT '1',\n" + 
		  "`reward_date` datetime DEFAULT NULL,\n" + 
		  "`delivered_date` datetime DEFAULT NULL,\n" + 
		  "`item_id` int(11) DEFAULT '0',\n" + 
		  "`item_count` int(11) DEFAULT '0',\n" + 
		  "`given_by` varchar(50) DEFAULT NULL,\n" + 
		  "`notified_date` datetime DEFAULT NULL,\n" + 
		  "PRIMARY KEY (`id`),\n" + 
		  "KEY `player_id` (`player_id`),\n" + 
		 " KEY `event_id` (`event_id`),\n" + 
		  "KEY `notified_date` (`notified_date`)\n" + 
		") ENGINE=MyISAM DEFAULT CHARSET=latin1";


		Statement stmt;
		try {
			stmt = con.createStatement();
			stmt.executeUpdate(chestTableQuery);
			stmt.executeUpdate(eventTableQuery);
			stmt.executeUpdate(rewardsTableQuery);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		
	}
}
