package mbrx.ff.energy;

import buildcraft.api.power.IPowerEmitter;
import buildcraft.api.power.IPowerReceptor;
import buildcraft.api.power.PowerHandler;
import buildcraft.api.power.PowerHandler.PowerReceiver;
import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkTempData;
import mbrx.ff.util.Counters;
import mbrx.ff.util.Util;
import net.minecraft.block.BlockFluid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeDirection;

/** Base class of all types of turbines, handles storage of energy, saving NBT tags and transmission of energy. Requires specialization for energy generation and for parametrization of behaviour. */
public abstract class TileEntityTurbineBase extends TileEntity implements IPowerEmitter {

  /**
   * Amount of kinetic energy stored in turbine, when it drops too low the
   * turbine will consume some water from above it and spin up again depending
   * on the pressure.
   */
  public float                        ffEnergy;

  /* Fixed constants for behaviour of turbines */
  public static final double         defaultMinEnergyForSending = 500;
  public static final double         ffToBcEnergy               = 0.04F;
  public static final ForgeDirection sendableDirections[]       = { ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.DOWN, ForgeDirection.EAST };

  public TileEntityTurbineBase() {
    super();
  }

  @Override
  public void writeToNBT(NBTTagCompound nbt) {
    super.writeToNBT(nbt);
    nbt.setFloat("ffEnergy", ffEnergy);
  }

  @Override
  public void readFromNBT(NBTTagCompound nbt) {
    super.readFromNBT(nbt);
    this.ffEnergy = nbt.getFloat("ffEnergy");
  }

  @Override
  public void updateEntity() {
    super.updateEntity();
    if (worldObj.isRemote) return;

    Chunk c = ChunkCache.getChunk(worldObj, xCoord >> 4, zCoord >> 4, false);
    if (c == null) return;
    ffEnergy = (float) Math.max(0.0, ffEnergy - getFFEnergyLossPerTick());
    if (ffEnergy < getMaxStoredEnergy() && yCoord < 253 && yCoord > 0 && Counters.tick % 10 == 0) doGenerateEnergy(c);

    //System.out.println("Turbine energy: "+ffEnergy);
    if (ffEnergy > getMinEnergyForSending() && FysiksFun.hasBuildcraft) {
      for (int i = 0; i < sendableDirections.length; i++)
        attemptToSendBCEnergy(sendableDirections[i]);
    }
  }

  private void attemptToSendBCEnergy(ForgeDirection orientation) {
    int x1 = xCoord + orientation.offsetX;
    int y1 = yCoord + orientation.offsetY;
    int z1 = zCoord + orientation.offsetZ;
    if (y1 < 0 || y1 > 255) return;
    Chunk c1 = ChunkCache.getChunk(worldObj, x1 >> 4, z1 >> 4, false);
    if (c1 == null) return;
    TileEntity entity = c1.getChunkBlockTileEntity(x1 & 15, y1, z1 & 15);
    if (!isPoweredTile(entity, orientation)) return;
    PowerReceiver receptor = ((IPowerReceptor) entity).getPowerReceiver(orientation.getOpposite());
    double minEnergyToSend = receptor.getMinEnergyReceived();
    double maxEnergyToSend = receptor.getMaxEnergyReceived();
    maxEnergyToSend = Math.min(maxEnergyToSend, getFFMaxSendableEnergy() * ffToBcEnergy);
    // Too little energy, don't send anything
    if (ffEnergy * ffToBcEnergy < minEnergyToSend) return;
    float energySent = (float) Math.min(ffEnergy * ffToBcEnergy, maxEnergyToSend);
    energySent = receptor.receiveEnergy(PowerHandler.Type.ENGINE, energySent, orientation.getOpposite());
    ffEnergy -= energySent / ffToBcEnergy;
    //System.out.println("Sending energy: " + energySent);
  }

  public boolean isPoweredTile(TileEntity tile, ForgeDirection side) {
    if (tile instanceof IPowerReceptor) return ((IPowerReceptor) tile).getPowerReceiver(side.getOpposite()) != null;
    return false;
  }

  public boolean canEmitPowerFrom(ForgeDirection side) {
    System.out.println("Tile entity @" + Util.xyzString(xCoord, yCoord, zCoord) + " was asked if he can give power?");
    for (int i = 0; i < sendableDirections.length; i++)
      if (sendableDirections[i] == side) return true;
    return false;
  }
  
  /*
   * Override these methods with new getters/setters for inherited turbines with
   * other properties
   */

  public abstract void doGenerateEnergy(Chunk c);

  public abstract double getFFMaxSendableEnergy();

  public abstract double getMaxStoredEnergy();

  public abstract double getFFEnergyLossPerTick();

  public abstract double getMinEnergyForSending();
  /**
   * The efficiency with which output scales with higher pressures. 100% means
   * output energy scales linearly with pressure.
   */
  public abstract double getPressureEfficiency();

}
