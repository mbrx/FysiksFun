package mbrx.ff.energy;

import net.minecraft.block.Block;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeDirection;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;

public class TileEntityGoldTurbine extends TileEntityLiquidTurbine {

  public TileEntityGoldTurbine() {
    super();
  }

  private static final double         defaultFFMaxSendableEnergy = 500.F;
  private static final double         defaultMaxStoredEnergy     = 5000;
  private static final double         defaultFFEnergyPerTon      = 2000.F;
  private static final int            survivablePressureSteps = 20;
  
  @Override
  public  double getFFMaxSendableEnergy() {
    return defaultFFMaxSendableEnergy;
  }
  @Override
  public  double getMaxStoredEnergy() {
    return defaultMaxStoredEnergy;
  }
  @Override
  public  double getFFEnergyPerTon() {
    return defaultFFEnergyPerTon;
  }
  @Override
  public  double getFFEnergyLossPerTick() {
    return getFFEnergyPerTon() / 600.F;
  }
  @Override
  public  int getMaxSurvivablePressure() {
    return survivablePressureSteps * BlockFFFluid.pressurePerY;
  }

  public  void doGenerateEnergy(Chunk c) {
    if(worldObj.isBlockIndirectlyGettingPowered(xCoord,yCoord,zCoord)) {
      return;
    }
    super.doGenerateEnergy(c);
  }
  
  /**
   * The efficiency with which output scales with higher pressures. 100% means
   * output energy scales linearly with pressure.
   */
  public  double getPressureEfficiency() {
    return 0.8;
  }
  @Override
  public boolean canSurviveLiquid(int id) {
    if(Fluids.stillLava.isSameLiquid(id)) return false;
    else return true;
  }
  @Override
  public double getMinEnergyForSending() {
    return 100.0;
  }
  
}
