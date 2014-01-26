package mbrx.ff.energy;

public class TileEntityIronGasTurbine extends TileEntityGasTurbine {

  public TileEntityIronGasTurbine() {
    super();
  }

  @Override
  public double getFFEnergyPerM3() {
    return 2000.0;
  }

  @Override
  public int getMaxSurvivablePressure() {
    return 4;
  }

  @Override
  public double getFFMaxSendableEnergy() {
    return 250.0;
  }

  @Override
  public double getMaxStoredEnergy() {
    return 2000.0;
  }

  @Override
  public double getFFEnergyLossPerTick() {
    return 2000.0 / 200.0; // Almost as bad as wood liquid turbines
  }

  @Override
  public double getPressureEfficiency() {
    return 0.5;
  }

  @Override
  public double getMinEnergyForSending() {
    return 100.0;
  }

  
}
