package mbrx.ff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import cpw.mods.fml.common.registry.GameRegistry;

public class Fluids {
  public static BlockFluid flowingWater, stillWater;
  public static BlockFluid flowingLava, stillLava;

  public static int        nLiquids;
  public static int        liquidIDs[] = new int[256];
  public static boolean    isLiquid[]  = new boolean[4096];
  public static BlockFluid fluid[]     = new BlockFluid[4096];

  private static class WorldUpdateState {
    int sweepSteps;
    int sweepCounter;
  };

  private static Hashtable<World, WorldUpdateState> worldUpdateState = new Hashtable<World, WorldUpdateState>();

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
    flowingWater = new BlockFluid(Block.waterMoving.blockID, Material.water, Block.waterStill.blockID, Block.waterMoving.blockID, "water");
    stillWater = new BlockFluid(Block.waterStill.blockID, Material.water, Block.waterStill.blockID, Block.waterMoving.blockID, "water");
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
    flowingLava = new BlockFluid(Block.lavaMoving.blockID, Material.lava, Block.lavaStill.blockID, Block.lavaMoving.blockID, "lava");
    stillLava = new BlockFluid(Block.lavaStill.blockID, Material.lava, Block.lavaStill.blockID, Block.lavaMoving.blockID, "lava");
    flowingLava.setLiquidUpdateRate(2);
    stillLava.setLiquidUpdateRate(2);
    flowingLava.setTickRandomly(false);
    stillLava.setTickRandomly(false);
    flowingLava.canCauseErosion = true;
    stillLava.canCauseErosion = true;
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

  /**
   * Performs the main ticks for all fluids by circulating over all layers in
   * multiple ticks. Ie, going down from layer 255 to bedrock and then starting
   * over.
   */
  public static void doWorldTick(World w) {
    WorldUpdateState wstate = worldUpdateState.get(w);
    if (wstate == null) {
      wstate = new WorldUpdateState();
      worldUpdateState.put(w, wstate);
    }
    // 1: ~15ms
    // 5: ~25ms
    // 20: ~80ms
    // 50: ~200ms
    // 3ms per step? Variation over time due to sweeping over areas with more
    // water
    // Dynamically change amount of steps?
    // int maxSteps=20;
    // if(wstate.sweepY > 80) maxSteps += 30;
    // if(wstate.sweepY < 50) maxSteps += 30;

    int mi, ma;
    wstate.sweepSteps++;

    // TODO - change the iteration order, from bottom to top!
    switch (wstate.sweepSteps % 5) {
    case 0:
      mi = 1;
      ma = 48;
      break;
    case 1:
      mi = 49;
      ma = 58;
      break;
    case 2:
      mi = 59;
      ma = 65;
      break;
    case 3:
      mi = 66;
      ma = 100;
      break;
    default:
      mi = 101;
      ma = 255;
      wstate.sweepCounter++;
    }
    // if(wstate.sweepY % 3 == 0) { mi=255; ma=75; }
    // else if(wstate.sweepY % 3 == 1) { mi=50; ma=75; }
    // else {mi = 1; ma=50; wstate.sweepCounter++; }
    // wstate.sweepY++;
    // for (int step = 0; step < 1; step++) {

    class WorkerThread implements Runnable {
      ChunkCoordIntPair                     xz;
      World                                 w;
      int                                   minY, maxY;
      WorldUpdateState                      wstate;
      Map<Integer, HashSet<CoordinateWXYZ>> delayedBlockMarkSets;

      public WorkerThread(World w, WorldUpdateState wstate, ChunkCoordIntPair xz, int minY, int maxY, Map<Integer, HashSet<CoordinateWXYZ>> delayedBlockMarkSets) {
        this.xz = xz;
        this.w = w;
        this.minY = minY;
        this.maxY = maxY;
        this.wstate = wstate;
        this.delayedBlockMarkSets = delayedBlockMarkSets;
      }

      @Override
      public void run() {
        Integer tid = (int) Thread.currentThread().getId();
        HashSet<CoordinateWXYZ> delayedBlockMarkSet = delayedBlockMarkSets.get(tid);
        if (delayedBlockMarkSet == null) {
          delayedBlockMarkSet = new HashSet<CoordinateWXYZ>();
          delayedBlockMarkSets.put(tid, delayedBlockMarkSet);
        }

        for (int y = minY; y <= maxY; y++) {
          Chunk c = w.getChunkFromChunkCoords(xz.chunkXPos, xz.chunkZPos);
          int x = xz.chunkXPos << 4;
          int z = xz.chunkZPos << 4;
          ChunkTempData tempData0 = null;
          for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
              int id = c.getBlockID(dx, y, dz);
              if (isLiquid[id]) {
                if (tempData0 == null) tempData0 = ChunkTempData.getChunk(w, x, y, z);
                BlockFluid b = (BlockFluid) Block.blocksList[id];
                b.updateTickSafe(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand, wstate.sweepCounter, delayedBlockMarkSet);
              }
            }
        }
      }
    }

    boolean useMultithreading = false;

