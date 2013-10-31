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
  
  public static Chunk getChunk(World w, int chunkX, int chunkZ, boolean forceLoad) {
    WorldCache wc;
    if(lastWorld.w != w) lastWorld=new WorldCache(w);
    wc = lastWorld;
    int x=chunkX&cacheMask;
    int z=chunkZ&cacheMask;
    if(wc.chunkCache[x][z] == null) {
      IChunkProvider provider = w.getChunkProvider();
      if(!provider.chunkExists(x, z)) {
        if(forceLoad == false) return null;
      }
      wc.chunkCache[x][z] = provider.provideChunk(x, z);            
    }
    return wc.chunkCache[x][z];
  }
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
  public static void removeCMLfromCache(World w, int chunkX, int chunkZ) {
    WorldCache wc;
    if(lastWorld.w != w) return;
    wc = lastWorld;
    int x=chunkX&cacheMask;
    int z=chunkZ&cacheMask;
    wc.cmlCache[x][z]=null;
  }
  public static ChunkTempData getTempData(World w, int chunkX, int chunkZ) {
    WorldCache wc;
    if(lastWorld == null || lastWorld.w != w) lastWorld=new WorldCache(w);
    wc = lastWorld;
    int x=chunkX&cacheMask;
    int z=chunkZ&cacheMask;
    if(wc.tempDataCache[x][z] == null) {
      wc.tempDataCache[x][z] = ChunkTempData.getChunk(w, chunkX, chunkZ);
    }
    return wc.tempDataCache[x][z];
  }
}
