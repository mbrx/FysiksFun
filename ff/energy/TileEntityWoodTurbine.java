package mbrx.ff.energy;

import net.minecraft.nbt.NBTTagCompound;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;

public class TileEntityWoodTurbine extends TileEntityLiquidTurbine {

  public TileEntityWoodTurbine() {
    super();
  }

  private static final double         defaultFFMaxSendableEnergy = 100.F;
  private static final double         defaultMaxStoredEnergy     = 2000;
  private static final double         defaultFFEnergyPerTon      = 2000.F;
  private static final int            survivablePressureSteps = 5;
  

  
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
    return getFFEnergyPerTon() / 100.F;
  }
  @Override
  public  int getMaxSurvivablePressure() {
    return survivablePressureSteps * BlockFFFluid.pressurePerY;
  }

  /**
   * The efficiency with which output scales with higher pressures. 100% means
   * output energy scales linearly with pressure.
   */
  public  double getPressureEfficiency() {
    return 0.1;
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
