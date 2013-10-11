package mbrx.ff;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class NetherFun {

  public static void doNetherFun(World w, int x, int z) {

    /**
     * Select a random layer in the chunk and melt all netherrack that is on
     * fire
     */
    if (FysiksFun.settings.netherrackCanMelt) {
      int y = FysiksFun.rand.nextInt(128) + 1;
      Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
      for (int dx = 0; dx < 16; dx++)
        for (int dz = 0; dz < 16; dz++) {
          int id = c.getBlockID(dx, y, dz);
          /* It's cheapest to check for fires first, and netherrack second */
          if (id == Block.fire.blockID && FysiksFun.rand.nextInt(16) == 0) {
            int id2 = c.getBlockID(dx, y - 1, dz);
            if (id2 == Block.netherrack.blockID) {
              Fluids.stillLava.setBlockContent(w, x + dx, y - 1, z + dz, 4);
              // Create an explosion also?

            }

          }
        }
    }
    
  }

}
