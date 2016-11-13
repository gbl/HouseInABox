/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.guntram.bukkit.hiab;

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

/**
 *
 * @author gbl
 */
public class AsyncBuilder implements Runnable {
    private int taskID;
    private int currentTurn;
    private int currentHeight;
    private final HouseInABoxPlugin plugin;
    private final CuboidClipboard clipboard;
    private final Vector position;
    private final EditSession session;
    private final int rotation;
    private final Location location;
    private final Player player;
    private final String doneMessage;

    private static Material[] matValues;
    private static Vector up;

    private final int INVALIDTASKID=-1;
    
    AsyncBuilder(HouseInABoxPlugin plugin, CuboidClipboard clipboard, 
            Vector position, EditSession session, int rotation, 
            Location location, Player player, String doneMessage) {
        this.plugin=plugin;
        this.clipboard=clipboard;
        this.position=position;
        this.session=session;
        this.rotation=rotation;
        this.location=location;
        this.player=player;
        this.doneMessage=doneMessage;
        if (matValues==null)
            matValues=Material.values();
        if (up==null)
            up=new Vector(0, 1, 0);
        currentTurn=currentHeight=0;
        taskID=INVALIDTASKID;
    }
    
    public void setTaskID(int id) {
        taskID=id;
    }

    @Override
    public void run() {
        // Just to make sure the async scheduler doesn't call us before our
        // creator had a chance to set the task id ...
        if (taskID==INVALIDTASKID)
            return;
        if (currentTurn==2) {
            if (player!=null && doneMessage!=null)
                player.sendMessage(doneMessage);
            plugin.getServer().getScheduler().cancelTask(taskID);
            return;
        }
        
        if (plugin.getShowFireWorks()) {
            Firework fw=(Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK);
            FireworkMeta fwm=fw.getFireworkMeta();
            FireworkEffect fwe=FireworkEffect.builder()
                    .flicker(true)
                    .withColor(currentTurn==0 ? Color.RED : Color.GREEN)
                    .with(currentTurn==0 ? Type.BURST : Type.CREEPER)
                    .trail(true)
                    .build();
            fwm.addEffect(fwe);
            fwm.setPower(2);
            fw.setFireworkMeta(fwm);
            
        }
        // This is, more or less, copied. from worldEdit's CuboidClipboard.paste
        // going bottom to up and solid blocks first. Also, we avoid
        // AsyncWorldEdit and it's physics.
        try {
            for (int x=0; x<clipboard.getSize().getBlockX(); x++) {
                for (int z=0; z<clipboard.getSize().getBlockZ(); z++) {
                    Vector where=new Vector(x, currentHeight, z);
                    BaseBlock block=clipboard.getBlock(where);
                    if (block==null)
                        continue;
                    Material material=matValues[block.getId()];
                    boolean placeInFirstRound=material.isOccluding();

                    if ((currentTurn==0) == placeInFirstRound) {
                        if (plugin.getFixWorldEditDoorGateRotation() && rotation!=0) {
                            if (
                                    material==Material.SPRUCE_FENCE_GATE
                                ||  material==Material.BIRCH_FENCE_GATE
                                ||  material==Material.JUNGLE_FENCE_GATE
                                ||  material==Material.DARK_OAK_FENCE_GATE
                                ||  material==Material.ACACIA_FENCE_GATE
                                ) {
                                    int data=block.getData();
                                    switch (rotation) {
                                        case  90: block.setData(((data+3)&3) | (data&~3)); break;
                                        case 180: block.setData(((data+2)&3) | (data&~3)); break;
                                        case 270: block.setData(((data+1)&3) | (data&~3)); break;
                                    }
                            }

                            if (
                                    material==Material.SPRUCE_DOOR
                                ||  material==Material.BIRCH_DOOR
                                ||  material==Material.JUNGLE_DOOR
                                ||  material==Material.DARK_OAK_DOOR
                                ||  material==Material.ACACIA_DOOR
                                ) {
                                    int data=block.getData();
                                    if ((data&8)==0) {
                                        switch (rotation) {
                                            case  90: block.setData(((data+1)&3) | (data&~3)); break;
                                            case 180: block.setData(((data+2)&3) | (data&~3)); break;
                                            case 270: block.setData(((data+3)&3) | (data&~3)); break;
                                        }
                                    }
                            }
                        }
                        if (material==Material.DOUBLE_PLANT) {
                            // Build double plants at once to stop the single
                            // lower part from breaking
                            int data=block.getData();
                            if ((data&8)==0) {
                                session.setBlock(where.add(position), block);
                                block.setData(data|8);
                                session.setBlock(where.add(position).add(up), block);
                            }
                        } else {
                            session.setBlock(where.add(position), block);
                        }
                    }
                }
            }
            if (++currentHeight>=clipboard.getHeight()) {
                currentHeight=0;
                currentTurn++;
            }
        } catch (MaxChangedBlocksException ex) {
            session.undo(session);
            plugin.getServer().getScheduler().cancelTask(taskID);
        }
    }
}
