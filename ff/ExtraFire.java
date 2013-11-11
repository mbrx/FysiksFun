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
    //if(w != null) return;
    
    // Block.fire.updateTick(w, x0, y0, z0, FysiksFun.rand);

    int fireCount=0;

    for (int dx = -2; dx <= 2; dx++)
      for (int dy = -2; dy <= 2; dy++)
        for (int dz = -2; dz <= 2; dz++) {

          int x2 = x0 + dx, y2 = y0 + dy, z2 = z0 + dz;
          if (y2 > 0 && y2 < 255) {
            Chunk c = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
            if (c == null) continue;
            int id = c.getBlockID(x2 & 15, y2, z2 & 15);
            if (id == Block.fire.blockID || id == Fluids.stillLava.blockID || id == Fluids.flowingLava.blockID) {
              /* We found another nearby fire, spread fire around us */
              fireCount++;
            } else if(id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {
              fireCount--;
            }
          }
        }    
    
    if (fireCount >= 4 && FysiksFun.rand.nextInt(fireCount*4) == 0)  {
      // System.out.println("Spreading fire!");
      for (int attempt = 0; attempt < 3; attempt++) {
        int dx = 0;
        int dy = 0;
        int dz = 0;
        for (int steps = 0; steps < 4+fireCount; steps++) {
          int dir = FysiksFun.rand.nextInt(6);
          int dx2 = dx + Util.dirToDx(dir);
          int dy2 = dy + Util.dirToDy(dir);
          int dz2 = dz + Util.dirToDz(dir);
          int x2 = x0 + dx2;
          int y2 = y0 + dy2;
          int z2 = z0 + dz2;
          Chunk c = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
          int id = c.getBlockID(x2 & 15, y2, z2 & 15);
          if (id != 0 && id != Block.fire.blockID) continue;
          dx = dx2;
          dy = dy2;
          dz = dz2;

          int idBelow = c.getBlockID(x2&15, y2-1, z2&15);
          if(idBelow != 0 && id != Block.fire.blockID && FysiksFun.rand.nextInt(11) == 0) 
            FysiksFun.setBlockWithMetadataAndPriority(w,x2, y2, z2, Block.fire.blockID, 0, 0);
          
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
              FysiksFun.setBlockWithMetadataAndPriority(w,x2nn, y2nn, z2nn, Block.dirt.blockID, 0, 0);
              // w.setBlock(x2nn, y2nn, z2nn, Block.dirt.blockID, 0, 0x02);
              continue;
            }

            int meta = w.getBlockMetadata(x2nn, y2nn, z2nn);
            Block b = Block.blocksList[id];
            if (b == null) continue;

            if (b.isFlammable(w, x2nn, y2nn, z2nn, meta, direction.getOpposite())) {
              FysiksFun.setBlockWithMetadataAndPriority(w,x2nn, y2nn, z2nn, Block.fire.blockID, 0, 0);
            }

          }
        }
      }
    }
  }

}
