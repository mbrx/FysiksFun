package mbrx.ff.solidBlockPhysics;

import java.util.HashSet;

import mbrx.ff.FysiksFun;
import mbrx.ff.WorkerPhysicsSweep;
import mbrx.ff.ecology.Trees;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkMarkUpdateTask;
import mbrx.ff.util.ChunkTempData;
import mbrx.ff.util.Counters;
import mbrx.ff.util.ObjectPool;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWood;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;
import buildcraft.factory.BlockFrame;
import buildcraft.transport.BlockGenericPipe;

public class SolidBlockPhysics {

  
  /**
   * Class instance fields for holding the current state of a block that is
   * beeing modified between multiple function calls
   */
  private int                                curClock, curBreak, curPressure;
  private int                                nSoundEffectsLeft;
  private int                                soundEffectAttempts;
  private static final int                   maxSoundEffects         = 3;
  private World jobWorld;
  private boolean restartPhysics;
  /** Current clock in the private clock unit of the solidBlockPhysics engine. Used for calculating connectedness to anchors */
  private int timeNow;
  

 
  /** Instances of this class is used for holding temporary data for the duration of physics calculations. The same instances must never be entered by more than one thread. */
  public SolidBlockPhysics() {
  }
  
  /* Constants for internal calculations */
  private static final int                   timeToFall              = 4;
  public static final int                   countdownToAction       = 80;
  public static final int                   fallForce               = 0;                // 20;

  /*
   * Use of the break counter: Value 0: The block is stable, can move at most
   * breakThreshold pressures. Value 1 - breakAtCounter-1: The block is not yet
   * breaking, but might soon. Can move atmost breakThreshold pressure, but
   * needs the pressure to exceed elasticPressure before it starts moving. Value
   * breakAtCounter - counterIsFalling-1: The block IS breaking (attempting to
   * move), and at the same time transmitting
   * breakThreshold+forceWhileBreaking*(curBreak-breakAtCounter), so it smoothly
   * steps up how much force is transmitted.
   */
  private static final int                   pressureBitsMask        = 0xffff;
  private static final int                   clockBitsStart          = 16;
  private static final int                   clockBitsMask           = 0x0ff;
  private static final int                   clockModulo             = 256;
  private static final int                   clockMaxDist            = 127;
  private static final int                   breakBitsStart          = 24;
  private static final int                   breakBitsMask           = 0x07f;
  private static final int                   breakAtCounter          = 40;
  private static final int                   counterIsFalling        = 127;
  private static int                         forceWhileBreaking      = 3;


