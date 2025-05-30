package io.github.bananapuncher714.crafters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import io.github.bananapuncher714.crafters.implementation.api.CraftInventoryManager;
import io.github.bananapuncher714.crafters.listeners.ChunkListener;
import io.github.bananapuncher714.crafters.listeners.CraftBlockListener;
import io.github.bananapuncher714.crafters.listeners.InventoryOpenListener;
import io.github.bananapuncher714.crafters.listeners.PlayerListener;
import io.github.bananapuncher714.crafters.util.ReflectionUtil;
import io.github.bananapuncher714.crafters.util.Utils;

/**
 * The main class of all classes;
 * Created on 2017-12-07
 * 
 * @author BananaPuncher714
 */
public class PublicCrafters extends JavaPlugin {
	private static PublicCrafters instance;

	private final Map< Material, EulerAngle > angles = new HashMap< Material, EulerAngle >();
	private final Map< Material, Vector > offsets = new HashMap< Material, Vector >();
	private final Set< UUID > privatePlayers = new HashSet< UUID >();
	private CraftInventoryManager manager;
	private double height = .7;
	private boolean marker = false;
	private boolean privateByDefault = false;
	private boolean virtual = false;
	private boolean dropItem = false;
	private int delay = 0;
	
	private boolean showResult = true;
	private boolean showResultName = true;
	private double resultHeight = .15;
	
	private final File saveFolder = new File( getDataFolder() + "/" + "saves" );

	private final Set< Location > adminTables = new HashSet< Location >();
	
	private final Map< String, String > messages = new HashMap< String, String >();
	
	//	private CakeListener cake;

	@Override
	public void onEnable() {
		instance = this;

		new Metrics( this );
		
		saveResource( "README.md", true );
		if ( !new File( getDataFolder(), "messages.yml" ).exists() ) {
            saveResource( "messages.yml", false );
        }
		saveDefaultConfig();
		
		loadConfig();
		loadMessages();
		
		manager = ReflectionUtil.getManager();
		getLogger().info( "Detected version '" + ReflectionUtil.getVersion() + "'" );
		
		registerListeners();
		registerCommands();

		// Update crafting tables a second later, since other plugins may load in custom recipes
		Bukkit.getScheduler().runTaskLater( this, () -> {
			loadChunks();
			loadData();
		}, 20 );
	}

	@Override
	public void onDisable() {
//		cake.stopAll();

		// Stop the player from duplicating items on server reload
		for ( Player player : Bukkit.getOnlinePlayers() ) {
			InventoryView view = player.getOpenInventory();
			if ( view == null || manager.getLocation( view.getTopInventory() ) == null ) {
				continue;
			}
			player.closeInventory();
		}
		// Save the inventories
		unloadChunks();
		saveData();
	}

	private void unloadChunks() {
		for ( World world : Bukkit.getWorlds() ) {
			for ( Chunk chunk : world.getLoadedChunks() ) {
				manager.unload( chunk );
			}
		}
//		manager.stopAll();
	}

	private void loadChunks() {
		for ( World world : Bukkit.getWorlds() ) {
			for ( Chunk chunk : world.getLoadedChunks() ) {
				Map< Location, List< ItemStack > > itemMap = CraftInventoryLoader.loadChunk( PublicCrafters.getInstance().getSaveFolder(), chunk.getWorld(), chunk.getX(), chunk.getZ() );
				for ( Location location : itemMap.keySet() ) {
					manager.load( location, itemMap.get( location ) );
				}
			}
		}
	}
	
	private void loadData() {
		File file = new File( getDataFolder() + "/" + "data.yml" );
		if ( !file.exists() ) {
			return;
		}
		FileConfiguration data = YamlConfiguration.loadConfiguration( file );
		if ( data.contains( "admin-tables" ) ) {
			for ( String loc : data.getStringList( "admin-tables" ) ) {
				adminTables.add( Utils.getLocationFromString( loc ) );
			}
		}
		
		if ( data.contains( "non-default" ) ) {
			for ( String uuidString : data.getStringList( "non-default" ) ) {
				UUID uuid = UUID.fromString( uuidString );
				privatePlayers.add( uuid );
			}
		}
	}
	
