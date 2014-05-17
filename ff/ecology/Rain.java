package mbrx.ff.ecology;

import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Counters;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

public class Rain {

  /** Precipation from rain on the chunk with center XZ */
  public static void doPrecipation(World w, int x, int z) {
    
    Chunk c = ChunkCache.getChunk(w, x>>4, z>>4, false);
    if(c == null) return;

    if (FysiksFun.settings.alwaysRaining || w.rainingStrength > 0.0) {
      int dx = (FysiksFun.rand.nextInt(117) + 7 * Counters.tick) % 16;
      int dz = (FysiksFun.rand.nextInt(187) + 11 * Counters.tick) % 16;
      BiomeGenBase biome = w.getBiomeGenForCoords(x + dx, z + dz);
      int rainChance = (int) (250.0 * FysiksFun.settings.waterRainRate * biome.rainfall * (FysiksFun.settings.alwaysRaining ? 1.0 : w.rainingStrength));

      //if (!FysiksFun.settings.rainInOceans && (biome == BiomeGenBase.ocean || biome == BiomeGenBase.frozenOcean)) {
      //  // do nothing
      //} else 
      if (biome.rainfall > 0.0 && FysiksFun.settings.waterRainRate > 0 && FysiksFun.rand.nextInt(18123) < rainChance) {
        for (int y2 = 255; y2 > 1; y2--) {
          int id = c.getBlockID(dx, y2, dz);
          if (id != 0) {
            // The following plants will drink the rain water
            /*if (id == Block.crops.blockID) break;
            if (id == Block.mushroomRed.blockID) break;
            if (id == Block.mushroomBrown.blockID) break;
            if (id == Block.melonStem.blockID) break;
            if (id == Block.pumpkinStem.blgockID) break;
            if (id == Block.tallGrass.blockID) break;
            if (id == Block.plantYellow.blockID) break;
            if (id == Block.plantRed.blockID) break;
            */

            int rainDelta = BlockFFFluid.maximumContent / 16;
            int rainAmount = rainDelta;
            if ((id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID)
                && Fluids.stillWater.getBlockContent(w, x + dx, y2, z + dz) > BlockFFFluid.maximumContent / 2) rainAmount += rainDelta;
            if (y2 >= 1 && c.getBlockID(dx, y2 - 1, dz) == Fluids.stillWater.blockID) rainAmount += rainDelta;
            if (y2 >= 2 && c.getBlockID(dx, y2 - 2, dz) == Fluids.stillWater.blockID) rainAmount += rainDelta;
            if (y2 >= 3 && c.getBlockID(dx, y2 - 3, dz) == Fluids.stillWater.blockID) rainAmount += rainDelta;

            try {
              BlockFFFluid.preventSetBlockLiquidFlowover = true;
              Fluids.flowingWater.setBlockContent(w, x + dx, y2 + 1, z + dz, rainAmount);
              Counters.rainCounter += rainAmount;
              //FysiksFun.scheduleBlockTick(w, Fluids.stillWater, x + dx, y2 + 1, z + dz, 10);
            } finally {
              BlockFFFluid.preventSetBlockLiquidFlowover = false;
            }
            break;
          }
        }
      }
    }
  }

}