  public void doSolidBlockPhysics(World w, Chunk chunk0, ChunkTempData tempData0, ExtendedBlockStorage[] blockStorage, int x0, int y0, int z0, int id,
      HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet) throws InterruptedException {

    jobWorld = w;
    if (Fluids.isLiquid[id]) {
      System.out.println("*** ERRROR - doing solid block physics on water");
    }

    /* Get current pressure, increase by our weight */
    int weight = SolidBlockPhysicsRules.blockWeight[id];
    if (weight < 0) weight = (FysiksFun.rand.nextInt(-weight) == 0 ? 1 : 0);

    int totalMoved = 0;
    int origTempValue = tempData0.getTempData(x0, y0, z0);
    int origPressure = (origTempValue & pressureBitsMask);
    curPressure = origPressure + weight; // (sweep==0?weight:0);
    curClock = (origTempValue >> clockBitsStart) & clockBitsMask;
    curBreak = (origTempValue >> breakBitsStart);
    boolean isFalling = curBreak == counterIsFalling;

    if (restartPhysics || origTempValue == 0 || y0 <= 1 || SolidBlockPhysicsRules.blockIsSink[id]) {
      curClock = timeNow;
      // curPressure = 0;
      curBreak = 0;
    }

    int simplifiedPhysics = SolidBlockPhysicsRules.blockDoSimplePhysics[id];
    if (simplifiedPhysics > 0) curPressure = 0;

    int origClock = curClock;
    int breakThreshold = SolidBlockPhysicsRules.blockStrength[id];

    boolean debugMe = false;

    /***** debug *****/
    if (id == Block.hardenedClay.blockID) debugMe = true;

    if (debugMe) {
      System.out.println("id:" + id + "@" + Util.xyzString(x0, y0, z0) + " origPressure: " + origPressure + " prevClock: " + curClock + " prevBreak: "
          + curBreak + " breakThreshold: " + breakThreshold);
    }

    // Propagate forces
    totalMoved = propagateForces(chunk0, tempData0, blockStorage, x0, y0, z0, id, timeNow, origPressure, isFalling, simplifiedPhysics,
        breakThreshold, debugMe);
    // Make blocks at y=1 as well as all sink blocks act as sinks/supports for
    // the forces
    if (y0 <= 1 || SolidBlockPhysicsRules.blockIsSink[id]) {
      curPressure = 0;
      curClock = timeNow;
      totalMoved = 0;
    }

    // Compute if we should INITIATE or CONTINUE a FALL
    boolean doFall = curBreak == counterIsFalling;
    boolean fallDueToClock = false;
    int timeCheck = (timeNow - timeToFall + clockModulo) % clockModulo;
    if (debugMe) System.out.println("timecheck: " + timeCheck);
    // Compute if we should start falling due to no support from ground (no
    // clock)
    if (curClock < timeCheck) {
      fallDueToClock = (timeCheck - curClock) < (clockModulo * 4) / 5;
    } else if (curClock > timeCheck) {
      fallDueToClock = (curClock - timeCheck) > clockModulo / 5;
    }
    if (debugMe) System.out.println("Fall due to clock: " + fallDueToClock);
    if (origClock != curClock) fallDueToClock = false;
    if (fallDueToClock) {
      curBreak = counterIsFalling;
      doFall = true;
    }

    // Compute if we should INITIATE a BREAK
    boolean doBreak = totalMoved >= breakThreshold;
    if (simplifiedPhysics > 0) doBreak = false;

    /*
     * Prevent all action if the chunk based "counter since start" haven't
     * reached zero
     */
    if (tempData0.solidBlockPhysicsCountdownToAction > 0) {
      if (debugMe) System.out.println("No action due to countdown: " + tempData0.solidBlockPhysicsCountdownToAction);
      if (curBreak == breakAtCounter) {
        curBreak = breakAtCounter - 1;
      }
      doBreak = false;
      doFall = false;
    }

    /* Update COUNTERS */
    if (doBreak == false && doFall == false) {
      curBreak = Math.max(curBreak - 1, 0);
    } else if (curBreak < breakAtCounter) {
      curBreak++;
      doBreak = false;
      doFall = false;
      if (debugMe) System.out.println("No break, but new counter: " + curBreak);
    } else {
      // Active breaking but not beeing able to move is in process, we need to
      // equalize the pressure by raising the curBreak counter
      if (debugMe) {
        System.out.println("time to increase break? curBreak: " + curBreak + " totalMoved: " + totalMoved + " breakThreshold: " + breakThreshold);
      }
      if (totalMoved >= breakThreshold + 2 * (curBreak - breakAtCounter)) curBreak = Math.min(curBreak + 1, counterIsFalling - 1);
      else if (isFalling == false && totalMoved < breakThreshold + 2 * (curBreak - breakAtCounter) - 2) curBreak--;
      if (curBreak < 0) System.out.println("wtf?");
    }

    /*
     * Wait with falling if sufficient time haven't passed since the last fall
     */
    if (doFall && !fallDueToClock) {
      if (debugMe) System.out.println("Wait with the fallling, curBreak is: " + curBreak);
      doFall = false;
    }

    if (debugMe) {
      System.out.println("  curclock: " + curClock + " origclock: " + origClock + " time: " + timeNow + " fall: " + doFall);
      System.out.println("  totalMoved: " + totalMoved + " curBreak: " + curBreak + " doBreak:" + doBreak + " curPressure:" + curPressure);
    }

    if ((doFall || doBreak) && fallOrBreak(chunk0, x0, y0, z0, id, weight, timeNow, doBreak, delayedBlockMarkSet, tempData0, debugMe)) {
      // Block moved successfully, nothing to do
    } else {
      // Update block with new values (will not be reached by
      // blocks that have successfully moved)
      if (debugMe) System.out.println("  " + Util.xyzString(x0, y0, z0) + " update clock:" + curClock + " count:" + curBreak + " pres:" + curPressure);
      int newTempValue = curPressure | (curClock << clockBitsStart) | (curBreak << breakBitsStart);
      if (newTempValue != origTempValue) tempData0.setTempData(x0, y0, z0, newTempValue);
    }
  }
  
  

