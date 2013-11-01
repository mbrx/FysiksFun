package mbrx.ff;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/** Singleton cache for all chunk related datatypes (the chunks themselves, chunkTempData, chunkMarkUpdater objects). */
public class ChunkCache {
  public static final int cacheSize = 256;
  public static final int cacheMask = 0xff;
  
  private static class WorldCache {
    public World w;
    public Chunk chunkCache[][];
    public ChunkMarkUpdater cmlCache[][];
    public ChunkTempData tempDataCache[][];
    
    public WorldCache(World w) {
      this.w = w;
      chunkCache = new Chunk[cacheSize][cacheSize];
      cmlCache = new ChunkMarkUpdater[cacheSize][cacheSize];
      tempDataCache = new ChunkTempData[cacheSize][cacheSize];
    }
  };
  
  private static WorldCache lastWorld=null;
  
  /** Returns the the chunk with the given CHUNK coordinates */
  public static Chunk getChunk(World w, int chunkX, int chunkZ, boolean forceLoad) {
    WorldCache wc;
    if(lastWorld == null || lastWorld.w != w) lastWorld=new WorldCache(w);
    wc = lastWorld;
    int x=chunkX&cacheMask;
    int z=chunkZ&cacheMask;
    if(wc.chunkCache[x][z] == null) {
      IChunkProvider provider = w.getChunkProvider();
      if(!provider.chunkExists(chunkX, chunkZ)) {
        if(forceLoad == false) return null;
      }
      wc.chunkCache[x][z] = provider.provideChunk(chunkX, chunkZ);            
    }
    return wc.chunkCache[x][z];
  }
  /** Returns the CML or creates and schedules one, in the chunk with the given CHUNK coordinates */
  public static ChunkMarkUpdater getCML(World w, int chunkX, int chunkZ) {
    WorldCache wc;
    if(lastWorld == null || lastWorld.w != w) lastWorld=new WorldCache(w);
    wc = lastWorld;
    int x=chunkX&cacheMask;
    int z=chunkZ&cacheMask;
    if(wc.cmlCache[x][z] == null) {
      wc.cmlCache[x][z] = ChunkMarkUpdater.getAndScheduleChunkMarkList(w, chunkX, chunkZ);
    }
    return wc.cmlCache[x][z];
  }
  /** Removes the CML (if found) in the chunk with the given CHUNK coordinates */
  public static void removeCMLfromCache(World w, int chunkX, int chunkZ) {
    WorldCache wc;
    if(lastWorld.w != w) return;
    wc = lastWorld;
    int x=chunkX&cacheMask;
    int z=chunkZ&cacheMask;
    wc.cmlCache[x][z]=null;
  }
  /** Returns the tempData object corresponding to the chunk with the given CHUNK coordinates */
  public static ChunkTempData getTempData(World w, int chunkX, int chunkZ) {
    WorldCache wc;
    if(lastWorld == null || lastWorld.w != w) lastWorld=new WorldCache(w);
    wc = lastWorld;
    int x=chunkX&cacheMask;
    int z=chunkZ&cacheMask;
    if(wc.tempDataCache[x][z] == null) {
      wc.tempDataCache[x][z] = ChunkTempData.getChunk(w, chunkX<<4, chunkZ<<4);
    }
    return wc.tempDataCache[x][z];
  }
}
