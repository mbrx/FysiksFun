package mbrx.ff;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class Evaporation {
  
  public static int evaporationStepsize = BlockFluid.maximumContent/16;
  
  /**
   * Evaporation removes water blocks from world surface from the chunk with
   * chunk center XZ
   */
  public static void doEvaporation(World w, int x, int z) {    
    
    if (FysiksFun.settings.undergroundWater) {
      doUndergroundEvaporation(w, x, z);
      //doHumidification(w, x, z);
    }
    doIndirectEvaporation(w, x, z);
    doSunlightEvaporation(w, x, z);

    doDirectHeatEvaporation(w, x, z);
  }

  /** Looks for heat sources (fire, lava) and evaporates any nearby water */
  private static void doDirectHeatEvaporation(World w, int x, int z) {
    if (FysiksFun.rand.nextInt(4) != 0) return;

    int y = (int) FysiksFun.rand.nextInt(160) + 2;
    Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
    for (int dx = 0; dx < 16; dx++) {
      for (int dz = 0; dz < 16; dz++) {
        int id = c.getBlockID(dx, y, dz);
        int heat=0;
        if(id == Block.fire.blockID) heat=1;
        else if(id == Fluids.stillLava.blockID || id == Fluids.flowingLava.blockID) { 
          heat=(Math.min(BlockFluid.maximumContent, Fluids.stillLava.getBlockContent(w, x+dx, y, z+dz))*4)/BlockFluid.maximumContent+1;
        }
        for (int range = 0; range < heat; range++) {
          for (int tries = 0; tries < 4; tries++) {
            int ddx = FysiksFun.rand.nextInt(range * 2 + 1) - range;
            int ddz = FysiksFun.rand.nextInt(range * 2 + 1) - range;
            int x2 = x + dx + ddx;
            int z2 = z + dz + ddz;
            Chunk c2 = c;
            if (x2 >> 4 != x >> 4 || z2 >> 4 != z >> 4) {
              if (!w.blockExists(x2, y, z2)) continue;
              else c2 = w.getChunkFromChunkCoords(x2 >> 4, z2 >> 4);
            }
            for (int ddy = 3; ddy >= -1; ddy--) {
              int y2 = y + ddy;
              int id2 = c2.getBlockID(x2 & 15, y2, z2 & 15);
              if (id2 == Fluids.stillWater.blockID || id2 == Fluids.flowingWater.blockID) {                
                Fluids.stillWater.evaporate(w, c2, x2, y2, z2, evaporationStepsize);
                
                // Heuristic to speed up: we produce 16 times less steam here, but make steam produce 16 times more water
                // when reaching the troposphere. This will unfortunately make it possible to "create" rain by boiling water.
                /*if(FysiksFun.rand.nextInt(16) == 0)
                  Fluids.stillWater.evaporate(w, c2, x2, y2, z2, evaporationStepsize);
                else 
                  Fluids.stillWater.consume(w, c2, x2, y2, z2, evaporationStepsize);
                */
                
                Counters.heatEvaporation++;
                heat--;
                tries = 100;
                break;
              }
            }
          }
        }
        
        
        /*if (id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {
          int amount = Fluids.stillWater.getBlockContent(w, x + dx, y, z + dz);
          Fluids.stillWater.setBlockContent(w, x + dx, y, z + dz, amount > 0 ? amount - 1 : 0);
          Counters.evaporationCounter++;
          // Only evaporate atmost one level per chunk tick
          return;
        }*/
        
      }
    }    
  }

  /**
   * Evaporates water liquid at very low world altitudes (y < 25) due to subterreanean
   * heat
   */
  private static void doUndergroundEvaporation(World w, int x, int z) {
    //if (FysiksFun.rand.nextInt(71) != 0) return;

    int y = (int) Math.round(Math.sqrt(1.0 * FysiksFun.rand.nextInt(24 * 24))) + 1;
    Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
    int tries;
    for (tries = 0; tries < 16; tries++) {
      int dx = FysiksFun.rand.nextInt(16);
      int dz = FysiksFun.rand.nextInt(16);
      int id = c.getBlockID(dx, y, dz);      
      if (id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {
        int amount = Fluids.stillWater.getBlockContent(w, x + dx, y, z + dz);
        if(amount > 0) {          
          // TODO - setting that either uses consume or evaporate - if we want steam or not
          //Fluids.stillWater.consume(w, c, x + dx, y, z + dz, evaporationStepsize);
          Fluids.stillWater.evaporate(w, c, x + dx, y, z + dz, evaporationStepsize);
          Counters.worldHeatEvaporation++;
        }
        return;
      }
    }

  }

  /**
   * Evaporates water liquid at a slow rate everywhere in a world
   */
  private static void doIndirectEvaporation(World w, int x, int z) {
    if (FysiksFun.rand.nextInt(921) != 0) return;
    Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);

    for (int tries = 0; tries < 10; tries++) {
      int y = 30 + FysiksFun.rand.nextInt(128);
      int dx = FysiksFun.rand.nextInt(16);
      int dz = FysiksFun.rand.nextInt(16);
      int id = c.getBlockID(dx, y, dz);
      if (id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {
        int id2 = c.getBlockID(dx, y - 1, dz);
        if (id2 == Block.glass.blockID || id2 == Fluids.stillWater.blockID || id2 == Fluids.flowingWater.blockID) continue;
       
        Fluids.stillWater.evaporate(w, c, x+dx, y, z+dz, evaporationStepsize);                       
        /*int amount = Fluids.stillWater.getBlockContent(w, x + dx, y, z + dz);
        Fluids.stillWater.setBlockContent(w, x + dx, y, z + dz, amount > 0 ? amount - 1 : 0);*/
        
        Counters.indirectEvaporation++;
        // Do atmost one evaporation event per chunk... thus oceans and rivers wont evaporate too much from this step
        break;
      }
    }
  }

  /**
   * Creates water at altitudes y=24 -> y=58 in air cells that are adjacent to a
   * dirt, gravel or cobblestone cell
   */
  private static void doHumidification(World w, int x, int z) {
    // Net effect of this is that water is created!
    
    // DEBUG
    // if (FysiksFun.rand.nextInt(31) != 0) return;

    // TODO - let the biome affect this?

    Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);

    for (int tries = 0; tries < 3; tries++) {
      int dx = FysiksFun.rand.nextInt(16);
      int dz = FysiksFun.rand.nextInt(16);
      int y1 = 24 + FysiksFun.rand.nextInt(17) + FysiksFun.rand.nextInt(17);
      int x1 = x+dx;
      int z1 = z+dz;
      
      int id = c.getBlockID(dx, y1, dz);
      if (id == 0) {
        for (int dir = 0; dir < 6; dir++) {
          int dx2 = Util.dirToDx(dir);
          int dy2 = Util.dirToDy(dir);
          int dz2 = Util.dirToDz(dir);
          Chunk chunk1 = ChunkCache.getChunk(w, (x1+dx2)>>4, (z1+dz2)>>4, false);
          if(chunk1 == null) continue;
          //int id2 = w.getBlockId(x1 + dx2, y1 + dy2, z1 + dz2);
          int id2 = chunk1.getBlockID((x1+dx2)&15, y1+dy2, (z1+dz2)&15);
          if (id2 == Block.dirt.blockID || id2 == Block.gravel.blockID || id2 == Block.cobblestone.blockID) {
            Fluids.flowingWater.setBlockContent(w, x1, y1, z1, evaporationStepsize);
            Counters.humidification++;
            break;
          }
        }
      }
    }
  }

  /** Evaporates water liquids that are in direct sunlight */
  public static void doSunlightEvaporation(World w, int x, int z) {

    IChunkProvider chunkProvider = w.getChunkProvider();
    Chunk chunk = chunkProvider.provideChunk(x>>4, z>>4);
    
    // Gives the current time of the day, so we only evaporate during daylight
    long timeNow = w.getWorldInfo().getWorldTime();
    if (w.rainingStrength == 0.0 && FysiksFun.rand.nextInt(100) == 0) {
      double sunIntensity = Math.cos(((double) (timeNow - 5000)) / 6000.0 * 1.57);
      if (sunIntensity < 0.0) return;
      for (int i = 0; i < 20; i++) {
        int dx = (FysiksFun.rand.nextInt(117) + 13 * Counters.tick) % 16;
        int dz = (FysiksFun.rand.nextInt(187) + 17 * Counters.tick) % 16;
        BiomeGenBase biome = w.getBiomeGenForCoords(x + dx, z + dz);
        if (i < biome.getFloatTemperature() * biome.getFloatTemperature() * FysiksFun.settings.waterEvaporationRate * 0.5) {
          for (int y2 = 255; y2 > 1; y2--) {
            int id = w.getBlockId(x + dx, y2, z + dz);
            if (id != 0) {
              if (id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {
                try {
                  BlockFluid.preventSetBlockLiquidFlowover = true;
                  int waterAmount = Fluids.flowingWater.getBlockContent(w, x + dx, y2, z + dz);
                  // Tweak to make large bodies of water not evaporate as quick - should promote larger pools of liquids rather than small pools                  
                  int idBelow = w.getBlockId(x + dx, y2-1, z + dz);
                  if((waterAmount >= BlockFluid.maximumContent/2 || idBelow == Fluids.stillWater.blockID || idBelow == Fluids.flowingWater.blockID) && FysiksFun.rand.nextInt(5) != 0) continue;
                  Fluids.flowingWater.evaporate(w, chunk, x+dx, y2, z+dz, evaporationStepsize);
                  Counters.directEvaporation++;
                } finally {
                  BlockFluid.preventSetBlockLiquidFlowover = false;
                }
              }
              break;
            }
          }
        }
      }
    }
  }

}
