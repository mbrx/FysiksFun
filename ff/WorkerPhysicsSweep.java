package mbrx.ff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

import buildcraft.factory.BlockFrame;
import buildcraft.transport.BlockGenericPipe;
import cpw.mods.fml.common.network.Player;
import mbrx.ff.FysiksFun.WorldObserver;
import mbrx.ff.MPWorldTicker.WorldUpdateState;
import mbrx.ff.ecology.ExtraFire;
import mbrx.ff.ecology.Trees;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.BlockFFGas;
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
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWood;
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
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

public class WorkerPhysicsSweep implements Runnable {
  ChunkCoordIntPair                          xz;
  World                                      w;
  WorldUpdateState                           wstate;
  Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;
  /**
   * Class instance fields for holding the current state of a block that is
   * beeing modified between multiple function calls
   */
  private int                                curClock, curBreak, curPressure;
  private int                                nSoundEffectsLeft;
  private int                                soundEffectAttempts;
  private static final int                   maxSoundEffects         = 3;

  public static int                          blockStrength[]         = new int[4096];
  /**
   * The weight of each blockID, negative weights corresponds to fractional
   * (stochastic) weights
   */
  public static int                          blockWeight[]           = new int[4096];
  public static boolean                      blockDoPhysics[]        = new boolean[4096];
  /**
   * 0 all normal blocks, 1+ blocks not affected by full (breakable) physics.
   * Lower numbers can support higher numbers.
   */
  public static int                          blockDoSimplePhysics[]  = new int[4096];
  public static boolean                      blockIsFragile[]        = new boolean[4096];

  private static final int                   ticksPerUpdate          = 1;
  private static final int                   timeToFall              = 4;

  // private static Semaphore vanillaMutex = FysiksFun.vanillaMutex; //new
  // Semaphore(1);

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

  public static int                          maxChunkDist            = 48;
  private static final int                   countdownToAction       = 80;
  private static final int                   fallForce               = 20;
  // private static final int numSweeps = 4;
  private static int                         elasticStrengthConstant = 120;              // 240;

  public static void postInit(Configuration physicsRuleConfig) {

    /* Setup a default value for all blocks */
    setupDefaultPhysicsRules();
    /* Initialize the physics-rules file */
    String cat = "physics";
    physicsRuleConfig.addCustomCategoryComment(cat, "All entries in here modify the rules for physics blocks");
    /*
     * For each block, if it exists in the config file use it otherwise add the
     * default value
     */
    maxChunkDist = physicsRuleConfig.get(cat, "0-maxDistanceForPhysics", "" + maxChunkDist, "Radius around player in which physics are computed",
        Property.Type.INTEGER).getInt(maxChunkDist);
    elasticStrengthConstant = physicsRuleConfig.get(cat, "0-elasticStrengthConstant", "" + elasticStrengthConstant,
        "only change this if you know what you do, it effects the total time before physics kick in", Property.Type.INTEGER).getInt(elasticStrengthConstant);

    physicsRuleConfig.get(cat, "1-example-do-full", "true",
        "If true, the full physics is run for this type of blocks, if false the simplified physics may still be applied", Property.Type.BOOLEAN);
    physicsRuleConfig
        .get(
            cat,
            "1-example-do-simplified",
            "0",
            "Integer order of execution of simplified physics. 0 disables simplified physics. Lower numbers can support higher numbers (but never blocks of full-physics). See leaves and vines for example",
            Property.Type.INTEGER);
    physicsRuleConfig.get(cat, "1-example-is-fragile", "true", "If true, the block will break (like glass) when falling", Property.Type.BOOLEAN);
    physicsRuleConfig.get(cat, "1-example-strength", "16",
        "Strength of block, must be less than elasticStrenghtConstant. Typical values 5 - 30 times the weight of the block.", Property.Type.INTEGER);
    physicsRuleConfig.get(cat, "1-example-weight", "4", "Weight of this block, typical values 1 - 16", Property.Type.INTEGER);

    physicsRuleConfig.get(cat, "2-start-of-rules", "", "Here comes the rules for each block in the game", Property.Type.STRING);

    for (int i = 0; i < 4096; i++) {
      Block b = Block.blocksList[i];
      if (i == 0 || b == null || b.blockID == 0) continue;
      String name = b.getUnlocalizedName().replace("tile.", "");
      if (i == Block.cobblestone.blockID) name = "cobbleStone";
      if (i == Block.stoneBrick.blockID) name = "stoneBrick";

      blockDoPhysics[i] = physicsRuleConfig.get(cat, name + "-do-full", blockDoPhysics[i] ? "true" : "false", null, Property.Type.BOOLEAN).getBoolean(
          blockDoPhysics[i]);
      if (!blockDoPhysics[i])
        blockDoSimplePhysics[i] = physicsRuleConfig.get(cat, name + "-do-simplified", "" + blockDoSimplePhysics[i], null, Property.Type.INTEGER).getInt(
            blockDoSimplePhysics[i]);
      if (blockDoPhysics[i] || blockDoSimplePhysics[i] != 0) {
        blockIsFragile[i] = physicsRuleConfig.get(cat, name + "-is-fragile", blockIsFragile[i] ? "true" : "false", null, Property.Type.BOOLEAN).getBoolean(
            blockIsFragile[i]);
        blockStrength[i] = physicsRuleConfig.get(cat, name + "-strength", "" + blockStrength[i], null, Property.Type.INTEGER).getInt(blockStrength[i]);
        blockWeight[i] = physicsRuleConfig.get(cat, name + "-weight", "" + blockWeight[i], null, Property.Type.INTEGER).getInt(blockWeight[i]);
      }
    }
  }

