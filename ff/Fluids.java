package mbrx.ff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldInfo;
import cpw.mods.fml.common.registry.GameRegistry;

public class Fluids {
  public static BlockFluid flowingWater, stillWater;
  public static BlockFluid flowingLava, stillLava;

  public static int        nLiquids;
  public static int        liquidIDs[] = new int[256];
  public static boolean    isLiquid[]  = new boolean[4096];
  public static BlockFluid fluid[]     = new BlockFluid[4096];


  public static void registerLiquidBlock(BlockFluid block) {
    liquidIDs[nLiquids++] = block.blockID;
    isLiquid[block.blockID] = true;
    fluid[block.blockID] = block;
  }

  public static void load() {
    for (int i = 0; i < 4096; i++) {
      isLiquid[i] = false;
      fluid[i] = null;
    }
  }

  public static void postInit() {

    /**** Water ****/
    // Remove old water blocks (still/moving)
    Block.blocksList[Block.waterMoving.blockID] = null;
    Block.blocksList[Block.waterStill.blockID] = null;

    // Register new blocks
    flowingWater = new BlockWater(Block.waterMoving.blockID, Material.water, Block.waterStill.blockID, Block.waterMoving.blockID, "water");
    stillWater = new BlockWater(Block.waterStill.blockID, Material.water, Block.waterStill.blockID, Block.waterMoving.blockID, "water");
    flowingWater.setLiquidUpdateRate(1); // was: 10);
    flowingWater.setTickRandomly(false);
    stillWater.setLiquidUpdateRate(1); // was: 10);
    stillWater.setTickRandomly(false);
    flowingWater.canCauseErosion = true;
    stillWater.canCauseErosion = true;
    flowingWater.canSeepThrough = true;
    stillWater.canSeepThrough = true;
    
    GameRegistry.registerBlock(flowingWater, "waterFlowing");
    GameRegistry.registerBlock(stillWater, "waterStill");

    /**** Lava ****/
    // Remove old lava blocks (still/moving)
    Block.blocksList[Block.lavaMoving.blockID] = null;
    Block.blocksList[Block.lavaStill.blockID] = null;

    // Register new blocks
    flowingLava = new BlockLava(Block.lavaMoving.blockID, Material.lava, Block.lavaStill.blockID, Block.lavaMoving.blockID, "lava");
    stillLava = new BlockLava(Block.lavaStill.blockID, Material.lava, Block.lavaStill.blockID, Block.lavaMoving.blockID, "lava");
    flowingLava.setLiquidUpdateRate(2);
    stillLava.setLiquidUpdateRate(2);
    flowingLava.setTickRandomly(false);
    stillLava.setTickRandomly(false);
    flowingLava.canCauseErosion = true;
    stillLava.canCauseErosion = true;
    flowingLava.erodeMultiplier = 10;
    stillLava.erodeMultiplier = 10;
    flowingLava.canSeepThrough = false;
    stillLava.canSeepThrough = false;
    GameRegistry.registerBlock(flowingLava, "lavaFlowing");
    GameRegistry.registerBlock(stillLava, "lavaStill");    
    
    registerLiquidBlock(stillWater);
    registerLiquidBlock(flowingWater);
    registerLiquidBlock(stillLava);
    registerLiquidBlock(flowingLava);

    if (FysiksFun.settings.flowingLiquidOil) {
      patchModLiquid("oilStill", "oilMoving", 1, false, false);
    }
    if (FysiksFun.settings.flowingHydrochloricAcid) {
      patchModLiquid("Still Hydrochloric Acid", "Flowing Hydrochloric Acid", 1, true, true);
    }

  }

  /** Verifies that the liquid blocks are still correct, otherwise writes them back again. */
  public static void checkBlockOverwritten() {
    if(Block.blocksList[stillWater.blockID] != stillWater) {
      Block.blocksList[stillWater.blockID] = null;
      GameRegistry.registerBlock(stillWater, "waterStill");
      Block.blocksList[stillWater.blockID] = stillWater;
      FysiksFun.logger.log(Level.INFO,"Re-installing water since another mod have overwritten it. If you don't want this, bug me about it and i'll make an option");
    }
    if(Block.blocksList[stillLava.blockID] != stillLava) {
      Block.blocksList[stillLava.blockID] = null;
      GameRegistry.registerBlock(stillLava, "lavaStill");
      Block.blocksList[stillLava.blockID] = stillLava;
      FysiksFun.logger.log(Level.INFO,"Re-installing lava since another mod have overwritten it. If you don't want this, bug me about it and i'll make an option");
    }
  }
  
