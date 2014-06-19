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
import mbrx.ff.ecology.ExtraFire;
import mbrx.ff.ecology.Trees;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.BlockFFGas;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import mbrx.ff.solidBlockPhysics.SolidBlockPhysics;
import mbrx.ff.solidBlockPhysics.SolidBlockPhysicsRules;
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
  ChunkCoordIntPair                          jobXZ;
  World                                      jobWorld;
  Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;
  SolidBlockPhysics                          solidBlockPhysics;

  public static final int                    ticksPerUpdate = 1;
  public static int                          maxChunkDist   = 48;

  public WorkerPhysicsSweep(World w, ChunkCoordIntPair xz, Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets) {
    this.jobXZ = xz;
    this.jobWorld = w;
    this.delayedBlockMarkSets = delayedBlockMarkSets;
    solidBlockPhysics = new SolidBlockPhysics();
  }

  @Override
  public void run() {

    // Don't do any physics in the end world, that is too hard for now
    if (jobWorld.provider.dimensionId == 1) return;

    try {

      Integer tid = (int) Thread.currentThread().getId();
      if (Counters.tick % ticksPerUpdate != 0) return;

      if (jobWorld.provider.dimensionId == 0) {
        SolidBlockPhysicsRules.blockStrength[Block.netherrack.blockID] = 16;
        SolidBlockPhysicsRules.blockWeight[Block.netherrack.blockID] = 4;
      } else {
        SolidBlockPhysicsRules.blockStrength[Block.netherrack.blockID] = 50;
        SolidBlockPhysicsRules.blockWeight[Block.netherrack.blockID] = 1;
      }

      HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet = delayedBlockMarkSets.get(tid);
      if (delayedBlockMarkSet == null) {
        delayedBlockMarkSet = new HashSet<ChunkMarkUpdateTask>();
        delayedBlockMarkSets.put(tid, delayedBlockMarkSet);
      }

      Chunk c = ChunkCache.getChunk(jobWorld, jobXZ.chunkXPos, jobXZ.chunkZPos, false);
      ChunkTempData tempData = ChunkCache.getTempData(jobWorld, jobXZ.chunkXPos, jobXZ.chunkZPos);
      int x = jobXZ.chunkXPos << 4;
      int z = jobXZ.chunkZPos << 4;
      int minDist = (int) FysiksFun.minXZDistSqToObserver(jobWorld, x, z);

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
      int staggeredTime = Counters.tick + jobXZ.chunkXPos + 411 * jobXZ.chunkZPos;
      boolean doOceanTicks = (staggeredTime % oceanUpdateRate) == 0;
      boolean doLiquidTicks = (staggeredTime % liquidUpdateRate) == 0;
      boolean doFireTicks = true; // (staggeredTime % liquidUpdateRate) == 0;
      boolean doGasTicks = (staggeredTime % gasUpdateRate) == 0;
      if (!doDetailedPhysics && !doLiquidTicks && !doGasTicks) return;
      int minY, maxY;
      if (isOcean && !doOceanTicks) minY = 70;
      else minY = 0;
      maxY = 192;

      solidBlockPhysics.startProcessingChunk(c, tempData, doDetailedPhysics);
      ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();

      for (int y = minY; y < maxY; y++) {
        int fluidCount = 0;
        int gasCount = 0;

        ExtendedBlockStorage ebs = blockStorage[y >> 4];
        int ox = 0, oz = 0;
        // This is a mechanism for visiting the blocks out-of-order (but atmost
        // once), however it is not good for the current physics rules
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

            // This is neccessary in order to make sure we don't "forget" some
            // data in the tempData. A bit inefficient but easier
            // than patching _every_ place in MC where a cell can be emptied.
            if (id == 0 || Gases.isGas[id]) {
              tempData.setTempData(dx, y, dz, 0);
            }

            // TODO: is this still neccessary? sometimes triggered...
            if (id != 0 && Block.blocksList[id] == null) {
              FysiksFun.logger.log(Level.WARNING, "[FF] Warning, there was a corrupted block at " + Util.xyzString(x + dx, y, z + dz)
                  + " - i've zeroed it for now but this is dangerous");
              if (ebs == null) {
                FysiksFun.logger.log(Level.SEVERE, "Cannot repair block since we don't have an ebs");
              } else {
                ebs.setExtBlockID(dx, y & 15, dz, 0);
                ebs.setExtBlockMetadata(dx, y & 15, dz, 0);
                FysiksFun.setBlockWithMetadataAndPriority(jobWorld, x + dx, y, z + dz, 0, 0, 0);
              }
              continue;
            }

            /* For fluids */
            if (doLiquidTicks && Fluids.isLiquid[id] && FysiksFun.settings.doFluids) {
              BlockFFFluid b = Fluids.asFluid[id];
              b.updateTickSafe(jobWorld, c, tempData, x + dx, y, z + dz, FysiksFun.rand, delayedBlockMarkSet);
              if (FysiksFun.rand.nextInt(5 * b.liquidUpdateRate) == 0) b.updateRandomWalk(jobWorld, c, tempData, x + dx, y, z + dz, FysiksFun.rand);
              if (FysiksFun.rand.nextInt(10) == 0) b.expensiveTick(jobWorld, c, tempData, x + dx, y, z + dz, FysiksFun.rand);
              fluidCount++;
            }

            /* For gases */
            else if (doGasTicks && Gases.isGas[id] && FysiksFun.settings.doGases) {
              BlockFFGas b = (BlockFFGas) Block.blocksList[id];
              b.updateTickSafe(jobWorld, x + dx, y, z + dz, FysiksFun.rand);
              if (FysiksFun.rand.nextInt(10) == 0) b.expensiveTick(jobWorld, c, tempData, x + dx, y, z + dz, FysiksFun.rand);
              gasCount++;
            }

            // Extra fire
            else if (doFireTicks && id == Block.fire.blockID && FysiksFun.settings.doExtraFire) ExtraFire.handleFireAt(jobWorld, x + dx, y, z + dz);

            // Perform solid block physics
            else if (FysiksFun.settings.doPhysics) {
              // Temporarily disabled when experimenting with OpenCL physics
              //if (y > 0 && (SolidBlockPhysicsRules.blockDoPhysics[id] || SolidBlockPhysicsRules.blockDoSimplePhysics[id] != 0))
              //  solidBlockPhysics.doSolidBlockPhysics(jobWorld, c, tempData, blockStorage, x0, y0, z0, id, delayedBlockMarkSet);
            }
          }
        }
        tempData.setFluidHistogram(y, fluidCount);
        tempData.setGasHistogram(y, gasCount);
      }
      // Finally, update the "last tick" counter for this chunk.
      tempData.solidBlockPhysicsLastTick = Counters.tick;
    } catch (Exception e) {
      System.out.println(Thread.currentThread().getName() + ": BlockPhysicsSweeperThread got an exception" + e);
      e.printStackTrace();
    }
  }

}
