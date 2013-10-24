package mbrx.ff;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

public class Rain {
  
  /** Precipation from rain on the chunk with center XZ */
  public static void doPrecipation(World w, int x, int z) {

    Chunk c = w.getChunkFromChunkCoords(x>>4, z>>4);
    
    if (FysiksFun.settings.alwaysRaining || w.rainingStrength > 0.0) {
      int dx = (FysiksFun.rand.nextInt(117) + 7 * Counters.tick) % 16;
      int dz = (FysiksFun.rand.nextInt(187) + 11 * Counters.tick) % 16;
      BiomeGenBase biome = w.getBiomeGenForCoords(x + dx, z + dz);
      int rainChance = (int) (250.0 / (0.001 + FysiksFun.settings.waterRainRate * biome.rainfall * (FysiksFun.settings.alwaysRaining ? 1.0 : w.rainingStrength)));
      if(rainChance<1) rainChance=1;
      
      if (!FysiksFun.settings.rainInOceans && (biome == BiomeGenBase.ocean || biome == BiomeGenBase.frozenOcean)) {
        // do nothing
      } else if (biome.rainfall > 0.0 && FysiksFun.settings.waterRainRate>0 && FysiksFun.rand.nextInt((int) rainChance) == 0) {
        for (int y2 = 256; y2 > 1; y2--) {
          int id = c.getBlockID(dx, y2, dz);
          if (id != 0) {
            // The following plants will drink the rain water
            /*if (id == Block.crops.blockID) break;
            if (id == Block.mushroomRed.blockID) break;
            if (id == Block.mushroomBrown.blockID) break;
            if (id == Block.melonStem.blockID) break;
            if (id == Block.pumpkinStem.blockID) break;
            if (id == Block.tallGrass.blockID) break;
            if (id == Block.plantYellow.blockID) break;
            if (id == Block.plantRed.blockID) break;
            */
            
            int rainAmount=1;
            if ((id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) && 
                Fluids.stillWater.getBlockContent(w, x+dx, y2, z+dz) > 4) rainAmount++;
            if (y2 >= 1 && c.getBlockID(dx, y2-1, dz) == Fluids.stillWater.blockID) rainAmount++;
            if (y2 >= 2 && c.getBlockID(dx, y2-2, dz) == Fluids.stillWater.blockID) rainAmount++;
            if (y2 >= 3 && c.getBlockID(dx, y2-3, dz) == Fluids.stillWater.blockID) rainAmount++;
            
            try {
              BlockFluid.preventSetBlockLiquidFlowover = true;
              Fluids.flowingWater.setBlockContent(w, x + dx, y2 + 1, z + dz, rainAmount);
              Counters.rainCounter+= rainAmount;
              FysiksFun.scheduleBlockTick(w, Fluids.stillWater, x + dx, y2 + 1, z + dz, 10);
            } finally {
              BlockFluid.preventSetBlockLiquidFlowover = false;
            }
            break;
          }
        }
      }
    }
  }

}

