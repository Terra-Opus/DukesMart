package com.ruinscraft.dukesmart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import net.md_5.bungee.api.ChatColor;

public class ShopListener implements Listener{
	private DukesMart plugin;
	
	private HashMap<Player, BukkitTask> hideDisplayTasks  = new HashMap<Player, BukkitTask>();
	
	private final int HIDE_SHOP_DISPLAY_SECONDS = 15;
	private final String SHOP_SIGN_NO_ITEM      = "" + ChatColor.WHITE + "?";
	private final String SHOP_SIGN_IDENTIFIER   = "" + ChatColor.DARK_PURPLE + "[Buy]";
	private final String SHOP_SIGN_OWNER_COLOR  = "" + ChatColor.DARK_BLUE;
	
	private final String PLUGIN_NAME = ChatColor.GOLD + "DukesMart";
	private final String MSG_PLAYER_INCOME_LAST_LOGIN = ChatColor.YELLOW + "Since last login, you made " + ChatColor.GOLD + "%d₸" + ChatColor.YELLOW + " from your chest shops." + ChatColor.GOLD + " /shop withdraw";
	private final String MSG_SHOP_CREATION_SUCCESS = ChatColor.AQUA + "Shop created! Now place your items to sell in chest below sign.";
	private final String MSG_SHOP_SECURITY_WARNING = "" + ChatColor.RED + "" + ChatColor.BOLD + "Don't forget to lock your shop chest!";

	private final String MSG_ERROR_SHULKER_CONTAINS_ITEM = "We're sorry, but you cannot sell shulkers containing items.\nTry again with an empty shulker box.";
	private final String MSG_ERROR_ITEM_CANNOT_EXCEED = "The item %s cannot exceed a stack size of %d. Your sign has been corrected.";
	private final String MSG_ERROR_NOT_ENOUGH_TERRAL = "Sorry, you do not have enough terrals to buy.";
	private final String MSG_ERROR_NOT_ENOUGH_SPACE = "You do not have enough free space for this purchase.";
	private final String MSG_ERROR_SHOP_OUT_OF_STOCK = "Sorry, this shop is out of stock. Come back later.";
	private final String MSG_ERROR_LEDGER_CLEARED = ChatColor.GRAY + "It seems your ledger was cleared in your extended absence...";
	private final String MSG_WARNING_INCOME_EXPIRES_SOON = ChatColor.RED + "Heads up! Your ledger income will expire in %d days. Don't forget to make a withdraw!";
    public ShopListener(DukesMart plugin) {
    	this.plugin = plugin;
    }
    
    @EventHandler
    /**
     * On player join, any >0 money in their shop ledger will be displayed.
     * If the player is new or does not have a ledger, it will be created.
     * @param evt - Player join event
     */
    public void onPlayerJoin(PlayerJoinEvent evt) {
        Player player = evt.getPlayer(); // The player who joined
        
        this.plugin.getMySQLHelper().getPlayerIncome(player).thenAccept(result -> {
        	/* if the returning value is '-1', then the player does
        	 * not have a ledger and must be created
        	 */
        	if(result == null) {
        		this.plugin.getMySQLHelper().setupLedger(player).thenAccept(createLedger -> {
        			if(createLedger) {
        				Bukkit.getLogger().info("New ledger created for " + player.getName() + " (UUID: " + player.getUniqueId() + ")");
        			}
        		});
        	}
        	else if(player.isOnline() && result.getIncome() > 0) {	
    			if(result.getDate() != null) {
    				if(!result.dateIsExpired()) {
    					player.sendMessage(String.format(MSG_PLAYER_INCOME_LAST_LOGIN, result.getIncome()));
            			
			            if(result.daysLeftBeforeExpire() > 0 && result.daysLeftBeforeExpire() <= 10) {
				            player.sendMessage(String.format(MSG_WARNING_INCOME_EXPIRES_SOON, result.daysLeftBeforeExpire()));
			            }
    				}
		            else{
		            	this.plugin.getMySQLHelper().clearPlayerLedger(player).thenAccept(cleared -> {
		            		if(cleared) {
		            			player.sendMessage(MSG_ERROR_LEDGER_CLEARED);
		            		}
		                });
		            }
	            } 
        	}
        });
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent evt) {
    	Player player = evt.getPlayer();
    	
    	if(this.plugin.getSelectedShopController().playerHasSelection(player)) {
    		this.plugin.getSelectedShopController().removeSelection(player);
    	}
    	
    	if(this.plugin.getNotifyPlayerController().playerHasTask(player)) {
    		this.plugin.getNotifyPlayerController().removePlayer(player);
    	}
    }
    /*
     * This will handle shop creation if a player
     * places a sign with proper values.
     */
    @EventHandler
    public void onSignChangeEvent(SignChangeEvent evt) {
    	Player player = evt.getPlayer();
    	Block block = evt.getBlock();
    	
    	if(block.getState() instanceof Sign) {
	    	if(validateShopSignEntry(evt.getLines())) {

	    		evt.setLine(0, SHOP_SIGN_IDENTIFIER);
    			evt.setLine(1, SHOP_SIGN_NO_ITEM);
	    		evt.setLine(3, SHOP_SIGN_OWNER_COLOR + player.getName());

	    		player.sendMessage(ChatColor.AQUA + "Hold an item you want to sell and right-click the sign to finish setup.");
	    	}
    	}
    }
    
