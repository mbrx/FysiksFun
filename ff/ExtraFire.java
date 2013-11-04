package mbrx.ff;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeDirection;

public class ExtraFire {

  public static void postInit() {
    Block.setBurnProperties(Block.wood.blockID, 15, 15); // was 5 5
    Block.setBurnProperties(Block.leaves.blockID, 45, 90); // was 30 60
    Block.setBurnProperties(Block.tallGrass.blockID, 90, 120); // was 60 100
  }

  public static void handleFireAt(World w, int x0, int y0, int z0) {
    for (int sample = 0; sample < 500; sample++) {
      int dx = FysiksFun.rand.nextInt(7) - 3;
      int dy = FysiksFun.rand.nextInt(3) - 1;
      int dz = FysiksFun.rand.nextInt(7) - 3;
      int x2 = x0 + dx, y2 = y0 + dy, z2 = z0 + dz;
      if (y2 > 0 && y2 < 255) {
        Chunk c = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
        if (c == null) continue;
        int id = c.getBlockID(x2 & 15, y2, z2 & 15);
        if (id == Block.fire.blockID) {
          /* We found another nearby fire, spread fire around us */
          System.out.println("Spreading!");
          for (int attempt = 0; attempt < 2000; attempt++) {
            dx = 0;
            dy = 0;
            dz = 0;
            for (int steps = 0; steps < 50; steps++) {
              int dir = FysiksFun.rand.nextInt(6);
              int dx2 = dx + Util.dirToDx(dir);
              int dy2 = dy + Util.dirToDy(dir);
              int dz2 = dz + Util.dirToDz(dir);
              x2 = x0 + dx2;
              y2 = y0 + dy2;
              z2 = z0 + dz2;
              c = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
              id = c.getBlockID(x2 & 15, y2, z2 & 15);
              if (id != 0 && id != Block.fire.blockID) continue;
              dx = dx2;
              dy = dy2;
              dz = dz2;

              for (dir = 0; dir < 6; dir++) {
                // This is a FORGE direction...
                ForgeDirection direction = ForgeDirection.getOrientation(dir);
                if (y2 + direction.offsetY < 0 || y2 + direction.offsetY > 255) continue;
                int x2nn = x2 + direction.offsetX;
                int y2nn = y2 + direction.offsetY;
                int z2nn = z2 + direction.offsetZ;

                c = ChunkCache.getChunk(w, x2nn >> 4, z2nn >> 4, false);
                id = c.getBlockID(x2nn & 15, y2nn, z2nn & 15);
                if (id == 0) continue;
                if (id == Block.grass.blockID) {
                  w.setBlock(x2nn, y2nn, z2nn, Block.dirt.blockID, 0, 0x02);
                  continue;
                }

                int meta = w.getBlockMetadata(x2nn, y2nn, z2nn);
                Block b = Block.blocksList[id];
                if (b == null) continue;

                if (b.isFlammable(w, x2nn, y2nn, z2nn, meta, direction.getOpposite())) {
                  w.setBlock(x2nn, y2nn, z2nn, Block.fire.blockID, 0, 0x02);
                }

              }
            }
          }
        }
      }
    }
  }

}
