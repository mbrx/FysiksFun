package mbrx.ff;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;

/** Makes some extra checks on entities, such as expiring items inside a block */
public class ExtraEntityBehaviours {

  public static void doTick(World w) {
    if (w != null) return;

    /* Make sure no squids can spawn... any time! */
    /*for (BiomeGenBase biome : BiomeGenBase.biomeList) {
      if (biome == null) continue;
      LinkedList<SpawnListEntry> toRemove = new LinkedList<SpawnListEntry>();
      List<SpawnListEntry> spawnable = (List<SpawnListEntry>) biome.getSpawnableList(EnumCreatureType.waterCreature);
      for (SpawnListEntry spawnEntry : spawnable) {
        if (spawnEntry.entityClass == EntitySquid.class) {
          System.out.println("Removing the squid spawn entry!");
          toRemove.add(spawnEntry);
        }
      }
      spawnable.removeAll(toRemove);
    }*/

    /*
    loopOverEntities:
    for (Entity e : ((List<Entity>) w.loadedEntityList)) {

      if (!(e instanceof EntityItem) && !(e instanceof EntityLivingBase)) continue;

      if (e instanceof EntityItem) {
        nItems++;
      }
      if (e instanceof EntitySquid) {
        nSquids++;
      }

      if (!(e instanceof EntityPlayer)) {
        double speedSq = e.motionX * e.motionX + e.motionY * e.motionY + e.motionZ * e.motionZ;
        if (speedSq > 20.0 * 20.0) {
          e.motionX = 0.0;
          e.motionY = 0.0;
          e.motionZ = 0.0;
          System.out.println("Removing " + e + " since he's moving too fast");
          w.removeEntity(e);
          continue;
        }
      }

      AxisAlignedBB bb = e.boundingBox;
      for (int corner = -1; corner < 8; corner++) {
        int x, y, z;
        if (corner == -1) {
          x = (int) e.posX;
          y = (int) e.posY;
          z = (int) e.posZ;
        } else {
          x = (int) ((corner & 1) == 0 ? bb.minX : bb.maxX);
          y = (int) ((corner & 2) == 0 ? bb.minY : bb.maxY);
          z = (int) ((corner & 4) == 0 ? bb.minZ : bb.maxZ);
        }
        Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
        if (c == null) continue;
        int id = c.getBlockID(x & 15, y, z & 15);
        if (id == 0 || Fluids.isLiquid[id] || Gases.isGas[id]) continue;

        e.motionX = 0.0;
        e.motionY = 0.0;
        e.motionZ = 0.0;

        if (e instanceof EntityItem) {
          //if(!Block.blocksList[id].isOpaqueCube()) continue;
          System.out.println("Removing entity item" + e + " inside a solid block");
          w.removeEntity(e);
          continue loopOverEntities;
        }
        if (e instanceof EntitySquid) {
          System.out.println("Removing a living entity " + e + " inside a solid block");
          w.removeEntity(e);
          continue loopOverEntities;
        }

      }
      
    }
    */
  }

}
