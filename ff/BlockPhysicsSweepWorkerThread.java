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

public class BlockPhysicsSweepWorkerThread implements Runnable {
  ChunkCoordIntPair                          xz;
  World                                      w;
  WorldUpdateState                           wstate;
  Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;

  public BlockPhysicsSweepWorkerThread(World w, WorldUpdateState wstate, ChunkCoordIntPair xz, Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets) {
    this.xz = xz;
    this.w = w;
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
      ChunkTempData tempData = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);
      int x = xz.chunkXPos << 4;
      int z = xz.chunkZPos << 4;

      ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();
      for (int y = 1; y < 254; y++) {
        ExtendedBlockStorage ebs = blockStorage[y >> 4];
        for (int dx = 0; dx < 16; dx++)
          for (int dz = 0; dz < 16; dz++) {

            int id = 0;
            if (ebs != null) {
              id = ebs.getExtBlockID(dx, y & 15, dz);
            } else id = c.getBlockID(dx, y, dz);
            if (id == 0 || Gases.isGas[id]) {
              tempData.setTempData(dx, y, dz, 0);
              continue;
            }
            if (Fluids.isLiquid[id]) continue;

            /* Get current pressure, increase by our weight */
            int weight = 1;
            int totalMoved = 0;
            int curPressure = tempData.getTempData(dx, y, dz) + weight;

            if (x + dx == -1832 && z + dz == 1330) System.out.println("" + Util.xyzString(x + dx, y, z + dz) + " prevPressure: " + curPressure);

            if (y <= 1) curPressure = 0;
            for (int dir = 0; dir < 6; dir++) {
              int x2 = x + dx + Util.dirToDx(dir);
              int y2 = y + Util.dirToDy(dir);
              int z2 = z + dz + Util.dirToDz(dir);
              Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
              if (c2 == null) continue;
              int id2 = c2.getBlockID(x2 & 15, y2, z2 & 15);
              if (id2 == 0 || Gases.isGas[id2] || Fluids.isLiquid[id2]) continue;
              ChunkTempData tempData2 = ChunkCache.getTempData(w, x2 >> 4, z2 >> 4);
              if (tempData2 == null) continue;
              int nnPressure = tempData2.getTempData(x2, y2, z2);

              if (curPressure == 0 && y > 1 && nnPressure > curPressure) {
                /*
                 * Special case: we have joined a new block with no previous pressure to this area. Copy the pressure
                 * from a neighbour.
                 */
                curPressure = nnPressure;
              } else if (nnPressure > curPressure + 1) {
                int toMove;
                // if (dir < 4) toMove = (nnPressure - curPressure) / 2;
                // else
                toMove = (nnPressure - curPressure) / 2;
                totalMoved += toMove;
                nnPressure -= toMove;
                curPressure += toMove;
                tempData2.setTempData(x2, y2, z2, nnPressure);

                if (x + dx == -1832 && z + dz == 1330) System.out.println("toMove " + toMove + " dy: " + Util.dirToDy(dir));
              }
            }
            if (y <= 1) curPressure = 0;
            int threshold = 30;
            moveBlock:
            if (totalMoved > 8 || curPressure > 100) {
              System.out.println("Block at coords " + Util.xyzString(x + dx, y, z + dz) + " causes a break pres:" + curPressure + " moved: " + totalMoved);
              for (int dir = 0; dir < 6; dir++) {
                int tX = x + dx + Util.dirToDx(dir);
                int tY = y + Util.dirToDy(dir);
                int tZ = z + dz + Util.dirToDz(dir);
                int tmpId = w.getBlockId(tX, tY, tZ);
                ChunkTempData tData = ChunkCache.getTempData(w, tX >> 4, tZ >> 4);
                int p = tData.getTempData(tX, tY, tZ);
                System.out.println("Neighbour " + Util.xyzString(tX, tY, tZ) + " id:" + tmpId + " pres:" + p);
              }

              /*
               * Make a walk towards lower pressures until we reach the point before an equilibrium point. There we will
               * break/fall something.
               */
              int x0 = x + dx, y0 = y, z0 = z + dz;
              int maxSteps = FysiksFun.rand.nextInt(128);
              for (int step = 0; step < -1; step++) {
                int bestPressure = curPressure;
                int bestDir = -1;
                for (int dir = 0; dir < 6; dir++) {
                  int x2 = x0 + Util.dirToDx(dir);
                  int y2 = y0 + Util.dirToDy(dir);
                  int z2 = z0 + Util.dirToDz(dir);
                  Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
                  if (c2 == null) continue;
                  int id2 = c2.getBlockID(x2 & 15, y2, z2 & 15);
                  if (id2 == 0 || Gases.isGas[id2] || Fluids.isLiquid[id2]) continue;
                  ChunkTempData tempData2 = ChunkCache.getTempData(w, x2 >> 4, z2 >> 4);
                  if (tempData2 == null) continue;
                  int nnPressure = tempData2.getTempData(x2, y2, z2);
                  if (nnPressure < bestPressure) {
                    bestPressure = nnPressure;
                    bestDir = dir;
                  }
                }
                if (bestDir == -1) break;
                if (bestPressure < curPressure - 4) break;
                if (y0 <= 1) break;

                x0 += Util.dirToDx(bestDir);
                y0 += Util.dirToDy(bestDir);
                z0 += Util.dirToDz(bestDir);
                curPressure = bestPressure;
              }

              /* Break and/or fall */
              System.out.println("Breaking point " + Util.xyzString(x0, y0, z0));
              Chunk c0 = ChunkCache.getChunk(w, x0 >> 4, z0 >> 4, true);
              ChunkTempData tempData0 = ChunkCache.getTempData(w, x0 >> 4, z0 >> 4);

              int myId = c0.getBlockID(x0 & 15, y0, z0 & 15);
              int myMeta = c0.getBlockMetadata(x0 & 15, y0, z0 & 15);
              int idBelow = c0.getBlockID(x0 & 15, y0 - 1, z0 & 15);
              System.out.println("ID below: " + idBelow);
              int targetX, targetY, targetZ, targetId;
              Chunk targetChunk = c0;

              if (idBelow == 0 || Gases.isGas[idBelow] || Fluids.isLiquid[idBelow]) {
                targetX = x0;
                targetY = y0 - 1;
                targetZ = z0;
                targetId = idBelow;
              } else {
                int r = FysiksFun.rand.nextInt(4);
                findFreeNeighbour:
                {
                  for (int dir = 0; dir < 4; dir++) {
                    targetX = x0 + Util.dirToDx((dir + r) % 4);
                    targetY = y0 + Util.dirToDy((dir + r) % 4);
                    targetZ = z0 + Util.dirToDz((dir + r) % 4);
                    targetChunk = ChunkCache.getChunk(w, targetX >> 4, targetZ >> 4, true);
                    targetId = targetChunk.getBlockID(targetX & 15, targetY, targetZ & 15);
                    if (idBelow == 0 || Gases.isGas[idBelow] || Fluids.isLiquid[idBelow]) break findFreeNeighbour;
                  }
                  // We could not find any place to move the block to. So don't move...
                  // TODO - reset the pressure here so we avoid checking these for lots of terrain?
                  break moveBlock;
                }
              }
              /* Fall as far down from target point as possible */
              while (true) {
                if (targetY <= 1) break;
                int id2 = targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15);
                if (id2 != 0) break;
                else targetY = targetY - 1;
              }

              System.out.println("Final target " + Util.xyzString(targetX, targetY, targetZ));
              /* Assign the original ID/meta to the target position */
              int targetMeta = targetChunk.getBlockMetadata(targetX & 15, targetY, targetZ & 15);
              targetChunk.setBlockIDWithMetadata(targetX & 15, targetY, targetZ & 15, myId, myMeta);
              ChunkTempData targetTempData = ChunkCache.getTempData(w, targetX >> 4, targetZ >> 4);
              tempData.setTempData(targetX, targetY, targetZ, 0); // TODO pressure here??
              delayedBlockMarkSet.add(new ChunkMarkUpdateTask(w, targetX, targetY, targetZ, targetId, targetMeta));
              /*
               * TODO - Move the target value to the original value (in case of liquids and gases). Handle fluid
               * levels!!
               */
              c0.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, 0, 0);
              tempData0.setTempData(x0, y0, z0, 0);
              delayedBlockMarkSet.add(new ChunkMarkUpdateTask(w, x0, y0, z0, myId, myMeta));
            } else tempData.setTempData(dx, y, dz, curPressure);
          }
      }
    } catch (Exception e) {
      System.out.println("BlockPhysicsSweeperThread got an exception" + e);
      e.printStackTrace();
    }
  }
}