  private int propagateForces(Chunk c, ChunkTempData tempData, ExtendedBlockStorage[] blockStorage, int x0, int y0, int z0, int id, int timeNow,
      int origPressure, boolean isFalling, int simplifiedPhysics, int breakThreshold, boolean debugMe) {

    /**
     * Amount of pressure that we will NOT move due to us already starting to
     * break.
     */
    int elasticPressure = (SolidBlockPhysicsRules.elasticStrengthConstant * Math.min(breakAtCounter - 1, curBreak)) / (breakAtCounter - 1);

    int totalMoved = 0;
    // Iterate over all neighbours, update our clock and pressure accordingly
    for (int dir = 0; dir < 6; dir++) {
      int x2 = x0 + Util.dirToDx(dir);
      int y2 = y0 + Util.dirToDy(dir);
      int z2 = z0 + Util.dirToDz(dir);
      int nnId = 0;
      boolean nnIsInSameChunk = (((x2 >> 4) == (x0 >> 4)) && ((z2 >> 4) == (z0 >> 4)));
      if (nnIsInSameChunk) {
        ExtendedBlockStorage nnEbs = blockStorage[y2 >> 4];
        if (nnEbs != null) {
          nnId = nnEbs.getExtBlockID(x2 & 15, y2 & 15, z2 & 15);
        } else nnId = c.getBlockID(x2 & 15, y2, z2 & 15);
      } else {
        Chunk c2 = ChunkCache.getChunk(jobWorld, x2 >> 4, z2 >> 4, false);
        if (c2 == null) continue;
        nnId = c2.getBlockID(x2 & 15, y2, z2 & 15);
      }
      if (Fluids.isLiquid[nnId] || Gases.isGas[nnId]) continue;
      if (!SolidBlockPhysicsRules.blockDoPhysics[nnId]) {
        if (SolidBlockPhysicsRules.blockDoSimplePhysics[nnId] == 0) continue;
        if (SolidBlockPhysicsRules.blockDoSimplePhysics[nnId] > simplifiedPhysics) continue;
      }
      // nnIsInSameChunk ? tempData : ChunkCache.getTempData(w, x2 >> 4, z2 >>
      // 4);
      ChunkTempData tempData2 = ChunkCache.getTempData(jobWorld, x2 >> 4, z2 >> 4);
      int nnTmpVal = tempData2.getTempData(x2, y2, z2);
      if (nnTmpVal == 0 && y0 != 2) continue;
      int nnPressure = nnTmpVal & pressureBitsMask;
      int nnClock = (nnTmpVal >> clockBitsStart) & clockBitsMask;
      int nnBreak = (nnTmpVal >> breakBitsStart);
      boolean nnIsFalling = nnBreak == counterIsFalling;

      /*
       * Neighbour belongs to a not-updating chunk. Treat neighbour as static
       * object.
       */
      if (tempData2.solidBlockPhysicsLastTick < Counters.tick - 2 * WorkerPhysicsSweep.ticksPerUpdate) {
        curClock = timeNow;
        if (nnPressure < curPressure) curPressure = curPressure - (curPressure - nnPressure) / 2;
        continue;
      }

      boolean ahead = false;

      /* Calculate whom of us is closer to the real clock. */
      int myClockDist = (curClock <= timeNow ? timeNow - curClock : timeNow + clockModulo - curClock);
      int nnClockDist = (nnClock <= timeNow ? timeNow - nnClock : timeNow + clockModulo - nnClock);
      ahead = nnClockDist < myClockDist;

      // Update our clock accordingly, but not if one of
      // us is falling but not the other
      if (nnIsFalling == isFalling) {
        // nnBreak != breakAtCounter && curBreak !=
        // breakAtCounter)
        if (ahead) curClock = nnClock;
        else nnClock = curClock;
      }

      if (debugMe) {
        System.out.println("  nn" + dir + " id:" + nnId + " pres:" + nnPressure + " clock: " + nnClock + " ahead " + ahead + " dy: " + Util.dirToDy(dir)
            + " nnBreak:" + nnBreak + " curP:" + curPressure);
      }
      if (simplifiedPhysics > 0) continue;

      /*
       * If we are restarting the physics computations, then just spread the
       * pressures without eliminating them.
       */
      if (restartPhysics) {
        if (nnPressure > curPressure) curPressure = nnPressure;
        continue;
      }

      boolean debugNN = false; // nnId == Block.hardenedClay.blockID;
      if (debugNN) {
        System.out.println("Checking neighbour.");
        System.out.println("myPressure: " + curPressure + " myBreak: " + curBreak + " nnPressure:" + nnPressure + " elastic: " + elasticPressure);
      }
      if (origPressure == 0 && !SolidBlockPhysicsRules.blockIsSink[id] && y0 > 1 && nnPressure > curPressure) {
        /*
         * Special case: we have joined a new block with no previous pressure to
         * this area. Copy the pressure from a neighbour.
         */
        curPressure = nnPressure;
      } else
      /*
       * Only allow forces to be transmitted through us if we are not too
       * broken. Forces will prefer to go through less broken blocks.
       */
      if (nnPressure > curPressure + 2 + elasticPressure) {
        int toMove;

        toMove = (nnPressure - curPressure - elasticPressure) / 2;

        if ((id == Block.planks.blockID || id == Block.wood.blockID) && (nnId == Block.stone.blockID)) {
          // || nnId == Block.cobblestone.blockID)) {

          /*
           * Planks and wood are stronger when supporting original stone. To be
           * used as support in mines.
           */
          if (toMove > breakThreshold * 3 && curBreak < breakAtCounter) toMove = breakThreshold * 3;
          totalMoved += toMove / 3;
          totalMoved = totalMoved / 3;
        } else {
          int maxMoved = breakThreshold + Math.max(0, (curBreak - breakAtCounter) * 2);
          if (debugMe) System.out.println("toMove before capping: " + toMove + " cap: " + maxMoved);
          if (toMove > maxMoved && !SolidBlockPhysicsRules.blockIsSink[id] && y0 > 1) toMove = maxMoved;

          totalMoved += toMove;
        }
        if (debugNN) {
          System.out.println("To move from NN: " + toMove);
        }

        nnPressure -= toMove;
        if (!SolidBlockPhysicsRules.blockIsSink[id]) curPressure += toMove;
        tempData2.setTempData(x2, y2, z2, nnPressure | (nnClock << clockBitsStart) | (nnBreak << breakBitsStart));

        if (debugMe) {
          System.out.println("  toMove " + toMove + " dy: " + Util.dirToDy(dir));
        }
      }
    }
    return totalMoved;
  }


