package mbrx.ff.fluids;

import java.util.Random;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkTempData;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class BlockPyroclastic extends BlockFFGas {

  public BlockPyroclastic(int id, Material material) {
    super(id, material);
    // TODO Auto-generated constructor stub
  }

  
  public void expensiveTick(World w, Chunk c, ChunkTempData tempData0, int x, int y, int z, Random rand) {
    int content=getBlockContent(c,x,y,z);
    
    // Acts like lava when it comes to igniting stuff
    if(rand.nextInt(10) == 0)
      Fluids.flowingLava.expensiveTick(w,c,tempData0,x,y,z,rand);
    
    /* Small chance to turn into lava */
    //if(w.rainingStrength > 0.0 || (content==1 && FysiksFun.rand.nextInt(40) == 0)) {
    if((content==1 && FysiksFun.rand.nextInt(40) == 0)) {
      Fluids.flowingLava.setBlockContent(w, c, tempData0, x,y,z, BlockFFFluid.pressurePerContent*content/4,"[pyroclasticToLava]",null);     
    }
  }
  
  /* Pyroclastic clouds condensates to lava */
  @Override
  protected void condenseFromAltitude(World w, int x, int y, int z, Chunk origChunk, int newContent) {
    /* High chance that we move the water straight down to where it should go to remove amount of liquid that is falling in the sky. */
    if (FysiksFun.rand.nextInt(8) != 0) {
      for (; y >= 1; y--) {
        if (origChunk.getBlockID(x & 15, y - 1, z & 15) != 0) break;
      }
    }
    Fluids.stillLava.setBlockContent(w, x, y, z, newContent * BlockFFFluid.maximumContent / 16);
    return;
  }   
}
