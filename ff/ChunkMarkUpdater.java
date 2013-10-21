package mbrx.ff;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import com.google.common.base.Objects;

import net.minecraft.world.World;
import mbrx.ff.FysiksFun.WorldObserver;

/**
 * Maps a chunk into all blocks that are marked to be updated within that chunk.
 * TODO garbage collect old instances (especially for the world!)
 */
class ChunkMarkUpdater {
  static int                                                scheduledMarkUpdates   = 0;
  static int                                                ticksLeft              = 0;

  public CoordinateWXZ                                      coordinate;
  private static Semaphore                                  mutex                  = new Semaphore(1);

  private HashSet<CoordinateWXYZ>                           markList;
  private static CoordinateWXZ                              tmpCoordinateWXZ       = new CoordinateWXZ(null, -1, -1);
  private static CoordinateWXYZ                             tmpCoordinateWXYZ      = new CoordinateWXYZ(null, -1, -1, -1);
  private static ArrayList<ChunkMarkUpdater>                tmpChunkBlockMarkList  = new ArrayList<ChunkMarkUpdater>();

  private static ArrayDeque<CoordinateWXYZ>                 coordinateWXYZFreePool = new ArrayDeque<CoordinateWXYZ>();

  private static ArrayDeque<ChunkMarkUpdater>               markChunkQueue         = new ArrayDeque<ChunkMarkUpdater>();
  private static Hashtable<CoordinateWXZ, ChunkMarkUpdater> markChunkHashtable     = new Hashtable<CoordinateWXZ, ChunkMarkUpdater>();
  private static ArrayDeque<ChunkMarkUpdater>               markChunkFreePool      = new ArrayDeque<ChunkMarkUpdater>();

  private static ChunkMarkUpdater getAndScheduleChunkMarkList(World w, int chunkX, int chunkZ) {
    // This function is only called by scheduleBlockMark, which acquires the
    // semaphore first
    /*
     * try { mutex.acquire(); } catch (InterruptedException e) {
     * e.printStackTrace(); return null; }
     */
    try {
      tmpCoordinateWXZ.set(w, chunkX, chunkZ);
      ChunkMarkUpdater cml = markChunkHashtable.get(tmpCoordinateWXZ);
      if (cml != null) return cml;
      cml = markChunkFreePool.poll();
      if (cml == null) cml = new ChunkMarkUpdater(w, chunkX, chunkZ);
      else cml.set(w, chunkX, chunkZ);
      markChunkQueue.add(cml);
      markChunkHashtable.put(cml.coordinate, cml);
      return cml;
    } finally {
      // mutex.release();
    }
  }

  private void set(World w, int chunkX, int chunkZ) {
    this.coordinate.set(w, chunkX, chunkZ);
  }

  /**
   * Schedule a block to be marked (sent to client) at some non-determined point
   * in the future
   */
  public static void scheduleBlockMark(World w, int x, int y, int z) {
    try {
      mutex.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
      return;
    }
    try {

      scheduledMarkUpdates++;
      ChunkMarkUpdater cml = getAndScheduleChunkMarkList(w, x >> 4, z >> 4);
      tmpCoordinateWXYZ.set(w, x, y, z);
      if (cml.markList.contains(tmpCoordinateWXYZ)) return;
      CoordinateWXYZ coord = coordinateWXYZFreePool.poll();
      if (coord == null) {
        coord = new CoordinateWXYZ(w, x, y, z);
      } else coord.set(w, x, y, z);
      cml.markList.add(coord);
    } finally {
      mutex.release();
    }

  }

  public ChunkMarkUpdater(World w, int chunkX, int chunkZ) {
    coordinate = new CoordinateWXZ(w, chunkX, chunkZ);
    markList = new HashSet<CoordinateWXYZ>();
  }

  public static void printStatistics() {
    System.out.println("Mark queue: " + ChunkMarkUpdater.markChunkQueue.size() + " Hashtable:" + markChunkHashtable.size() + " free pool:"
        + markChunkFreePool.size());
    System.out.println("Calls to scheduleBlockMark: " + scheduledMarkUpdates / 300);
    scheduledMarkUpdates = 0;
  }

  public static void doTick() {
    if (Counters.tick % 5 != 0) return;
    ticksLeft += FysiksFun.settings.maxUpdatesPerTick;

    try {
      mutex.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
      return;
    }
    try {

      /*
       * First mark a number of chunks are observed at close range (< 1 chunk
       * away) by some observer
       */
      Iterator<ChunkMarkUpdater> iterator = markChunkQueue.iterator();
      ChunkMarkUpdater cml;
      final int markRadiusSq_coarce = 1 * 1; // Measured in chunk coordinates
      final double markRadiusSq_fine = 8.0d * 8.0d; // Measured in block
                                                    // coordinates

      tmpChunkBlockMarkList.clear();
      while (iterator.hasNext()) {
        // if (ticksLeft < 1024) break;

        cml = iterator.next();
        boolean doSend = false;
        for (WorldObserver o : FysiksFun.observers) {
          if (o.w != cml.coordinate.getWorld()) continue;
          int x = (int) o.posX, z = (int) o.posZ;
          int dx = (x >> 4) - cml.coordinate.getX();
          int dz = (z >> 4) - cml.coordinate.getZ();
          if (dx * dx + dz * dz <= markRadiusSq_coarce) {
            for (CoordinateWXYZ coord : cml.markList) {
              double dx2 = o.posX - coord.getX();
              double dy2 = o.posY - coord.getY();
              double dz2 = o.posZ - coord.getZ();
              if (dx2 * dx2 + dy2 * dy2 + dz2 * dz2 < markRadiusSq_fine) {
                doSend = true;
                break;
              }
            }
          }
          if (doSend) break;
        }
        if (doSend) {
          Counters.earlyTick++;
          ticksLeft -= cml.markChunk();
          tmpChunkBlockMarkList.add(cml);
          // Allow a percentage of ticks to be spent on the global update of
          // chunks further away
          if (ticksLeft < FysiksFun.settings.maxUpdatesPerTick / 2) break;
        }
      }
      for (ChunkMarkUpdater cml2 : tmpChunkBlockMarkList) {
        cml2.releaseChunk();
      }
      tmpChunkBlockMarkList.clear();

      // Next, mark blocks to be sent to clients
      while (ticksLeft > 0) {
        ChunkMarkUpdater s = markChunkQueue.pollFirst();
        if (s == null) break;
        ticksLeft -= s.markChunk();
        s.releaseChunk();
      }
    } finally {
      mutex.release();
    }

  }

  private void releaseChunk() {
    if (markList.size() != 0) System.out.println("ERROR - a ChunkBlockMarkList is released with a non-empty mark list, this is a BUG!");
    markChunkQueue.remove(this);
    markChunkHashtable.remove(coordinate);
    markChunkFreePool.add(this);
  }

  /**
   * Marks all updated blocks in this chunk to be sent to the user, returns
   * estimate of network cost (1 - 8)
   */
  private int markChunk() {
    int collateral = 0;

    Counters.markQueueCounter += collateral;
    for (CoordinateWXYZ coord : markList) {
      coord.getWorld().markBlockForUpdate(coord.getX(), coord.getY(), coord.getZ());
      coord.set(null, 0, 0, 0); // MB 130810
      collateral++;
      Counters.markQueueCounter++;
    }
    coordinateWXYZFreePool.addAll(markList);
    markList.clear();

    if (collateral < 64) return collateral / 8 + 1;
    else return 32;
  }
}
