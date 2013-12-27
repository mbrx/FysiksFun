package mbrx.ff;

import net.minecraft.block.Block;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Implements a few extra behaviours for various blocks, such as grass under water becomming dirt, and dirt under a
 * pillar of water becoming clay.
 */
public class ExtraBlockBehaviours {

  public static void doChunkTick(World world, ChunkCoordIntPair xz) {
    int x0 = xz.chunkXPos << 4;
    int z0 = xz.chunkZPos << 4;
    Chunk c = ChunkCache.getChunk(world, xz.chunkXPos, xz.chunkZPos, false);
    if (c == null) return;
    for (int tries = 0; tries < 8; tries++) {
      int dx = (FysiksFun.rand.nextInt(16) + Counters.tick) % 16;
      int dz = (FysiksFun.rand.nextInt(16) + Counters.tick / 16) % 16;
      int y = FysiksFun.rand.nextInt(200) + 1;
      int id = c.getBlockID(dx, y, dz);
      int idAbove = c.getBlockID(dx, y + 1, dz);
      if (id == Block.grass.blockID && Fluids.stillWater.isSameLiquid(idAbove)) {
        FysiksFun.setBlockWithMetadataAndPriority(world, x0 + dx, y, z0 + dz, Block.dirt.blockID, 0, 0);
      } else if (FysiksFun.rand.nextInt(5000) < FysiksFun.settings.clayToDirtChance && id == Block.dirt.blockID && Fluids.stillWater.isSameLiquid(idAbove)) {
        
        makeClay:
        {
          for (int dy = 2; dy < 5; dy++)
            if (!Fluids.stillWater.isSameLiquid(c.getBlockID(dx, y + dy, dz))) break makeClay;
          FysiksFun.setBlockWithMetadataAndPriority(world, x0+dx, y, z0+dz, Block.blockClay.blockID, 0,0);
        }

      }
    }
  }

}
