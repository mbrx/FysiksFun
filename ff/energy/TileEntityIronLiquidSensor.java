package mbrx.ff.energy;

import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;

public class TileEntityIronLiquidSensor extends TileEntityLiquidSensor {

  public TileEntityIronLiquidSensor() {
    super();
  }

  @Override
  public int getPressureScale() {
    return 1;
  }

  @Override
  public int getMaxSurvivablePressure() {
    return 16;
  }

  @Override
  public boolean getSurvivesLiquid(BlockFFFluid fluid) {
    if(fluid == Fluids.stillLava || fluid == Fluids.flowingLava) return false;
    else return true;
  }

}