    if (useMultithreading) {
      /* Multi threaded implementation of fluid updates */

      // Make sure all chunks/tempData are loaded...
      for (Object o : w.activeChunkSet) {
        ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
        Chunk c = w.getChunkFromChunkCoords(xz.chunkXPos, xz.chunkZPos);
        int x = xz.chunkXPos << 4;
        int z = xz.chunkZPos << 4;
        ChunkTempData tempData0 = ChunkTempData.getChunk(w, x, 64, z);
      }

      // ExecutorService executor = Executors.newFixedThreadPool(2);
      Map<Integer, HashSet<CoordinateWXYZ>> delayedBlockMarkSets = Collections.synchronizedMap(new Hashtable<Integer, HashSet<CoordinateWXYZ>>());

      List<Future> toWaitFor = new ArrayList<Future>();
      for (int oddeven = 0; oddeven < 2; oddeven++) {
        for (Object o : w.activeChunkSet) {
          ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
          if ((xz.chunkXPos + xz.chunkZPos) % 2 == oddeven) {
            Runnable worker = new WorkerThread(w, wstate, xz, mi, ma, delayedBlockMarkSets);
            Future f = FysiksFun.executor.submit(worker);
            toWaitFor.add(f);
          }
        }
        for (Future f : toWaitFor) {
          try {
            if (f != null) f.get();
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

        /* Now, go through all updated blocks and mark them for updates */
        for (HashSet<CoordinateWXYZ> delayedBlockMarkSet : delayedBlockMarkSets.values()) {
          for (CoordinateWXYZ coord : delayedBlockMarkSet) {
            ChunkMarkUpdater.scheduleBlockMark(coord.getWorld(), coord.getX(), coord.getY(), coord.getZ());
          }
        }
      }
    } else {
      /* Single threaded implementation of fluid updates */
      for (Object o : w.activeChunkSet) {
        ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
        Chunk c = w.getChunkFromChunkCoords(xz.chunkXPos, xz.chunkZPos);
        int x = xz.chunkXPos << 4;
        int z = xz.chunkZPos << 4;
        ChunkTempData tempData0 = ChunkTempData.getChunk(w, x, 0, z);

        // Don't process some of the chunks, when the current chunk has too much
        // fluids in it (is probably some kind of ocean)
        if (wstate.sweepCounter % 3 != 0) {
          int cnt = 0;
          for (int y2 = 1; y2 < 255; y2++)
            cnt += tempData0.getFluidHistogram(y2);
          if (cnt > 2000) continue;
        }
        for (int y = mi; y <= ma; y++) {
          // Don't check layers that are not know to contain water, except for
          // every 4 complete sweeps
          // (since water might have been added externally)
          if (wstate.sweepCounter % 6 != 0) {
            boolean checkCarefully = false;
            if (tempData0.getFluidHistogram(y) != 0) checkCarefully = true;
            // Check layers above/below water, since it may be propagated
            //if (y - 1 > 0 && tempData0.getFluidHistogram(y - 1) != 0) checkCarefully = true;
            if (y + 1 < 255 && tempData0.getFluidHistogram(y + 1) != 0) checkCarefully = true;
            // TODO - check layers from adjacent chunks?
            if (checkCarefully == false) continue;
          }

          int cnt = 0;
          for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
              int id = c.getBlockID(dx, y, dz);
              if (isLiquid[id]) {
                BlockFluid b = (BlockFluid) Block.blocksList[id];
                b.updateTickSafe(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand, wstate.sweepCounter, null);
                cnt++;
              }
            }
          tempData0.setFluidHistogram(y, cnt);
          // if(cnt != 0)
          // System.out.println(""+(x>>4)+" "+(y>>4)+" histogram["+y+"]: = "+cnt);
        }
      }
    }

  }

  /**
   * Schedules a block to be "marked". On a server this will either send the
   * block to clients. private static BlockUpdateState tmpLookupState = new
   * BlockUpdateState();
   * 
   * /** Returns true if the two liquids given by blockID's can mix and cause an
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
    if (Block.blocksList[incomingBlockID].blockMaterial == Material.lava) {
      lavaAmount = incommingAmount;
      waterAmount = targetAmount;
    } else if (Block.blocksList[targetBlockID].blockMaterial == Material.lava) {
      lavaAmount = targetAmount;
      waterAmount = incommingAmount;
    }
    int nReactions = Math.min(lavaAmount, waterAmount); // / 2);
    lavaAmount -= nReactions;
    waterAmount -= nReactions; // Math.max(1, nReactions * 2);
    boolean generated = false;

    for (int i = 0; i < nReactions; i++) {
      int r = w.rand.nextInt(40);
      if (r == 0) {
        w.setBlock(x, y, z, Block.obsidian.blockID, 0, 0x02);
        generated = true;
        break;
      } else if (r <= 4) {
        w.setBlock(x, y, z, Block.cobblestone.blockID, 0, 0x02);
        generated = true;
        break;
      }
    }
    w.playSoundEffect((double) ((float) x + 0.5F), (double) ((float) y + 0.5F), (double) ((float) z + 0.5F), "random.fizz", 0.5F,
        2.6F + (w.rand.nextFloat() - w.rand.nextFloat()) * 0.8F);
    for (int i = 0; i < nReactions + waterAmount * 2; i++) {
      w.spawnParticle("largesmoke", (double) x + Math.random(), (double) y + 1.2D, (double) z + Math.random(), 0.0D, 0.0D, 0.0D);
    }

    if (Block.blocksList[incomingBlockID].blockMaterial == Material.lava) {
      if (!generated) w.setBlock(x, y, z, targetBlockID, 8 - waterAmount, 0x02);
      return lavaAmount;
    } else {
      if (!generated) w.setBlock(x, y, z, targetBlockID, 8 - lavaAmount, 0x02);
      return waterAmount;
    }
  }

}
