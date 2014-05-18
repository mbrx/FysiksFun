package mbrx.ff.util;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

import com.google.common.base.Objects;

import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * Class responsible for storing extra temporary data for chunks that contain
 * any FF blocks. This data will become reset on server restart, so it should
 * only be used for temporary data such as propagation of pressures or
 * computations of paths
 */
public class ChunkTempData {

  private static Hashtable<CoordinateWXYZ, ChunkTempData> chunks         = new Hashtable<CoordinateWXYZ, ChunkTempData>();
  // private byte[] tempData;
  private int[]                                           tempData;
  /**
   * The coordinate in CHUNk coordinates (ie. shifted down) of this tempData. Y
   * should always be 0
   */
  public CoordinateWXYZ                                   coordinate;
  private static CoordinateWXYZ                           tempCoordinate = new CoordinateWXYZ(null, 0, 0, 0);

  /** Tick when the chunk was last updated by physics. */
  public int                                              solidBlockPhysicsLastTick;
  /**
   * Countdown for when chunk may start beeing affected by physics. Used to
   * avoid incorrect result when chunks are first loaded or when they have been
   * out of range.
   */
  public int                                              solidBlockPhysicsCountdownToAction;
  /** When the chunkTempData was last used */
  public int                                              lastAccess;
  /** Contains a list of the amount of liquids on each layer of the chunk */
  private short[]                                         fluidHistogramData;
  /** Contains a list of the amount of gases on each layer of the chunk */
  private short[]                                         gasHistogramData;
  private boolean                                         histogramsInitialized;

  private static boolean                                  foo            = false;

  /** Removes old chunkTempData for chunks that are no longer loaded. */
  public static void cleanup(World w) {
    //System.out.println("Total tempDataEntries: "+chunks.size()+" size: "+(chunks.size()*16*16*256*4)/(1024*1024)+" mb");

    synchronized (chunks) {
      Set<Entry<CoordinateWXYZ, ChunkTempData>> toDelete = new HashSet<Entry<CoordinateWXYZ, ChunkTempData>>();
      Set<Entry<CoordinateWXYZ, ChunkTempData>> toIterate = new HashSet<Entry<CoordinateWXYZ, ChunkTempData>>();
      toIterate.addAll(chunks.entrySet());
      for (Entry<CoordinateWXYZ, ChunkTempData> entry : toIterate) {
        CoordinateWXYZ coord = entry.getKey();
        ChunkTempData tmpData = entry.getValue();
        Chunk c = ChunkCache.getChunk(coord.getWorld(), coord.getX(), coord.getZ(), false);
        if (c == null) {
          toDelete.add(entry);
          ChunkCache.removeChunkCache(coord);
          continue;
        }
        if (coord.getWorld().activeChunkSet.contains(c)) continue;
        if (Counters.tick > tmpData.lastAccess + 200) {
          toDelete.add(entry);
        }
      }
      for (Entry<CoordinateWXYZ, ChunkTempData> entry : toDelete) {
        chunks.entrySet().remove(entry);
      }
    }
  }

  /** X, Z ín CHUNK coordinates */
  private ChunkTempData(World w, int x, int y, int z) {
    Counters.genericCounter++;
    lastAccess = Counters.tick;

    tempData = new int[16 * 16 * 256];
    coordinate = new CoordinateWXYZ(w, x, 0, z); // Goes outside object pool
                                                 // since the tempData is
                                                 // permanent
    for (int dy = 0; dy < 256; dy++)
      for (int dz = 0; dz < 16; dz++)
        for (int dx = 0; dx < 16; dx++)
          tempData[dx + (dz << 4) + (dy << 8)] = 0;

    fluidHistogramData = new short[256];
    gasHistogramData = new short[256];
    histogramsInitialized = false;

    solidBlockPhysicsLastTick = -999;
    solidBlockPhysicsCountdownToAction = -999;

    // initializeHistogram();
    if (chunks.get(coordinate) == null) {
      synchronized (chunks) {

        if (chunks.get(coordinate) != null) {
          System.out.println("Warning, creating a chunk for a coordinate that already exists... this may be a sign of something much worse!");
          Util.printStackTrace();
        }

        chunks.put(coordinate, this);
      }
    }

  }