  /**
   * Returns true if block successfully fell or broke. If doBreak false we
   * assume we are supposed to make a fall.
   * 
   * @param delayedBlockMarkSet
   * @param debugMe
   * @param tempData0
   */
  private boolean fallOrBreak(Chunk c, int x0, int y0, int z0, int id, int weight, int timeNow, boolean doBreak,
      HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet, ChunkTempData tempData0, boolean debugMe) {

    boolean canMoveSideways = doBreak;
    int myMeta = c.getBlockMetadata(x0 & 15, y0, z0 & 15);

    int idAbove = c.getBlockID(x0 & 15, y0 + 1, z0 & 15);
    int idBelow = c.getBlockID(x0 & 15, y0 - 1, z0 & 15);
    int targetX, targetY, targetZ, targetId;
    Chunk targetChunk = c;

    if (!SolidBlockPhysicsRules.blockDoPhysics[idBelow]) {
      targetX = x0;
      targetY = y0 - 1;
      targetZ = z0;
      targetId = idBelow;
    } else {
      if (!canMoveSideways) {
        // Returned through a class variable (which works since it is bound to
        // the thread that is executing),
        // signifies we have landed.
        curBreak = 0;
        return false;
      }
      int r = FysiksFun.rand.nextInt(4);
      findFreeNeighbour:
      {
        for (int dir = 0; dir < 4; dir++) {
          targetX = x0 + Util.dirToDx((dir + r) % 4);
          targetY = y0 + Util.dirToDy((dir + r) % 4);
          targetZ = z0 + Util.dirToDz((dir + r) % 4);
          targetChunk = ChunkCache.getChunk(jobWorld, targetX >> 4, targetZ >> 4, false);
          if (targetChunk == null) continue; // WAS return false;
          targetId = targetChunk.getBlockID(targetX & 15, targetY, targetZ & 15);

          if (!SolidBlockPhysicsRules.blockDoPhysics[targetId]) {
            int idBelowTarget = targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15);
            if (!SolidBlockPhysicsRules.blockDoPhysics[idBelowTarget]) break findFreeNeighbour;
          }
        }
        return false;
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
        synchronized (FysiksFun.vanillaMutex) {
          nSoundEffectsLeft--;
          float volume = (float) (0.25F * effectiveWeight + soundEffectAttempts * 0.1);
          float pitch = (float) (FysiksFun.rand.nextFloat() + 1.0F) * 0.5F / effectiveWeight;
          String soundEffect = blockIdToSound(id);
          jobWorld.playSoundEffect(targetX + 0.5, targetY + 0.5, targetZ + 0.5, soundEffect, volume, pitch);
        }
      }
    }
    if (SolidBlockPhysicsRules.blockIsFragile[id] && (doBreak || hasLanded)) {
      jobWorld.playSoundEffect(targetX + 0.5, targetY + 0.5, targetZ + 0.5, "random.break", 1.0F, 1.0F);
      id = 0;
    }

