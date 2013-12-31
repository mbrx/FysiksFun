package mbrx.ff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

import cpw.mods.fml.common.network.Player;
import mbrx.ff.FysiksFun.WorldObserver;
import mbrx.ff.MPWorldTicker.WorldUpdateState;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class BlockPhysicsSweepWorkerThread implements Runnable {
  ChunkCoordIntPair                          xz;
  World                                      w;
  WorldUpdateState                           wstate;
  Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;

  public static int                          blockStrength[]  = new int[4096];
  public static int                          blockWeight[]    = new int[4096];
  public static boolean                      blockDoPhysics[] = new boolean[4096];

  private static final int                   ticksPerUpdate   = 2;
  private static final int                   timeToFall       = 20;
  /** Mutex for locking the threads whenever a vanilla function is called. */
  private static Semaphore                   vanillaMutex     = new Semaphore(1);
  private static final int                   clockBitsStart   = 19;
  private static final int                   pressureBitMask  = 0x7ffff;

  public static void postInit() {
    for (int i = 0; i < 4096; i++) {
      Block b = Block.blocksList[i];
      blockStrength[i] = 16;
      blockWeight[i] = 4;
      if (b == null || !b.isOpaqueCube()) blockDoPhysics[i] = false;
      else blockDoPhysics[i] = !(Fluids.isLiquid[i] || Gases.isGas[i] || i == 0);
    }
    blockStrength[Block.dirt.blockID] = 4;
    blockStrength[Block.sand.blockID] = 4;
    blockStrength[Block.grass.blockID] = 4;
    blockStrength[Block.stone.blockID] = 40;
    blockStrength[Block.cobblestone.blockID] = 12;
    blockStrength[Block.gravel.blockID] = 4;

    blockStrength[Block.leaves.blockID] = 4;
    blockWeight[Block.leaves.blockID] = 1;
    blockStrength[Block.wood.blockID] = 20;
    blockWeight[Block.wood.blockID] = 2;
  }

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
      //if(tid != 4711) return;
      if (Counters.tick % ticksPerUpdate != 0) return;

      HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet = delayedBlockMarkSets.get(tid);
      if (delayedBlockMarkSet == null) {
        delayedBlockMarkSet = new HashSet<ChunkMarkUpdateTask>();
        delayedBlockMarkSets.put(tid, delayedBlockMarkSet);
      }

      Chunk c = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, false);
      ChunkTempData tempData = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);
      int x = xz.chunkXPos << 4;
      int z = xz.chunkZPos << 4;

      int minDist = 1000 * 1000;
      for (FysiksFun.WorldObserver wo : FysiksFun.observers) {
        int dist = (int) ((wo.posX - x) * (wo.posX - x) + (wo.posZ - z) * (wo.posZ - z));
        if (dist < minDist) minDist = dist;
      }
      if (minDist > 128 * 128) return;

      boolean restartPhysics = false;
      if (tempData.physicsLastTick < Counters.tick - ticksPerUpdate) restartPhysics = true;

      tempData.physicsLastTick = Counters.tick;
      int timeNow = Counters.tick % 1024;

      ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();
      for (int dx = 0; dx < 16; dx++)
        updateAllBlocks:
        for (int dz = 0; dz < 16; dz++) {
          /**
           * Naturally occurring blocks that have a straight line connecting them to the bottom of the world. Prevents
           * too massive collapses of the original landscapes.
           */
          //boolean connectedToBottom = true;

          for (int y = 1; y < 255; y++) {
            ExtendedBlockStorage ebs = blockStorage[y >> 4];
            int x0 = x + dx, y0 = y, z0 = z + dz;

            int id = 0;
            if (ebs != null) {
              id = ebs.getExtBlockID(dx, y & 15, dz);
            } else id = c.getBlockID(dx, y, dz);
            if (id == 0 || Gases.isGas[id]) {
              tempData.setTempData(dx, y, dz, 0);
              continue;
            }
            if (!blockDoPhysics[id]) continue;
            //if (Fluids.isLiquid[id]) continue;

            /* Get current pressure, increase by our weight */
            int weight = blockWeight[id];
            int totalMoved = 0;
            int tmpVal = tempData.getTempData(dx, y, dz);
            int curPressure = (tmpVal & pressureBitMask) + weight;
            int curClock = (tmpVal >> clockBitsStart);

            /*if (connectedToBottom) {
              if (id != Block.dirt.blockID && id != Block.stone.blockID) connectedToBottom = false;
              else if (y > 2) connectedToBottom = false;
            }*/

            if (restartPhysics || tmpVal == 0 || y <= 1) {
              //|| connectedToBottom) {
              curClock = (Counters.tick % 1024);
              curPressure = 0;
            }
            int origClock = curClock;
            boolean debugMe=false;
            
            debugMe = y>4;
            //if (x0 == -1827 && z0 == 1330)
            //  debugMe=true;
            if(debugMe) {
              System.out.println("" + Util.xyzString(x + dx, y, z + dz) + " prevPressure: " + curPressure + " prevClock: " + curClock);
            }

            for (int dir = 0; dir < 6; dir++) {
              int x2 = x + dx + Util.dirToDx(dir);
              int y2 = y + Util.dirToDy(dir);
              int z2 = z + dz + Util.dirToDz(dir);
              int id2 = 0;
              if ((x2 >> 4) == (x >> 4) && (z2 >> 4) == (z >> 4)) {
                if (ebs != null) {
                  id2 = ebs.getExtBlockID(x2 & 15, y2 & 15, z2 & 15);
                } else id2 = c.getBlockID(x2 & 15, y2, z2 & 15);
              } else {
                Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
                if (c2 == null) continue;
                id2 = c2.getBlockID(x2 & 15, y2, z2 & 15);
              }
              if (!blockDoPhysics[id2]) continue;
              ChunkTempData tempData2 = ChunkCache.getTempData(w, x2 >> 4, z2 >> 4);
              int nnTmpVal = tempData2.getTempData(x2, y2, z2);
              if (nnTmpVal == 0) continue;
              int nnPressure = nnTmpVal & pressureBitMask;
              int nnClock = nnTmpVal >> clockBitsStart;

              /* Neighbour belongs to an not-updating chunk. Treat neighbour as static object. */
              if (tempData2.physicsLastTick < Counters.tick - ticksPerUpdate) {
                curClock = timeNow;
                if (nnPressure < curPressure) curPressure = curPressure - (curPressure - nnPressure) / 2;
                continue;
              }

              boolean ahead = false;
              if (nnClock > curClock) ahead = (nnClock - curClock) < 512;
              else if (nnClock == curClock) ahead = false;
              else if (nnClock < curClock) ahead = (curClock - nnClock) > 512;
              if (ahead) curClock = nnClock;

              if (debugMe) {
                System.out.println("  nn"+dir+" id:"+id2+" pres:"+nnPressure+" clock: "+ nnClock + " ahead " + ahead + " dy: " + Util.dirToDy(dir));
              }

              if (curPressure == 0 && y > 1 && nnPressure > curPressure) {
                /*
                 * Special case: we have joined a new block with no previous pressure to this area. Copy the pressure
                 * from a neighbour.
                 */
                curPressure = nnPressure;
              } else if (nnPressure > curPressure + 1) {
                int toMove;
                toMove = (nnPressure - curPressure) / 2;
                if (dir < 4) totalMoved += toMove * 2;
                else totalMoved += toMove;
                nnPressure -= toMove;
                curPressure += toMove;
                tempData2.setTempData(x2, y2, z2, nnPressure | (nnClock << clockBitsStart));

                if (debugMe) {
                  System.out.println("  toMove " + toMove + " dy: " + Util.dirToDy(dir));
                }
              }
            }
            if (y <= 1) {
              curPressure = 0;
              curClock = Counters.tick % 1024;
              totalMoved = 0;
            }

            int breakThreshold = blockStrength[id];
            boolean doFall = false;
            int timeCheck = (timeNow - timeToFall + 1024) % 1024;
            if (curClock < timeCheck) doFall = (timeCheck - curClock) < 900;
            else if (curClock > timeCheck) doFall = (curClock - timeCheck) > 100;
            boolean doBreak = totalMoved > breakThreshold; // && FysiksFun.rand.nextInt(20) == 0;

            // TODO - random sampling that resets the tempData for air (is this actually needed ?)
            // TODO - only do physics close enough to the players....
            // TODO - two 8-bit counters for the clock for each cell. Counter (A) counts the clock from the world bottom. 
            // whenever counter A is (1) not updated and (2) older than x cycles then (3) set (B) to a point y cycles in the future. 
            // unless it is already set to a point earlier than now+y. If we reach the point where now>(b) then we let the block fall and set (b) to be now+1.
            // TODO - two counters are not needed. Just keep a track of when each chunk was last ticked. If it haven't been ticked for a while then 
            // it should fall. Treat border cases by assuming that they always have an updated clock.

            //if(y>3) {
            if (debugMe) {
              System.out.println("  curclock: " + curClock + " origclock: " + origClock + " time: " + timeNow + " fall: " + doFall);
              System.out.println("  totalMoved: " + totalMoved + " doBreak:" + doBreak + " curPressure:" + curPressure);
            }

            if (origClock != curClock) doFall = false;
            //if (connectedToBottom) doBreak = false;

            moveBlock:
            if (doFall || doBreak) {
              boolean canMoveSideways = doBreak;
              int myId = id;
              int myMeta = c.getBlockMetadata(x0 & 15, y0, z0 & 15);

              if (y > 4  && false) {
                System.out.println((doBreak?"Break":"Fall")+" @" + Util.xyzString(x + dx, y, z + dz) + " pres:" + curPressure + " moved: " + totalMoved + " clock: " + curClock
                    + " timeNow: " + (timeNow % 1024));

                for (int dir = 0; dir < 6; dir++) {
                  int tX = x + dx + Util.dirToDx(dir);
                  int tY = y + Util.dirToDy(dir);
                  int tZ = z + dz + Util.dirToDz(dir);
                  int tmpId = w.getBlockId(tX, tY, tZ);
                  ChunkTempData tData = ChunkCache.getTempData(w, tX >> 4, tZ >> 4);
                  int p = tData.getTempData(tX, tY, tZ);
                  System.out.println("Neighbour " + Util.xyzString(tX, tY, tZ) + " id:" + tmpId + " pres:" + (p & 0xffff) + " clock: " + (p >> clockBitsStart));
                }
              }
              

              /* Break and/or fall */
              ChunkTempData tempData0 = ChunkCache.getTempData(w, x0 >> 4, z0 >> 4);

              int idBelow = c.getBlockID(x0 & 15, y0 - 1, z0 & 15);
              int targetX, targetY, targetZ, targetId;
              Chunk targetChunk = c;

              // TODO Possible bug. When we decide not to move a block that breaks from pressure. It may result in not even updating the current pressure. Should this be done?
              if (!blockDoPhysics[idBelow]) {
                targetX = x0;
                targetY = y0 - 1;
                targetZ = z0;
                targetId = idBelow;
              } else {
                if (!canMoveSideways) break moveBlock;
                int r = FysiksFun.rand.nextInt(4);
                findFreeNeighbour:
                {
                  for (int dir = 0; dir < 4; dir++) {
                    targetX = x0 + Util.dirToDx((dir + r) % 4);
                    targetY = y0 + Util.dirToDy((dir + r) % 4);
                    targetZ = z0 + Util.dirToDz((dir + r) % 4);
                    targetChunk = ChunkCache.getChunk(w, targetX >> 4, targetZ >> 4, false);
                    if (targetChunk == null) continue;
                    targetId = targetChunk.getBlockID(targetX & 15, targetY, targetZ & 15);

                    if (!blockDoPhysics[targetId]) {
                      idBelow = targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15);
                      if (!blockDoPhysics[idBelow]) break findFreeNeighbour;
                    }
                  }
                  // We could not find any place to move the block to. So don't move...
                  // TODO - reset the pressure here so we avoid checking these for lots of terrain? NO
                  // maybe increase pressure instead to reduce this guys moved forces?
                  break moveBlock;
                }
              }

              if (doBreak) Counters.brokenBlocks++;
              else Counters.fallenBlocks++;

              /*
              if (doBreak) {
                if (Counters.brokenBlocks < 10 && Counters.tick > 1000) {
                  vanillaMutex.acquire();
                  Block.blocksList[id].dropBlockAsItem(w, x0, y0, z0, 0, 0);
                  vanillaMutex.release();
                }
                FysiksFun.setBlockIDandMetadata(w, c, x0, y0, z0, 0, 0, myId, myMeta, delayedBlockMarkSet);
                Counters.brokenBlocks++;
                continue;
              } */

              // To avoid from falling too fast
              curClock = timeNow;

              /* Fall as far down from target point as possible in the begining of a simulation */
              /*if (Counters.tick < 1000) {
                while (true) {
                  if (targetY <= 1) break;
                  int id2 = targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15);
                  if (id2 != 0) break;
                  else targetY = targetY - 1;
                }
              }*/

              //System.out.println("Final target " + Util.xyzString(targetX, targetY, targetZ));
              /* Assign the original ID/meta to the target position */

              if (Block.blocksList[targetId] instanceof ITileEntityProvider) {
                /* Find the corresponding tile entity... and move it too */
                //TileEntity entity = targetChunk.getChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
                targetChunk.removeChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
                System.out.println("Crushing a tile entity!");
              }

              int targetMeta = targetChunk.getBlockMetadata(targetX & 15, targetY, targetZ & 15);
              FysiksFun.setBlockIDandMetadata(w, targetChunk, targetX, targetY, targetZ, myId, myMeta, targetId, targetMeta, delayedBlockMarkSet);
              ChunkTempData targetTempData = ChunkCache.getTempData(w, targetX >> 4, targetZ >> 4);
              tempData.setTempData(targetX, targetY, targetZ, (curPressure / 2) | (curClock << 16)); // Pressure to use here?
              /*
               * TODO - Move the target value to the original value (in case of liquids and gases). Handle fluid
               * levels!!
               */

              /* Update the corresponding TileEntity instance if the moved block was a tile entity type of block */
              if (Block.blocksList[myId] instanceof ITileEntityProvider) {
                /* Find the corresponding tile entity... and move it too */
                TileEntity entity = c.getChunkBlockTileEntity(x0 & 15, y0, z0 & 15);
                System.out.println("Attempting to move a tileentity");
                if (entity == null) {
                  System.out.println("Warning - could not find a corresponding tile entity at position: " + Util.xyzString(x0, y0, z0) + " id:" + myId);
                } else {
                  c.removeChunkBlockTileEntity(x0 & 15, y0, z0 & 15);
                  entity.xCoord = targetX;
                  entity.yCoord = targetY;
                  entity.zCoord = targetZ;
                  entity.validate();
                  targetChunk.addTileEntity(entity);
                }

              }

              FysiksFun.setBlockIDandMetadata(w, c, x0, y0, z0, 0, 0, myId, myMeta, delayedBlockMarkSet);
              tempData0.setTempData(x0, y0, z0, 0);
              removeEntitiesInsideBlock(w, targetX, targetY, targetZ);
              continue updateAllBlocks;
            }

            tempData.setTempData(dx, y, dz, curPressure | (curClock << clockBitsStart));
          }
        }
    } catch (Exception e) {
      System.out.println("BlockPhysicsSweeperThread got an exception" + e);
      e.printStackTrace();
    }
  }

  private void removeEntitiesInsideBlock(World w2, int targetX, int targetY, int targetZ) {
    ArrayList<Entity> allEntities = new ArrayList((List<Entity>) w.loadedEntityList);
    LinkedList<Entity> toRemove = new LinkedList<Entity>();

    for (Entity e : allEntities) {
      if (e instanceof EntityItem) {
        AxisAlignedBB aabb = e.boundingBox;
        if (((aabb.minX >= targetX && aabb.minX <= targetX + 1.0) || (aabb.maxX >= targetX && aabb.maxX <= targetX + 1.0))
            && ((aabb.minY >= targetY && aabb.minY <= targetY + 1.0) || (aabb.maxY >= targetX && aabb.maxY <= targetX + 1.0))
            && ((aabb.minZ >= targetZ && aabb.minZ <= targetZ + 1.0) || (aabb.maxZ >= targetX && aabb.maxZ <= targetX + 1.0))) toRemove.add(e);
      }
    }
    if (toRemove.size() > 0) {
      System.out.println("Removing " + (toRemove.size()) + " entityitems inside a block that has moved");
      try {
        vanillaMutex.acquire();
        for (Entity e : toRemove) {
          w.removeEntity(e);
        }
        // w.loadedEntityList.removeAll(toRemove);
      } catch (InterruptedException e1) {
        e1.printStackTrace();
      } finally {
        vanillaMutex.release();
      }
    }
  }
}