  private static void patchModLiquid(String stillName, String flowingName, int updateRate, boolean causesErosion, boolean leaksThrough) {

    Block oldBlockFlowing = null;
    Block oldBlockStill = null;
    for (Block b : Block.blocksList) {
      if (b != null && b.getUnlocalizedName() != null && b.getUnlocalizedName().matches("tile." + flowingName)) oldBlockFlowing = b;
      if (b != null && b.getUnlocalizedName() != null && b.getUnlocalizedName().matches("tile." + stillName)) oldBlockStill = b;
    }
    if (oldBlockFlowing == null) {
      FysiksFun.logger.log(Level.WARNING, "Cannot patch behaviour of block:" + "tile." + flowingName + " since it was not found (is the mod installed?)");
    }
    if (oldBlockStill == null) {
      FysiksFun.logger.log(Level.WARNING, "Cannot patch behaviour of block:" + "tile." + stillName + " since it was not found (is the mod installed?)");
    }
    if (oldBlockFlowing != null && oldBlockStill != null && FysiksFun.settings.flowingLiquidOil) {
      Block.blocksList[oldBlockFlowing.blockID] = null;
      Block.blocksList[oldBlockStill.blockID] = null;

      // Register new blocks
      BlockFluid blockFlowing = new BlockFluid(oldBlockFlowing, oldBlockFlowing.blockID, oldBlockFlowing.blockMaterial, oldBlockStill.blockID,
          oldBlockFlowing.blockID, "oil");
      BlockFluid blockStill = new BlockFluid(oldBlockStill, oldBlockStill.blockID, oldBlockStill.blockMaterial, oldBlockStill.blockID, oldBlockFlowing.blockID,
          "oil");
      blockFlowing.setLiquidUpdateRate(updateRate);
      blockStill.setLiquidUpdateRate(updateRate);
      blockFlowing.setTickRandomly(false);
      blockStill.setTickRandomly(false);
      blockFlowing.canCauseErosion = causesErosion;
      blockStill.canCauseErosion = causesErosion;
      blockFlowing.canSeepThrough = leaksThrough;
      blockStill.canSeepThrough = leaksThrough;
      GameRegistry.registerBlock(blockFlowing, flowingName);
      GameRegistry.registerBlock(blockStill, stillName);

      registerLiquidBlock(blockStill);
      registerLiquidBlock(blockFlowing);
    }

  }


  /** Returns true if the two liquids given by blockID's can mix and cause an
   * interaction
   */
  public static boolean liquidCanInteract(int block1, int block2) {
    if (Block.blocksList[block1] != null && Block.blocksList[block2] != null) {
      if (Block.blocksList[block1].blockMaterial == Material.lava) return Block.blocksList[block2].blockMaterial == Material.water;
      else if (Block.blocksList[block1].blockMaterial == Material.water) return Block.blocksList[block2].blockMaterial == Material.lava;
    }
    return false;
  }

