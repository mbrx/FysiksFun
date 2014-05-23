package mbrx.ff.fluids;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public interface FluidInteraction {
  public boolean canInteract(Block affectedBlock, Block incommingBlock);
  /**
   * Create the effect of interaction between the two liquids mixing in the
   * given cell. Returns the amount of incoming liquid should be LEFT after the
   * interaction (ie. any changes to the affected block must be handled by the interaction routine. 
   */
  public int liquidInteract(World w, int x, int y, int z, int incomingBlockID, int incommingAmount, int targetBlockID, int targetAmount);

}
