package mbrx.ff.solidBlockPhysics;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Counters;
import mbrx.ff.util.SoundQueue;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Overrides the placement behaviour so that stone blocks cannot be placed in
 * the world by a player.
 */
public class BlockFFStone extends BlockStone {

  private static int    maxShatterDepth = 20;
  public static boolean overrideShatter = false;

  public BlockFFStone(int par1) {
    super(par1);
  }

  public boolean canPlaceBlockAt(World par1World, int x, int y, int z) {
    return FysiksFun.settings.canPlaceStone;
  }

  /**
   * Called right before the block is destroyed by a player. Args: world, x, y,
   * z, metaData
   */
  public void onBlockDestroyedByPlayer(World w, int x, int y, int z, int meta) {
    shatterStone(w, x, y, z, blockID, meta);
  }

  /*public void breakBlock(World w, int x, int y, int z, int oldId, int oldMetaData) {    
  }*/
  public void shatterStone(World w, int x, int y, int z, int oldId, int oldMetaData) {

    if (overrideShatter) return;
    if ((Util.smear((Counters.tick / 600) * 17) % 3173) + y > 74) return;
    if (maxShatterDepth <= 0) return;
    --maxShatterDepth;

    for (int dir = 0; dir < 6; dir++) {
      if (FysiksFun.rand.nextInt(5) != 0) continue;
      int x1 = x + Util.dirToDx(dir);
      int y1 = y + Util.dirToDy(dir);
      int z1 = z + Util.dirToDz(dir);
      if (y1 < 1 || y1 > 254) continue;
      Chunk c = ChunkCache.getChunk(w, x1 >> 4, z1 >> 4, false);
      if (c == null) continue;
      int id = c.getBlockID(x1 & 15, y1, z1 & 15);
      if (id == blockID) {
        // TODO - thread safety here????
        FysiksFun.setBlockWithMetadataAndPriority(w, x1, y1, z1, Block.cobblestone.blockID, 0, 0);
        shatterStone(w, x1, y1, z1, blockID, -1);

        /**
         * This may or may not work depending on threading issues and if we are
         * crossing a chunk boundary. In worst case nothing realy happens.
         */
        SolidBlockPhysics.addPressure(w, x1, y1, z1, 10000);
        float volume = 0.75F + FysiksFun.rand.nextFloat() * 0.5F;
        float pitch = 1.0F;
        SoundQueue.queueSound(w, x1 + 0.5, y1 + 0.5, z1 + 0.5, "fysiksfun:rubble", volume, pitch);
      }
    }
    ++maxShatterDepth;
  }
}
