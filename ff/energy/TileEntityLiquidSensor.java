package mbrx.ff.energy;

import net.minecraft.world.chunk.Chunk;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Util;

public abstract class TileEntityLiquidSensor extends TileEntityFFSensorBase {

  public TileEntityLiquidSensor() {
    super();
  }

  @Override
  public int computeSignal() {
    int newSignal = 0;
    int baseSignalValue = 1;
    int pressureScale = getPressureScale();;

    for (int dir = 0; dir < 6; dir++) {
      int x = xCoord + Util.dirToDx(dir);
      int y = yCoord + Util.dirToDy(dir);
      int z = zCoord + Util.dirToDz(dir);
      if (y < 0 || y > 255) continue;
      Chunk c = ChunkCache.getChunk(worldObj, x >> 4, z >> 4, false);
      if (c == null) continue;
      int id = c.getBlockID(x & 15, y, z & 15);
      if (!Fluids.isLiquid[id]) continue;
      int content = Fluids.asFluid[id].getBlockContent(worldObj, x, y, z);
      if(content < BlockFFFluid.maximumContent/16) continue;
      int signalHere = baseSignalValue;
      int scale = pressureScale * (BlockFFFluid.pressurePerY-BlockFFFluid.pressureLossPerStep);
      if (content > BlockFFFluid.maximumContent)
        signalHere = baseSignalValue + (content-BlockFFFluid.maximumContent + scale/2)/scale;
      if (signalHere > 15) signalHere = 15;
      if (signalHere > newSignal) newSignal = signalHere;
    }
    //System.out.println("new signal: " + newSignal);
    return newSignal;
  }

  public abstract int getPressureScale();
  public abstract int getMaxSurvivablePressure();  
  public abstract boolean getSurvivesLiquid(BlockFFFluid fluid);
  
}
