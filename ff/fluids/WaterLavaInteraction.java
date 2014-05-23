package mbrx.ff.fluids;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class WaterLavaInteraction implements FluidInteraction {

  public WaterLavaInteraction() {
  }

  @Override
  public boolean canInteract(Block affectedBlock, Block incommingBlock) {
    return (Fluids.stillLava.isSameLiquid(affectedBlock.blockID) && Fluids.stillWater.isSameLiquid(incommingBlock.blockID)) ||
        (Fluids.stillWater.isSameLiquid(affectedBlock.blockID) && Fluids.stillLava.isSameLiquid(incommingBlock.blockID));
  }

  @Override
  public int liquidInteract(World w, int x, int y, int z, int incomingBlockID, int incommingAmount, int targetBlockID, int targetAmount) {
    int lavaAmount = 0, waterAmount = 0;
    int reactionStepSize = BlockFFFluid.maximumContent / 8;
    if (Block.blocksList[incomingBlockID].blockMaterial == Material.lava) {
      lavaAmount = incommingAmount;
      waterAmount = targetAmount;
    } else if (Block.blocksList[targetBlockID].blockMaterial == Material.lava) {
      lavaAmount = targetAmount;
      waterAmount = incommingAmount;
    }
    int nReactions = Math.min(lavaAmount, waterAmount) / reactionStepSize + 1;
    nReactions = Math.min(1, nReactions);

    lavaAmount = Math.max(0, lavaAmount - nReactions * reactionStepSize);
    waterAmount = Math.max(0, waterAmount - nReactions * reactionStepSize);
    int steamAmount = nReactions;
    boolean generated = false;
    /*
     * Check if the interaction happens in air/water/gas, if so move the result
     * as far down as possible
     */
    int targetY = y;
    Chunk chunk0 = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    for (; targetY > 0; targetY--) {
      int id = chunk0.getBlockID(x & 15, targetY - 1, z & 15);
      if (id == 0) continue;
      if (Fluids.stillWater.isSameLiquid(id)) continue;
      if (Gases.isGas[id]) continue;
      break;
    }

    boolean hasObsidianNeighbour = false;
    for (int dx = -1; dx <= 1; dx++)
      for (int dy = -1; dy <= 1; dy++)
        for (int dz = -1; dz <= 1; dz++) {
          if (targetY + dy > 0 && targetY + dy < 255) {
            Chunk chunk1 = ChunkCache.getChunk(w, (x + dx) >> 4, (z + dz) >> 4, false);
            if (chunk1 != null && chunk1.getBlockID((x + dx) & 15, targetY + dy, (z + dz) & 15) == Block.obsidian.blockID) hasObsidianNeighbour = true;
          }
        }
    if (FysiksFun.rand.nextInt(10) < nReactions) w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.fizz", 1.0F, 1.0F);

    for (int i = 0; i < nReactions; i++) {
      int r = w.rand.nextInt(200);
      int newId = 0;
      if (r == 0 || (r <= 3 && hasObsidianNeighbour)) newId = Block.obsidian.blockID;
      else if (r <= 10) newId = Block.stone.blockID;
      else if (r <= 20) newId = Block.cobblestone.blockID;

      if (newId != 0) {
        FysiksFun.setBlockWithMetadataAndPriority(w, x, targetY, z, newId, 0, 0);
        generated = true;
        break;
      }
    }

    /*
     * w.playSoundEffect((double) ((float) x + 0.5F), (double) ((float) y +
     * 0.5F), (double) ((float) z + 0.5F), "random.fizz", 0.5F, 2.6F +
     * (w.rand.nextFloat() - w.rand.nextFloat()) * 0.8F); for (int i = 0; i <
     * nReactions + waterAmount * 2; i++) { w.spawnParticle("largesmoke",
     * (double) x + Math.random(), (double) y + 1.2D, (double) z +
     * Math.random(), 0.0D, 0.0D, 0.0D); }
     */

    for (int dist = 1; dist < 2 && steamAmount > 0; dist++) {
      for (int dir0 = 4; dir0 < 6 + 4 && steamAmount > 0; dir0++) {
        int dir = dir0 % 6;
        int x1 = x + Util.dirToDx(dir) * dist;
        int y1 = y + Util.dirToDy(dir) * dist;
        int z1 = z + Util.dirToDz(dir) * dist;
        int id = w.getBlockId(x1, y1, z1);
        if (id == 0 || id == Gases.steam.blockID) {
          int origAmount = id == 0 ? 0 : Gases.steam.getBlockContent(w, x1, y1, z1);
          int amount = origAmount + steamAmount;
          if (amount > 16) {
            steamAmount = amount - 16;
            amount = 16;
          } else steamAmount = 0;
          Gases.steam.setBlockContent(w, x1, y1, z1, amount);
          if (steamAmount <= 0) {
            dist = 32;
            break;
          }
        }
      }
    }
    if (Block.blocksList[incomingBlockID].blockMaterial == Material.lava) {
      if (!generated) Fluids.stillWater.setBlockContent(w, x, y, z, waterAmount);
      return lavaAmount;
    } else {
      if (!generated) Fluids.stillLava.setBlockContent(w, x, y, z, lavaAmount);
      return waterAmount;
    }
  }

}
