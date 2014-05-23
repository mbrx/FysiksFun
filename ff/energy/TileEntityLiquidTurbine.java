package mbrx.ff.energy;

import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkTempData;
import net.minecraft.world.chunk.Chunk;

public abstract class TileEntityLiquidTurbine extends TileEntityTurbineBase {

  public TileEntityLiquidTurbine() {
    super();
  }

  public void doGenerateEnergy(Chunk c) {
    int idAbove = c.getBlockID(xCoord & 15, yCoord + 1, zCoord & 15);
    if (idAbove != 0 && Fluids.isLiquid[idAbove]) {
      BlockFFFluid fluid = Fluids.asFluid[idAbove];
      ChunkTempData tempData = ChunkCache.getTempData(worldObj, xCoord >> 4, zCoord >> 4);
      /*
       * Make extra ticks of the TWO liquid blocks above, to make sure the
       * pressure is updated correctly.
       */
      if (fluid.isSameLiquid(c.getBlockID(xCoord & 15, yCoord + 2, zCoord & 15)))
        fluid.updateTickSafe(worldObj, c, tempData, xCoord, yCoord + 2, zCoord, FysiksFun.rand, null);
      fluid.updateTickSafe(worldObj, c, tempData, xCoord, yCoord + 1, zCoord, FysiksFun.rand, null);
      int contentAbove = fluid.getBlockContent(c, tempData, xCoord, yCoord + 1, zCoord);

      int idBelow = c.getBlockID(xCoord & 15, yCoord - 1, zCoord & 15);
      if (idBelow != 0 && !fluid.isSameLiquid(idBelow)) return;
      int contentBelow = idBelow == 0 ? 0 : fluid.getBlockContent(c, tempData, xCoord, yCoord - 1, zCoord);

      if (FysiksFun.rand.nextInt(100) == 0 && contentAbove - contentBelow > BlockFFFluid.maximumContent + getMaxSurvivablePressure()) {
        System.out.println("Turbine exploded due to overpressure");
        worldObj.newExplosion(null, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, 2.0F, true, true);
      }

      if(!canSurviveLiquid(idAbove)) {
        System.out.println("Turbine exploded due to bad liquid");
        worldObj.newExplosion(null, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, 2.0F, true, true);
      }
      
      double pressureMultiplier = 1.0;
      if (contentAbove > BlockFFFluid.maximumContent) {
        pressureMultiplier = 1.0 + getPressureEfficiency() * (contentAbove - BlockFFFluid.maximumContent) / (double) BlockFFFluid.pressurePerY;
        contentAbove = BlockFFFluid.maximumContent;
      }
      int amountToMove = Math.min(contentAbove, BlockFFFluid.maximumContent - contentBelow);
      int maxAccordingToEnergyBuffer = (int) ((getMaxStoredEnergy() - ffEnergy) / (getFFEnergyPerTon() * pressureMultiplier / BlockFFFluid.maximumContent));
      amountToMove = Math.min(amountToMove, maxAccordingToEnergyBuffer);
      System.out.println("Amount to move: " + amountToMove);

      if (amountToMove <= 0) return;
      contentBelow = Math.min(BlockFFFluid.maximumContent, contentBelow + amountToMove);
      contentAbove = Math.max(0, contentAbove - amountToMove);
      fluid.setBlockContent(worldObj, c, tempData, xCoord, yCoord + 1, zCoord, contentAbove, "Moved by Turbine", null);
      fluid.setBlockContent(worldObj, c, tempData, xCoord, yCoord - 1, zCoord, contentBelow, "Moved by Turbine", null);
      ffEnergy += (pressureMultiplier * amountToMove * getFFEnergyPerTon()) / BlockFFFluid.maximumContent;
      // System.out.println("Moved liquids, multiplier: " + pressureMultiplier +
      // " moved: " + amountToMove + " energyGain: "
      // + (pressureMultiplier * amountToMove * getFFEnergyPerTon()) /
      // BlockFFFluid.maximumContent);
    }
  }
  
  /*
   * Override these methods with new getters/setters for inherited turbines with
   * other properties
   */
  
  public abstract double getFFEnergyPerTon();

  public abstract int getMaxSurvivablePressure();

  public abstract boolean canSurviveLiquid(int id);
}
