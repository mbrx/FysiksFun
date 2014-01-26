package mbrx.ff.energy;

import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;

public class TileEntityGoldLiquidSensor extends TileEntityLiquidSensor {

  public TileEntityGoldLiquidSensor() {
    super();
  }

  @Override
  public int getPressureScale() {
    return 2;
  }

  @Override
  public int getMaxSurvivablePressure() {
    return 32;
  }

  @Override
  public boolean getSurvivesLiquid(BlockFFFluid fluid) {
    if(fluid == Fluids.stillLava || fluid == Fluids.flowingLava) return false;
    else return true;
  }

}
