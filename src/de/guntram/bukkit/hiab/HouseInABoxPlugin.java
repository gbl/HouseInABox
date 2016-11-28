package de.guntram.bukkit.hiab;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.data.DataException;
import com.sk89q.worldedit.schematic.SchematicFormat;
import java.io.File;
import java.io.IOException;
import static java.lang.Math.abs;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class HouseInABoxPlugin extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private String metaName;
    private Material magicBlock;
    private Logger logger;
    private int minHeight, maxHeight;
    private int maxBlocks;
    private Plugin griefPrevention;
    private int griefPreventionClaimDistance;
    private boolean fixWorldEditDoorGateRotation;
    private boolean showFireWorksWhileBuilding;
    private int buildDelayTicks;
    private String startMessage, endMessage;
    
    static final int TICKSPERSEC=20;
    
    @Override
    public void onEnable() {
        logger=getLogger();
        saveDefaultConfig();
        config=getConfig();
        metaName=config.getString("displayname", "House in a Box");
        fixWorldEditDoorGateRotation=config.getBoolean("fixworldeditdoorgaterotation", false);
        showFireWorksWhileBuilding=config.getBoolean("fireworks", true);
        magicBlock=Material.getMaterial(config.getString("magicblock", "BEDROCK"));
        if (magicBlock==null) {
            logger.warning("Cannot parse material name. Resorting to bedrock.");
            magicBlock=Material.BEDROCK;
        }
        maxBlocks=config.getInt("maxblocks", 30*30*20);
        minHeight=config.getInt("minheight", 5);        // no pasting to bedrock
        maxHeight=config.getInt("maxheight", 120);      // no pasting in sky or nether top
        griefPrevention = getServer().getPluginManager().getPlugin("GriefPrevention");
        griefPreventionClaimDistance=config.getInt("claimdistance", 100);
        buildDelayTicks=config.getInt("delayticks", TICKSPERSEC);
        startMessage=config.getString("startmessage", "Building your %");
        endMessage=config.getString("endmessage", "Enjoy your %!");
        this.getServer().getPluginManager().registerEvents(this, this);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.ANVIL) {
            ItemStack is=event.getCurrentItem();
            if (is==null) return;
            ItemMeta im=is.getItemMeta();
            if (im==null) return;
            String name=im.getDisplayName();
            if (name==null) return;
            if (name.startsWith(metaName)) {
                event.getWhoClicked().sendMessage("You can't craft build boxes");
                event.setResult(Event.Result.DENY);
            }
        }
    }

    // run the handler twice; once before GP, and once after GP
    @EventHandler(priority=EventPriority.NORMAL)
    public void onPlaceBlockNormal(BlockPlaceEvent event) {
        onPlaceBlock(event, 1);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPlaceBlockHighest(BlockPlaceEvent event) {
        onPlaceBlock(event, 2);
    }

    public void onPlaceBlock(BlockPlaceEvent event, int round) {
        if (event.isCancelled())
            return;
        Player player=event.getPlayer();
        if (player==null)
            return;
        Block block=event.getBlockPlaced();
        if (block==null || block.getType()!=magicBlock)
            return;
        ItemStack item=event.getItemInHand();
        if (item==null)
            return;
        ItemMeta meta=item.getItemMeta();
        String name=meta.getDisplayName();
        if (name==null)
            return;
        int metaPos=name.indexOf(metaName+": ");
        if (metaPos>=0) {
            Location locPlayer=player.getLocation();
            Location locBlock=block.getLocation();
            
            if (round==1) {
                if (locBlock.getBlockY() <= minHeight
                ||  locBlock.getBlockY() >= maxHeight) {
                    player.sendMessage("You can't build at that level");
                    event.setCancelled(true);
                    return;
                }

                if (griefPrevention!=null && isCloseToClaim(player, block.getLocation())) {
                    player.sendMessage("Too close to a claim you don't have build permission in!");
                    event.setCancelled(true);
                    return;
                }
            }
            
            if (round==2) {
                String schematicName=name.substring(metaPos+metaName.length()+2);
                int rotation;
                locPlayer.subtract(locBlock);
                if (abs(locPlayer.getBlockX()) > abs(locPlayer.getBlockZ())) {
                    if (locPlayer.getBlockX() > 0)
                        rotation=270;
                    else
                        rotation=90;
                } else {
                    if (locPlayer.getBlockZ() > 0)
                        rotation=0;
                    else
                        rotation=180;
                }
                if (!startMessage.isEmpty()) {
                    player.sendMessage(startMessage.replaceAll("%", schematicName));
                }
                if (!build(event.getBlock().getLocation(), schematicName, rotation, player)) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        String commandName=cmd.getName();
        if (!(commandName.equals("hiab")))
            return false;
        if (args.length==2 && args[0].equals("give")) {
            if (!(sender instanceof Player))
                sender.sendMessage("This command needs a player name");
            else
                give((Player)sender, args[1]);
            return true;
        }
        if (args.length==3 && args[0].equals("give")) {
            Player player=Bukkit.getPlayer(args[2]);
            if (player==null)
                sender.sendMessage("Player "+args[2]+" not found");
            else
                give(player, args[1]);
            return true;
        }
        return false;
    }

    private boolean give(Player player, String arg) {
        ItemStack itemStack=new ItemStack(magicBlock, 1);
        ItemMeta meta=itemStack.getItemMeta();
        meta.setDisplayName(metaName+": "+arg);
        itemStack.setItemMeta(meta);
        HashMap<Integer, ItemStack> notGiven = player.getInventory().addItem(itemStack);
        if (notGiven!=null && !notGiven.isEmpty()) {
            return false;
        }
        return true;
    }

    private boolean build(Location location, String schematicName, int rotation, Player player) {
        Biome biome=location.getBlock().getBiome();
        File file=new File(this.getDataFolder(), schematicName+"-"+biome.toString().toLowerCase()+".schematic");
        if (!(file.exists()))
            file=new File(this.getDataFolder(), schematicName+".schematic");
        if (!(file.exists())) {
            logger.log(Level.INFO, "File "+file.getAbsolutePath()+" not found!");
            return false;
        }
        SchematicFormat format=SchematicFormat.MCEDIT;
        CuboidClipboard clip;
        try {
            clip=format.load(file);
        } catch (IOException | DataException ex) {
            logger.log(Level.INFO, ex.getMessage(), ex);
            return false;
        }
        
        clip.rotate2D(rotation);
        
        Vector vector=new Vector(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        BukkitWorld world=new BukkitWorld(location.getWorld());
        EditSession session=new EditSession(world, maxBlocks);

        AsyncBuilder builder=new AsyncBuilder(this, clip, 
                vector.add(clip.getOffset()), session, rotation, location,
                player,
                endMessage==null ? null : endMessage.replaceAll("%", schematicName)
        );
        int task=getServer().getScheduler().scheduleSyncRepeatingTask(this, builder, buildDelayTicks, buildDelayTicks);
        builder.setTaskID(task);
        return true;
    }

    private boolean isCloseToClaim(Player player, Location location) {
        Collection<Claim> claims = GriefPrevention.instance.dataStore.getClaims();
        for (Claim claim: claims) {
            Location edge1=claim.getLesserBoundaryCorner();
            if (edge1.getWorld()!=location.getWorld())
                continue;
            Location edge2=claim.getGreaterBoundaryCorner();
            String msg;
            if (edge1.getBlockX()-griefPreventionClaimDistance < location.getBlockX()
            &&  edge1.getBlockZ()-griefPreventionClaimDistance < location.getBlockZ()
            &&  edge2.getBlockX()+griefPreventionClaimDistance > location.getBlockX()
            &&  edge2.getBlockZ()+griefPreventionClaimDistance > location.getBlockZ()
            &&  (msg=claim.allowBuild(player, Material.STONE))!=null) {
                //player.sendMessage(msg);
                //player.sendMessage(
                //        "x1="+edge1.getBlockX()+" z1="+edge1.getBlockZ()+
                //        " x2="+edge2.getBlockX()+" z2="+edge2.getBlockZ()+
                //        " claimdistance="+griefPreventionClaimDistance +
                //        " owner:"+claim.getOwnerName()+" id:"+claim.getID()
                //);
                return true;
            }
        }
        return false;
    }
    
    public boolean getFixWorldEditDoorGateRotation() {
        return fixWorldEditDoorGateRotation;
    }
    
    public boolean getShowFireWorks() {
        return showFireWorksWhileBuilding;
    }
}