	private void saveData() {
		File file = new File( getDataFolder() + "/" + "data.yml" );
		if ( !file.exists() ) {
			try {
				file.createNewFile();
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
		FileConfiguration data = YamlConfiguration.loadConfiguration( file );
		
		List< String > tableStrings = new ArrayList< String >();
		for ( Location location : adminTables ) {
			tableStrings.add( Utils.getStringFromLocation( location ) );
		}
		data.set( "admin-tables", tableStrings );
		
		data.set( "non-default", privatePlayers.stream().map( UUID::toString ).collect( Collectors.toList() ) );
			
		try {
			data.save( file );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}
	
	public void reload() {
		loadConfig();
		loadMessages();
	}

	private void registerListeners() {
		Bukkit.getPluginManager().registerEvents( new InventoryOpenListener( this ), this );
		Bukkit.getPluginManager().registerEvents( new CraftBlockListener( this ), this );
		Bukkit.getPluginManager().registerEvents( new ChunkListener( manager ), this );
		Bukkit.getPluginManager().registerEvents( new PlayerListener( this ), this );
//		Bukkit.getPluginManager().registerEvents( new CraftingListener(), this );
//		cake = new CakeListener();
//		Bukkit.getPluginManager().registerEvents( cake, this );
	}

	private void registerCommands() {
		CraftingCommand command = new CraftingCommand( this );
		getCommand( "craftingtable" ).setExecutor( command );
		getCommand( "craftingtable" ).setTabCompleter( command );
	}

	private void loadConfig() {
		FileConfiguration config = YamlConfiguration.loadConfiguration( new File( getDataFolder() + "/" + "config.yml" ) );
		height = config.getDouble( "item-height" );
		marker = config.getBoolean( "marker" );
		delay = config.getInt( "update-delay" );
		privateByDefault = config.getBoolean( "private-by-default" );
		dropItem = config.getBoolean( "drop-item" );
		virtual = config.getBoolean( "virtual" );
		showResult = config.getBoolean( "display-crafted-item", true );
		showResultName = config.getBoolean( "display-crafted-item-name", true );
		resultHeight = config.getDouble( "crafted-item-height", .15 );
		
		angles.clear();
		if ( config.getConfigurationSection( "orientation" ) != null ) {
			for ( String key : config.getConfigurationSection( "orientation" ).getKeys( false ) ) {
				Material mat = Utils.getMaterialFromString( key );
				if ( mat == null ) {
					getLogger().warning( "Invalid material found in the config: '" + key + "'" );
					continue;
				}

				String data = config.getString( "orientation." + key );
				String[] orientation = data.split( "[^\\d.-]+" );
				if ( orientation.length != 3 ) {
					getLogger().warning( "Invalid orientation for material '" + key + "':'" + data + "'" );
					continue;
				}
				try {
					EulerAngle angle = new EulerAngle( Math.toRadians( Double.parseDouble( orientation[ 0 ] ) ), Math.toRadians( Double.parseDouble( orientation[ 1 ] ) ), Math.toRadians( Double.parseDouble( orientation[ 2 ] ) ) );
					angles.put( mat, angle );
				} catch ( Exception exception ) {
					getLogger().warning( "Invalid orientation for material '" + key + "':'" + data + "'" );
				}
			}
		}
		offsets.clear();
		if ( config.getConfigurationSection( "offset" ) != null ) {
			for ( String key : config.getConfigurationSection( "offset" ).getKeys( false ) ) {
				Material mat = Utils.getMaterialFromString( key );

				if ( mat == null ) {
					getLogger().warning( "Invalid material found in the config: '" + key + "'" );
					continue;
				}

				String data = config.getString( "offset." + key );
				String[] orientation = data.split( "[^\\d.-]+" );
				if ( orientation.length != 3 ) {
					getLogger().warning( "Invalid offset for material '" + key + "':'" + data + "'" );
					continue;
				}
				try {
					Vector vector = new Vector( Double.parseDouble( orientation[ 0 ] ), Double.parseDouble( orientation[ 1 ] ), Double.parseDouble( orientation[ 2 ] ) );
					offsets.put( mat, vector );
				} catch ( Exception exception ) {
					getLogger().warning( "Invalid offset for material '" + key + "':'" + data + "'" );
				}
			}
		}
	}

	private void loadMessages() {
		messages.clear();
		FileConfiguration config = YamlConfiguration.loadConfiguration( new File( getDataFolder() + "/" + "messages.yml" ) );
		for ( String key : config.getKeys( true ) ) {
			messages.put( key, ChatColor.translateAlternateColorCodes( '&', config.getString( key ) ) );
		}
	}
	
	public boolean isPrivate( UUID playerUUID ) {
		return privatePlayers.contains( playerUUID );
	}

	public void setPrivate( UUID uuid, boolean pr ) {
		if ( pr ) {
			privatePlayers.add( uuid );
		} else if ( privatePlayers.contains( uuid ) ) {
			privatePlayers.remove( uuid );
		}
	}

	public CraftInventoryManager getManager() {
		return manager;
	}

	public static PublicCrafters getInstance() {
		return instance;
	}

	public double getHeight() {
		return height;
	}

	public boolean isMarker() {
		return marker;
	}

	public boolean isPrivateByDefault() {
		return privateByDefault;
	}
	
	public boolean isDropItem() {
		return dropItem;
	}

	public boolean isVirtual() {
		return virtual;
	}
	
	public boolean isShowResult() {
		return showResult;
	}
	
	public boolean isShowResultName() {
		return showResultName;
	}
	
	public double getResultHeight() {
		return resultHeight;
	}

	public int getUpdateDelay() {
		return delay;
	}

	public File getSaveFolder() {
		return saveFolder;
	}

	public EulerAngle getAngleForMaterial( Material material ) {
		return angles.get( material );
	}

	public Vector getOffsetForMaterial( Material material ) {
		return offsets.get( material );
	}
	
	public Set< Location > getAdminTables() {
		return adminTables;
	}
	
	public String getMessageFor( String key ) {
		return messages.getOrDefault( key, "" );
	}
}
