package mbrx.ff;

import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class BlockSteam extends BlockGas {

  public BlockSteam(int id, Material material) {
    super(id, material);
  }

  /* Gas condensates to water */
  @Override
  protected void condenseFromAltitude(World w, int x, int y, int z, Chunk origChunk, int newContent) {
    // Discard 50% of the water that evaporates to reduce water inflation
    if(FysiksFun.rand.nextInt(2) != 1) return;
    
    /* High chance that we move the water straight down to where it should go to remove amount of liquid that is falling in the sky. */
    if (FysiksFun.rand.nextInt(8) != 0) {
      for (; y >= 1; y--) {
        int id = origChunk.getBlockID(x & 15, y - 1, z & 15);
        if(id == 0) continue;
        else if(Gases.isGas[id]) continue;
        else break;
      }
    }
    Fluids.stillWater.setBlockContent(w, x, y, z, newContent * BlockFluid.maximumContent / 16);
    return;
  }  
}
