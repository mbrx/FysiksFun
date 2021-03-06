package mbrx.ff.fluids;

import java.util.Random;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkTempData;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeDirection;

public class BlockFFLava extends BlockFFFluid {

  public BlockFFLava(int id, Material par2Material, int stillID, int movingID, String n,Block replacedBlock) {
    super(id, par2Material, stillID, movingID, n, replacedBlock);
  }

  public void expensiveTick(World world, Chunk chunk0, ChunkTempData tempData0, int x0, int y0, int z0, Random r) {
    /* Trigger fire in every flamable block nearby */
    for (int tries = 0; tries < 3; tries++) {
      int dx = FysiksFun.rand.nextInt(5) - 2;
      int dz = FysiksFun.rand.nextInt(5) - 2;

      for (int dy = -1; dy < 3; dy++) {
        for (int dir = 0; dir < 6; dir++) {
          // This is a FORGE direction...
          ForgeDirection direction = ForgeDirection.getOrientation(dir);
          if (y0 + dy + direction.offsetY < 0 || y0 + dy + direction.offsetY > 255) continue;
          int x1 = x0 + direction.offsetX + dx;
          int y1 = y0 + dy + direction.offsetY;
          int z1 = z0 + direction.offsetZ + dz;

          int id = world.getBlockId(x1, y1, z1);
          if (id == 0) continue;
          if (id == Block.grass.blockID) {
            world.setBlock(x1, y1, z1, Block.dirt.blockID, 0, 0x02);
            continue;
          }
          if (id == Block.ice.blockID) {
            Fluids.stillWater.setBlockContent(world, x1, y1, z1, Fluids.stillWater.maximumContent);
            continue;
          }
          if (id == Block.blockSnow.blockID) {
            int meta = world.getBlockMetadata(x1, y1, z1);
            Fluids.stillWater.setBlockContent(world, x1, y1, z1, Fluids.stillWater.maximumContent / 16 * meta);
            continue;
          }

          int meta = world.getBlockMetadata(x1, y1, z1);
          Block b = Block.blocksList[id];
          if (b == null) continue;

          if (b.isFlammable(world, x1, y1, z1, meta, direction.getOpposite())) {
            world.setBlock(x1, y1, z1, Block.fire.blockID, 0, 0x02);
          }
        }
      }
    }
  }
}