    if (doBreak) Counters.brokenBlocks++;
    else Counters.fallenBlocks++;

    // To avoid from falling too fast
    curBreak = counterIsFalling;
    curClock = timeNow;

    // Check if there where any blocks above us that is
    // not handled by the physics. If so, break them
    // into items.
    if (!SolidBlockPhysicsRules.blockDoPhysics[idAbove] && idAbove != 0 && !Fluids.isLiquid[idAbove] && !Gases.isGas[idAbove]) {
      synchronized (FysiksFun.vanillaMutex) {
        Block.blocksList[idAbove].dropBlockAsItem(jobWorld, x0, y0 + 1, z0, 0, 0);
      }
      // This is done with the wrong 'old meta', but that doesn't matter since
      // we in the worst case just make a mark too much
      FysiksFun.setBlockIDandMetadata(jobWorld, c, x0, y0 + 1, z0, 0, 0, idAbove, 0, delayedBlockMarkSet);
    }

    // Rewrite grass to dirt when it is moved
    if (id == Block.grass.blockID) id = Block.dirt.blockID;

    if (Block.blocksList[targetId] instanceof ITileEntityProvider) {
      /*
       * Find the corresponding tile entity... and move it too
       */
      targetChunk.removeChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
      System.out.println("Crushing a tile entity!");
    }

    if (debugMe) System.out.println("  tar:" + Util.xyzString(targetX, targetY, targetZ) + " update clock:" + curClock + " count:" + curBreak + " pres:" + 0);

    /*
     * Assign the original ID/meta to the target position
     */
    ChunkTempData targetTempData = ChunkCache.getTempData(jobWorld, targetX >> 4, targetZ >> 4);
    int targetMeta = targetChunk.getBlockMetadata(targetX & 15, targetY, targetZ & 15);
    int targetTmp = targetTempData.getTempData(targetX, targetY, targetZ);
    FysiksFun.setBlockIDandMetadata(jobWorld, targetChunk, targetX, targetY, targetZ, id, myMeta, targetId, targetMeta, delayedBlockMarkSet);
    // If target is very close to a player then update immediately
    if (FysiksFun.minDistSqToObserver(jobWorld, targetX, targetY, targetZ) < 4.0) {
      jobWorld.markBlockForUpdate(targetX, targetY, targetZ);
    }
    int newPressure = curPressure + fallForce * (weight > 0 ? weight : 0);
    targetTempData.setTempData(targetX, targetY, targetZ, newPressure | (curClock << clockBitsStart) | (curBreak << breakBitsStart));