  /**
   * Create the effect of interaction between the two liquids mixing in the
   * given cell. Returns the amount of incoming liquid should be LEFT after the
   * interaction
   */
  public static int liquidInteract(World w, int x, int y, int z, int incomingBlockID, int incommingAmount, int targetBlockID, int targetAmount) {
    int lavaAmount = 0, waterAmount = 0;
    int reactionStepSize = BlockFluid.maximumContent / 8;
    if (Block.blocksList[incomingBlockID].blockMaterial == Material.lava) {
      lavaAmount = incommingAmount;
      waterAmount = targetAmount;
    } else if (Block.blocksList[targetBlockID].blockMaterial == Material.lava) {
      lavaAmount = targetAmount;
      waterAmount = incommingAmount;
    }
    int nReactions = Math.min(lavaAmount, waterAmount) / reactionStepSize + 1;
    nReactions = Math.min(1, nReactions);
    //System.out.println("lavaAmount: "+lavaAmount+" waterAmount: "+waterAmount);

    lavaAmount = Math.max(0, lavaAmount - nReactions * reactionStepSize);
    waterAmount = Math.max(0, waterAmount - nReactions * reactionStepSize);
    int steamAmount = nReactions;
    boolean generated = false;
    /*
     * Check if the interaction happens in air/water/gas, if so move the result
     * as far down as possible
     */
    int targetY = y;
    Chunk chunk0 = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    for (; targetY > 0; targetY--) {
      int id = chunk0.getBlockID(x & 15, targetY - 1, z & 15);
      // if (id != 0 && !Fluids.stillWater.isSameLiquid(id) && !Gases.isGas[id])
      // break;
      if (id == 0) continue;
      //if (Fluids.isLiquid[id]) continue;
      if(Fluids.stillWater.isSameLiquid(id)) continue;
      if (Gases.isGas[id]) continue;
      break;
    }

    boolean hasObsidianNeighbour = false;
    for (int dx = -1; dx <= 1; dx++)
      for (int dy = -1; dy <= 1; dy++)
        for (int dz = -1; dz <= 1; dz++) {
          if (targetY + dy > 0 && targetY + dy < 255) {
            Chunk chunk1 = ChunkCache.getChunk(w, (x + dx)>>4, (z + dz)>>4, false);
            if (chunk1 != null && chunk1.getBlockID((x + dx) & 15, targetY + dy, (z + dz) & 15) == Block.obsidian.blockID) hasObsidianNeighbour = true;
          }
        }
    for (int i = 0; i < nReactions; i++) {
      int r = w.rand.nextInt(200);
      int newId = 0;
      if (r == 0 || (r <= 3 && hasObsidianNeighbour)) newId = Block.obsidian.blockID;
      else if (r <= 10) newId = Block.gravel.blockID;
      else if (r <= 20) newId = Block.cobblestone.blockID;

      if (newId != 0) {
        FysiksFun.setBlockWithMetadataAndPriority(w, x, targetY, z, newId, 0, 0);
        generated = true;
        break;
      }
    }
    
    /*
    w.playSoundEffect((double) ((float) x + 0.5F), (double) ((float) y + 0.5F), (double) ((float) z + 0.5F), "random.fizz", 0.5F,
        2.6F + (w.rand.nextFloat() - w.rand.nextFloat()) * 0.8F);
    for (int i = 0; i < nReactions + waterAmount * 2; i++) {
      w.spawnParticle("largesmoke", (double) x + Math.random(), (double) y + 1.2D, (double) z + Math.random(), 0.0D, 0.0D, 0.0D);
    }
    */
    
    // DEBUG
    // steamAmount=0;
    
     // Optimisation
    //steamAmount=Math.max(1, steamAmount/2);
    //System.out.println("nReactions: "+nReactions+" steamAmount:"+steamAmount);
    for (int dist = 1; dist < 2 && steamAmount > 0; dist++) {
      for (int dir0 = 4; dir0 < 6 + 4 && steamAmount > 0; dir0++) {
        int dir = dir0 % 6;
        int x1 = x + Util.dirToDx(dir) * dist;
        int y1 = y + Util.dirToDy(dir) * dist;
        int z1 = z + Util.dirToDz(dir) * dist;
        int id = w.getBlockId(x1, y1, z1);
        if (id == 0 || id == Gases.steam.blockID) {
          int origAmount = id == 0 ? 0 : Gases.steam.getBlockContent(w, x1, y1, z1);
          int amount = origAmount + steamAmount;
          //System.out.println("Orig amount: "+origAmount);
          if (amount > 16) {
            steamAmount = amount - 16;
            amount = 16;
          } else steamAmount = 0;          
          Gases.steam.setBlockContent(w, x1, y1, z1, amount);
          //System.out.println("Setting amount @"+Util.xyzString(x1, y1, z1)+":"+amount+" leftOver: "+steamAmount);
          if(steamAmount <= 0) { dist=32; break; }
        }
      }
    }
    if (Block.blocksList[incomingBlockID].blockMaterial == Material.lava) {
      if (!generated) flowingWater.setBlockContent(w, x, y, z, waterAmount);
      return lavaAmount;
    } else {
      if (!generated) flowingLava.setBlockContent(w, x, y, z, lavaAmount);
      return waterAmount;
    }
  }

}
