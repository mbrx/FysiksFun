package mbrx.ff.energy;

import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;

public class TileEntityDiamondLiquidSensor extends TileEntityLiquidSensor {

  public TileEntityDiamondLiquidSensor() {
    super();
  }

  @Override
  public int getPressureScale() {
    return 4;
  }

  @Override
  public int getMaxSurvivablePressure() {
    return 128;
  }

  @Override
  public boolean getSurvivesLiquid(BlockFFFluid fluid) {
    return true;
  }

}
