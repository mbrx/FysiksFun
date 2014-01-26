package mbrx.ff.energy;

import mbrx.ff.fluids.BlockFFGas;
import mbrx.ff.fluids.Gases;
import net.minecraft.world.chunk.Chunk;

public abstract class TileEntityGasTurbine extends TileEntityTurbineBase {
  
  public TileEntityGasTurbine() {
    super();
  }

  @Override
  public void doGenerateEnergy(Chunk c) {
    System.out.println("gas turbine, generate energy ");
    if(yCoord > 253 || yCoord < 2) return;
    
    int idBelow = c.getBlockID(xCoord&15, yCoord-1, zCoord&15);
    if(!Gases.isGas[idBelow]) return;
    BlockFFGas gas = Gases.asGas[idBelow];
    int contentBelow = gas.getBlockContent(c, xCoord, yCoord-1, zCoord);
    System.out.println("Content below: "+contentBelow);
    int idAbove = c.getBlockID(xCoord&15, yCoord+1, zCoord&15);
    
    int contentAbove=0;    
    if(idAbove == 0) contentAbove=0;
    else if(gas.blockID == idAbove) contentAbove=Gases.asGas[idAbove].getBlockContent(c, xCoord, yCoord+1, zCoord);
    else return;
    System.out.println("Content above: "+contentAbove+" id above: "+idAbove);
    int pressureDifference = contentBelow - contentAbove;
    if(pressureDifference <= 0) return;

    if(pressureDifference >= getMaxSurvivablePressure()) {
      worldObj.newExplosion(null, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, 2.0F, true, true);
      return;
    }

    contentBelow=contentBelow-1;
    contentAbove=contentAbove+1;
    ffEnergy += (1.0D+contentBelow*getPressureEfficiency())*getFFEnergyPerM3();
    System.out.println("new energy: "+ffEnergy);
    gas.setBlockContent(worldObj, c, xCoord, yCoord+1, zCoord, contentAbove);
    gas.setBlockContent(worldObj, c, xCoord, yCoord-1, zCoord, contentBelow);
  }

  public abstract double getFFEnergyPerM3();
  public abstract int getMaxSurvivablePressure();

}
