package mbrx.ff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

import buildcraft.transport.BlockGenericPipe;
import cpw.mods.fml.common.network.Player;
import mbrx.ff.FysiksFun.WorldObserver;
import mbrx.ff.MPWorldTicker.WorldUpdateState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockOre;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class WorkerPhysicsSweep implements Runnable {
  ChunkCoordIntPair                          xz;
  World                                      w;
  WorldUpdateState                           wstate;
  Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;

  public static int                          blockStrength[]         = new int[4096];
  /** The weight of each blockID, negative weights corresponds to fractional (stochastic) weights */
  public static int                          blockWeight[]           = new int[4096];
  public static boolean                      blockDoPhysics[]        = new boolean[4096];
  /** 0 all normal blocks, 1+ blocks not affected by full (breakable) physics. Lower numbers can support higher numbers. */
  public static int                          blockDoSimplePhysics[]  = new int[4096];
  public static boolean						 blockIsFragile[]		 = new boolean[4096];
  
  private static final int                   ticksPerUpdate          = 1;
  private static final int                   timeToFall              = 4;
  /** Mutex for locking the threads whenever a vanilla function is called. */
  private static Semaphore                   vanillaMutex            = new Semaphore(1);
  private static final int                   pressureBitsMask        = 0x7ffff;
  private static final int                   clockBitsStart          = 19;
  private static final int                   clockBitsMask           = 0x0ff;
  private static final int                   clockModulo             = 256;
  private static final int                   clockMaxDist            = 127;
  private static final int                   breakBitsStart          = 27;
  private static final int                   breakBitsMask           = 0x0f;
  private static final int                   breakAtCounter          = 14;
  private static final int                   counterIsFalling        = 15;
  private static final int                   maxChunkDist            = 48;
  private static final int                   countdownToAction       = 20;
  private static final int                   fallForce               = 20;
  //private static final int                   numSweeps              = 4;
  private static final int                   elasticStrengthConstant = 200;

  public static void postInit() {
    for (int i = 0; i < 4096; i++) {
      Block b = Block.blocksList[i];
      blockStrength[i] = 16;
      blockWeight[i] = 4;
      blockDoSimplePhysics[i] = 0;
      blockDoPhysics[i] = false;
      blockIsFragile[i] = false;
      if (b == null) continue;
      if (b.isOpaqueCube()) blockDoPhysics[i] = true;

      /* Default value for all ores */
      if (b instanceof BlockOre) {
        blockStrength[i] = 40;
        blockWeight[i] = 8;
      } else if(FysiksFun.hasBuildcraft && b instanceof BlockGenericPipe) { 
          blockStrength[i] = 20;
          blockWeight[i] = 2;
          blockDoPhysics[i] = true;    	  
      } else if (b instanceof ITileEntityProvider) {
        blockStrength[i] = 20;
        blockWeight[i] = 4;
        blockDoPhysics[i] = true;
      }
      if(b instanceof BlockBreakable) blockIsFragile[i]=true;
      
      if (Fluids.isLiquid[i] || Gases.isGas[i] || i == 0) blockDoPhysics[i] = false;
      //if (!blockDoPhysics[i]) continue;
    }

    /*
    blockDoPhysics[Block.leaves.blockID] = true;
    blockStrength[Block.leaves.blockID] = 10; //        100 times weight!
    blockWeight[Block.leaves.blockID] = -10;
    */

    blockDoPhysics[Block.bedrock.blockID] = true;
    blockWeight[Block.bedrock.blockID] = 0;

    blockDoPhysics[Block.leaves.blockID] = false;
    blockDoSimplePhysics[Block.leaves.blockID] = 1;
    blockDoPhysics[Block.vine.blockID] = false;
    blockDoSimplePhysics[Block.vine.blockID] = 2;

    blockStrength[Block.gravel.blockID] = 4;
    blockWeight[Block.gravel.blockID] = 4;
    blockStrength[Block.dirt.blockID] = 2;
    blockWeight[Block.dirt.blockID] = 1;
    blockStrength[Block.sand.blockID] = 4;
    blockStrength[Block.cobblestone.blockID] = 40; // 5 times weight
    blockWeight[Block.cobblestone.blockID] = 8;

    blockStrength[Block.stone.blockID] = 200; //      100 times weight
    blockWeight[Block.stone.blockID] = 2; // stone is unplaceable, low weight for now!
    blockStrength[Block.stoneBrick.blockID] = 72; // 12 times weight
    blockWeight[Block.stoneBrick.blockID] = 6;
    blockStrength[Block.brick.blockID] = 60; //      15 times weight
    blockWeight[Block.brick.blockID] = 4;

    blockStrength[Block.wood.blockID] = 60; //       15 times weight
    blockWeight[Block.wood.blockID] = 4;
    blockStrength[Block.planks.blockID] = 20; //     10 times weight
    blockWeight[Block.planks.blockID] = 2;

    blockDoPhysics[Block.thinGlass.blockID] = true;
    blockIsFragile[Block.thinGlass.blockID] = true;
    blockStrength[Block.thinGlass.blockID] = 5; // 5 times weight
    blockWeight[Block.thinGlass.blockID] = 1;
    blockDoPhysics[Block.glass.blockID] = true;
    blockStrength[Block.glass.blockID] = 10; //      5 times weight
    blockWeight[Block.glass.blockID] = 2;
    blockIsFragile[Block.glass.blockID] = true;
    blockStrength[Block.glowStone.blockID] = 40; //     20 times weight (it's anyway too expensive to use for this?)
    blockWeight[Block.glowStone.blockID] = 2;
    blockIsFragile[Block.glowStone.blockID] = true;
    
    blockStrength[Block.fence.blockID] = 10; // 5 times weight 
    blockWeight[Block.fence.blockID] = 2;

    blockStrength[Block.blockLapis.blockID] = 120; // 20 times weight
    blockWeight[Block.blockLapis.blockID] = 6;
    blockStrength[Block.blockNetherQuartz.blockID] = 72; // 24 times weight (!) 
    blockWeight[Block.blockNetherQuartz.blockID] = 3;
    blockStrength[Block.blockIron.blockID] = 120; // 30 times weight 
    blockWeight[Block.blockIron.blockID] = 4;
    blockStrength[Block.blockGold.blockID] = 240; // 30 times weight 
    blockWeight[Block.blockGold.blockID] = 8;
    blockStrength[Block.blockDiamond.blockID] = 240; // 80 times weight (!) 
    blockWeight[Block.blockDiamond.blockID] = 3;
    blockStrength[Block.blockEmerald.blockID] = 240; // 80 times weight (!) 
    blockWeight[Block.blockEmerald.blockID] = 3;

    blockStrength[Block.obsidian.blockID] = 160; // 10 times weight
    blockWeight[Block.obsidian.blockID] = 16;

    blockStrength[Block.blockSnow.blockID] = 3; // 3 times weight
    blockWeight[Block.blockSnow.blockID] = 1;
    blockStrength[Block.ice.blockID] = 9; // 3 times weight
    blockWeight[Block.ice.blockID] = 3;

    blockStrength[Block.hay.blockID] = 6; // 3 times weight 
    blockWeight[Block.hay.blockID] = 2;
    blockStrength[Block.cloth.blockID] = 6; // 3 times weight 
    blockWeight[Block.cloth.blockID] = 2;

    blockStrength[Block.hardenedClay.blockID] = 60; // 15 times weight 
    blockWeight[Block.hardenedClay.blockID] = 4;

    /* Hell */
    // Netherrack is special, modified inside the 'run' method
    //blockStrength[Block.netherrack.blockID] = 16;
    //blockWeight[Block.netherrack.blockID] = 4;
    blockStrength[Block.slowSand.blockID] = 4;
    blockWeight[Block.slowSand.blockID] = 4;
    blockStrength[Block.netherBrick.blockID] = 80; // 20 times weight
    blockWeight[Block.netherBrick.blockID] = 4;

    /* Misc furniture */

    /* Aliases */

    blockStrength[Block.grass.blockID] = blockStrength[Block.dirt.blockID];
    blockWeight[Block.grass.blockID] = blockWeight[Block.dirt.blockID];
    blockStrength[Block.cobblestoneMossy.blockID] = blockStrength[Block.cobblestone.blockID];
    blockWeight[Block.cobblestoneMossy.blockID] = blockWeight[Block.cobblestone.blockID];
    blockStrength[Block.cobblestoneWall.blockID] = blockStrength[Block.cobblestone.blockID];
    blockWeight[Block.cobblestoneWall.blockID] = blockWeight[Block.cobblestone.blockID];
    blockStrength[Block.bookShelf.blockID] = blockStrength[Block.planks.blockID];
    blockWeight[Block.bookShelf.blockID] = blockWeight[Block.planks.blockID];

  }

  public WorkerPhysicsSweep(World w, WorldUpdateState wstate, ChunkCoordIntPair xz, Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets) {
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
      //int sweep = (Counters.tick/ticksPerUpdate) % numSweeps;

      /* TODO: these modifications are dangerous inside a server with multiple worlds to be ticked (or maybe not?) */
      if (w.provider.dimensionId == 0) {
        blockStrength[Block.netherrack.blockID] = 16;
        blockWeight[Block.netherrack.blockID] = 4;
      } else {
        blockStrength[Block.netherrack.blockID] = 50;
        blockWeight[Block.netherrack.blockID] = 1;
      }

      HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet = delayedBlockMarkSets.get(tid);
      if (delayedBlockMarkSet == null) {
        delayedBlockMarkSet = new HashSet<ChunkMarkUpdateTask>();
        delayedBlockMarkSets.put(tid, delayedBlockMarkSet);
      }

      Chunk c = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, false);
      ChunkTempData tempData = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);
      int x = xz.chunkXPos << 4;
      int z = xz.chunkZPos << 4;

      int minDist = maxChunkDist * maxChunkDist;
      for (FysiksFun.WorldObserver wo : FysiksFun.observers) {
        int dist = (int) ((wo.posX - x) * (wo.posX - x) + (wo.posZ - z) * (wo.posZ - z));
        if (dist < minDist) minDist = dist;
      }
      // Only run the physics calculations for chunks within this distance from an observer
      if (minDist >= maxChunkDist * maxChunkDist) return;

      boolean restartPhysics = false;
      if (tempData.physicsLastTick < Counters.tick - ticksPerUpdate) {
        tempData.physicsCountdownToAction = countdownToAction;
        restartPhysics = true;
      } else {
        tempData.physicsCountdownToAction = Math.max(0, tempData.physicsCountdownToAction - 1);
      }

      int timeNow = Counters.tick % clockModulo;
      /** Number of sound effects we are still allowed to play for this chunk */
      int maxSoundEffects = 5, nSoundEffectsLeft = maxSoundEffects;
      int soundEffectAttempts = 0;

      ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();

      for (int y = 1; y < 255; y++) {
        ExtendedBlockStorage ebs = blockStorage[y >> 4];
        int ox = FysiksFun.rand.nextInt(16);
        int oz = FysiksFun.rand.nextInt(16);
        for (int dxTmp = 0; dxTmp < 16; dxTmp++) {
          int dx = (dxTmp + ox) & 15;
          updateAllBlocks:
          for (int dzTmp = 0; dzTmp < 16; dzTmp++) {
            int dz = (dzTmp + oz) & 15;

            int x0 = x + dx, y0 = y, z0 = z + dz;

            int id = 0;
            if (ebs != null) {
              id = ebs.getExtBlockID(dx, y & 15, dz);
            } else id = c.getBlockID(dx, y, dz);
            if (id == 0 || Gases.isGas[id]) {
              tempData.setTempData(dx, y, dz, 0);
              continue;
            }
            if (!blockDoPhysics[id] && blockDoSimplePhysics[id] == 0) continue; // It's not a proper "simple physics" block

            /* Get current pressure, increase by our weight */
            int weight = blockWeight[id];
            if (weight < 0) weight = (FysiksFun.rand.nextInt(-weight) == 0 ? 1 : 0);

            int totalMoved = 0;
            int tmpVal = tempData.getTempData(dx, y, dz);
            int origPressure = (tmpVal & pressureBitsMask);
            int curPressure = origPressure + weight; //(sweep==0?weight:0);
            int curClock = (tmpVal >> clockBitsStart) & clockBitsMask;
            int curBreak = (tmpVal >> breakBitsStart);
            boolean isFalling = curBreak == counterIsFalling;

            if (restartPhysics || tmpVal == 0 || y <= 1 || id == Block.bedrock.blockID) {
              curClock = timeNow;
              //curPressure = 0;
              curBreak = 0;
            }

            int simplifiedPhysics = blockDoSimplePhysics[id];
            if (simplifiedPhysics > 0) curPressure = 0;

            int origClock = curClock;
            int breakThreshold = blockStrength[id];
            /** Amount of pressure that we will NOT move due to us already starting to break. */
            int elasticPressure = (breakThreshold / 2) * curBreak;
            elasticPressure = elasticStrengthConstant / (breakAtCounter - 1) * curBreak;

            boolean debugMe = false;

            /***** debug *****/
            if (Counters.tick % 50 == 0) {
              if (id == Block.hardenedClay.blockID) debugMe = true;
              //if (id == Block.planks.blockID) debugMe = true;
              // if(x0 == -1837 && z0==1339) debugMe=true;             
              //if (id == Block.bedrock.blockID && x0 == 222 && z0 == 119) debugMe = true;
            }

            if (debugMe) {
              System.out.println("id:" + id + "@" + Util.xyzString(x + dx, y, z + dz) + " prevPressure: " + curPressure + " prevClock: " + curClock
                  + " prevBreak: " + curBreak);
            }

            /* Iterate over all neighbours, update our clock and pressure accordingly */
            for (int dir = 0; dir < 6; dir++) {
              int x2 = x + dx + Util.dirToDx(dir);
              int dy = Util.dirToDy(dir);
              int y2 = y + dy;
              int z2 = z + dz + Util.dirToDz(dir);
              int nnId = 0;
              if ((x2 >> 4) == (x >> 4) && (z2 >> 4) == (z >> 4)) {
                ExtendedBlockStorage nnEbs = blockStorage[y2 >> 4];
                if (nnEbs != null) {
                  nnId = nnEbs.getExtBlockID(x2 & 15, y2 & 15, z2 & 15);
                } else nnId = c.getBlockID(x2 & 15, y2, z2 & 15);
              } else {
                Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
                if (c2 == null) continue;
                nnId = c2.getBlockID(x2 & 15, y2, z2 & 15);
              }
              /*if(Fluids.isLiquid[nnId] && dy != 0) {
                Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
                ChunkTempData tempData2 = ChunkCache.getTempData(w, x2 >> 4, z2 >> 4);
                int nnTmpVal = tempData2.getTempData(x2, y2, z2);
                int liquidContent = Fluids.fluid[nnId].getBlockContent(c2, tempData2, x2,y2,z2);
                if(dy > 0) {
                  // Note the difference in rounding of the cases where dy>0
                  int liquidWeight = (liquidContent*6) / BlockFluid.maximumContent;
                  curPressure += liquidWeight;
                } else {
                  int liftingForce = (liquidContent / BlockFluid.maximumContent) * 6;
                  if(liftingForce > 0) {
                    curPressure = Math.max(0,curPressure-liftingForce);
                    if(curPressure < 5000) curClock = timeNow;
                  }
                }
              }*/
              if (!blockDoPhysics[nnId]) {
                if (blockDoSimplePhysics[nnId] == 0) continue;
                if (blockDoSimplePhysics[nnId] > simplifiedPhysics) continue;
              }
              ChunkTempData tempData2 = ChunkCache.getTempData(w, x2 >> 4, z2 >> 4);
              int nnTmpVal = tempData2.getTempData(x2, y2, z2);
              if (nnTmpVal == 0 && y != 2) continue;
              int nnPressure = nnTmpVal & pressureBitsMask;
              int nnClock = (nnTmpVal >> clockBitsStart) & clockBitsMask;
              int nnBreak = (nnTmpVal >> breakBitsStart);
              boolean nnIsFalling = nnBreak == counterIsFalling;

              /* Neighbour belongs to a not-updating chunk. Treat neighbour as static object. */
              if (tempData2.physicsLastTick < Counters.tick - 2 * ticksPerUpdate) {
                curClock = timeNow;
                if (nnPressure < curPressure) curPressure = curPressure - (curPressure - nnPressure) / 2;
                continue;
              }

              boolean ahead = false;

              /* Calculate whom of us is closer to the real clock. */
              int myClockDist = (curClock <= timeNow ? timeNow - curClock : timeNow + clockModulo - curClock);
              int nnClockDist = (nnClock <= timeNow ? timeNow - nnClock : timeNow + clockModulo - nnClock);
              ahead = nnClockDist < myClockDist;

              // Update our clock accordingly, but not if one of us is falling but not the other
              if (nnIsFalling == isFalling) {
                //                  nnBreak != breakAtCounter && curBreak != breakAtCounter)
                if (ahead) curClock = nnClock;
                else nnClock = curClock;
              }

              if (debugMe) {
                System.out.println("  nn" + dir + " id:" + nnId + " pres:" + nnPressure + " clock: " + nnClock + " ahead " + ahead + " dy: "
                    + Util.dirToDy(dir) + " nnBreak:" + nnBreak + " curP:" + curPressure);
              }
              if (simplifiedPhysics > 0) continue;

              /* If we are restarting the physics computations, then just spread the pressures without eliminating them. */
              if (restartPhysics) { // || tempData.physicsCountdownToAction > 18) {
                if (nnPressure > curPressure) curPressure = nnPressure;
                continue;
              }

              if (origPressure == 0 && id != Block.bedrock.blockID && y > 1 && nnPressure > curPressure) {
                /*
                 * Special case: we have joined a new block with no previous pressure to this area. Copy the pressure
                 * from a neighbour.
                 */
                curPressure = nnPressure;
              } else
              /* Only allow forces to be transmitted through us if we are not too broken. Forces will prefer to go through less broken blocks. */
              if (nnPressure > curPressure + 2 + elasticPressure) {
                int toMove;

                toMove = (nnPressure - curPressure - elasticPressure) / 2;

                if ((id == Block.planks.blockID || id == Block.wood.blockID) && nnId == Block.stone.blockID) {
                  /* Planks and wood are stronger when supporting original stone. To be used as support in mines. */
                  if (toMove > breakThreshold * 3 && curBreak < breakAtCounter) toMove = breakThreshold * 3;
                  totalMoved += toMove / 3;
                  totalMoved = totalMoved / 3;
                } else {
                  if (curBreak < breakAtCounter && toMove > breakThreshold && id != Block.bedrock.blockID && y > 1) toMove = breakThreshold;
                  if (dir < 4) totalMoved += toMove * 2;
                  else totalMoved += toMove;
                }

                nnPressure -= toMove;
                if (id != Block.bedrock.blockID) curPressure += toMove;
                tempData2.setTempData(x2, y2, z2, nnPressure | (nnClock << clockBitsStart) | (nnBreak << breakBitsStart));

                if (debugMe) {
                  System.out.println("  toMove " + toMove + " dy: " + Util.dirToDy(dir));
                }
              }
            }
            /* Make y=1 as well as all bedrock act as sinks/supports */
            if (y <= 1 || id == Block.bedrock.blockID) {
              curPressure = 0;
              curClock = timeNow;
              totalMoved = 0;
            }

            /* Compute if we should INITIATE or CONTINUE a FALL */
            boolean doFall = curBreak == counterIsFalling;
            boolean fallDueToClock = false;
            int timeCheck = (timeNow - timeToFall + clockModulo) % clockModulo;

            /* Compute if we should start falling due no support from ground (no clock) */
            if (curClock < timeCheck) {
              fallDueToClock = (timeCheck - curClock) < (clockModulo * 4) / 5;
            } else if (curClock > timeCheck) {
              fallDueToClock = (curClock - timeCheck) > clockModulo / 5;
            }
            if (origClock != curClock) fallDueToClock = false;
            doFall = doFall || fallDueToClock;

            /* Compute if we should INITIATE a BREAK */
            boolean doBreak = totalMoved >= breakThreshold;
            if (simplifiedPhysics > 0) doBreak = false;

            /* Prevent all action if the chunk based "counter since start" haven't reached zero */
            if (tempData.physicsCountdownToAction > 0) {
              if (debugMe) System.out.println("No action due to countdown: " + tempData.physicsCountdownToAction);
              doBreak = false;
              doFall = false;
            }

            /* Update COUNTERS */
            if (doBreak == false && doFall == false) {
              //if(sweep == numSweeps-1)
              curBreak = Math.max(curBreak - 1, 0);
            } else if (curBreak < breakAtCounter) {
              curBreak = Math.min(breakAtCounter, curBreak + 1);
              //curBreak = Math.min(breakAtCounter, curBreak + Math.max(1, Math.min(4,totalMoved / breakThreshold)));
              doBreak = false;
              doFall = false;
              if (debugMe) System.out.println("No break, but new counter: " + curBreak);
            }
            /* Wait with falling if sufficient time haven't passed since the last fall */
            if (doFall && !fallDueToClock) {
              if (debugMe) System.out.println("Wait with the fallling");
              doFall = false;
            }

            if (debugMe) {
              System.out.println("  curclock: " + curClock + " origclock: " + origClock + " time: " + timeNow + " fall: " + doFall);
              System.out.println("  totalMoved: " + totalMoved + " breakCounter: " + curBreak + " doBreak:" + doBreak + " curPressure:" + curPressure);
            }

            moveBlock:
            if (doFall || doBreak) {
              boolean canMoveSideways = doBreak;
              int myId = id;
              int myMeta = c.getBlockMetadata(x0 & 15, y0, z0 & 15);

              /*
              if (y > 4) {
                System.out.println((doBreak ? "Break" : "Fall") + " @" + Util.xyzString(x + dx, y, z + dz) + " pres:" + curPressure + " moved: " + totalMoved
                    + " clock: " + curClock + " timeNow: " + timeNow);

                for (int dir = 0; dir < 6; dir++) {
                  int tX = x + dx + Util.dirToDx(dir);
                  int tY = y + Util.dirToDy(dir);
                  int tZ = z + dz + Util.dirToDz(dir);
                  int tmpId = w.getBlockId(tX, tY, tZ);
                  ChunkTempData tData = ChunkCache.getTempData(w, tX >> 4, tZ >> 4);
                  int p = tData.getTempData(tX, tY, tZ);
                  System.out.println("Neighbour " + Util.xyzString(tX, tY, tZ) + " id:" + tmpId + " pres:" + (p & pressureBitsMask) + " clock: "
                      + ((p >> clockBitsStart) & clockBitsMask));
                }
              }*/

              /* Break and/or fall */
              ChunkTempData tempData0 = ChunkCache.getTempData(w, x0 >> 4, z0 >> 4);

              int idAbove = c.getBlockID(x0 & 15, y0 + 1, z0 & 15);
              int idBelow = c.getBlockID(x0 & 15, y0 - 1, z0 & 15);
              int targetX, targetY, targetZ, targetId;
              Chunk targetChunk = c;

              if (!blockDoPhysics[idBelow]) {
                targetX = x0;
                targetY = y0 - 1;
                targetZ = z0;
                targetId = idBelow;
              } else {
                if (!canMoveSideways) {
                  curBreak = 0;
                  break moveBlock;
                }
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
                      int idBelowTarget = targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15);
                      if (!blockDoPhysics[idBelowTarget]) break findFreeNeighbour;
                    }
                  }
                  // We could not find any place to move the block to. So don't move...
                  // TODO - reset the pressure here so we avoid checking these for lots of terrain? NO
                  // maybe increase pressure instead to reduce this guys moved forces?
                  curBreak = 0;
                  break moveBlock;
                }
              }

              int idBelowTarget = targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15);
              boolean hasLanded = (idBelowTarget != 0 && !Fluids.isLiquid[idBelowTarget] && !Gases.isGas[idBelowTarget]);
              if (doBreak || hasLanded) {
                soundEffectAttempts++;
                if (FysiksFun.rand.nextInt(maxSoundEffects) < nSoundEffectsLeft) {
                  int effectiveWeight = weight <= 0 ? 1 : weight;
                  if (id == Block.stone.blockID) effectiveWeight = 8;
                  else if (id == Block.dirt.blockID) effectiveWeight = 4;
                  vanillaMutex.acquire();
                  nSoundEffectsLeft--;
                  float volume = (float) (0.5F * effectiveWeight + soundEffectAttempts * 0.2);
                  float pitch = (float) (FysiksFun.rand.nextFloat() + 1.0F) * 0.5F / effectiveWeight;
                  w.playSoundEffect(targetX + 0.5, targetY + 0.5, targetZ + 0.5, "fysiksfun:rubble", volume, pitch);
                  vanillaMutex.release();
                }
              }
              if(blockIsFragile[id] && (doBreak || hasLanded)) {            	  
                  w.playSoundEffect(targetX + 0.5, targetY + 0.5, targetZ + 0.5, "random.break", 1.0F, 1.0F);
            	  id=0;
            	  myId=0;
              }
              
              if (doBreak) Counters.brokenBlocks++;
              else Counters.fallenBlocks++;

              // To avoid from falling too fast
              curBreak = counterIsFalling;
              curClock = timeNow;

              // Check if there where any blocks above us that is not handled by the physics. If so, break them into items. 
              if (!blockDoPhysics[idAbove] && idAbove != 0 && !Fluids.isLiquid[idAbove] && !Gases.isGas[idAbove]) {
                vanillaMutex.acquire();
                Block.blocksList[idAbove].dropBlockAsItem(w, x0, y0 + 1, z0, 0, 0);
                vanillaMutex.release();
                // This is done with the wrong 'old meta', but that doesn't matter since we in the worst case just make a mark too much
                FysiksFun.setBlockIDandMetadata(w, c, x0, y0 + 1, z0, 0, 0, idAbove, 0, delayedBlockMarkSet);
              }

              // Rewrite grass to dirt when it is moved
              if (myId == Block.grass.blockID) myId = Block.dirt.blockID;

              if (Block.blocksList[targetId] instanceof ITileEntityProvider) {
                /* Find the corresponding tile entity... and move it too */
                //TileEntity entity = targetChunk.getChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
                targetChunk.removeChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
                System.out.println("Crushing a tile entity!");
              }

              if (debugMe)
                System.out.println("  tar:" + Util.xyzString(targetX, targetY, targetZ) + " update clock:" + curClock + " count:" + curBreak + " pres:" + 0);

              /* Assign the original ID/meta to the target position */
              ChunkTempData targetTempData = ChunkCache.getTempData(w, targetX >> 4, targetZ >> 4);
              int targetMeta = targetChunk.getBlockMetadata(targetX & 15, targetY, targetZ & 15);
              int targetTmp = tempData.getTempData(targetX, targetY, targetZ);
              FysiksFun.setBlockIDandMetadata(w, targetChunk, targetX, targetY, targetZ, myId, myMeta, targetId, targetMeta, delayedBlockMarkSet);
              int newPressure = curPressure + fallForce * (weight > 0 ? weight : 0);
              tempData.setTempData(targetX, targetY, targetZ, newPressure | (curClock << clockBitsStart) | (curBreak << breakBitsStart));

              /* If the target block is not empty, a gas, or a liquid; then crush it and make a drop of it */
              if (targetId != 0 && !Fluids.isLiquid[targetId] && !Gases.isGas[targetId]) {
                boolean doDrop = false;

                if (Block.blocksList[targetId] instanceof BlockDoor) {
                  if (targetY > 0 && targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15) == targetId) doDrop = true;
                } else if (Block.blocksList[targetId] instanceof BlockBed) {
                  BlockBed bed = (BlockBed) Block.blocksList[targetId];
                  if (bed.isBlockHeadOfBed(targetMeta)) doDrop = true;
                } else doDrop = true;
                vanillaMutex.acquire();
                if (doDrop) Block.blocksList[targetId].dropBlockAsItem(w, targetX, targetY, targetZ, 0, 0);
                if (Block.blocksList[targetId] instanceof ITileEntityProvider) {
                  TileEntity targetEntity = targetChunk.getChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
                  targetChunk.removeChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
                }
                vanillaMutex.release();
                targetId = 0;
              }
              /* From here on targetId must be zero, or a liquid, or a gas */

              /* Update the corresponding TileEntity instance if the moved block was a tile entity type of block */
              if (Block.blocksList[myId] instanceof ITileEntityProvider) {
                vanillaMutex.acquire();
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
                vanillaMutex.release();
              }

              boolean useSlowSetBlock = false;
              useSlowSetBlock = true; // !!!! for now
              if (idAbove == 0 || (Block.blocksList[idAbove] != null && !Block.blocksList[idAbove].isOpaqueCube())) {
                useSlowSetBlock = true;
              }

              /* Swap our block with the target, and update target entity if applicable  */
              if (useSlowSetBlock) {
                vanillaMutex.acquire();
                //BlockFluid.preventSetBlockLiquidFlowover=true; // Not needed, breakBlock is not called!  
                c.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, targetId, targetMeta);
                delayedBlockMarkSet.add(new ChunkMarkUpdateTask(w, x0, y0, z0, myId, myMeta));
                //BlockFluid.preventSetBlockLiquidFlowover=false;
                vanillaMutex.release();
              } else {
                // No need to use preventSetBlockLiquidFlowover here
                FysiksFun.setBlockIDandMetadata(w, c, x0, y0, z0, targetId, targetMeta, myId, myMeta, delayedBlockMarkSet);
              }
              tempData0.setTempData(x0, y0, z0, targetTmp);

              /* Finally, after moving. If block above seems to be a tree, then allow it to be triggered early. */
              if (idAbove == Block.wood.blockID && myId == Block.wood.blockID && idBelow != Block.wood.blockID) {
                Trees.checkAndTickTree(w, x0, z0);
              }

              continue updateAllBlocks;
            }
            // Update block with new values (will not be reached by blocks that have successfully moved)
            if (debugMe) System.out.println("  " + Util.xyzString(x0, y0, z0) + " update clock:" + curClock + " count:" + curBreak + " pres:" + curPressure);
            tempData.setTempData(dx, y, dz, curPressure | (curClock << clockBitsStart) | (curBreak << breakBitsStart));
          }
        }
      }
      // Finally, update the "last tick" counter for this chunk. Important to do this last.
      tempData.physicsLastTick = Counters.tick;
    } catch (Exception e) {
      System.out.println("BlockPhysicsSweeperThread got an exception" + e);
      e.printStackTrace();
    }
  }

  @Deprecated
  private void removeEntitiesInsideBlock(int targetX, int targetY, int targetZ) {
    ArrayList<Entity> allEntities = new ArrayList((List<Entity>) w.loadedEntityList);
    LinkedList<Entity> toRemove = new LinkedList<Entity>();

    for (Entity e : allEntities) {
      if (!(e instanceof EntityPlayer)) {
        AxisAlignedBB aabb = e.boundingBox;
        if (((aabb.minX >= targetX && aabb.minX <= targetX + 1.0) || (aabb.maxX >= targetX && aabb.maxX <= targetX + 1.0))
            && ((aabb.minY >= targetY && aabb.minY <= targetY + 1.0) || (aabb.maxY >= targetX && aabb.maxY <= targetX + 1.0))
            && ((aabb.minZ >= targetZ && aabb.minZ <= targetZ + 1.0) || (aabb.maxZ >= targetX && aabb.maxZ <= targetX + 1.0))) toRemove.add(e);
        else if (e.posX >= targetX && e.posX <= targetX + 1.0 && e.posY >= targetY && e.posY <= targetY + 1.0 && e.posZ >= targetZ && e.posZ <= targetZ + 1.0)
          toRemove.add(e);
      }
    }
    if (toRemove.size() > 0) {
      System.out.println("Removing " + (toRemove.size()) + " entity inside a block that has moved");
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
