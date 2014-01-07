package mbrx.ff;

import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;

import mbrx.ff.MPWorldTicker.WorldUpdateState;
import net.minecraft.block.Block;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

class WorkerLiquidSweep implements Runnable {

  /** Chunks within this distance from an observer is updated every sweep. Others chunks only on every X'th sweep. */
  private static final int                   chunkDistFullUpdates = 64;

  ChunkCoordIntPair                          xz;
  World                                      w;
  int                                        minY, maxY;
  WorldUpdateState                           wstate;
  Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;

  public WorkerLiquidSweep(World w, WorldUpdateState wstate, ChunkCoordIntPair xz, int minY, int maxY,
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

    try {

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

      if ((wstate.sweepCounter % 4) != 0) {
        int minDist = chunkDistFullUpdates * chunkDistFullUpdates;
        for (FysiksFun.WorldObserver wo : FysiksFun.observers) {
          int dist = (int) ((wo.posX - x) * (wo.posX - x) + (wo.posZ - z) * (wo.posZ - z));
          if (dist < minDist) minDist = dist;
        }
        // On these frames dont run the update for chunks far away from player
        if (minDist >= chunkDistFullUpdates * chunkDistFullUpdates) return;
      }

      // Don't process some of the chunks, when the current chunk has too much
      // fluids in it (is probably some kind of ocean)
      if (wstate.sweepCounter % 3 != 0) {
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
            if (ebs != null) {
              id = ebs.getExtBlockID(dx, y & 15, dz);
            } else id = c.getBlockID(dx, y, dz);

            if (id != 0 && Block.blocksList[id] == null) {
              FysiksFun.logger.log(Level.WARNING, "[FF] Warning, there was a corrupted block at " + Util.xyzString(x + dx, y, z + dz)
                  + " - i've zeroed it for now but this is dangerous");
              FysiksFun.setBlockWithMetadataAndPriority(w, x + dx, y, z + dz, 0, 0, 0);
              continue;
            }

            /* For fluids */
            if (Fluids.isLiquid[id] && FysiksFun.settings.doFluids) {
              BlockFluid b = (BlockFluid) Block.blocksList[id];
              b.updateTickSafe(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand, wstate.sweepCounter, delayedBlockMarkSet);
              // b.updateTickSafe(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand, wstate.sweepCounter, null);
              if (FysiksFun.rand.nextInt(5 * b.liquidUpdateRate) == 0) b.updateRandomWalk(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand);
              if (FysiksFun.rand.nextInt(10) == 0) b.expensiveTick(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand);
              fluidCount++;
            }

            /* For gases */
            if (Gases.isGas[id] && FysiksFun.settings.doGases) {
              BlockGas b = (BlockGas) Block.blocksList[id];
              b.updateTickSafe(w, x + dx, y, z + dz, FysiksFun.rand);
              if (FysiksFun.rand.nextInt(10) == 0) b.expensiveTick(w, c, tempData0, x + dx, y, z + dz, FysiksFun.rand);
              gasCount++;
            }

            /* Extra fire - this is not executed "always", only when in same chunk as moving water!! */
            if (id == Block.fire.blockID && FysiksFun.settings.doExtraFire) ExtraFire.handleFireAt(w, x + dx, y, z + dz);
          }
        tempData0.setFluidHistogram(y, fluidCount);
        tempData0.setGasHistogram(y, gasCount);
      }
    } catch (Exception e) {
      System.out.println("BlocksweeperThread got an exception" + e);
      e.printStackTrace();
    }
  }
}
