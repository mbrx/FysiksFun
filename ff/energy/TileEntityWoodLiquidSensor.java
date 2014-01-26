package mbrx.ff.energy;

import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;

public class TileEntityWoodLiquidSensor extends TileEntityLiquidSensor {

  public TileEntityWoodLiquidSensor() {
    super();
  }

  @Override
  public int getPressureScale() {
    return 100; // effecitvely makes it binary: water/no-water
  }

  @Override
  public int getMaxSurvivablePressure() {
    return 8;
  }

  @Override
  public boolean getSurvivesLiquid(BlockFFFluid fluid) {
    if(fluid == Fluids.stillLava || fluid == Fluids.flowingLava) return false;
    else return true;
  }

}
