package mbrx.ff.energy;

public class TileEntityDiamondGasTurbine extends TileEntityGasTurbine {

  public TileEntityDiamondGasTurbine() {
    super();
  }

  @Override
  public double getFFEnergyPerM3() {
    return 2000.0;
  }

  @Override
  public int getMaxSurvivablePressure() {
    return 16;
  }

  @Override
  public double getFFMaxSendableEnergy() {
    return 1000.0;
  }

  @Override
  public double getMaxStoredEnergy() {
    return 4000.0;
  }

  @Override
  public double getFFEnergyLossPerTick() {
    return 2000.0 / 400.0;
  }

  @Override
  public double getPressureEfficiency() {
    return 2.0;
  }

  @Override
  public double getMinEnergyForSending() {
    return 100.0;
  }

}
