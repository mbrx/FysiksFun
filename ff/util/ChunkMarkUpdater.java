package mbrx.ff.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import com.google.common.base.Objects;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import mbrx.ff.FysiksFun;
import mbrx.ff.FysiksFun.WorldObserver;

/**
 * Maps a chunk into all blocks that are marked to be updated within that chunk. TODO garbage collect old instances
 * (especially for the world!)
 */
public class ChunkMarkUpdater {
  static int scheduledMarkUpdates = 0;
  static int ticksLeft            = 0;

  private static class MarkOriginalValue {
    int originalId, originalMeta;

    public MarkOriginalValue(int id, int meta) {
      this.originalId = id;
      this.originalMeta = meta;
    }

    public void set(int id, int meta) {
      this.originalId = id;
      this.originalMeta = meta;
    }
  }

  public CoordinateWXZ                                      coordinate;
  //private static Semaphore                                  mutex                  = new Semaphore(1);

  private HashMap<CoordinateWXYZ, MarkOriginalValue>        markList;
  private static CoordinateWXZ                              tmpCoordinateWXZ       = new CoordinateWXZ(null, -1, -1);
  private static CoordinateWXYZ                             tmpCoordinateWXYZ      = new CoordinateWXYZ(null, -1, -1, -1);
  private static MarkOriginalValue                          tmpMarkTask            = new MarkOriginalValue(-1, -1);
  private static ArrayList<ChunkMarkUpdater>                tmpChunkBlockMarkList  = new ArrayList<ChunkMarkUpdater>();

  //private static ArrayDeque<CoordinateWXYZ>                 coordinateWXYZFreePool = new ArrayDeque<CoordinateWXYZ>();

  /**
   * A queue of CML objects containing all chunks that have blocks that should be marked
   */
  private static ArrayDeque<ChunkMarkUpdater>               markChunkQueue         = new ArrayDeque<ChunkMarkUpdater>();
  /** A hashtable mapping chunk coordinates to the CML for that chunk */
  private static Hashtable<CoordinateWXZ, ChunkMarkUpdater> markChunkHashtable     = new Hashtable<CoordinateWXZ, ChunkMarkUpdater>();
  private static ArrayDeque<ChunkMarkUpdater>               markChunkFreePool      = new ArrayDeque<ChunkMarkUpdater>();

  /**
   * Gives the chunkMarkUpdater object for a given chunk, this object contains the list of blocks within that chunk that
   * needs to be updated.
   */
  public static ChunkMarkUpdater getAndScheduleChunkMarkList(World w, int chunkX, int chunkZ) {
    // This function is only called by scheduleBlockMark, which acquires the
    // semaphore first
    /*
     * try { mutex.acquire(); } catch (InterruptedException e) { e.printStackTrace(); return null; }
     */
    try {
      ChunkMarkUpdater cml;
      // Since we explicitly remove the cml from the cache when it is "released"
      // then we are safe to cache them like this
      tmpCoordinateWXZ.set(w, chunkX, chunkZ);
      cml = markChunkHashtable.get(tmpCoordinateWXZ);
      if (cml == null) {
        cml = markChunkFreePool.poll();
        if (cml == null) cml = new ChunkMarkUpdater(w, chunkX, chunkZ);
        else cml.set(w, chunkX, chunkZ);
        markChunkQueue.add(cml);
        markChunkHashtable.put(cml.coordinate, cml);
      }
      return cml;
    } finally {
      // mutex.release();
    }
  }

  private void set(World w, int chunkX, int chunkZ) {
    this.coordinate.set(w, chunkX, chunkZ);
  }

  /**
   * Schedule a block to be marked (sent to client) at some non-determined point in the future
   */
  public static void scheduleBlockMark(World w, int x, int y, int z) {
    scheduleBlockMark(w, x, y, z, -1, -1);
  }

  public static void scheduleBlockMark(World w, int x, int y, int z, int originalId, int originalMeta) {
    //scheduleBlockMarkSafe(w, x, y, z, originalId, originalMeta);
    
    synchronized(FysiksFun.vanillaMutex) {
      scheduleBlockMarkSafe(w, x, y, z, originalId, originalMeta);
    }
    /*
    try {
      mutex.acquire();
      
    } catch (InterruptedException e) {
      e.printStackTrace();
      return;
    } finally {
      mutex.release();
    }*/
  }

