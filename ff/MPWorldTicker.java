package mbrx.ff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkMarkUpdateTask;
import mbrx.ff.util.ChunkMarkUpdater;
import mbrx.ff.util.ChunkTempData;
import mbrx.ff.util.Counters;
import mbrx.ff.util.ObjectPool;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Provides a multithreaded server for ticking all blocks at given layers of a
 * world in sweeps that cycle back.
 */
public class MPWorldTicker {

  static class WorldUpdateState {
    /**
     * Counts the number of steps that subdivides what parts of the world is
     * investigated
     */
    int sweepStepCounter;
    /** Counts the total number of sweeps done on the world */
    int sweepCounter;
  };

  private static Hashtable<World, WorldUpdateState> worldUpdateState = new Hashtable<World, WorldUpdateState>();

  /**
   * Performs multithreaded calls to all update functions for all modules of FF,
   * for every loaded chunk in the given world.
   */
  public static void doUpdateChunks(World w) {

    // A thread dependent 'safe' set for scheduling delayed block mark requests
    Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets = Collections.synchronizedMap(new Hashtable<Integer, HashSet<ChunkMarkUpdateTask>>());
    Set alreadyScheduled = new HashSet();

    // Schedule jobs, first the odd coordinates, then the even coordinates
    for (int oddeven = 0; oddeven < 2; oddeven++) {
      List<Future> toWaitFor = new ArrayList<Future>();
      for (Object o : w.activeChunkSet) {
        ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
        if (((xz.chunkXPos + xz.chunkZPos) & 1) == oddeven) {
          Chunk chunk = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, true);
          ChunkTempData tempData = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);
          Runnable worker = new WorkerUpdateChunks(w, chunk, xz, delayedBlockMarkSets);
          Future f = FysiksFun.executor.submit(worker);
          toWaitFor.add(f);
          alreadyScheduled.add(xz);
        }
      }
      /* Make a sweep around every observer and process a few more chunks in those directions to allow water etc. to flow */
      for (FysiksFun.WorldObserver observer : FysiksFun.observers) {
        double angle = ((double) (Counters.tick / 200)) * Math.PI * 2.0 / 100.0;
        for (int dist = 0; dist < 1024; dist += 8) {
          int x = (int) (observer.posX + dist * Math.sin(angle));
          int z = (int) (observer.posZ + dist * Math.cos(angle));
          if(((x+z)&1) == oddeven) {
            ChunkCoordIntPair xz = new ChunkCoordIntPair(x>>4,z>>4);
            if(!alreadyScheduled.contains(xz)) {
              Chunk chunk = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, true);
              Runnable worker = new WorkerUpdateChunks(w, chunk, xz, delayedBlockMarkSets);
              Future f = FysiksFun.executor.submit(worker);
              toWaitFor.add(f);
              alreadyScheduled.add(xz);
            }
          }
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
        ObjectPool.poolChunkMarkUpdateTask.releaseObject(task);
      }
      delayedBlockMarkSet.clear();
    }
  }

  /**
   * Performs the main ticks for all functions requiring block ticks. by
   * circulating over all layers in multiple ticks. Ie, going down from layer
   * 255 to bedrock and then starting over.
   */
  public static void doBlockSweeps(World w) {

    WorldUpdateState wstate = worldUpdateState.get(w);
    if (wstate == null) {
      wstate = new WorldUpdateState();
      worldUpdateState.put(w, wstate);
    }
    int mi, ma;
    wstate.sweepStepCounter++;

    int nStepsPerSweep = 6;
    int sweep = wstate.sweepStepCounter % nStepsPerSweep;
    int yPerStep = 256 / nStepsPerSweep;
    mi = sweep * yPerStep;
    ma = Math.min(255, (sweep + 1) * yPerStep);
    if (sweep == 0) wstate.sweepCounter++;

    // Make sure all chunks/tempData are loaded... if this is not done first we
    // may have problems since the loading
    // functions are not thread safe
    for (Object o : w.activeChunkSet) {
      ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
      Chunk c = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, true);
      ChunkTempData tmp = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);
    }

    // A thread dependent 'safe' set for scheduling delayed block mark requests
    Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets = Collections.synchronizedMap(new Hashtable<Integer, HashSet<ChunkMarkUpdateTask>>());

    Set alreadyScheduled = new HashSet();

    // Schedule jobs, first the odd coordinates, then the even coordinates
    for (int oddeven = 0; oddeven < 2; oddeven++) {
      List<Future> toWaitFor = new ArrayList<Future>();
      for (Object o : w.activeChunkSet) {
        ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
        if (((xz.chunkXPos + xz.chunkZPos) & 1) == oddeven) {
          Runnable physicsWorker = new WorkerPhysicsSweep(w, wstate, xz, delayedBlockMarkSets);
          Future f = FysiksFun.executor.submit(physicsWorker);
          toWaitFor.add(f);
          alreadyScheduled.add(xz);
        }
      }
      /* Make a sweep around every observer and process a few more chunks in those directions to allow water etc. to flow */
      for (FysiksFun.WorldObserver observer : FysiksFun.observers) {
        double angle = ((double) (Counters.tick / 200)) * Math.PI * 2.0 / 100.0;
        for (int dist = 0; dist < 1024; dist += 8) {
          int x = (int) (observer.posX + dist * Math.sin(angle));
          int z = (int) (observer.posZ + dist * Math.cos(angle));
          if(((x+z)&1) == oddeven) {
            ChunkCoordIntPair xz = new ChunkCoordIntPair(x>>4,z>>4);
            if(!alreadyScheduled.contains(xz)) {
              Chunk chunk = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, true);
              Runnable worker = new WorkerUpdateChunks(w, chunk, xz, delayedBlockMarkSets);
              Future f = FysiksFun.executor.submit(worker);
              toWaitFor.add(f);
              alreadyScheduled.add(xz);
            }
          }
        }
      }
      for (Future f : toWaitFor) {
        try {
          f.get();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    /* Go through all updated blocks and mark them for updates */
    for (HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet : delayedBlockMarkSets.values()) {
      for (ChunkMarkUpdateTask coord : delayedBlockMarkSet) {
        ChunkMarkUpdater.scheduleBlockMarkSafe(coord.getWorld(), coord.getX(), coord.getY(), coord.getZ(), coord.getOrigId(), coord.getOrigMeta());
        ObjectPool.poolChunkMarkUpdateTask.releaseObject(coord);
      }
      delayedBlockMarkSet.clear();
    }
    delayedBlockMarkSets.clear();
  }

}
