package mbrx.ff;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

public class FFTinkerLiquidMetal extends BlockFluid {

  public FFTinkerLiquidMetal(Block superWrapper, int id, Material par2Material, int stillID, int movingID) {
    super(superWrapper, id, par2Material, stillID, movingID,"TinkerFluid");
  }

  public void onBlockAdded(World w, int x, int y, int z) {
    System.out.println("Tinker metal: "+w.getBlockMetadata(x, y, z));
    super.onBlockAdded(w, x, y, z);    
  }
}
