package mbrx.ff.fluids;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.SoundQueue;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class CombustibleFluidAndLavaInteraction implements FluidInteraction {

  public CombustibleFluidAndLavaInteraction() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public boolean canInteract(Block targetBlock, Block incomingBlock) {
    int targetID = targetBlock.blockID;
    int incomingID = incomingBlock.blockID;
    return ((Fluids.isLiquid[incomingID] && Fluids.asFluid[incomingID].explodeStrength > 0 && Fluids.stillLava.isSameLiquid(targetID)))
        || ((Fluids.isLiquid[targetID] && Fluids.asFluid[targetID].explodeStrength > 0 && Fluids.stillLava.isSameLiquid(incomingID)));
  }

  @Override
  public int liquidInteract(World w, int x, int y, int z, int incomingBlockID, int incommingAmount, int targetBlockID, int targetAmount) {
    boolean incommingIsLava = Fluids.stillLava.isSameLiquid(incomingBlockID);
    int combustibleAmount = incommingIsLava ? targetAmount : incommingAmount;
    int lavaAmount = incommingIsLava ? incommingAmount : targetAmount;

    /* Count how many air blocks we have around the target block. If there are none, then we cannot burn or explode */
    int foundAir = 0;
    for (int dx = -1; dx <= 1; dx++)
      for (int dy = -1; dy <= 1; dy++)
        for (int dz = -1; dz <= 1; dz++) {
          Chunk c = ChunkCache.getChunk(w, (x + dx) >> 4, (z + dz) >> 4, false);
          int id = c.getBlockID((x + dx) & 15, (y + dy), (z + dz) & 15);
          if (id == 0) foundAir++;
        }
    // If there is no air don't explode
    if (foundAir == 0) return incommingAmount;

    /* We always keep the full amount of lava, and eliminate the full block of fuel */
    if (incommingIsLava) Fluids.asFluid[targetBlockID].setBlockContent(w, x, y, z, 0);

    Fluids.asFluid[targetBlockID].setBlockContent(w, x, y, z, 0);
    float explodeStrength;
    if (incommingIsLava) explodeStrength = Fluids.asFluid[targetBlockID].explodeStrength;
    else explodeStrength = Fluids.asFluid[incomingBlockID].explodeStrength;
    float radius = (float) ((explodeStrength * combustibleAmount) / (float) BlockFFFluid.maximumContent);
    synchronized (FysiksFun.vanillaMutex) {
      w.newExplosion(null, x, y, z, radius, true, true);
      SoundQueue.queueSound(w, x + 0.5, y + 0.5, z + 0.5, "random.explode", radius / 4.f, 1.0f);
    }
    return incommingIsLava ? incommingAmount : 0;
  }

}
