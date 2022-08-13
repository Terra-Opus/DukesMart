// current: v1.16
package com.ruinscraft.dukesmart;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class DukesMart extends JavaPlugin {
	private FileConfiguration config = getConfig();
	private MySQLHelper mySQLHelper;
	private ShopListener sl;
	private NotifyPlayerIncomeTaskController notifyController;
	private SelectedShopController selectController;

	private String currencyItem = getConfig().getString("shop.currency-item");
	public String currencySymbol = getConfig().getString("shop.currency-symbol");
	public String currencyNameS = getConfig().getString("shop.currency-name-s");
	public String currencyNameP = getConfig().getString("shop.currency-name-p");
	private int ledgerDelete = getConfig().getInt("player.ledger-delete");

	
	public final Material SHOP_CURRENCY_MATERIAL   = Material.valueOf(currencyItem);
	public final XMaterial SHOP_CURRENCY_XMATERIAL = XMaterial.valueOf(currencyItem);

    @Override
    public void onEnable() {
    	
    	// check config.yml for database credentials
    	if(!config.contains("mysql.host") || !config.contains("mysql.port") || !config.contains("mysql.database")
    	   || !config.contains("mysql.username") || !config.contains("mysql.password")) {
    		
    		getLogger().info("Failed to find database information in config.yml!");
    		getLogger().info("Adding default values to config.yml");
    		
    		config.addDefault("mysql.host", "localhost");
    		config.addDefault("mysql.port", 3306);
    		config.addDefault("mysql.database", "DukesMart-DB");
    		config.addDefault("mysql.username", "DukesMart-USER");
    		config.addDefault("mysql.password", "DukesMart-PASSWORD");
    		
    		config.options().copyDefaults(true);
            saveConfig();
    		
    	}
    	
    	setupMySQLHelper();
    	
		if(!config.contains("shop")) {
			config.addDefault("shop.currency-item", "GOLD_INGOT");
			config.addDefault("shop.currency-symbol", "G");
			config.addDefault("shop.currency-name-s", "gold ingot");
			config.addDefault("shop.currency-name-p", "gold ingots");
			config.options().copyDefaults(true);
			saveConfig();
		}

    	this.sl = new ShopListener(this);
    	this.notifyController = new NotifyPlayerIncomeTaskController(this);
    	this.selectController = new SelectedShopController();
    	
    	this.getCommand("shop").setExecutor(new ShopCommandExecutor(this));
    	this.getCommand("shop").setTabCompleter(this);
    	
    	Bukkit.getPluginManager().registerEvents(this.sl, this);
    	
    	getLogger().info("DukesMart has been enabled!");
    }

    @Override
    public void onDisable() {
    	getLogger().info("DukesMart has been disabled!");
    }

    public void setupMySQLHelper() {
    	// load MySQL database info
    	String host     = config.getString("mysql.host");
    	int    port     = config.getInt("mysql.port");
    	String database = config.getString("mysql.database");
    	String username = config.getString("mysql.username");
    	String password = config.getString("mysql.password");
    	
    	this.mySQLHelper = new MySQLHelper(host, port, database, username, password);
    }
    
    public MySQLHelper getMySQLHelper() {
    	return this.mySQLHelper;
    }
    
    public NotifyPlayerIncomeTaskController getNotifyPlayerController() {
    	return this.notifyController;
    }
    
    public SelectedShopController getSelectedShopController() {
    	return this.selectController;
    }
}

//hello there
//General Kenobi