  public void initializeHistogram() {
    World w = coordinate.getWorld();
    int x = coordinate.getX();
    int y = coordinate.getY();
    int z = coordinate.getZ();

    Chunk chunk = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    if (chunk == null) return;
    histogramsInitialized = true;

    for (int y1 = 0; y1 < 256; y1++) {
      int fluidCnt = 0;
      int gasCnt = 0;
      for (int x1 = 0; x1 < 16; x1++)
        for (int z1 = 0; z1 < 16; z1++) {
          int id = chunk.getBlockID(x1, y1, z1);
          if (Fluids.isLiquid[id]) fluidCnt++;
          else if (Gases.isGas[id]) gasCnt++;
        }
      fluidHistogramData[y1] = (short) fluidCnt;
      gasHistogramData[y1] = (short) gasCnt;
    }
  }

  /**
   * Expects arguments in WORLD coordinates (ie. not shifted down). Y-coordinate
   * is currently ignored.
   */
  public static ChunkTempData getChunk(World w, int x, int y, int z) {
    return getChunk(w, x, z);
  }

  /** Expects arguments in WORLD coordinates (ie. not shifted down). */
  public static ChunkTempData getChunk(World w, int x, int z) {
    synchronized (chunks) {
      tempCoordinate.set(w, x >> 4, 0, z >> 4);
      ChunkTempData chunk = chunks.get(tempCoordinate);
      if (chunk == null) {
        chunk = new ChunkTempData(w, x >> 4, 0, z >> 4);
      }
      chunk.lastAccess = Counters.tick;
      return chunk;
    }
  }

  /**
   * Gets all the 32 bits of value in this cell. XZ in world coordinates (ie. >
   * 16 is allowed).
   */
  public int getTempData(int x, int y, int z) {
    return tempData[(x & 15) + ((z & 15) << 4) + (y << 8)];
  }

  /**
   * Gets lowermost 16 bits of value in this cell. XZ in world coordinates (ie.
   * > 16 is allowed).
   */
  public int getTempData16(int x, int y, int z) {
    return tempData[(x & 15) + ((z & 15) << 4) + (y << 8)] & 0xffff;
  }

  /**
   * Assigns the given value to this cell. XZ in world coordinates (ie. > 16 is
   * allowed)
   */
  public void setTempData(int x, int y, int z, int data) {
    tempData[(x & 15) + ((z & 15) << 4) + (y << 8)] = data;
  }

  /**
   * Sets the lowermost 16 bits in this cell. XZ in world coordinates (ie. > 16
   * is allowed). OVERWRITES the higher bits
   */
  public void setTempData16(int x, int y, int z, int data) {
    tempData[(x & 15) + ((z & 15) << 4) + (y << 8)] = data;
  }

  public static int getTempData(World w, int x, int y, int z) {
    synchronized (chunks) {
      tempCoordinate.set(w, x >> 4, 0, z >> 4);
      ChunkTempData chunk = chunks.get(tempCoordinate);
      if (chunk == null) {
        chunk = new ChunkTempData(w, x >> 4, 0, z >> 4);
        return 0;
      } else {
        return chunk.tempData[(x & 15) + ((z & 15) << 4) + (y << 8)];
      }
    }

  }

  public static void setTempData(World w, int x, int y, int z, int data) {
    synchronized (chunks) {
      tempCoordinate.set(w, x >> 4, 0, z >> 4);
      ChunkTempData chunk = chunks.get(tempCoordinate);
      if (chunk == null) {
        chunk = new ChunkTempData(w, x >> 4, 0, z >> 4);
      }
      chunk.tempData[(x & 15) + ((z & 15) << 4) + (y << 8)] = data;
    }
  }

  public void addFluidHistogram(int y, int delta) {
    if (!histogramsInitialized) {
      initializeHistogram();
    }
    fluidHistogramData[y] += delta;
    if (fluidHistogramData[y] < 0 || fluidHistogramData[y] > 256) {
      FysiksFun.logger.log(Level.SEVERE, "Our histogram counter at layer " + y + " has value " + fluidHistogramData[y] + ". This should not happen");
    }
  }

  public int getFluidHistogram(int y) {
    return fluidHistogramData[y];
  }

  public int getGasHistogram(int y) {
    return gasHistogramData[y];
  }

  public void setFluidHistogram(int y, int value) {
    fluidHistogramData[y] = (short) value;
  }

  public void setGasHistogram(int y, int value) {
    gasHistogramData[y] = (short) value;
  }

}