  public static void scheduleBlockMarkSafe(World w, int x, int y, int z, int originalId, int originalMeta) {
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    int newId = c.getBlockID(x & 15, y, z & 15);
    int newMeta = c.getBlockMetadata(x & 15, y, z & 15);

    scheduledMarkUpdates++;
    ChunkMarkUpdater cml = ChunkCache.getCML(w, x >> 4, z >> 4);
    tmpCoordinateWXYZ.set(w, x, y, z);

    MarkOriginalValue mov = cml.markList.get(tmpCoordinateWXYZ);
    if (mov == null) {
      /* Block had not been scheduled before, schedule it and note the original value for it */
      mov = new MarkOriginalValue(originalId, originalMeta);
      
      CoordinateWXYZ coord = ObjectPool.poolCoordinateWXYZ.getObject();
      coord.set(w, x, y, z);
      
      /*CoordinateWXYZ coord = coordinateWXYZFreePool.poll();
      if (coord == null) {
        coord = new CoordinateWXYZ(w, x, y, z);
      } else coord.set(w, x, y, z);*/
      
      cml.markList.put(coord, mov);
      Counters.chunkMarkScheduled++;
    } else {
      if (mov.originalId == newId && mov.originalMeta == newMeta) {
        /*
         * The block has been restored to it's original shape before it has been updated. Don't bother to make an
         * update.
         */
        cml.markList.remove(tmpCoordinateWXYZ);
        // fuck it, let just Java take care of the GC'ing for now
        Counters.chunkMarkUndo++;
      } else {
        /* It already existed there, we have not changed it back in anyway. Nothing left to do */
      }
    }
  }

  /** Assumes that we are called in a non-threaded environment */
  /*
   * public static void scheduleBlockMarkSingleThread(World w, int x, int y, int z) { ChunkMarkUpdater cml =
   * getAndScheduleChunkMarkList(w, x >> 4, z >> 4); tmpCoordinateWXYZ.set(w, x, y, z); if
   * (cml.markList.contains(tmpCoordinateWXYZ)) return; CoordinateWXYZ coord = coordinateWXYZFreePool.poll(); if (coord
   * == null) { coord = new CoordinateWXYZ(w, x, y, z); } else coord.set(w, x, y, z); cml.markList.add(coord); }
   */

  public ChunkMarkUpdater(World w, int chunkX, int chunkZ) {
    coordinate = new CoordinateWXZ(w, chunkX, chunkZ);
    markList = new HashMap<CoordinateWXYZ, MarkOriginalValue>();
  }

  public static void printStatistics() {
    /*
    System.out.println("Mark queue: " + ChunkMarkUpdater.markChunkQueue.size() + " chunks, Hashtable:" + markChunkHashtable.size() + " free pool:"
        + markChunkFreePool.size());
    System.out.println("Calls to scheduleBlockMark: " + scheduledMarkUpdates / 300 + " per tick (avg)");
    */
    
    scheduledMarkUpdates = 0;
  }

  public static void doTick() {
    if (Counters.tick % 5 != 0) return;
    ticksLeft += FysiksFun.settings.maxUpdatesPerTick;

    synchronized(FysiksFun.vanillaMutex) {
    //System.out.println("markChunkFreePool: "+markChunkFreePool.size()+"ht: "+markChunkHashtable.size()+" q: "+markChunkQueue.size());
    
      /*
       * First mark a number of chunks are observed at close range (< 1 chunk away) by some observer
       */
      // Ugly hack... cloning the list to avoid concurrent modification errors
      Iterator<ChunkMarkUpdater> iterator = markChunkQueue.clone().iterator();
      ChunkMarkUpdater cml;
      final int markRadiusSq_coarce = 1 * 1; // Measured in chunk coordinates
      final double markRadiusSq_fine = 12.0d * 12.0d; // Measured in block
      // coordinates

      tmpChunkBlockMarkList.clear();
      while (iterator.hasNext()) {
        if (ticksLeft < FysiksFun.settings.maxUpdatesPerTick / 2) break;

        cml = iterator.next();
        
        boolean doSend = false;
        for (WorldObserver o : FysiksFun.observers) {
          if (o.w != cml.coordinate.getWorld()) continue;
          int x = (int) o.posX, z = (int) o.posZ;
          int dx = (x >> 4) - cml.coordinate.getX();
          int dz = (z >> 4) - cml.coordinate.getZ();
          if (dx * dx + dz * dz <= markRadiusSq_coarce) {
            for (CoordinateWXYZ coord : cml.markList.keySet()) {
              double dx2 = o.posX - coord.getX();
              double dy2 = o.posY - coord.getY();
              double dz2 = o.posZ - coord.getZ();
              if (dy2 > 0) dy2 /= 2; // Makes clouds be updated more often than oceans below the player
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
    }

  }

  private void releaseChunk() {
    if (markList.size() != 0) System.out.println("ERROR - a ChunkBlockMarkList is released with a non-empty mark list, this is a BUG!");
    ChunkCache.removeCMLfromCache(coordinate.getWorld(), coordinate.getX(), coordinate.getZ());
    markChunkQueue.remove(this);
    markChunkHashtable.remove(coordinate);
    markChunkFreePool.add(this);
  }

  /**
   * Marks all updated blocks in this chunk to be sent to the user, returns estimate of network cost (1 - 8)
   */
  private int markChunk() {
    int collateral = 0;

    Counters.markQueueCounter += collateral;

    for (CoordinateWXYZ coord : markList.keySet()) { //origMarkList.keySet()) {
      coord.getWorld().markBlockForUpdate(coord.getX(), coord.getY(), coord.getZ());
      coord.set(null, 0, 0, 0);
      collateral++;
      Counters.markQueueCounter++;
    }    
    ObjectPool.poolCoordinateWXYZ.releaseAll(markList.keySet());
    markList.clear();

    if (collateral < 64) return collateral / 8 + 1;
    else return 32;
  }
}