    /*
     * If the target block is not empty, a gas, or a liquid; then crush it and
     * make a drop of it
     */
    if (targetId != 0 && !Fluids.isLiquid[targetId] && !Gases.isGas[targetId]) {
      boolean doDrop = false;

      if (Block.blocksList[targetId] instanceof BlockDoor) {
        if (targetY > 0 && targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15) == targetId) doDrop = true;
      } else if (Block.blocksList[targetId] instanceof BlockBed) {
        BlockBed bed = (BlockBed) Block.blocksList[targetId];
        if (bed.isBlockHeadOfBed(targetMeta)) doDrop = true;
      } else doDrop = true;
      synchronized (FysiksFun.vanillaMutex) {
        if (doDrop) Block.blocksList[targetId].dropBlockAsItem(jobWorld, targetX, targetY, targetZ, 0, 0);
        if (Block.blocksList[targetId] instanceof ITileEntityProvider) {
          TileEntity targetEntity = targetChunk.getChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
          targetChunk.removeChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
        }
      }
      targetId = 0;
      targetTmp = 0;
    }
    /*
     * From here on targetId must be zero, or a liquid, or a gas
     */

    /*
     * Update the corresponding TileEntity instance if the moved block was a
     * tile entity type of block
     */
    if (Block.blocksList[id] instanceof ITileEntityProvider) {
      synchronized (FysiksFun.vanillaMutex) {
        /*
         * Find the corresponding tile entity... and move it too
         */
        TileEntity entity = c.getChunkBlockTileEntity(x0 & 15, y0, z0 & 15);
        if (entity == null) {
          System.out.println("Warning - could not find a corresponding tile entity at position: " + Util.xyzString(x0, y0, z0) + " id:" + id);
        } else {
          c.removeChunkBlockTileEntity(x0 & 15, y0, z0 & 15);
          entity.xCoord = targetX;
          entity.yCoord = targetY;
          entity.zCoord = targetZ;
          entity.validate();
          targetChunk.addTileEntity(entity);
        }
      }
    }

    boolean useSlowSetBlock = false;
    useSlowSetBlock = true; // !!!! for now
    if (idAbove == 0 || (Block.blocksList[idAbove] != null && !Block.blocksList[idAbove].isOpaqueCube())) {
      useSlowSetBlock = true;
    }

    /** DEBUG - let water/steam be eliminated when blocks fall throught them */
    targetId = 0;
    targetTmp = 0;
    targetMeta = 0;

    /*
     * Swap our block with the target, and update target entity if applicable
     */
    if (useSlowSetBlock) {
      synchronized (FysiksFun.vanillaMutex) {
        c.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, targetId, targetMeta);
        ChunkMarkUpdateTask task = ObjectPool.poolChunkMarkUpdateTask.getObject();
        task.set(jobWorld, x0, y0, z0, id, myMeta);
        delayedBlockMarkSet.add(task);
      }
    } else {
      FysiksFun.setBlockIDandMetadata(jobWorld, c, x0, y0, z0, targetId, targetMeta, id, myMeta, delayedBlockMarkSet);
    }
    tempData0.setTempData(x0, y0, z0, targetTmp);

    /*
     * Finally, after moving. If block above seems to be a tree, then allow it
     * to be triggered early.
     */
    if (idAbove == Block.wood.blockID && id == Block.wood.blockID && idBelow != Block.wood.blockID) {
      Trees.checkAndTickTree(jobWorld, x0, z0);
    }
    return true;
  }

  private String blockIdToSound(int id) {
    Block b = Block.blocksList[id];
    if (b == null) return "fysiksfun:rubble";
    if (b instanceof BlockWood || b instanceof BlockLog) return "fysiksfun:woodCrack";
    return "fysiksfun:rubble";
  }
  

  public static void addPressure(World w, int x1, int y1, int z1, int increment) {
    ChunkTempData tempData = ChunkCache.getTempData(w, x1 >> 4, z1 >> 4);
    int tmpVal = tempData.getTempData(x1, y1, z1);
    int curPressure = tmpVal & pressureBitsMask;
    int newPressure = Math.max(0, Math.min(pressureBitsMask, curPressure + increment));
    tmpVal = (tmpVal & ~pressureBitsMask) | newPressure;
    tempData.setTempData(x1, y1, z1, tmpVal);
  }

  /** Start of the processing for a chunk, sets up chunk specific variables. */
  public void startProcessingChunk(Chunk c, ChunkTempData tempData, boolean doDetailedPhysics) {
    
    if (doDetailedPhysics) {
      if (tempData.solidBlockPhysicsLastTick < Counters.tick - WorkerPhysicsSweep.ticksPerUpdate * 5) {
        tempData.solidBlockPhysicsCountdownToAction = countdownToAction;
        restartPhysics = true;
      } else {
        tempData.solidBlockPhysicsCountdownToAction = Math.max(0, tempData.solidBlockPhysicsCountdownToAction - 1);
      }
    }

    timeNow = Counters.tick % clockModulo;

    /**
     * Number of sound effects we are still allowed to play for this chunk
     */
    nSoundEffectsLeft = maxSoundEffects;
    soundEffectAttempts = 0;

  }

}
