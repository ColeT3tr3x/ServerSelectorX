package xyz.derkades.serverselectorx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import xyz.derkades.derkutils.Cooldown;
import xyz.derkades.derkutils.ListUtils;
import xyz.derkades.derkutils.bukkit.Colors;
import xyz.derkades.derkutils.bukkit.IconMenu;
import xyz.derkades.derkutils.bukkit.ItemBuilder;

public class SelectorMenu extends IconMenu {
	
	private FileConfiguration config;
	private Player player;
	private int slots;
	
	private BukkitTask refreshTimer;
	
	public SelectorMenu(Player player, FileConfiguration config) {
		super(Main.getPlugin(), Colors.parseColors(config.getString("title", UUID.randomUUID().toString())), 9, player);
		this.config = config;
		this.player = player;
		
		this.slots = config.getInt("rows", 6) * 9;
		setSize(slots);
	}

	@Override
	public void open() {
		addItems();
		
		Cooldown.addCooldown(config.getName() + player.getName(), 0); //Remove cooldown if menu opened successfully
		super.open();
		
		refreshTimer = Bukkit.getScheduler().runTaskTimer(Main.getPlugin(), () -> {
			addItems();
			super.refreshItems();
		}, 1*20, 1*20);
	}

	private void addItems() {	
		for (final String key : config.getConfigurationSection("menu").getKeys(false)) {
			final ConfigurationSection section = config.getConfigurationSection("menu." + key);
			
			String materialString = "STONE";
			int data = 0;
			String name = "";
			List<String> lore = new ArrayList<>();
			int amount = 1;
			boolean enchanted = false;
			
			String action = section.getString("action");
			
			if (action.startsWith("srv")) {
				String serverName = action.substring(4);
				
				if (Main.isOnline(serverName)) {
					Map<String, String> placeholders = Main.PLACEHOLDERS.get(serverName);
					
					boolean dynamicMatchFound = false;
					
					if (section.contains("dynamic")) {
						for (String dynamic : section.getConfigurationSection("dynamic").getKeys(false)) {
							String placeholder = dynamic.split(":")[0];
							String result = dynamic.split(":")[1];
							
							if (!placeholders.containsKey(placeholder)) {
								Main.getPlugin().getLogger().warning("Dynamic feature contains rule with placeholder " + placeholder + " which has not been received from the server.");
								continue;
							}
							
							if (placeholders.get(placeholder).equals(result)) {
								//Placeholder result matches with placeholder result in rule
								dynamicMatchFound = true;
								ConfigurationSection dynamicSection = section.getConfigurationSection("dynamic." + dynamic);
								
								materialString = dynamicSection.getString("item");
								data = dynamicSection.getInt("data", 0);
								name = dynamicSection.getString("name");
								lore = dynamicSection.getStringList("lore");
								enchanted = dynamicSection.getBoolean("enchanted", false);
							}
						}
					}
					
					if (!dynamicMatchFound) {
						//No dynamic rule matched, fall back to online
						materialString = section.getString("online.item");
						data = section.getInt("online.data", 0);
						name = section.getString("online.name", "error");
						lore = section.getStringList("online.lore");
						enchanted = section.getBoolean("online.enchanted", false);
					}
					
					for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
						List<String> newLore = new ArrayList<>();
						for (String string : lore) {
							newLore.add(string.replace("{" + placeholder.getKey() + "}", placeholder.getValue()));
						}
						lore = newLore;
						
						name = name.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
					}
					
					if (section.getBoolean("dynamic-item-count", false)) {
						if (!placeholders.containsKey("online")) {
							Main.getPlugin().getLogger().warning("Dynamic item count is enabled but player count is unknown.");
							Main.getPlugin().getLogger().warning("Is the PlayerCount addon installed?");
						} else {
							amount = Integer.parseInt(placeholders.get("online"));
						}
					} else {
						amount = section.getInt("item-count", 1); 
					}
				} else {
					//Server is offline
					ConfigurationSection offlineSection = section.getConfigurationSection("offline");
					
					materialString = offlineSection.getString("item");
					data = offlineSection.getInt("data", 0);
					name = offlineSection.getString("name");
					lore = offlineSection.getStringList("lore");
					enchanted = offlineSection.getBoolean("enchanted", false);
					amount = section.getInt("item-count", 1); 
				}
				
			} else if (action.startsWith("sel:")) {
				//Add all online counts of servers in the submenu
				int totalOnline = 0;
				
				FileConfiguration subConfig = Main.getConfigurationManager().getByName(action.substring(4));
				for (final String subKey : subConfig.getConfigurationSection("menu").getKeys(false)){
					final ConfigurationSection subSection = subConfig.getConfigurationSection("menu." + subKey);
					String subAction = subSection.getString("action");
					if (!subAction.startsWith("srv:")) 
						continue;
					
					String serverName = subAction.substring(4);
					
					if (!Main.PLACEHOLDERS.containsKey(serverName)) {
						continue;
					}
					
					Map<String, String> placeholders = Main.PLACEHOLDERS.get(serverName);
					if (placeholders.containsKey("online")) {
						totalOnline += Integer.parseInt(placeholders.get("online"));
					}
				}
				
				materialString = section.getString("item");
				data = section.getInt("data", 0);
				name = section.getString("name", "error");
				lore = ListUtils.replaceInStringList(section.getStringList("lore"), new Object[] {"{total}"}, new Object[] {totalOnline});
				enchanted = section.getBoolean("enchanted", false);
			} else {
				//Not a server
				materialString = section.getString("item");
				data = section.getInt("data", 0);
				name = section.getString("name", "error");
				lore = section.getStringList("lore");
				enchanted = section.getBoolean("enchanted", false);
			}
			
			final ItemBuilder builder;
			
			if (materialString.startsWith("head:")) {
				String owner = materialString.split(":")[1];
				if (owner.equals("auto")) {
					builder = new ItemBuilder(player.getName());
				} else {
					builder = new ItemBuilder(owner);
				}
			} else {
				Material material = Material.valueOf(materialString);
				if (material == null) material = Material.STONE;
				
				builder = new ItemBuilder(material);
				builder.data(data);
			}
			
			name = name.replace("{player}", player.getName());
			lore = ListUtils.replaceInStringList(lore, new Object[] {"{player}"}, new Object[] {player.getName()});
			
			builder.amount(amount);
			builder.name(Main.PLACEHOLDER_API.parsePlaceholders(player, name));
			builder.lore(Main.PLACEHOLDER_API.parsePlaceholders(player, lore));

			int slot = Integer.valueOf(key);
			
			ItemStack item = builder.create();
			
			if (enchanted) item = Main.addGlow(item);
			
			if (slot < 0) {
				for (int i = 0; i < slots; i++) {
					if (!items.containsKey(i)) {
						items.put(i, item);
					}
				}
			} else {
				items.put(slot, item);
			}
		}
	}

	@Override
	public boolean onOptionClick(OptionClickEvent event) {		
		int slot = event.getPosition();
		Player player = event.getPlayer();
		
		final boolean permissionsEnabled = Main.getPlugin().getConfig().getBoolean("per-icon-permissions");
		final boolean hasPermission = player.hasPermission("ssx.icon." + config.getName().replace(".yml", "") + "." + slot);
		final boolean hasWildcardPermission = player.hasPermission("ssx.icon." + config.getName().replace(".yml", "") + ".*");
		
		if (permissionsEnabled && !hasPermission && !hasWildcardPermission) {
			return true;
		}
		
		String action = config.getString("menu." + slot + ".action");
		
		if (action == null) {
			//If the action is null (so 'slot' is not found in the config) it is probably a wildcard
			action = config.getString("menu.-1.action");
			
			if (action == null) { //If it is still null it must be missing
				action = "msg:Action missing";
			}
		}
		
		if (action.startsWith("url:")){ //Send url message
			String url = action.substring(4);
			String message = Colors.parseColors(config.getString("url-message", "&3&lClick here"));
			
			player.spigot().sendMessage(
					new ComponentBuilder(message)
					.event(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
					.create()
					);
			return true;
		} else if (action.startsWith("cmd:")){ //Execute command
			String command = action.substring(4);
			
			//Send command 2 ticks later to let the GUI close first (for commands that open a GUI)
			Bukkit.getScheduler().scheduleSyncDelayedTask(Main.getPlugin(), () -> {
				Bukkit.dispatchCommand(player, Main.PLACEHOLDER_API.parsePlaceholders(player, command));
			}, 2);
			return true;
		} else if (action.startsWith("consolecmd:")) {
			String command = action.substring(11);
			command = command.replace("{player}", player.getName());
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Main.PLACEHOLDER_API.parsePlaceholders(player, command));
			return true;
		} else if (action.startsWith("bungeecmd:")) { //BungeeCord command
			String command = action.substring(10);
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("sync player %s %s", player.getName(), Main.PLACEHOLDER_API.parsePlaceholders(player, command)));
			return true;
		} else if (action.startsWith("sel:")){ //Open selector
			String configName = action.substring(4);
			FileConfiguration config = Main.getConfigurationManager().getByName(configName);
			if (config == null){
				player.sendMessage(ChatColor.RED + "This server selector does not exist.");
				return true;
			} else {				
				new SelectorMenu(player, config).open();
				
				return false;
			}
		} else if (action.startsWith("world:")){ //Teleport to world
			String worldName = action.substring(6);
			World world = Bukkit.getWorld(worldName);
			if (world == null){
				player.sendMessage(ChatColor.RED + "A world with the name " + worldName + " does not exist.");
				return true;
			} else {
				player.teleport(world.getSpawnLocation());
				return true;
			}
		} else if (action.startsWith("srv:")){ //Teleport to server
			String serverName = action.substring(4);
			
			// If offline-cancel-connect is turned on and the server is offline, send message and cancel connecting
			if (Main.getConfigurationManager().getConfig().getBoolean("offline-cancel-connect", false) && !Main.isOnline(serverName)) {
				player.sendMessage(Colors.parseColors(Main.getConfigurationManager().getConfig().getString("offline-cancel-connect-message", "error")));
				return true;
			}
			
			Main.teleportPlayerToServer(player, serverName);
			return true;
		} else if (action.equalsIgnoreCase("toggleInvis")) {
			if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
				player.removePotionEffect(PotionEffectType.INVISIBILITY);
			} else {
				player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false));
			}
			return true;
		} else if (action.equalsIgnoreCase("toggleSpeed")) {
			if (player.hasPotionEffect(PotionEffectType.SPEED)) {
				player.removePotionEffect(PotionEffectType.SPEED);
			} else {
				int amplifier = Main.getConfigurationManager().getConfig().getInt("speed-amplifier", 3);
				player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, amplifier, true, false));
			}
			return true;
		} else if (action.startsWith("msg:")){ //Send message
			String message = action.substring(4);
			player.sendMessage(Main.PLACEHOLDER_API.parsePlaceholders(player, message));
			return true;
		} else if (action.equals("close")){ //Close selector
			return true; //Return true = close
		} else {
			return false; //Return false = stay open
		}
	
	}
	
	@Override
	public void onClose(MenuCloseEvent event) {
		if (refreshTimer != null) refreshTimer.cancel();
	}

}
