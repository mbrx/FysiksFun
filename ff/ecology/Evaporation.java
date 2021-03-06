package mbrx.ff.ecology;

import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkTempData;
import mbrx.ff.util.Counters;
import mbrx.ff.util.Settings;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class Evaporation {

  public static int evaporationStepsize = BlockFFFluid.maximumContent / 16;

  /**
   * Evaporation removes water blocks from world surface from the chunk with
   * chunk center XZ
   */
  public static void doEvaporation(World w, int x, int z) {

    if (FysiksFun.settings.undergroundWater) {
      doUndergroundEvaporation(w, x, z);
      doHumidification(w, x, z);
    }
    doIndirectEvaporation(w, x, z);
    doSunlightEvaporation(w, x, z);
    doDirectHeatEvaporation(w, x, z);
    doUndergroundSteamToWater(w, x, z);
  }

  private static void doUndergroundSteamToWater(World w, int x, int z) {
    if (FysiksFun.rand.nextInt(51) != 0) return;

    int y = (int) FysiksFun.rand.nextInt(64) + 16;
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    if (c == null) return;

    for (int dx = 0; dx < 16; dx++) {
      for (int dz = 0; dz < 16; dz++) {
        int id = c.getBlockID(dx, y, dz);
        int x1 = x + dx;
        int y1 = y;
        int z1 = z + dz;
        if (id == Gases.steam.blockID) {
          int amount = Gases.steam.getBlockContent(c, x1, y1, z1);
          amount = (BlockFFFluid.maximumContent * amount / 16);
          if(FysiksFun.rand.nextInt(2) == 0)
            Fluids.flowingWater.setBlockContent(w, x1, y1, z1, amount);
          else 
            // Sometimes the steam just dries up... so we get an equilibrium and don't fill too much water into the underground
            Fluids.flowingWater.setBlockContent(w, x1, y1, z1, 0);
          
        }
      }
    }
  }

  /** Looks for heat sources (fire, lava) and evaporates any nearby water */
  private static void doDirectHeatEvaporation(World w, int x, int z) {
    // if (FysiksFun.rand.nextInt(2) != 0) return;

    int y = (int) FysiksFun.rand.nextInt(160) + 2;
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    if (c == null) return;

    for (int dx = 0; dx < 16; dx++) {
      for (int dz = 0; dz < 16; dz++) {
        int id = c.getBlockID(dx, y, dz);
        int heat = 0;
        boolean isDirectFire = false;
        boolean isLavaHeat = false;
        int lavaContent = 0;
        if (id == Block.fire.blockID) {
          isDirectFire = true;
          heat = 1;
        } else if (id == Fluids.stillLava.blockID || id == Fluids.flowingLava.blockID) {
          isLavaHeat = true;
          lavaContent = Math.min(BlockFFFluid.maximumContent, Fluids.stillLava.getBlockContent(w, x + dx, y, z + dz));
          heat = (lavaContent * 3) / BlockFFFluid.maximumContent + 1;
        }
        // Effectively not called when heat=0 (ie. nothing that is heating)
        for (int range = 0; range < heat; range++) {
          for (int tries = 0; tries < 20; tries++) {
            int ddx = FysiksFun.rand.nextInt(range * 2 + 1) - range;
            int ddz = FysiksFun.rand.nextInt(range * 2 + 1) - range;
            int x2 = x + dx + ddx;
            int z2 = z + dz + ddz;
            Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
            if (c2 == null) continue;
            for (int ddy = 3; ddy >= -1; ddy--) {
              int y2 = y + ddy;
              int id2 = c2.getBlockID(x2 & 15, y2, z2 & 15);
              if (id2 == Fluids.stillWater.blockID || id2 == Fluids.flowingWater.blockID) {
                // System.out.println("Direct heat evaportation @"+Util.xyzString(x2,
                // y2, z2)+" evaporate: "+evaporationStepsize);
                Fluids.stillWater.evaporate(w, c2, x2, y2, z2, evaporationStepsize, true);

                Counters.heatEvaporation++;
                heat--;
                tries = 100;

                /*
                 * Consume some of the fire/lava that made the water evaporate
                 * in the first place
                 */
                if (isLavaHeat) {
                  lavaContent = Math.max(0, lavaContent - evaporationStepsize / 4);
                  Fluids.stillLava.setBlockContent(w, x + dx, y, z + dz, lavaContent);
                  // System.out.println("New lava content @"+Util.xyzString(x+dx,y,z+dz)+" is "+lavaContent);
                } else if (isDirectFire) {
                  int burnedId = c.getBlockID(dx, y - 1, dz);
                  if (burnedId == Block.grass.blockID) {
                    FysiksFun.setBlockWithMetadataAndPriority(w, x + dx, y - 1, z + dz, Block.dirt.blockID, 0, 0);
                  } else {
                    // Remove whatever it is that is burning...
                    // Ignore this for now, vanilla MC does consume most
                    // burnable things, and burning fluids are already handled
                    // by the burning routine
                    // if (FysiksFun.rand.nextInt(16) == 7)
                    // FysiksFun.setBlockWithMetadataAndPriority(w, x + dx, y -
                    // 1, z + dz, 0, 0, 0);
                  }
                }
                break;
              }
            }
          }
        }

        /*
         * if (id == Fluids.stillWater.blockID || id ==
         * Fluids.flowingWater.blockID) { int amount =
         * Fluids.stillWater.getBlockContent(w, x + dx, y, z + dz);
         * Fluids.stillWater.setBlockContent(w, x + dx, y, z + dz, amount > 0 ?
         * amount - 1 : 0); Counters.evaporationCounter++; // Only evaporate
         * atmost one level per chunk tick return; }
         */

      }
    }
  }

  /**
   * Evaporates water liquid at very low world altitudes (y < 25) due to
   * subterreanean heat
   */
  private static void doUndergroundEvaporation(World w, int x, int z) {
    // if (FysiksFun.rand.nextInt(71) != 0) return;

    int y = 1 + FysiksFun.rand.nextInt(24); 
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    if (c == null) return;
    ChunkTempData tempData = ChunkCache.getTempData(w, x>>4, z>>4);
    
    int tries;
    for (tries = 0; tries < 8; tries++) {
      int dx = FysiksFun.rand.nextInt(16);
      int dz = FysiksFun.rand.nextInt(16);
      //if (c.getBiomeGenForWorldCoords(dx, dz, w.provider.worldChunkMgr) == BiomeGenBase.ocean) continue;
      int id = c.getBlockID(dx, y, dz);
      if (id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {        
        int amount = Fluids.stillWater.getBlockContent(c, tempData, x+dx, y, z+dz);
        if (amount > 0) {
          Fluids.stillWater.evaporate(w, c, x + dx, y, z + dz, evaporationStepsize, false);
          Counters.worldHeatEvaporation++;
        }
        return;
      }
    }

  }

  /**
   * Evaporates water liquid at a slow rate everywhere in a world. Note this is
   * VERY slow... and maybe not even be useful!
   */
  private static void doIndirectEvaporation(World w, int x, int z) {
    if (FysiksFun.rand.nextInt(921) != 0) return;
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    if (c == null) return;

    for (int tries = 0; tries < 10; tries++) {
      int y = 30 + FysiksFun.rand.nextInt(128);
      int dx = FysiksFun.rand.nextInt(16);
      int dz = FysiksFun.rand.nextInt(16);
      int id = c.getBlockID(dx, y, dz);
      if (id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {
        int id2 = c.getBlockID(dx, y - 1, dz);
        if (id2 == Block.glass.blockID || id2 == Fluids.stillWater.blockID || id2 == Fluids.flowingWater.blockID) continue;

        Fluids.stillWater.evaporate(w, c, x + dx, y, z + dz, evaporationStepsize, false);
        /*
         * int amount = Fluids.stillWater.getBlockContent(w, x + dx, y, z + dz);
         * Fluids.stillWater.setBlockContent(w, x + dx, y, z + dz, amount > 0 ?
         * amount - 1 : 0);
         */

        Counters.indirectEvaporation++;
        // Do atmost one evaporation event per chunk... thus oceans and rivers
        // wont evaporate too much from this step
        break;
      }
    }
  }

  /**
   * Creates water at altitudes y=24 -> y=58 in air cells that are adjacent to a
   * dirt, gravel or cobblestone cell. Also converts underground steam back to
   * water
   */
  private static void doHumidification(World w, int x, int z) {
    // Net effect of this is that water is created!

    if (FysiksFun.rand.nextInt(10) != 0) return;

    // TODO - let the biome effect the underground humidification.

    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    if (c == null) return;

    for (int tries = 0; tries < 1; tries++) {
      int dx = FysiksFun.rand.nextInt(16);
      int dz = FysiksFun.rand.nextInt(16);
      int y1 = 24 + FysiksFun.rand.nextInt(17) + FysiksFun.rand.nextInt(17);
      int x1 = x + dx;
      int z1 = z + dz;

      int id = c.getBlockID(dx, y1, dz);
      if (id == 0) {
        for (int dir = 0; dir < 6; dir++) {
          int dx2 = Util.dirToDx(dir);
          int dy2 = Util.dirToDy(dir);
          int dz2 = Util.dirToDz(dir);
          Chunk chunk1 = ChunkCache.getChunk(w, (x1 + dx2) >> 4, (z1 + dz2) >> 4, false);
          if (chunk1 == null) continue;
          // int id2 = w.getBlockId(x1 + dx2, y1 + dy2, z1 + dz2);
          int id2 = chunk1.getBlockID((x1 + dx2) & 15, y1 + dy2, (z1 + dz2) & 15);
          if (id2 == Block.dirt.blockID || id2 == Block.gravel.blockID || id2 == Block.cobblestone.blockID) {
            if (id2 == Block.cobblestone.blockID && FysiksFun.rand.nextInt(2) != 0) continue;
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

    Chunk chunk = ChunkCache.getChunk(w, x >> 4, z >> 4, true);

    // Gives the current time of the day, so we only evaporate during daylight
    long timeNow = w.getWorldInfo().getWorldTime();
    if (w.rainingStrength == 0.0 && FysiksFun.rand.nextInt(10) == 0) {
      double sunIntensity = Math.cos(((double) (timeNow - 5000)) / 6000.0 * 1.57);
      if (sunIntensity < 0.0) return;
      for (int i = 0; i < 10; i++) {
        int dx = (FysiksFun.rand.nextInt(117) + 13 * Counters.tick) % 16;
        int dz = (FysiksFun.rand.nextInt(187) + 17 * Counters.tick) % 16;
        BiomeGenBase biome = w.getBiomeGenForCoords(x + dx, z + dz);
        if (i < biome.getFloatTemperature() * biome.getFloatTemperature() * FysiksFun.settings.waterEvaporationRate * 0.5) {
          for (int y2 = 255; y2 > 1; y2--) {
            int id = w.getBlockId(x + dx, y2, z + dz);
            if (id == 0) continue;

            if (id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {
              try {
                BlockFFFluid.preventSetBlockLiquidFlowover = true;
                int waterAmount = Fluids.flowingWater.getBlockContent(w, x + dx, y2, z + dz);
                // Tweak to make large bodies of water not evaporate as quick
                // - should promote larger pools of liquids rather than small
                // pools
                int idBelow = w.getBlockId(x + dx, y2 - 1, z + dz);
                if ((waterAmount >= BlockFFFluid.maximumContent / 2 || idBelow == Fluids.stillWater.blockID || idBelow == Fluids.flowingWater.blockID)
                    && FysiksFun.rand.nextInt(10) != 0) continue;
                Fluids.flowingWater.evaporate(w, chunk, x + dx, y2, z + dz, evaporationStepsize, false);
                if (FysiksFun.settings.waterEvaporationMakesClouds && FysiksFun.rand.nextInt(10) == 0)
                  Gases.steam.setBlockContent(w, chunk, x + dx, Math.max(100, y2 + 1), z + dz, 1);
                // FysiksFun.rand.nextInt(10) == 0);
                Counters.directEvaporation++;
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

}