  private static void setupDefaultPhysicsRules() {
    int rubWood = Util.findBlockIdFromName("blockRubWood");

    for (int i = 1; i < 4096; i++) {
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
      } else if (FysiksFun.hasBuildcraft && b instanceof BlockGenericPipe) {
        blockStrength[i] = 20;
        blockWeight[i] = 2;
        blockDoPhysics[i] = true;
      } else if (FysiksFun.hasBuildcraft && b instanceof BlockFrame) {
        blockStrength[i] = 120;
        blockWeight[i] = 2;
        blockDoPhysics[i] = true;
      } else if (b instanceof ITileEntityProvider) {
        blockStrength[i] = 40;
        blockWeight[i] = 4;
        blockDoPhysics[i] = true;
      } else if (b instanceof BlockStairs) {
        blockStrength[i] = 20;
        blockWeight[i] = 4;
        blockDoPhysics[i] = true;
      } else if (b instanceof BlockFence) {
        blockStrength[i] = 20;
        blockWeight[i] = 3;
        blockDoPhysics[i] = true;
        blockIsFragile[i] = true;
      } else if (b instanceof BlockLog || i == rubWood) {
        blockStrength[i] = 120; // 30 times weight, needed to avoid trees from
                                // breaking!
        blockWeight[i] = 4;
      } else if (b instanceof BlockWood) { // Poor name in vanilla, this is the
                                           // planks!!
        blockStrength[i] = 50; // 25 times weight
        blockWeight[i] = 2;
      }
      if (b instanceof BlockBreakable) blockIsFragile[i] = true;

      if (Fluids.isLiquid[i] || Gases.isGas[i] || i == 0) blockDoPhysics[i] = false;
      // if (!blockDoPhysics[i]) continue;
    }

    /*
     * blockDoPhysics[Block.leaves.blockID] = true;
     * blockStrength[Block.leaves.blockID] = 10; // 100 times weight!
     * blockWeight[Block.leaves.blockID] = -10;
     */

    blockDoPhysics[Block.bedrock.blockID] = true;
    blockWeight[Block.bedrock.blockID] = 0;

    blockDoPhysics[Block.leaves.blockID] = false;
    blockDoSimplePhysics[Block.leaves.blockID] = 1;
    /*blockWeight[Block.leaves.blockID] = 1;
    blockStrength[Block.leaves.blockID] = 10;*/

