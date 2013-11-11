package mbrx.ff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import net.minecraft.block.Block;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Provides a multithreaded server for ticking all blocks at given layers of a world in sweeps that cycle back.
 */
public class MPWorldTicker {

  private static class WorldUpdateState {
    int sweepStepCounter;
    int sweepCounter;
  };

  private static Hashtable<World, WorldUpdateState> worldUpdateState = new Hashtable<World, WorldUpdateState>();

  private static class ChunkUpdateWorkerThread implements Runnable {
    World                                 world;
    Chunk                                 chunk;
    Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;
    ChunkCoordIntPair                     xz;

    public ChunkUpdateWorkerThread(World w, Chunk c, ChunkCoordIntPair xz, Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets) {
      this.world = w;
      this.chunk = c;
      this.xz = xz;
      this.delayedBlockMarkSets = delayedBlockMarkSets;
    }

    @Override
    public void run() {
      int x = xz.chunkXPos << 4;
      int z = xz.chunkZPos << 4;
      Settings settings = FysiksFun.settings;

      if(settings.doVolcanoes) Volcanoes.doChunkTick(world,xz);      
      if (settings.doRain) Rain.doPrecipation(world, x, z);
      if (settings.doEvaporation) Evaporation.doEvaporation(world, x, z);
      if (settings.doTreeFalling) Trees.doTrees(world, x, z);
      if (settings.doDynamicPlants) Plants.doPlants(world, x, z);      
      if (world.provider.dimensionId == -1 && settings.doNetherfun)
        NetherFun.doNetherFun(world, x, z);
    }
  }

  private static class BlockSweepWorkerThread implements Runnable {
    ChunkCoordIntPair                     xz;
    World                                 w;
    int                                   minY, maxY;
    WorldUpdateState                      wstate;
    Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;

    public BlockSweepWorkerThread(World w, WorldUpdateState wstate, ChunkCoordIntPair xz, int minY, int maxY,
        Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets) {
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
      HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet = delayedBlockMarkSets.get(tid);
      if (delayedBlockMarkSet == null) {
        delayedBlockMarkSet = new HashSet<ChunkMarkUpdateTask>();
        delayedBlockMarkSets.put(tid, delayedBlockMarkSet);
      }

      Chunk c = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, false);
      ChunkTempData tempData0 = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);
      int x = xz.chunkXPos << 4;
      int z = xz.chunkZPos << 4;

