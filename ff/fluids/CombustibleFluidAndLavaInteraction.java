package mbrx.ff.fluids;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public class CombustibleFluidAndLavaInteraction implements FluidInteraction {

  public CombustibleFluidAndLavaInteraction() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public boolean canInteract(Block targetBlock, Block incomingBlock) {
    int targetID = targetBlock.blockID;
    int incomingID = incomingBlock.blockID;
    return ((Fluids.isLiquid[incomingID] && Fluids.asFluid[incomingID].explodeStrength > 0 && Fluids.stillLava.isSameLiquid(targetID)))
        || ((Fluids.isLiquid[targetID] && Fluids.asFluid[targetID].explodeStrength > 0 && Fluids.stillLava.isSameLiquid(incomingID)));
  }

  @Override
  public int liquidInteract(World w, int x, int y, int z, int incomingBlockID, int incommingAmount, int targetBlockID, int targetAmount) {
    boolean incommingIsLava = Fluids.stillLava.isSameLiquid(incomingBlockID);
    int combustibleAmount = incommingIsLava ? targetAmount : incommingAmount;
    int lavaAmount = incommingIsLava ? incommingAmount : targetAmount;

    /* We always keep the full amount of lava, and eliminate the full block of fuel */
    if (incommingIsLava) Fluids.asFluid[targetBlockID].setBlockContent(w, x, y, z, 0);
    float explodeStrength;
    if (incommingIsLava) explodeStrength = Fluids.asFluid[targetBlockID].explodeStrength;
    else explodeStrength = Fluids.asFluid[incomingBlockID].explodeStrength;    
    float radius = (float) ((explodeStrength * combustibleAmount) / (float) BlockFFFluid.maximumContent);
    w.newExplosion(null, x, y, z, radius, true, true);
    w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.explode", radius / 4.f, 1.0f);

    return incommingIsLava ? incommingAmount : 0;
  }

}
