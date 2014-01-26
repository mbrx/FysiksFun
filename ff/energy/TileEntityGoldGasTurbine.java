package mbrx.ff.energy;

import net.minecraft.world.chunk.Chunk;

public class TileEntityGoldGasTurbine extends TileEntityGasTurbine {

  public TileEntityGoldGasTurbine() {
    super();
  }

  public  void doGenerateEnergy(Chunk c) {
    if(worldObj.isBlockIndirectlyGettingPowered(xCoord,yCoord,zCoord)) {
      return;
    }
    super.doGenerateEnergy(c);
  }
  @Override
  public double getFFEnergyPerM3() {
    return 2400.0;
  }

  @Override
  public int getMaxSurvivablePressure() {
    return 12;
  }

  @Override
  public double getFFMaxSendableEnergy() {
    return 500.0;
  }

  @Override
  public double getMaxStoredEnergy() {
    return 5000.0;
  }

  @Override
  public double getFFEnergyLossPerTick() {
    return 2000.0 / 2000.0;
  }

  @Override
  public double getPressureEfficiency() {
    return 2.0;
  }

  @Override
  public double getMinEnergyForSending() {
    return 500.0;
  }

}