    /**
     * This function handles all Player interactions with a shop sign.
     * @param evt - called Player interact event
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent evt) {
        Player player = evt.getPlayer();

        if (evt.getAction() == Action.RIGHT_CLICK_BLOCK) {
        	Block clickedBlock = evt.getClickedBlock();
        	
            if (blockIsSign(clickedBlock)){
            	
            	
            	// a shop is defined as a sign (formatted)
            	// and a chest block immediately below it.
                Sign sign = (Sign) clickedBlock.getState();

                BlockData data = clickedBlock.getBlockData();
                Block block = null;
                // first, get the block that the sign is attached to

                if (data instanceof Directional)
                {
                    Directional directional = (Directional)data;
                    block = clickedBlock.getRelative(directional.getFacing().getOppositeFace());
                }
                
                // if the attached block is NOT chest, try the block below it
                if(!blockIsStorage(block)) {
                	block = clickedBlock.getRelative(BlockFace.DOWN, 1);
                }
                
                if(blockIsStorage(block)) {
                	Container container = (Container) block.getState();
                	
                	if(signIsShop(sign)) {
                		updateSign(sign);
                		
                		// if player is holding a dye, cancel the event to prevent dying over the shop sign text
                		if(playerHoldingDye(player)) {
                			evt.setCancelled(true);
                		}
                		
                		if(shopSignHasNoItem(sign)) {
                			// you must clone the item, otherwise it will be affected later
                			ItemStack itemToSell = player.getInventory().getItemInMainHand().clone();
                			
                			// if player has something in hand (and is owner) set the shop's item
                			if(!itemIsAir(itemToSell)) {
                				
            					if(itemIsShulkerBox(itemToSell)) {
            						if(itemToSell.getItemMeta() instanceof BlockStateMeta) {
            							BlockStateMeta bsm = (BlockStateMeta) itemToSell.getItemMeta();
            							if(bsm.getBlockState() instanceof ShulkerBox) {
	            							ShulkerBox shulkerBox = (ShulkerBox) bsm.getBlockState();
	            							Inventory shulkerContents = shulkerBox.getInventory();
	            							for(ItemStack i : shulkerContents.getContents()) {
	            								if(i != null) {
	            									sendError(player, this.MSG_ERROR_SHULKER_CONTAINS_ITEM);
	            									return;
	            								}
	            							}
            							}
            						}
            					}
            					// if player has a writable book, strip any unfinished writing from it
            					else if(itemIsWritableBook(itemToSell)) {
            						itemToSell.setItemMeta(XMaterial.WRITABLE_BOOK.parseItem().getItemMeta());
            					}
            					
                				sign.setLine(1, getItemDisplayName(itemToSell));
                				// update quantities if they exceed the stack size for the item
                				updateSignQuantity(player, sign, itemToSell);
                				
	                			sign.update();
	                			
            					this.plugin.getMySQLHelper().registerShop(player, sign, itemToSell).thenAccept(callback -> {
            						if(player.isOnline()) {
		                				player.sendMessage(this.MSG_SHOP_CREATION_SUCCESS);
		                				new ActionBarNotifyTask(player, this.MSG_SHOP_SECURITY_WARNING, 3).runTaskTimer(this.plugin, 0, 20*2);                				
            						}
	                			});	
                			}
                		}
                		else{
                			Location shopLocation = getShopLocation(sign);
                			Shop selectedShop = this.plugin.getSelectedShopController().getSelection(player);
                			
                			// if player has a shop selected and selected shop
                			// matches the location of the sign, make a purchase
                			if(selectedShop != null && selectedShop.getLocation().equals(shopLocation)){

		                		// prevent players from buying from their own shop(s)
		                		if(selectedShop.playerOwnsShop(player)) {
		                			return;
		                		}
		                		
		                		ItemStack itemToBuy = selectedShop.getItem();
		                		itemToBuy.setAmount(selectedShop.getQuantity());
		                		
		                		Inventory storeStock = container.getInventory();

		                		// check if the chest inventory contains the item
		                		boolean hasStock = false;
		                		
		                		if(itemIsWritableBook(itemToBuy)) {
		                			ItemStack writableBook = new ItemStack(XMaterial.WRITABLE_BOOK.parseMaterial());
		                			hasStock = shopChestContainsItem(storeStock, writableBook, selectedShop);
		                		}
		                		else if(itemIsFinishedBook(itemToBuy)) {
		                			itemToBuy = shopChestFindBook(storeStock, itemToBuy, selectedShop);
		                			if(itemToBuy != null) {
		                				hasStock = true;
		                			}
		                		}
		                		else if(itemIsEnchantedBook(itemToBuy)) {
		                			itemToBuy = shopChestFindEnchantedBook(storeStock, itemToBuy, selectedShop);
		                			if(itemToBuy != null) {
		                				hasStock = true;
		                			}
		                		}
		                		else if(itemIsPlayerHead(itemToBuy)) {
		                			itemToBuy = shopChestFindPlayerHead(storeStock, sign);
		                			
		                			if(itemToBuy != null) {
		                				itemToBuy.setAmount(selectedShop.getQuantity());
		                				hasStock = true;
		                			}
		                		}
		                		else if(itemIsShulkerBox(itemToBuy)) {
		                			itemToBuy = shopChestFindShulkerBox(storeStock, itemToBuy.clone(), sign);
		                			
		                			if(itemToBuy != null) {
		                				itemToBuy.setAmount(selectedShop.getQuantity());
		                				hasStock = true;
		                			}
		                		}
		                		else {
		                			hasStock = shopChestContainsItem(storeStock, itemToBuy, selectedShop);
		                		}
		                		
		                		if(hasStock) {   			
		                			if(playerCanStoreItem(player, itemToBuy, selectedShop.getQuantity())) {
		                				player.updateInventory();
		                				PlayerInventory pi = player.getInventory();
		                				
			                			if(pi.containsAtLeast(new ItemStack(plugin.SHOP_CURRENCY_MATERIAL), selectedShop.getPrice())){

			                				storeStock.removeItem(itemToBuy);
				                			pi.removeItem(new ItemStack(plugin.SHOP_CURRENCY_MATERIAL, selectedShop.getPrice()));
				                			
				                			if(itemIsPlayerHead(selectedShop.getItem())){
	                							updatePlayerHeadShopSign(container.getInventory(), sign);
	                						}

				                			pi.addItem(itemToBuy);
				                			
				                			this.plugin.getMySQLHelper().processTransaction(player, selectedShop).thenAccept(result -> {
					                			if(player.isOnline()) {
					                				Material itemType = selectedShop.getItem().getType();
					                				String purchaseConfirmation = ChatColor.AQUA + "Purchased " + selectedShop.getQuantity() + "x " + materialPrettyPrint(itemType) + " for " + ChatColor.GOLD + selectedShop.getPrice() + "₸";
					                				new ActionBarNotifyTask(player, purchaseConfirmation, 3).runTaskTimer(this.plugin, 0, 20*2);

					                				Player owner = Bukkit.getPlayer(UUID.fromString(selectedShop.getOwner()));
					                				if(owner != null && owner.isOnline() && selectedShop.getPrice() > 0) {
					                					this.plugin.getNotifyPlayerController().addTask(owner);
					                				}
					                			}		
				                			});
			                			}
			                			else {
			                				sendError(player, MSG_ERROR_NOT_ENOUGH_TERRAL);
			                			}
		                			}
		                			else {
		                				sendError(player, MSG_ERROR_NOT_ENOUGH_SPACE);
		                			}
		                		}
		                		else {
		                			sendError(player, MSG_ERROR_SHOP_OUT_OF_STOCK);
		                		}
	                		}
                			else {
                				// player has not selected any shop
                				// shop information must be retrieved
                				this.plugin.getMySQLHelper().getShopFromLocation(shopLocation).thenAccept(shop ->{
                					if(player.isOnline() && shop != null) {
                						player.sendMessage(ChatColor.GREEN + "Shop selected.");
                						
                						this.plugin.getSelectedShopController().addSelection(player, shop);
                						
	                					Bukkit.getScheduler().runTask(this.plugin, () -> {
	                						if(itemIsPlayerHead(shop.getItem())){
	                							updatePlayerHeadShopSign(container.getInventory(), sign);
	                						}
	                						displayShopInformation(player, shop);
	                					});
                					}
                				});			
                			}
		                }
                	}
                }
            }
        }
    }
    
	private void updatePlayerHeadShopSign(Inventory inventory, Sign sign) {
		ItemStack newPlayerHead = shopChestFindPlayerHead(inventory, sign);
		
		if(newPlayerHead != null) {
			if(newPlayerHead.getItemMeta().hasDisplayName()) {
				sign.setLine(1, newPlayerHead.getItemMeta().getDisplayName());
			}
			else {
				sign.setLine(1, prettyPrint(newPlayerHead.getType().toString()));
			}
			sign.update();
		}
	}

	private ItemStack shopChestFindPlayerHead(Inventory storeStock, Sign sign) {
		ItemStack[] chestItems = storeStock.getContents();

		for(int i = 0; i < chestItems.length; i++) {
			ItemStack temp = chestItems[i];
			
			if(itemIsPlayerHead(temp)) {
				ItemStack head = temp.clone();
				head.setAmount(1);
				return head;
			}
		}
		
		return null;
	}
	
	private ItemStack shopChestFindShulkerBox(Inventory storeStock, ItemStack shulkerToFind, Sign sign) {
		ItemStack[] chestItems = storeStock.getContents();
		
		for(int i = 0; i < chestItems.length; i++) {
			ItemStack temp = chestItems[i];
			
			if(itemIsShulkerBox(temp)) {
				// very important to check for meta! new shulkers that have never held
				// an item do NOT have meta according to bukkit!
				if(temp.hasItemMeta()) {
					ItemMeta meta = temp.getItemMeta();

					if(meta instanceof BlockStateMeta) {
						BlockStateMeta bsm = (BlockStateMeta) meta;

						if(bsm.getBlockState() instanceof ShulkerBox) {
							ShulkerBox shulker = (ShulkerBox) bsm.getBlockState();

							if(shulkerToFind.getType().equals(shulker.getType())) {
								if(shulker.getSnapshotInventory().isEmpty()) {
									return temp.clone();
								}
							}
						}
					}
				}
				// if it doesn't have meta, it is safe to assume it is empty. check for same material
				else if(shulkerToFind.getType().equals(temp.getType())){
					return temp.clone();
				}
			}
		}
		
		return null;
	}
	private boolean itemIsPlayerHead(ItemStack itemToBuy) {
		return itemToBuy != null && itemToBuy.getType().equals(Material.PLAYER_HEAD);
	}

	private String getItemDisplayName(ItemStack item) {
    	ItemMeta meta = item.getItemMeta();
    	Material mat = item.getType();
    	
    	// Custom/display names
		if(meta.hasDisplayName()) {
			return "" + ChatColor.ITALIC + meta.getDisplayName();
		}
		else if(itemIsPolishedBlackstone(item)) {
			XMaterial itemMaterial = XMaterial.matchXMaterial(item);
			String[] temp = materialPrettyPrint(itemMaterial.parseMaterial()).split(" ");
			if(temp.length > 2) {
				String out = "PB Stone";
				for(int i = 2; i < temp.length; i++) {
					out += temp[i] + " ";
				}
				
				return out;
			}
			else {
				return "P Blackstone";
			}
		}
		// Written book names
		else if(itemIsFinishedBook(item)) {
			BookMeta bookMeta = (BookMeta) meta;
			if(bookMeta.hasTitle()) {
				return "" + ChatColor.ITALIC + bookMeta.getTitle();
			}
		}
		// Potion names
		else if(itemIsPotion(item)) {
			return "" + ChatColor.DARK_RED + getPotionName(item);
		}
		// fix for raw meats
		else if((mat.name().contains("PORK") || mat.name().contains("CHICKEN") || mat.name().contains("MUTTON") || mat.name().contains("BEEF") || mat.name().equals("RABBIT") || mat.name().contains("SALMON") || mat.name().contains("COD")) && !mat.name().contains("COOKED")) {
			XMaterial itemMaterial = XMaterial.matchXMaterial(item);
			return "Raw " + materialPrettyPrint(itemMaterial.parseMaterial());
		}
		// fix for chestplates
		else if(mat.name().contains("CHESTPLATE")) {
			XMaterial itemMaterial = XMaterial.matchXMaterial(item);
			String[] temp = materialPrettyPrint(itemMaterial.parseMaterial()).split(" ");
			return temp[0] + " Chest.";
		}

		XMaterial itemMaterial = XMaterial.matchXMaterial(item);
		return materialPrettyPrint(itemMaterial.parseMaterial());
	}

	/**
     * Updates a sign's text to reflect any changes,
     * such as item or owner's name
     * 
     * @param sign Sign to update
     */
	private void updateSign(Sign sign) {
		Location location = sign.getLocation();

		this.plugin.getMySQLHelper().getShopFromLocation(location).thenAccept(shop -> {
			if(shop != null) {
				Bukkit.getScheduler().runTask(this.plugin, () -> {
					sign.setLine(3, this.SHOP_SIGN_OWNER_COLOR + shop.getOwnerName());
					sign.update();
				});	
			}
		});
	}

