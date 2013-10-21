package mbrx.ff;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;

import com.google.common.base.Objects;

import net.minecraft.world.World;

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

  /*
   * TODO - implement a mechanism for retiring old chunkTempData that have not
   * been used for some time...
   */

  private ChunkTempData(World w, int x, int y, int z) {
    tempData = new byte[16 * 16 * 16 * 2];
    coordinate = new CoordinateWXYZ(w, x, y, z);
    for (int dy = 0; dy < 16; dy++)
      for (int dz = 0; dz < 16; dz++)
        for (int dx = 0; dx < 16; dx++) {
          tempData[(dx << 1) + (dz << 5) + (dy << 9)] = 0;
          tempData[(dx << 1) + (dz << 5) + (dy << 9) + 1] = 0;
        }
    if (chunks.get(coordinate) == null) chunks.put(coordinate, this);
  }

  public static ChunkTempData getChunk(World w, int x, int y, int z) {
    tempCoordinate.set(w, x >> 4, y >> 4, z >> 4);
    ChunkTempData chunk = chunks.get(tempCoordinate);
    if (chunk == null) {
      chunk = new ChunkTempData(w, x >> 4, y >> 4, z >> 4);
    }
    return chunk;

    /*if (chunk == null) {
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
    // else
    // System.out.println("(getChunk) Found old ChunkTempData: "+Util.xyzString(x>>4,
    // y>>4, z>>4));
    return chunk;
    */
  }

  public int getTempData(int x, int y, int z) {
    int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 15) << 9);
    return ((int) tempData[baseAddr] & 0xFF) + (((int) tempData[baseAddr + 1] & 0xFF) << 8);
  }

  public void setTempData(int x, int y, int z, int data) {
    int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 15) << 9);
    tempData[baseAddr] = (byte) (data & 255);
    tempData[baseAddr + 1] = (byte) (data >> 8);
  }

  // for efficiency: we are skipping the synchronized keyword here
  public static int getTempData(World w, int x, int y, int z) {
    tempCoordinate.set(w, x >> 4, y >> 4, z >> 4);
    ChunkTempData chunk = chunks.get(tempCoordinate);
    if (chunk == null) {
      chunk = new ChunkTempData(w, x >> 4, y >> 4, z >> 4);
      return 0;
    } else {
      int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 15) << 9);
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
    tempCoordinate.set(w, x >> 4, y >> 4, z >> 4);
    ChunkTempData chunk = chunks.get(tempCoordinate);
    if (chunk == null) {
      chunk = new ChunkTempData(w, x >> 4, y >> 4, z >> 4);
    }
    int baseAddr = ((x & 15) << 1) + ((z & 15) << 5) + ((y & 15) << 9);
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

}
