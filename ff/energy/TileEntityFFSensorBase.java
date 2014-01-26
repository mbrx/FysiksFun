package mbrx.ff.energy;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Counters;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;

/** Base class for all fluid/gas sensors. Needs to be a tileEntity since fluid/gas updates to not trigger block updates. */
public abstract class TileEntityFFSensorBase extends TileEntity {

  /** The currently active signal */
  public int signal;
  
  public TileEntityFFSensorBase() {
    super();
  }

  @Override
  public void writeToNBT(NBTTagCompound nbt) {
    super.writeToNBT(nbt);
    nbt.setInteger("signal", signal);
  }

  @Override
  public void readFromNBT(NBTTagCompound nbt) {
    super.readFromNBT(nbt);
    this.signal = nbt.getInteger("signal");
  }

  @Override
  public void updateEntity() {
    super.updateEntity();
    if (worldObj.isRemote) return;

    
    int newSignal = computeSignal();
    if(newSignal == signal) return;
    signal=newSignal;

    System.out.println("attempting to trigger a change...");
    Chunk c0 = ChunkCache.getChunk(worldObj,xCoord>>4,zCoord>>4, false);
    int selfId = c0.getBlockID(xCoord&15, yCoord, zCoord&15);
    System.out.println("selfid = "+selfId);
    if(Block.blocksList[selfId] == null) return;
    Block.blocksList[selfId].updateTick(worldObj, xCoord, yCoord, zCoord, FysiksFun.rand);
    for(int dir=0;dir<6;dir++) {
      int x=xCoord + Util.dirToDx(dir);
      int y=yCoord + Util.dirToDy(dir);
      int z=zCoord + Util.dirToDz(dir);
      Chunk c = ChunkCache.getChunk(worldObj,x>>4,z>>4, false);
      if(c == null) continue;
      int id = c.getBlockID(x&15, y, z&15);
      if(id == 0 || Block.blocksList[id] == null) continue;
      System.out.println("trigger tile change...");
      Block.blocksList[id].onNeighborTileChange(worldObj, x, y, z, xCoord, yCoord, zCoord);
      Block.blocksList[id].onNeighborBlockChange(worldObj, x, y, z, selfId);
      Block.blocksList[selfId].updateTick(worldObj, x,y,z, FysiksFun.rand);
    }
    System.out.println("signal = "+signal);
  }
  
  public abstract int computeSignal();

}
