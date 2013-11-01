package mbrx.ff;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

import com.google.common.base.Objects;

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
  private byte[]                                          tempData;
  private CoordinateWXYZ                                  coordinate;
  private static CoordinateWXYZ                           tempCoordinate = new CoordinateWXYZ(null, 0, 0, 0);
  private static Semaphore                                mutex          = new Semaphore(1);

  /** Contains a list of the amount of liquids on each layer of the chunk */
  private short[]                                         fluidHistogramData;
  /** Contains a list of the amount of gases on each layer of the chunk */
  private short[]                                         gasHistogramData;
  private boolean                                         histogramsInitialized;

  private ChunkTempData(World w, int x, int y, int z) {
    tempData = new byte[16 * 256 * 16 * 2];
    coordinate = new CoordinateWXYZ(w, x, y, z);
    for (int dy = 0; dy < 256; dy++)
      for (int dz = 0; dz < 16; dz++)
        for (int dx = 0; dx < 16; dx++) {
          tempData[(dx << 1) + (dz << 5) + (dy << 9)] = 0;
          tempData[(dx << 1) + (dz << 5) + (dy << 9) + 1] = 0;
        }
    fluidHistogramData = new short[256];
    gasHistogramData = new short[256];
    histogramsInitialized = false;
    
    // initializeHistogram();
    if (chunks.get(coordinate) == null) chunks.put(coordinate, this);

  }

  public void initializeHistogram() {
    World w = coordinate.getWorld();
    int x = coordinate.getX();
    int y = coordinate.getY();
    int z = coordinate.getZ();

    IChunkProvider chunkProvider = w.getChunkProvider();
    Chunk chunk;
    if (chunkProvider.chunkExists(x >> 4, z >> 4)) chunk = chunkProvider.provideChunk(x >> 4, z >> 4);
    else {
      return;
    }
    histogramsInitialized = true;

    for (int y1 = 0; y1 < 256; y1++) {
      int fluidCnt = 0;
      int gasCnt = 0;
      for (int x1 = 0; x1 < 16; x1++)
        for (int z1 = 0; z1 < 16; z1++) {
          int id = chunk.getBlockID(x1, y1, z1);
          if (Fluids.isLiquid[id]) fluidCnt++;
          else if(Gases.isGas[id]) gasCnt++;
        }
      fluidHistogramData[y1] = (short) fluidCnt;
      gasHistogramData[y1] = (short) gasCnt;
    }
  }

  public static ChunkTempData getChunk(World w, int x, int y, int z) {
    return getChunk(w, x, z);
  }

  /** Expects arguments in WORLD coordinates. */
  public static ChunkTempData getChunk(World w, int x, int z) {
    tempCoordinate.set(w, x >> 4, 0, z >> 4);
    ChunkTempData chunk = chunks.get(tempCoordinate);
    if (chunk == null) {
      chunk = new ChunkTempData(w, x >> 4, 0, z >> 4);
    }
    return chunk;
  }

  public int getTempData(int x, int y, int z) {
    int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 255) << 9);
    return ((int) tempData[baseAddr] & 0xFF) + (((int) tempData[baseAddr + 1] & 0xFF) << 8);
  }

  public void setTempData(int x, int y, int z, int data) {
    int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 255) << 9);
    tempData[baseAddr] = (byte) (data & 255);
    tempData[baseAddr + 1] = (byte) (data >> 8);
  }

  // for efficiency: we are skipping the synchronized keyword here
  public static int getTempData(World w, int x, int y, int z) {
    tempCoordinate.set(w, x >> 4, y >> 8, z >> 4);
    ChunkTempData chunk = chunks.get(tempCoordinate);
    if (chunk == null) {
      chunk = new ChunkTempData(w, x >> 4, y >> 8, z >> 4);
      return 0;
    } else {
      int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 255) << 9);
      return ((int) chunk.tempData[baseAddr] & 0xFF) + (((int) chunk.tempData[baseAddr + 1] & 0xFF) << 8);
    }
    /*
    if (chunk == null) {
      try {
        mutex.acquire();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      chunk = chunks.get(tempCoordinate);
      if (chunk == null) {
        chunk = new ChunkTempData(w, x >> 4, y >> 4, z >> 4);
      }
      mutex.release();
      return 0;
    } else {
      int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 15) << 9);
      return ((int) chunk.tempData[baseAddr] & 0xFF) + (((int) chunk.tempData[baseAddr + 1] & 0xFF) << 8);
    }*/

  }

  // TODO - see if we can avoid some of these sync commands
  // for efficiency: we are skipping the synchronized keyword here
  public static void setTempData(World w, int x, int y, int z, int data) {
    tempCoordinate.set(w, x >> 4, y >> 8, z >> 4);
    ChunkTempData chunk = chunks.get(tempCoordinate);
    if (chunk == null) {
      chunk = new ChunkTempData(w, x >> 4, y >> 8, z >> 4);
    }
    int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 255) << 9);
    chunk.tempData[baseAddr] = (byte) (data & 255);
    chunk.tempData[baseAddr + 1] = (byte) (data >> 8);
    /*    
    if (chunk == null) {
      try {
        mutex.acquire();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      chunk = chunks.get(tempCoordinate);
      if (chunk == null) {
        chunk = new ChunkTempData(w, x >> 4, y >> 4, z >> 4);
      }
      mutex.release();
    }
    int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 15) << 9);
    chunk.tempData[baseAddr] = (byte) (data & 255);
    chunk.tempData[baseAddr + 1] = (byte) (data >> 8);
    */
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