    blockDoPhysics[Block.vine.blockID] = false;
    blockDoSimplePhysics[Block.vine.blockID] = 2;

    blockStrength[Block.gravel.blockID] = 4;
    blockWeight[Block.gravel.blockID] = 4;

    blockStrength[Block.dirt.blockID] = 4;
    blockWeight[Block.dirt.blockID] = 1;

    blockStrength[Block.sand.blockID] = 4;
    blockStrength[Block.cobblestone.blockID] = 80; // 10 times weight
    blockWeight[Block.cobblestone.blockID] = 8;

    blockStrength[Block.stone.blockID] = 200; // 50 times weight
    blockWeight[Block.stone.blockID] = 4; // stone is unplaceable, low weight
                                          // for now!
    blockStrength[Block.stoneBrick.blockID] = 180; // 30 times weight
    blockWeight[Block.stoneBrick.blockID] = 6;
    blockStrength[Block.brick.blockID] = 160; // 40 times weight
    blockWeight[Block.brick.blockID] = 4;

    blockDoPhysics[Block.thinGlass.blockID] = true;
    blockIsFragile[Block.thinGlass.blockID] = true;
    blockStrength[Block.thinGlass.blockID] = 5; // 5 times weight
    blockWeight[Block.thinGlass.blockID] = 1;
    blockDoPhysics[Block.glass.blockID] = true;
    blockStrength[Block.glass.blockID] = 20; // 10 times weight
    blockWeight[Block.glass.blockID] = 2;
    blockIsFragile[Block.glass.blockID] = true;
    blockStrength[Block.glowStone.blockID] = 40; // 20 times weight (it's
    // anyway too expensive
    // to use for this?)
    blockWeight[Block.glowStone.blockID] = 2;
    blockIsFragile[Block.glowStone.blockID] = true;

    blockStrength[Block.fence.blockID] = 10; // 5 times weight
    blockWeight[Block.fence.blockID] = 2;

    blockStrength[Block.blockLapis.blockID] = 180; // 30 times weight
    blockWeight[Block.blockLapis.blockID] = 6;
    blockStrength[Block.blockNetherQuartz.blockID] = 90; // 30 times weight
    blockWeight[Block.blockNetherQuartz.blockID] = 3;
    blockStrength[Block.blockIron.blockID] = 160; // 40 times weight
    blockWeight[Block.blockIron.blockID] = 4;
    blockStrength[Block.blockGold.blockID] = 240; // 30 times weight
    blockWeight[Block.blockGold.blockID] = 8;
    blockStrength[Block.blockDiamond.blockID] = 240; // 80 times weight (!)
    blockWeight[Block.blockDiamond.blockID] = 3;
    blockStrength[Block.blockEmerald.blockID] = 240; // 80 times weight (!)
    blockWeight[Block.blockEmerald.blockID] = 3;

    blockStrength[Block.obsidian.blockID] = 240; // 15 times weight
    blockWeight[Block.obsidian.blockID] = 16;

    blockStrength[Block.blockSnow.blockID] = 4; // 4 times weight
    blockWeight[Block.blockSnow.blockID] = 1;
    blockStrength[Block.ice.blockID] = 15; // 5 times weight
    blockWeight[Block.ice.blockID] = 3;

    blockStrength[Block.hay.blockID] = 8; // 4 times weight
    blockWeight[Block.hay.blockID] = 2;
    blockStrength[Block.cloth.blockID] = 8; // 4 times weight
    blockWeight[Block.cloth.blockID] = 2;

    blockStrength[Block.hardenedClay.blockID] = 120; // 30 times weight
    blockWeight[Block.hardenedClay.blockID] = 4;

    /* Hell */
    // Netherrack is special, modified inside the 'run' method
    // blockStrength[Block.netherrack.blockID] = 16;
    // blockWeight[Block.netherrack.blockID] = 4;
    blockStrength[Block.slowSand.blockID] = 4;
    blockWeight[Block.slowSand.blockID] = 4;
    blockStrength[Block.netherBrick.blockID] = 120; // 30 times weight
    blockWeight[Block.netherBrick.blockID] = 4;

