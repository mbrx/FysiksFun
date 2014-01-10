package mbrx.ff;

import net.minecraft.block.BlockStone;
import net.minecraft.world.World;

/** Overrides the placement behaviour so that stone blocks cannot be placed in the world by a player. */ 
public class BlockFFStone extends BlockStone {

  public BlockFFStone(int par1) {
    super(par1);
  }

  public boolean canPlaceBlockAt(World par1World, int x, int y, int z) {
    return false;
  }

}