	private boolean shopChestContainsItem(Inventory storeStock, ItemStack itemToBuy, Shop shop) {
		if(storeStock instanceof DoubleChestInventory) {
			DoubleChestInventory dci = (DoubleChestInventory) storeStock;
			if(dci.getLeftSide().containsAtLeast(itemToBuy, shop.getQuantity())) {
				storeStock = dci.getLeftSide();
				return true;
			}
			else if(dci.getRightSide().containsAtLeast(itemToBuy, shop.getQuantity())) {
				storeStock = dci.getRightSide();
				return true;
			}
		}
		else{
			if(storeStock.containsAtLeast(itemToBuy, shop.getQuantity())) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Special finding method for checking written book existence
	 * @param storeStock
	 * @param itemToBuy
	 * @param shop
	 * @return ItemStack representing correct book in chest, null on failure
	 */
	private ItemStack shopChestFindBook(Inventory storeStock, ItemStack itemToBuy, Shop shop) {
		// special check for written books
		// this part does not check for quantity, just
		// that the item itself exists somewhere
		if(itemIsFinishedBook(itemToBuy)) {
			ItemStack[] chestItems = storeStock.getContents();
			boolean success = true;
			for(int i = 0; i < chestItems.length; i++) {
				ItemStack temp = chestItems[i];
				// if the item in chest is 
				if(itemIsFinishedBook(temp)) {
					// compare values
					BookMeta tempMeta = (BookMeta) temp.getItemMeta();
					BookMeta buyMeta  = (BookMeta) itemToBuy.getItemMeta();
					
					if(tempMeta.hasAuthor() && tempMeta.hasTitle() && tempMeta.hasPages()) {
						if(tempMeta.getAuthor().compareTo(buyMeta.getAuthor()) != 0) {
							success = false;
							continue;
						}
						
						if(tempMeta.getTitle().compareTo(buyMeta.getTitle()) != 0) {
							success = false;
							continue;
						}
						
						if(tempMeta.getPageCount() != buyMeta.getPageCount()) {
							success = false;
							continue;
						}
						
						List<String> tempPages = tempMeta.getPages();
						List<String> buyPages  = buyMeta.getPages();
						
						for(int j = 0; j < tempPages.size(); j++) {
							if(tempPages.get(j).compareTo(buyPages.get(j)) != 0) {
								success = false;
								break;
							}
						}
						
						if(success) {
							itemToBuy = chestItems[i].clone();
							itemToBuy.setAmount(1);
							break;
						}
					}
				}
				success = true;
			}
		}
		
		if(storeStock instanceof DoubleChestInventory) {
			DoubleChestInventory dci = (DoubleChestInventory) storeStock;
			if(dci.getLeftSide().containsAtLeast(itemToBuy, shop.getQuantity())) {
				storeStock = dci.getLeftSide();
				return itemToBuy;
			}
			else if(dci.getRightSide().containsAtLeast(itemToBuy, shop.getQuantity())) {
				storeStock = dci.getRightSide();
				return itemToBuy;
			}
		}
		else{
			if(storeStock.containsAtLeast(itemToBuy, shop.getQuantity())) {
				return itemToBuy;
			}
		}
		
		return null;
	}
	
	/**
	 * Special finding method for checking enchanted book existence
	 * @param storeStock
	 * @param itemToBuy
	 * @param shop
	 * @return ItemStack representing correct enchanted book in chest, null on failure
	 */
	private ItemStack shopChestFindEnchantedBook(Inventory storeStock, ItemStack itemToBuy, Shop shop) {
		// special check for written books
		// this part does not check for quantity, just
		// that the item itself exists somewhere
		if(itemIsEnchantedBook(itemToBuy)) {
			ItemStack[] chestItems = storeStock.getContents();
			boolean success = true;
			for(int i = 0; i < chestItems.length; i++) {
				ItemStack temp = chestItems[i];
				// if the item in chest is 
				if(itemIsEnchantedBook(temp)) {
					if(temp.hasItemMeta() && temp.getItemMeta() instanceof EnchantmentStorageMeta) {
		        		EnchantmentStorageMeta tempMeta = (EnchantmentStorageMeta) temp.getItemMeta();
		        		EnchantmentStorageMeta buyMeta = (EnchantmentStorageMeta) itemToBuy.getItemMeta();
		        		
		        		if(tempMeta.hasStoredEnchants()) {
		        			if(!tempMeta.equals(buyMeta)) {
		        				success = false;
		        				continue;
		        			}

							if(success) {
								itemToBuy = chestItems[i].clone();
								itemToBuy.setAmount(1);
								break;
							}
		        		}
					}
				}
				success = true;
			}
		}
		
		if(storeStock instanceof DoubleChestInventory) {
			DoubleChestInventory dci = (DoubleChestInventory) storeStock;
			if(dci.getLeftSide().containsAtLeast(itemToBuy, shop.getQuantity())) {
				storeStock = dci.getLeftSide();
				return itemToBuy;
			}
			else if(dci.getRightSide().containsAtLeast(itemToBuy, shop.getQuantity())) {
				storeStock = dci.getRightSide();
				return itemToBuy;
			}
		}
		else{
			if(storeStock.containsAtLeast(itemToBuy, shop.getQuantity())) {
				return itemToBuy;
			}
		}
		
		return null;
	}
	
	@EventHandler
    public void onBlockBreak(BlockBreakEvent evt) {
    	Player player = evt.getPlayer();
    	Block  block  = evt.getBlock();
    	
    	if(blockIsSign(block)) {
    		Sign sign = (Sign) block.getState();
    		
    		if(signIsShop(sign) && !shopSignHasNoItem(sign)) {
				Location shopLocation = sign.getLocation();
				
				this.plugin.getMySQLHelper().getShopFromLocation(shopLocation).thenAccept(shop -> {
					if(shop != null) {
						this.plugin.getMySQLHelper().removeShop(player, shop).thenAccept(result -> {
							if(player.isOnline()) {
								if(result) {
									this.plugin.getSelectedShopController().removeShop(shop);
									player.sendMessage(ChatColor.AQUA + "Shop removed.");
								}
							}
						});
					}
				});
    		}
    	}
    }
	
	/**
	 * Checks whether a block is a sign.
	 * @param block
	 * @return True if block is sign, False otherwise
	 */
	private boolean blockIsSign(Block block) {
    	return block != null && (
    		    block.getType().equals(Material.ACACIA_WALL_SIGN)
    		 || block.getType().equals(Material.BIRCH_WALL_SIGN)
    		 || block.getType().equals(Material.CRIMSON_WALL_SIGN)
    		 || block.getType().equals(Material.DARK_OAK_WALL_SIGN)
    		 || block.getType().equals(Material.JUNGLE_WALL_SIGN)
    		 || block.getType().equals(Material.OAK_WALL_SIGN)
    		 || block.getType().equals(Material.SPRUCE_WALL_SIGN)
    		 || block.getType().equals(Material.WARPED_WALL_SIGN)
    		 );
    }
    
    /**
     * Checks whether a block is a chest, double chest, or barrel
     * (note that Enderchests are not checked)
     * @param block - Block to check
     * @return True if block is chest, False otherwise
     */
    private boolean blockIsStorage(Block block) {
    	return block != null && (block.getState() instanceof Chest || block.getState() instanceof DoubleChest || block.getState() instanceof Barrel);
    }
    
    private String materialPrettyPrint(Material material) {
    	String[] words = material.toString().split("_");
    	String output = "";
    	
    	for( String word : words) {
    		output += word.substring(0,1).toUpperCase() + word.substring(1).toLowerCase() + " ";
    	}
    	output = output.trim();
    	return output;
    }
    
	private boolean playerCanStoreItem(Player player, ItemStack itemToBuy, short quantity) {
		PlayerInventory inv = player.getInventory();
		int maxStackSize = itemToBuy.getMaxStackSize();

		for(ItemStack item : inv.getContents()) {
			if(item == null || itemIsAir(item)) {
				continue;
			}
			// check if the slot's amount + quantity is
			// less than or equal to 64 (full stack)
			else if(item.isSimilar(itemToBuy) && item.getAmount() + quantity <= maxStackSize) {
				return true;
			}
		}
		
		// otherwise, return if there's a free, empty slot
		return inv.firstEmpty() >= 0;
	}
    
    private boolean shopSignHasNoItem(Sign sign) {
    	return sign.getLine(1).equals(SHOP_SIGN_NO_ITEM);
    }

	/**
     * Displays a Scoreboard containing information related to
     * the shop the player has selected.
     * @param shopLocation Location of the selected shop sign
     */
    private void displayShopInformation(Player player, Shop shop) {
    	if(this.hideDisplayTasks.containsKey(player)) {
    		this.hideDisplayTasks.get(player).cancel();
    		this.hideDisplayTasks.replace(player, null);
    	}
    	
    	Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
    	Objective obj = board.registerNewObjective("DukesMart", "Shop", this.PLUGIN_NAME);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        ItemStack item = shop.getItem();
        Map<Enchantment, Integer> itemEnchantments = item.getEnchantments();
        ItemMeta  meta = item.getItemMeta();
        
        ArrayList<String> shopInfoElements = new ArrayList<String>();
        shopInfoElements.add("" + ChatColor.YELLOW + shop.getName());
        shopInfoElements.add("" + ChatColor.GRAY + "------------------------------------");
        shopInfoElements.add("" + ChatColor.RED + ChatColor.BOLD + "For sale");
        shopInfoElements.add(materialPrettyPrint(item.getType()));
        if(meta.hasDisplayName() || itemIsPlayerHead(item)) {
        	
        	if(itemIsPlayerHead(item)) {
        		Block signBlock = player.getWorld().getBlockAt(shop.getLocation());
        		
        		if(blockIsSign(signBlock)) {
        			Sign sign = (Sign) signBlock.getState();
        			shopInfoElements.add(truncateText(sign.getLine(1)));
        		}
        	}
        	else {
        		shopInfoElements.add(truncateText("" + ChatColor.GOLD + ChatColor.ITALIC + "\"" + meta.getDisplayName() + "\""));
        	}
        }
        
        if(itemIsFinishedBook(item)) {
        	BookMeta bookmeta = (BookMeta) meta;
        	
        	if(bookmeta.hasTitle()) {
        		shopInfoElements.add(truncateText("" + ChatColor.ITALIC + "\"" + bookmeta.getTitle() + "\""));
        	}
        	
        	if(bookmeta.hasAuthor()) {
        		shopInfoElements.add(" - by " + bookmeta.getAuthor());
        	}
        	
        	if(bookmeta.hasPages()) {
        		shopInfoElements.add(" - " + bookmeta.getPageCount() + " pages");
        	}
        }
        else if(itemIsFilledMap(item)) {
        	MapMeta mapmeta = (MapMeta) meta;
        	
        	if(mapmeta.hasMapView()) {
        		MapView mapview = mapmeta.getMapView();
        		
        		shopInfoElements.add(" - Map #" + mapview.getId());
        	}
        }
        else if(itemIsPotion(item) || itemIsTippedArrow(item)) {
        	shopInfoElements.add("" + ChatColor.AQUA + getPotionName(item));
        }
        else if(itemIsBanner(item) || itemIsShield(item)) {
        	List<Pattern> patterns = null;
        	
        	if(itemIsBanner(item) && item.getItemMeta() instanceof BannerMeta) {
	        	BannerMeta bannerMeta = (BannerMeta) item.getItemMeta();
	        	patterns = bannerMeta.getPatterns();
        	}
        	else if(item.getItemMeta() instanceof BlockStateMeta){
                BlockStateMeta bmeta = (BlockStateMeta) item.getItemMeta();
                if(bmeta instanceof Banner) {
	                Banner banner = (Banner) bmeta.getBlockState();
	                patterns = banner.getPatterns();
                }
        	}
        	
        	if(patterns != null) {
	        	for(Pattern pattern : patterns) {
	        		shopInfoElements.add(truncateText(" - " + prettyPrint(pattern.getColor().name()) + " " + prettyPrint(pattern.getPattern().name())));
	        	}
        	}
        }
        else if(itemIsEnchantedBook(item)) {
        	if(item.hasItemMeta() && item.getItemMeta() instanceof EnchantmentStorageMeta) {
        		EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) item.getItemMeta();

        		if(enchantMeta.hasStoredEnchants()) {      			
        			for(Entry<Enchantment, Integer> e : enchantMeta.getStoredEnchants().entrySet()) {
        				String enchant = e.getKey().getKey().toString().split(":")[1];
        	        	
        	        	shopInfoElements.add(" - " + ChatColor.ITALIC + prettyPrint(enchant) + " " + e.getValue());
        			}
        		}
        	}
        }
        
        for(Entry<Enchantment, Integer> entry : itemEnchantments.entrySet()) {
        	String enchantmentName = entry.getKey().getKey().toString().split(":")[1];

        	shopInfoElements.add(" - " + ChatColor.ITALIC + prettyPrint(enchantmentName) + " " + entry.getValue());
        } 
        shopInfoElements.add("" + ChatColor.RED + ChatColor.BOLD + "Quantity: " + ChatColor.WHITE + shop.getQuantity());
        shopInfoElements.add("" + ChatColor.RED + ChatColor.BOLD + "Cost: " + ChatColor.WHITE + shop.getPrice() + " Terral");
        shopInfoElements.add("" + ChatColor.GRAY + "------------------------------------");
        shopInfoElements.add("" + ChatColor.YELLOW + "To purchase, right click again.");

        int counter = shopInfoElements.size();
        for(String line : shopInfoElements) {
        	obj.getScore(shopGuiPad(line)).setScore(counter);
        	counter--;
        }

        player.setScoreboard(board);
        
        // Schedule scoreboard to be cleared in 30s
        if(!this.hideDisplayTasks.containsKey(player)) {
        	this.hideDisplayTasks.put(player, null);
        }
        
        
        BukkitTask clear = new HideShopDisplayTask(player).runTaskLater(this.plugin, 20*HIDE_SHOP_DISPLAY_SECONDS);
        this.hideDisplayTasks.replace(player, clear);
	}
    


	private String truncateText(String message) {
    	if(message.length() >= 38) {
    		return message.substring(0, 34) + "...";
    	}
    	else {
    		return message;
    	}
    }
    
    private String prettyPrint(String message) {
    	String[] words = message.split("_");
    	String  output = "";
    	
    	for( String word : words) {
    		output += word.substring(0,1).toUpperCase() + word.substring(1).toLowerCase() + " ";
    	}
    	output = output.trim();
    	return output;
    }
    
    private String shopGuiPad(String message) {
    	message += ChatColor.RESET;
    	
    	while(message.length() < 32) {
    		message += " ";
    	}
    	
    	return message;
    }
	/**
     * Returns the Location data of a shop sign
     * @param s Sign representing a shop
     * @return Sign location data
     */
    private Location getShopLocation(Sign s) {
		return s.getLocation();
	}

	/*
     * Helper methods below this comment.
     */
    
    /**
     * Sends an error message to the player.
     * @param p Player to send message to
     * @param message Message to output
     */
    private void sendError(Player player, String message) {
    	if(player.isOnline()) {
    		player.sendMessage(ChatColor.RED + message);
    	}
    }
    
    /**
     * Validates a shop sign's price tag to make
     * sure a player entered the format correctly.
     * 
     * Format is {quantity} " for " ${price}
     * 
     * @param line String to validate (should be 3rd line)
     * @return True if valid, False if not
     */
    private boolean validateShopPrice(String line) {
    	String[] tokens = line.split(" ");
    	if(tokens.length == 3) {
	    	// check the first token contains only integers
	    	if(!tokens[0].isEmpty()) {
	    		
	    		for(char c : tokens[0].toCharArray()) {
	    			if(!Character.isDigit(c)) {
	    				return false;
	    			}
	    		}
	    		
	    		int quantity_int = Integer.parseInt(tokens[0]);
	    		
	    		if(quantity_int < 1 || quantity_int > 64) {
	    			return false;
	    		}
	    	}
	    	// then, check if the word "for" is present
	    	if(!tokens[1].isEmpty() && !tokens[1].equalsIgnoreCase("for")) {
	    		return false;
	    	}
	    	
	    	// finally, check the format of the price tag
	    	// first char should be '$', rest should be digit
	    	if(!tokens[2].isEmpty()) {
	    		String price = tokens[2];
	    		if(price.length() > 1) {
		    		if(price.charAt(0) != '$') {
		    			return false;
		    		}
		    		
		    		int price_int = Integer.parseInt(price.substring(1));
		    		
		    		if(price_int < 0 || price_int > 1000000) {
		    			return false;
		    		}
	    		}
	    		else {
	    			return false;
	    		}
	    	}
	    	
	    	// everything appears to check out!
	    	return true;
    	}
    	else {
    		return false;
    	}
    }
    
    /**
     * Checks if a shop is valid. This is used to distinct
     * other player made signs from shop signs.
     * 
     * A shop sign is validated if the first line contains "[Buy]"
     * and the price/quantity information follows the appropriate
     * structure.
     * 
     * @param sign Sign created by player
     * @return True if sign represents a valid shop, false otherwise.
     */
    private boolean validateShopSignEntry(String[] lines) {
    	return lines[0].equalsIgnoreCase("[Buy]") && validateShopPrice(lines[2]);
    }
    /**
     * Checks if a sign object is a shop.
     * @param s Sign to check
     * @return True if sign, False otherwise
     */
    private boolean signIsShop(Sign sign) {
    	return sign.getLine(0).equals(SHOP_SIGN_IDENTIFIER) && validateShopPrice(sign.getLine(2));
    }
    
    /**
     * Checks if the player-entered quantity for an item exceeds that
     * allowed by the ItemStack.
     * 
     */
    private void updateSignQuantity(Player player, Sign sign, ItemStack item) {
    	String[] lines = sign.getLines();
    	String[] tokens = lines[2].split(" ");
    	int quantity = Integer.parseInt(tokens[0]);
    	int maxAllowed = item.getMaxStackSize();
    	
    	if(quantity > maxAllowed) {
    		sign.setLine(2, maxAllowed + " " + tokens[1] + " " + tokens[2]);
    		sign.update();
    		
    		if(player.isOnline()) {
    			player.sendMessage(String.format(MSG_ERROR_ITEM_CANNOT_EXCEED, prettyPrint(item.getType().name()), maxAllowed));
    		}
    	}
    }

    private boolean itemIsFinishedBook(ItemStack item) {
    	return item != null && item.getType().equals(XMaterial.WRITTEN_BOOK.parseMaterial());
    }
    
    private boolean itemIsWritableBook(ItemStack item) {
    	return item != null && item.getType().equals(XMaterial.WRITABLE_BOOK.parseMaterial());
    }
    
    private boolean itemIsAir(ItemStack item) {
    	return item != null && item.getType().equals(XMaterial.AIR.parseMaterial());
    }
    
    private boolean itemIsBanner(ItemStack item) {
    	return item != null && item.getType().name().contains("BANNER");
    }
    
    private boolean itemIsShield(ItemStack item) {
    	return item != null && item.getType().equals(XMaterial.SHIELD.parseMaterial());
    }
    
    private boolean itemIsPotion(ItemStack item) {
		return item != null && item.getType().name().contains("POTION");
	}
    
    private boolean itemIsFilledMap(ItemStack item) {
    	return item != null && item.getType().equals(XMaterial.FILLED_MAP.parseMaterial());
    }
    
    private boolean itemIsShulkerBox(ItemStack item) {
    	return item != null && item.getType().name().contains("SHULKER_BOX");
    }
    
    private boolean itemIsEnchantedBook(ItemStack item) {
    	return item != null && item.getType().equals(XMaterial.ENCHANTED_BOOK.parseMaterial());
    }
    
    private boolean itemIsTippedArrow(ItemStack item) {
    	return item != null && item.getType().equals(XMaterial.TIPPED_ARROW.parseMaterial());
    }
    
    private boolean itemIsPolishedBlackstone(ItemStack item) {
    	return item != null && item.getType().name().contains("POLISHED_BLACKSTONE");
    }
    
    private boolean itemIsDye(ItemStack item) {
    	return item != null && item.getType().name().contains("_DYE");
    }
    
    private boolean playerHoldingDye(Player player) {
    	if(player.isOnline()) {
    		ItemStack inHand = player.getInventory().getItemInMainHand();
    		
    		if(itemIsDye(inHand)) {
    			return true;
    		}
    	}
    	
    	return false;
    }
    private String getPotionName(ItemStack potion) {
    	PotionMeta meta = (PotionMeta) potion.getItemMeta();
    	String name = prettyPrint(meta.getBasePotionData().getType().name());
    	
    	if(meta.getBasePotionData().isUpgraded()) {
    		name += " II";
    	}
    	
    	if(meta.getBasePotionData().isExtended()) {
    		name += " (Extended)";
    	}
    	
    	return name;
    }
}