    /* IC2 specifics */
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

    // Don't do any physics in the end world, that is too hard for now
    if (w.provider.dimensionId == 1) return;

    try {

      Integer tid = (int) Thread.currentThread().getId();
      if (Counters.tick % ticksPerUpdate != 0) return;

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
      int minDist = (int) FysiksFun.minXZDistSqToObserver(w, x, z);

      /**
       * Determines if the solid block physics should be done for this block,
       * also if fluids should be done on every tick. Otherwise only fluids are
       * done, and at a slower rate.
       */
      boolean doDetailedPhysics = true;
      // Only run the physics calculations for chunks within this distance
      // from an observer
      if (minDist >= maxChunkDist * maxChunkDist) doDetailedPhysics = false;
      int liquidUpdateRate = 10;
      int gasUpdateRate = 10;
      int totalLiquid = 0;
      for (int y = 0; y < 255; y++)
        totalLiquid += tempData.getFluidHistogram(y);
      boolean isOcean = totalLiquid > 16 * 16 * 4;
      int oceanUpdateRate;
      if (isOcean) oceanUpdateRate = 30;
      else oceanUpdateRate = 10;

      // if (totalLiquid > 16 * 16 * 4) liquidUpdateRate = 30;
      if (!doDetailedPhysics) {
        liquidUpdateRate *= 3;
        gasUpdateRate *= 3;
        oceanUpdateRate *= 3;
      }
      int staggeredTime = Counters.tick + xz.chunkXPos + 411 * xz.chunkZPos;
      boolean doOceanTicks = (staggeredTime % oceanUpdateRate) == 0;
      boolean doLiquidTicks = (staggeredTime % liquidUpdateRate) == 0;
      boolean doFireTicks = (staggeredTime % liquidUpdateRate) == 0;
      boolean doGasTicks = (staggeredTime % gasUpdateRate) == 0;

      if (!doDetailedPhysics && !doLiquidTicks && !doGasTicks) return;
      int minY, maxY;
      if (isOcean && !doOceanTicks) minY = 70;
      else minY = 0;
      maxY = 192;

      boolean restartPhysics = false;
      if (doDetailedPhysics) {
        if (tempData.solidBlockPhysicsLastTick < Counters.tick - ticksPerUpdate) {
          tempData.solidBlockPhysicsCountdownToAction = countdownToAction;
          restartPhysics = true;
        } else {
          tempData.solidBlockPhysicsCountdownToAction = Math.max(0, tempData.solidBlockPhysicsCountdownToAction - 1);
        }
      }

      int timeNow = Counters.tick % clockModulo;
      /**
       * Number of sound effects we are still allowed to play for this chunk
       */
      nSoundEffectsLeft = maxSoundEffects;
      soundEffectAttempts = 0;

      ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();

      // Don't process some of the chunks, when the current chunk has too much
      // fluids in it (is probably some kind of ocean)
      // TODO: sum the fluidHistograms over y, to compute if liquid ticks should
      // be done or not
      /*
       * if (wstate.sweepCounter % 3 != 0) { int cnt = 0; for (int y2 = 1; y2 <
       * 255; y2++) cnt += tempData0.getFluidHistogram(y2); if (cnt > 2000)
       * return; }
       */

      for (int y = minY; y < maxY; y++) {
        int fluidCount = 0;
        int gasCount = 0;

        ExtendedBlockStorage ebs = blockStorage[y >> 4];
        int ox = 0, oz = 0;
        // int ox=FysiksFun.rand.nextInt(16);
        // int oz=FysiksFun.rand.nextInt(16);
        for (int dxTmp = 0; dxTmp < 16; dxTmp++) {
          int dx = (dxTmp + ox) & 15;
          for (int dzTmp = 0; dzTmp < 16; dzTmp++) {
            int dz = (dzTmp + oz) & 15;
            int x0 = x + dx, y0 = y, z0 = z + dz;

            int id = 0;
            if (ebs != null) {
              id = ebs.getExtBlockID(dx, y & 15, dz);
            } else id = c.getBlockID(dx, y, dz);

            // TODO: is this neccessary? Test it!
            /*if (id == 0 || Gases.isGas[id]) {
              tempData.setTempData(dx, y, dz, 0);
            }*/

            // TODO: is this still neccessary? sometimes triggered...
            if (id != 0 && Block.blocksList[id] == null) {
              FysiksFun.logger.log(Level.WARNING, "[FF] Warning, there was a corrupted block at " + Util.xyzString(x + dx, y, z + dz)
                  + " - i've zeroed it for now but this is dangerous");
              if (ebs == null) {
                FysiksFun.logger.log(Level.SEVERE, "Cannot repair block since we don't have an ebs");
              } else {
                ebs.setExtBlockID(dx, y & 15, dz, 0);
                ebs.setExtBlockMetadata(dx, y & 15, dz, 0);
                FysiksFun.setBlockWithMetadataAndPriority(w, x + dx, y, z + dz, 0, 0, 0);
              }
              continue;
            }

            /* For fluids */
            if (doLiquidTicks && Fluids.isLiquid[id] && FysiksFun.settings.doFluids) {
              BlockFFFluid b = Fluids.asFluid[id];
              b.updateTickSafe(w, c, tempData, x + dx, y, z + dz, FysiksFun.rand, wstate.sweepCounter, delayedBlockMarkSet);
              if (FysiksFun.rand.nextInt(5 * b.liquidUpdateRate) == 0) b.updateRandomWalk(w, c, tempData, x + dx, y, z + dz, FysiksFun.rand);
              if (FysiksFun.rand.nextInt(10) == 0) b.expensiveTick(w, c, tempData, x + dx, y, z + dz, FysiksFun.rand);
              fluidCount++;
            }

            /* For gases */
            if (doGasTicks && Gases.isGas[id] && FysiksFun.settings.doGases) {
              BlockFFGas b = (BlockFFGas) Block.blocksList[id];
              b.updateTickSafe(w, x + dx, y, z + dz, FysiksFun.rand);
              if (FysiksFun.rand.nextInt(10) == 0) b.expensiveTick(w, c, tempData, x + dx, y, z + dz, FysiksFun.rand);
              gasCount++;
            }

            // Perform solid block physics
            if (FysiksFun.settings.doPhysics) {
              if (y > 0 && (blockDoPhysics[id] || blockDoSimplePhysics[id] != 0))
                doSolidBlockPhysics(c, tempData, blockStorage, x, y, z, x0, y0, z0, dx, dz, id, timeNow, restartPhysics, delayedBlockMarkSet);
            }

            // Extra fire
            if (doFireTicks && id == Block.fire.blockID && FysiksFun.settings.doExtraFire) ExtraFire.handleFireAt(w, x + dx, y, z + dz);

          }
        }
        tempData.setFluidHistogram(y, fluidCount);
        tempData.setGasHistogram(y, gasCount);
      }
      // Finally, update the "last tick" counter for this chunk. Important
      // to do this last.
      tempData.solidBlockPhysicsLastTick = Counters.tick;
    } catch (Exception e) {
      System.out.println(Thread.currentThread().getName() + ": BlockPhysicsSweeperThread got an exception" + e);
      e.printStackTrace();
    }
  }

  private void doSolidBlockPhysics(Chunk c, ChunkTempData tempData, ExtendedBlockStorage[] blockStorage, int x, int y, int z, int x0, int y0, int z0, int dx,
      int dz, int id, int timeNow, boolean restartPhysics, HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet) throws InterruptedException {

    /* Get current pressure, increase by our weight */
    int weight = blockWeight[id];
    if (weight < 0) weight = (FysiksFun.rand.nextInt(-weight) == 0 ? 1 : 0);

    int totalMoved = 0;
    int origTempValue = tempData.getTempData(dx, y, dz);
    int origPressure = (origTempValue & pressureBitsMask);
    curPressure = origPressure + weight; // (sweep==0?weight:0);
    curClock = (origTempValue >> clockBitsStart) & clockBitsMask;
    curBreak = (origTempValue >> breakBitsStart);
    boolean isFalling = curBreak == counterIsFalling;

    if (restartPhysics || origTempValue == 0 || y <= 1 || id == Block.bedrock.blockID) {
      curClock = timeNow;
      // curPressure = 0;
      curBreak = 0;
    }

    int simplifiedPhysics = blockDoSimplePhysics[id];
    if (simplifiedPhysics > 0) curPressure = 0;

    int origClock = curClock;
    int breakThreshold = blockStrength[id];

    boolean debugMe = false;

    /***** debug *****/
    if (Counters.tick % 50 == 0) {
      if (id == Block.hardenedClay.blockID) debugMe = true;
      // if (id == Block.planks.blockID) debugMe = true;
    }
    // if(x+dx == -676 && z+dz == 541) debugMe=true;

    if (debugMe) {
      System.out.println("id:" + id + "@" + Util.xyzString(x + dx, y, z + dz) + " origPressure: " + origPressure + " prevClock: " + curClock + " prevBreak: "
          + curBreak + " breakThreshold: " + breakThreshold);
    }

    // Propagate forces
    totalMoved = propagateForces(c, tempData, blockStorage, x, y, z, dx, dz, id, timeNow, restartPhysics, origPressure, isFalling, simplifiedPhysics,
        breakThreshold, debugMe);
    // Make blocks at y=1 as well as all bedrock blocks act as sinks/supports
    if (y <= 1 || id == Block.bedrock.blockID) {
      curPressure = 0;
      curClock = timeNow;
      totalMoved = 0;
    }

    // Compute if we should INITIATE or CONTINUE a FALL
    boolean doFall = curBreak == counterIsFalling;
    boolean fallDueToClock = false;
    int timeCheck = (timeNow - timeToFall + clockModulo) % clockModulo;

    // Compute if we should start falling due no support from ground (no clock)
    if (curClock < timeCheck) {
      fallDueToClock = (timeCheck - curClock) < (clockModulo * 4) / 5;
    } else if (curClock > timeCheck) {
      fallDueToClock = (curClock - timeCheck) > clockModulo / 5;
    }
    if (origClock != curClock) fallDueToClock = false;
    doFall = doFall || fallDueToClock;

    // Compute if we should INITIATE a BREAK
    boolean doBreak = totalMoved >= breakThreshold;
    if (simplifiedPhysics > 0) doBreak = false;

    /*
     * Prevent all action if the chunk based "counter since start" haven't
     * reached zero
     */
    if (tempData.solidBlockPhysicsCountdownToAction > 0) {
      if (debugMe) System.out.println("No action due to countdown: " + tempData.solidBlockPhysicsCountdownToAction);
      if (curBreak == breakAtCounter) {
        curBreak = breakAtCounter - 1;
        //System.out.println("Would have broken, preventing it due to countdown");
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
      else if (totalMoved < breakThreshold + 2 * (curBreak - breakAtCounter) - 2) curBreak--;
      if (curBreak < 0) System.out.println("wtf?");
    }

    /*
     * Wait with falling if sufficient time haven't passed since the last fall
     */
    if (doFall && !fallDueToClock) {
      if (debugMe) System.out.println("Wait with the fallling");
      doFall = false;
    }

    if (debugMe) {
      System.out.println("  curclock: " + curClock + " origclock: " + origClock + " time: " + timeNow + " fall: " + doFall);
      System.out.println("  totalMoved: " + totalMoved + " breakCounter: " + curBreak + " doBreak:" + doBreak + " curPressure:" + curPressure);
    }

    if ((doFall || doBreak) && fallOrBreak(c, x0, y0, z0, id, weight, timeNow, doBreak, delayedBlockMarkSet, tempData, debugMe)) {
      // Block moved successfully, nothing to do
    } else {
      // Update block with new values (will not be reached by
      // blocks that have successfully moved)
      if (debugMe) System.out.println("  " + Util.xyzString(x0, y0, z0) + " update clock:" + curClock + " count:" + curBreak + " pres:" + curPressure);
      int newTempValue = curPressure | (curClock << clockBitsStart) | (curBreak << breakBitsStart);
      if (newTempValue != origTempValue) tempData.setTempData(dx, y, dz, newTempValue);
    }
  }

  private int propagateForces(Chunk c, ChunkTempData tempData, ExtendedBlockStorage[] blockStorage, int x, int y, int z, int dx, int dz, int id, int timeNow,
      boolean restartPhysics, int origPressure, boolean isFalling, int simplifiedPhysics, int breakThreshold, boolean debugMe) {

    /**
     * Amount of pressure that we will NOT move due to us already starting to
     * break.
     */
    int elasticPressure = (elasticStrengthConstant * Math.min(breakAtCounter - 1, curBreak)) / (breakAtCounter - 1);

    int totalMoved = 0;
    // Iterate over all neighbours, update our clock and pressure accordingly
    for (int dir = 0; dir < 6; dir++) {
      int x2 = x + dx + Util.dirToDx(dir);
      int dy = Util.dirToDy(dir);
      int y2 = y + dy;
      int z2 = z + dz + Util.dirToDz(dir);
      int nnId = 0;
      boolean nnIsInSameChunk = ((x2 >> 4) == (x >> 4) && (z2 >> 4) == (z >> 4));
      if (nnIsInSameChunk) {
        ExtendedBlockStorage nnEbs = blockStorage[y2 >> 4];
        if (nnEbs != null) {
          nnId = nnEbs.getExtBlockID(x2 & 15, y2 & 15, z2 & 15);
        } else nnId = c.getBlockID(x2 & 15, y2, z2 & 15);
      } else {
        Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
        if (c2 == null) continue;
        nnId = c2.getBlockID(x2 & 15, y2, z2 & 15);
      }
      if (!blockDoPhysics[nnId]) {
        if (blockDoSimplePhysics[nnId] == 0) continue;
        if (blockDoSimplePhysics[nnId] > simplifiedPhysics) continue;
      }
      ChunkTempData tempData2 = nnIsInSameChunk ? tempData : ChunkCache.getTempData(w, x2 >> 4, z2 >> 4);
      int nnTmpVal = tempData2.getTempData(x2, y2, z2);
      if (nnTmpVal == 0 && y != 2) continue;
      int nnPressure = nnTmpVal & pressureBitsMask;
      int nnClock = (nnTmpVal >> clockBitsStart) & clockBitsMask;
      int nnBreak = (nnTmpVal >> breakBitsStart);
      boolean nnIsFalling = nnBreak == counterIsFalling;

      /*
       * Neighbour belongs to a not-updating chunk. Treat neighbour as static
       * object.
       */
      if (tempData2.solidBlockPhysicsLastTick < Counters.tick - 2 * ticksPerUpdate) {
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
      if (origPressure == 0 && id != Block.bedrock.blockID && y > 1 && nnPressure > curPressure) {
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
          if (toMove > maxMoved && id != Block.bedrock.blockID && y > 1) toMove = maxMoved;

          totalMoved += toMove;
          /*if (dir < 4) totalMoved += toMove * 2;
          else totalMoved += toMove;*/
        }
        if (debugNN) {
          System.out.println("To move from NN: " + toMove);
        }

        nnPressure -= toMove;
        if (id != Block.bedrock.blockID) curPressure += toMove;
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
   * @param tempData
   * @throws InterruptedException
   */
  private boolean fallOrBreak(Chunk c, int x0, int y0, int z0, int id, int weight, int timeNow, boolean doBreak,
      HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet, ChunkTempData tempData, boolean debugMe) throws InterruptedException {

    boolean canMoveSideways = doBreak;
    int myMeta = c.getBlockMetadata(x0 & 15, y0, z0 & 15);

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
        curBreak = 0; // Returned through a class variable, signifies we have
                      // landed.
        return false;
      }
      int r = FysiksFun.rand.nextInt(4);
      findFreeNeighbour:
      {
        for (int dir = 0; dir < 4; dir++) {
          targetX = x0 + Util.dirToDx((dir + r) % 4);
          targetY = y0 + Util.dirToDy((dir + r) % 4);
          targetZ = z0 + Util.dirToDz((dir + r) % 4);
          targetChunk = ChunkCache.getChunk(w, targetX >> 4, targetZ >> 4, false);
          if (targetChunk == null) continue; // WAS return false;
          targetId = targetChunk.getBlockID(targetX & 15, targetY, targetZ & 15);

          if (!blockDoPhysics[targetId]) {
            int idBelowTarget = targetChunk.getBlockID(targetX & 15, targetY - 1, targetZ & 15);
            if (!blockDoPhysics[idBelowTarget]) break findFreeNeighbour;
          }
        }
        // curBreak = 0;
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
          w.playSoundEffect(targetX + 0.5, targetY + 0.5, targetZ + 0.5, soundEffect, volume, pitch);
        }
      }
    }
    if (blockIsFragile[id] && (doBreak || hasLanded)) {
      w.playSoundEffect(targetX + 0.5, targetY + 0.5, targetZ + 0.5, "random.break", 1.0F, 1.0F);
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
    if (!blockDoPhysics[idAbove] && idAbove != 0 && !Fluids.isLiquid[idAbove] && !Gases.isGas[idAbove]) {
      synchronized (FysiksFun.vanillaMutex) {
        Block.blocksList[idAbove].dropBlockAsItem(w, x0, y0 + 1, z0, 0, 0);
      }
      // This is done with the wrong 'old meta', but that doesn't matter since
      // we in the worst case just make a mark too much
      FysiksFun.setBlockIDandMetadata(w, c, x0, y0 + 1, z0, 0, 0, idAbove, 0, delayedBlockMarkSet);
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
    ChunkTempData targetTempData = ChunkCache.getTempData(w, targetX >> 4, targetZ >> 4);
    int targetMeta = targetChunk.getBlockMetadata(targetX & 15, targetY, targetZ & 15);
    int targetTmp = tempData.getTempData(targetX, targetY, targetZ);
    FysiksFun.setBlockIDandMetadata(w, targetChunk, targetX, targetY, targetZ, id, myMeta, targetId, targetMeta, delayedBlockMarkSet);
    // If target is very close to a player then update immedately
    if (FysiksFun.minDistSqToObserver(w, targetX, targetY, targetZ) < 4.0) {
      w.markBlockForUpdate(targetX, targetY, targetZ);
    }
    int newPressure = curPressure + fallForce * (weight > 0 ? weight : 0);
    tempData.setTempData(targetX, targetY, targetZ, newPressure | (curClock << clockBitsStart) | (curBreak << breakBitsStart));

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
        if (doDrop) Block.blocksList[targetId].dropBlockAsItem(w, targetX, targetY, targetZ, 0, 0);
        if (Block.blocksList[targetId] instanceof ITileEntityProvider) {
          TileEntity targetEntity = targetChunk.getChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
          targetChunk.removeChunkBlockTileEntity(targetX & 15, targetY, targetZ & 15);
        }
      }
      targetId = 0;
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

    /*
     * Swap our block with the target, and update target entity if applicable
     */
    if (useSlowSetBlock) {
      synchronized (FysiksFun.vanillaMutex) {
        c.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, targetId, targetMeta);
        ChunkMarkUpdateTask task = ObjectPool.poolChunkMarkUpdateTask.getObject();
        task.set(w, x0, y0, z0, id, myMeta);
        delayedBlockMarkSet.add(task);
      }
    } else {
      FysiksFun.setBlockIDandMetadata(w, c, x0, y0, z0, targetId, targetMeta, id, myMeta, delayedBlockMarkSet);
    }
    tempData0.setTempData(x0, y0, z0, targetTmp);

    /*
     * Finally, after moving. If block above seems to be a tree, then allow it
     * to be triggered early.
     */
    if (idAbove == Block.wood.blockID && id == Block.wood.blockID && idBelow != Block.wood.blockID) {
      Trees.checkAndTickTree(w, x0, z0);
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

}
