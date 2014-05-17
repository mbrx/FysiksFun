package mbrx.ff.util;

import java.util.HashMap;

import mbrx.ff.FysiksFun;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * Singleton cache for all chunk related datatypes (the chunks themselves,
 * chunkTempData, chunkMarkUpdater objects).
 */
public class ChunkCache {
  public static final int                 cacheSize   = 256;
  public static final int                 cacheMask   = 0xff;
  private static HashMap<Thread, Boolean> isRecursive = new HashMap<Thread, Boolean>();

  private static class WorldCache {
    public World            w;
    public Chunk            chunkCache[][];
    public ChunkMarkUpdater cmlCache[][];
    public ChunkTempData    tempDataCache[][];

    public WorldCache(World w) {
      this.w = w;
      chunkCache = new Chunk[cacheSize][cacheSize];
      cmlCache = new ChunkMarkUpdater[cacheSize][cacheSize];
      tempDataCache = new ChunkTempData[cacheSize][cacheSize];
    }
  };

  private static Object     lastWorldSync = new Object();
  private static WorldCache lastWorld     = null;

  /** Returns the the chunk with the given CHUNK coordinates */
  public static Chunk getChunk(World w, int chunkX, int chunkZ, boolean forceLoad) {
    WorldCache wc;
    // synchronized (lastWorldSync) {
    if (lastWorld == null || lastWorld.w != w) synchronized(lastWorldSync) { lastWorld = new WorldCache(w); }
    wc = lastWorld;
    // }
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    Chunk c = wc.chunkCache[x][z];
    if (c == null || c.xPosition != chunkX || c.zPosition != chunkZ) {
      IChunkProvider provider = w.getChunkProvider();
      if (!provider.chunkExists(chunkX, chunkZ)) {
        if (forceLoad == false) return null;
      }

      // System.out.println(Thread.currentThread().getName() +
      // "getChunk aquire: "+FysiksFun.vanillaMutex.availablePermits());

      synchronized (FysiksFun.vanillaMutex) {
        c = provider.provideChunk(chunkX, chunkZ);
        wc.chunkCache[x][z] = c;
      }
    }
    return c;
  }

  /**
   * Returns the CML or creates and schedules one, in the chunk with the given
   * CHUNK coordinates
   */
  public static ChunkMarkUpdater getCML(World w, int chunkX, int chunkZ) {
    WorldCache wc;
    // synchronized (lastWorldSync) {
    if (lastWorld == null || lastWorld.w != w) synchronized(lastWorldSync) { lastWorld = new WorldCache(w); }
    wc = lastWorld;
    // }
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    if (wc.cmlCache[x][z] == null) {
      wc.cmlCache[x][z] = ChunkMarkUpdater.getAndScheduleChunkMarkList(w, chunkX, chunkZ);
    }
    return wc.cmlCache[x][z];
  }

  /** Removes the CML (if found) in the chunk with the given CHUNK coordinates */
  public static void removeCMLfromCache(World w, int chunkX, int chunkZ) {
    WorldCache wc;
    // synchronized (lastWorldSync) {
    if (lastWorld == null || lastWorld.w != w) return;
    wc = lastWorld;
    // }
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    wc.cmlCache[x][z] = null;
  }

  /**
   * Returns the tempData object corresponding to the chunk with the given CHUNK
   * coordinates
   */
  public static ChunkTempData getTempData(World w, int chunkX, int chunkZ) {
    WorldCache wc;
    // synchronized (lastWorldSync) {
    if (lastWorld == null || lastWorld.w != w) synchronized(lastWorldSync) { lastWorld = new WorldCache(w); }
    wc = lastWorld;
    // }
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    ChunkTempData tempData = wc.tempDataCache[x][z];
    if (tempData == null || tempData.coordinate.getX() != chunkX || tempData.coordinate.getZ() != chunkZ) {
      tempData = ChunkTempData.getChunk(w, chunkX << 4, chunkZ << 4);
      wc.tempDataCache[x][z] = tempData;
    }
    return tempData;
  }
}