      // Don't process some of the chunks, when the current chunk has too much
      // fluids in it (is probably some kind of ocean)
      if (wstate.sweepCounter % 3 != 0)  {
        int cnt = 0;
        for (int y2 = 1; y2 < 255; y2++)
          cnt += tempData0.getFluidHistogram(y2);
        if (cnt > 2000) return;
      }

      ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();
      for (int y = minY; y < maxY; y++) {

        if (wstate.sweepCounter % 6 != 0) {
          boolean checkCarefully = false;
          if (tempData0.getFluidHistogram(y) != 0) checkCarefully = true;
          if (tempData0.getGasHistogram(y) != 0) checkCarefully = true;

          // Check layers above/below water and gas, since it may be
          // propagated "fast"
          if (y + 4 < 255 && tempData0.getFluidHistogram(y + 4) != 0) checkCarefully = true;
          if (y + 3 < 255 && tempData0.getFluidHistogram(y + 3) != 0) checkCarefully = true;
          if (y + 2 < 255 && tempData0.getFluidHistogram(y + 2) != 0) checkCarefully = true;
          if (y + 1 < 255 && tempData0.getFluidHistogram(y + 1) != 0) checkCarefully = true;
          if (y - 1 > 0 && tempData0.getGasHistogram(y - 1) != 0) checkCarefully = true;
          if (y - 2 > 0 && tempData0.getGasHistogram(y - 2) != 0) checkCarefully = true;
          // if (y - 3 > 0 && tempData0.getGasHistogram(y - 3) != 0)
          // checkCarefully = true;
          // TODO - check layers from adjacent chunks?
          if (checkCarefully == false) continue;
        }

        int fluidCount = 0;
        int gasCount = 0;
        
        ExtendedBlockStorage ebs = blockStorage[y >> 4];
        
        for (int dx = 0; dx < 16; dx++)
          for (int dz = 0; dz < 16; dz++) {
            
            int id = 0;
            if(ebs != null) {
              id = ebs.getExtBlockID(dx, y&15, dz);
            } else id = c.getBlockID(dx, y, dz);

            /* For fluids */
            if (Fluids.isLiquid[id] && FysiksFun.settings.doFluids) {
              BlockFluid b = (BlockFluid) Block.blocksList[id];
              b.updateTickSafe(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand, wstate.sweepCounter, delayedBlockMarkSet);
              // b.updateTickSafe(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand, wstate.sweepCounter, null);
              if (FysiksFun.rand.nextInt(10 * b.liquidUpdateRate) == 0) b.updateRandomWalk(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand);
              if (FysiksFun.rand.nextInt(10) == 0) b.expensiveTick(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand);
              fluidCount++;
            }

            /* For gases */
            if (Gases.isGas[id] && FysiksFun.settings.doGases) {
              BlockGas b = (BlockGas) Block.blocksList[id];
              b.updateTickSafe(w, x + dx, y, z + dz, FysiksFun.rand);
              gasCount++;
            }

            /* Extra fire */
            if (id == Block.fire.blockID && FysiksFun.settings.doExtraFire) ExtraFire.handleFireAt(w, x+dx, y, z + dz);
          }
        tempData0.setFluidHistogram(y, fluidCount);
        tempData0.setGasHistogram(y, gasCount);
      }
    }
  }

  /**
   * Performs multithreaded calls to all update functions for all modules of FF, for every loaded chunk in the given
   * world.
   */
  public static void doUpdateChunks(World w) {

    // A thread dependent 'safe' set for scheduling delayed block mark requests
    Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets = Collections.synchronizedMap(new Hashtable<Integer, HashSet<ChunkMarkUpdateTask>>());

    // Schedule jobs, first the odd coordinates, then the even coordinates
    List<Future> toWaitFor = new ArrayList<Future>();
    for (int oddeven = 0; oddeven < 2; oddeven++) {
      for (Object o : w.activeChunkSet) {
        ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
        if ((xz.chunkXPos + xz.chunkZPos) % 2 == oddeven) {
          Chunk chunk = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, true);
          ChunkTempData tempData = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);
          Runnable worker = new ChunkUpdateWorkerThread(w, chunk, xz, delayedBlockMarkSets);
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
    }

    /* Go through all updated blocks and mark them for updates */
    for (HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet : delayedBlockMarkSets.values()) {
      for (ChunkMarkUpdateTask task : delayedBlockMarkSet) {
        ChunkMarkUpdater.scheduleBlockMark(task.getWorld(), task.getX(), task.getY(), task.getZ(), task.getOrigId(), task.getOrigMeta());
      }
    }
  }

  /**
   * Performs the main ticks for all functions requiring block ticks. by circulating over all layers in multiple ticks.
   * Ie, going down from layer 255 to bedrock and then starting over.
   */
  public static void doBlockSweeps(World w) {
    WorldUpdateState wstate = worldUpdateState.get(w);
    if (wstate == null) {
      wstate = new WorldUpdateState();
      worldUpdateState.put(w, wstate);
    }
    int mi, ma;
    wstate.sweepStepCounter++;

    int nStepsPerSweep = 8;
    int sweep = wstate.sweepStepCounter % nStepsPerSweep;
    int yPerStep = 256 / nStepsPerSweep;
    mi = sweep * yPerStep;
    ma = Math.min(255, (sweep + 1) * yPerStep);
    if (sweep == 0) wstate.sweepCounter++;

    // Make sure all chunks/tempData are loaded... if this is not done first we may have problems since the loading
    // functions are not thread safe
    for (Object o : w.activeChunkSet) {
      ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
      Chunk c = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, true);
      ChunkTempData tmp = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);
    }

    // A thread dependent 'safe' set for scheduling delayed block mark requests
    Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets = Collections.synchronizedMap(new Hashtable<Integer, HashSet<ChunkMarkUpdateTask>>());

    // Schedule jobs, first the odd coordinates, then the even coordinates
    List<Future> toWaitFor = new ArrayList<Future>();
    for (int oddeven = 0; oddeven < 2; oddeven++) {
      for (Object o : w.activeChunkSet) {
        ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
        if ((xz.chunkXPos + xz.chunkZPos) % 2 == oddeven) {
          Runnable worker = new BlockSweepWorkerThread(w, wstate, xz, mi, ma, delayedBlockMarkSets);
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
    }
    /* Go through all updated blocks and mark them for updates */
    for (HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet : delayedBlockMarkSets.values()) {
      for (ChunkMarkUpdateTask coord : delayedBlockMarkSet) {
        ChunkMarkUpdater.scheduleBlockMarkSafe(coord.getWorld(), coord.getX(), coord.getY(), coord.getZ(), coord.getOrigId(), coord.getOrigMeta());
      }
    }
  }

}
