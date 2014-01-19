package mbrx.ff;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class BlockWater extends BlockFluid {

  public BlockWater(int blockID, Material water, int blockID2, int stillID, String string, Block replacedBlock) {
    super(blockID, water, blockID2, stillID, string,replacedBlock);
  }

  /*public BlockWater(int id, Material par2Material, int stillID, int movingID, String n) {
    super(id, par2Material, stillID, movingID, n, null);
  }*/

  public void expensiveTick(World world, Chunk chunk0, ChunkTempData tempData0, int x0, int y0, int z0, Random r) {
    int idBelow = chunk0.getBlockID(x0&15, y0-1, z0&15);
    /* Turn adjacent snow into ice (consuming the water) */
    if(idBelow == Block.blockSnow.blockID) {
      int meta = chunk0.getBlockMetadata(x0&15, y0-1, z0&15);
      if(FysiksFun.rand.nextInt(16) < meta) {
        FysiksFun.setBlockWithMetadataAndPriority(world, x0, y0-1, z0, Block.ice.blockID, 0, 0);
        setBlockContent(world,chunk0,tempData0,x0,y0,z0,0,"[soaked up by snow]",null);
      } else {
        FysiksFun.setBlockWithMetadataAndPriority(world, x0, y0-1, z0, 0, 0, 0);
      }
    }
    /* Evaporate due to fire */
    for(int dir=0;dir<6;dir++) {
      int dx=Util.dirToDx(dir);
      int dy=Util.dirToDy(dir);
      int dz=Util.dirToDz(dir);
      int x1 = x0+dx;
      int y1 = y0+dy;
      int z1 = z0+dz;
      Chunk c = ChunkCache.getChunk(world, x1>>4, z1>>4, false);
      if(c == null) continue;
      int id = c.getBlockID(x1&15, y1, z1&15);
      if(id == Block.fire.blockID) {
        /* Boil water to steam, and remove the fire (by making steam in the fire's place) */
        int content = getBlockContent(world, x0,y0,z0);
        content = Math.max(0,Math.min(maximumContent,content)-maximumContent/8);
        setBlockContent(world, x0,y0,z0, content);
        Gases.steam.setBlockContent(world, c, x1, y1, z1, 1);
      }
    }
    
  }
}
