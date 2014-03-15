package mbrx.ff.ecology;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkCache;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.WorldGenBigTree;

/**
 * A helper class that is used by the vanilla WorldGenBigTree generator (after
 * rewriting it from FFCore)
 * 
 * @author mathias
 * 
 */
public class WorldGenTreeHelper {

  /**
   * Places a line of the specified block ID into the world from the first
   * coordinate triplet to the second.
   * 
   * @param worldGenBigTree
   */
  public static void placeBlockLine(World w, int[] start, int[] stop, int id) {
    // System.out.println("FF - placeBlockLine called!");

    int pos[] = new int[3];
    int prevPos[] = new int[3];
    pos[0] = start[0];
    pos[1] = start[1];
    pos[2] = start[2];
    prevPos[0] = 0;
    prevPos[1] = -1;
    prevPos[2] = 0;

    int dx = Math.abs(stop[0] - start[0]);
    int dz = Math.abs(stop[2] - start[2]);
    int deltaXZ = Math.max(dx, dz);
    boolean isBranch = deltaXZ > 0;
    if (isBranch) {
      // if(id == Block.wood.blockID) id = Block.planks.blockID;
    }
    if (!isBranch) {
      // Wood as root
      start[1] = start[1] - 2;
    }

    for (int step = 0; step < 300; step++) {
      float alpha = step / 300.0F;
      if (step > 100 && isBranch) if (id == Block.wood.blockID) id = Block.leaves.blockID;

      /*
       * We change only one coordinate at a time, to make sure we get a
       * completely connected line
       */
      // int dim=step%3;
      for (int dim = 0; dim < 3; dim++)
        pos[dim] = (int) (start[dim] * (1.0 - alpha) + stop[dim] * alpha + 0.5);
      if (pos[0] != prevPos[0] || pos[1] != prevPos[1] || pos[2] != prevPos[2]) {
        int meta = 0;
        if (deltaXZ != 0) {
          if (dx == deltaXZ) meta = 4;
          else meta = 8;
        }
        int x = prevPos[0];
        int y = prevPos[1];
        int z = pos[2];
        Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
        if (c != null && x != -1 && y != -1) FysiksFun.setBlockIDandMetadata(w, c, x, y, z, id, meta, 0, 0, null);
        y = pos[1];
        if (c != null && x != -1 && y != -1) FysiksFun.setBlockIDandMetadata(w, c, x, y, z, id, meta, 0, 0, null);
        x = pos[0];
        c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
        if (c != null) FysiksFun.setBlockIDandMetadata(w, c, x, y, z, id, meta, 0, 0, null);
        // w.setBlock(prevPos[0], prevPos[1], pos[2], id, meta, 0x03);
        // w.setBlock(prevPos[0], pos[1], pos[2], id, meta, 0x03);
        // w.setBlock(pos[0], pos[1], pos[2], id, meta, 0x03);
      }
      for (int dim = 0; dim < 3; dim++)
        prevPos[dim] = pos[dim];
    }
  }

  /*
   * byte b5 = 0; int l = Math.abs(aint3[0] - par1ArrayOfInteger[0]); int i1 =
   * Math.abs(aint3[2] - par1ArrayOfInteger[2]); int j1 = Math.max(l, i1);
   * 
   * if (j1 > 0) { if (l == j1) { b5 = 4; } else if (i1 == j1) { b5 = 8; } }
   */

}
