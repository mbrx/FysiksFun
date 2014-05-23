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
  public static final int                 cacheSize   = 128;
  public static final int                 cacheMask   = 0x7f;
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

  private static HashMap<World, WorldCache> worldCaches = new HashMap<World, WorldCache>();

  /** Empties the cache of all Chunk, ChunkTempData and CMLCacheData */
  public static void resetCache() {
    worldCaches = new HashMap<World, WorldCache>();
  }
  
  /**
   * Thread safe method for finding or creating the WorldCache for a given
   * world.
   */
  private static WorldCache getWorldCache(World w) {
    WorldCache wc = worldCaches.get(w);
    // The outermost test is to avoid having to enter an exclusive region. 
    if (wc == null) {
      synchronized (worldCaches) {
        // sic. We need to check again after getting into the exclusive region
        // since another thread can created the cache *after* we made the test
        // for it.
        wc = worldCaches.get(w);
        if (wc == null) {
          wc = new WorldCache(w);
          worldCaches.put(w, wc);
        }
      }
    }
    return wc;
  }

  /** Returns the the chunk with the given CHUNK coordinates */
  public static Chunk getChunk(World w, int chunkX, int chunkZ, boolean forceLoad) {
    WorldCache wc = getWorldCache(w);
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    Chunk c = wc.chunkCache[x][z];
    if (c == null || c.xPosition != chunkX || c.zPosition != chunkZ) {
      IChunkProvider provider = w.getChunkProvider();
      if (!provider.chunkExists(chunkX, chunkZ)) {
        if (forceLoad == false) return null;
      }
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
    WorldCache wc = getWorldCache(w);
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    if (wc.cmlCache[x][z] == null) {
      wc.cmlCache[x][z] = ChunkMarkUpdater.getAndScheduleChunkMarkList(w, chunkX, chunkZ);
    }
    return wc.cmlCache[x][z];
  }

  /** Removes the CML (if found) in the chunk with the given CHUNK coordinates */
  public static void removeCMLfromCache(World w, int chunkX, int chunkZ) {
    WorldCache wc = getWorldCache(w);
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    wc.cmlCache[x][z] = null;
  }

  /**
   * Returns the tempData object corresponding to the chunk with the given CHUNK
   * coordinates
   */
  public static ChunkTempData getTempData(World w, int chunkX, int chunkZ) {
    WorldCache wc = getWorldCache(w);
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    ChunkTempData tempData = wc.tempDataCache[x][z];
    if (tempData == null || tempData.coordinate.getX() != chunkX || tempData.coordinate.getZ() != chunkZ) {
      tempData = ChunkTempData.getChunk(w, chunkX << 4, chunkZ << 4);
      wc.tempDataCache[x][z] = tempData;
    }
    return tempData;
  }

  public static void removeChunkCache(CoordinateWXYZ coord) {
    WorldCache wc = getWorldCache(coord.getWorld());
    int chunkX = coord.getX();
    int chunkZ = coord.getZ();
    int x = chunkX & cacheMask;
    int z = chunkZ & cacheMask;
    ChunkTempData tempData = wc.tempDataCache[x][z];
    if(tempData == null) return;
    if(tempData.coordinate.getX() == chunkX && tempData.coordinate.getZ() == chunkZ) {
      wc.tempDataCache[x][z]=null;
    }
  }
}
