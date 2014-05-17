package mbrx.ff.ecology;

import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Counters;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockMycelium;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class Plants {

  public static int minDrink = BlockFFFluid.maximumContent / 32;

  public static void doPlants(World w, int x, int z) {
    
    if (FysiksFun.settings.plantGrowth == 0) return;
    if (FysiksFun.settings.plantGrowth != 0 && FysiksFun.rand.nextInt(1 + 517 / FysiksFun.settings.plantGrowth) != 0) return;

    doCrops(w, x, z);
    doOtherPlants(w, x, z);

    if(FysiksFun.settings.mycelliumGrowth)
      doMycelium(w, x, z);
  }

  private static void doMycelium(World w, int x, int z) {
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, true);

    for (int tries = 0; tries < 10; tries++) {
      int dx = FysiksFun.rand.nextInt(16);
      int dz = (FysiksFun.rand.nextInt(16)+Counters.tick)%16;
      
      for (int y = 1; y < 255; y++) {
        int id = c.getBlockID(dx, y, dz);
        if (id == Block.mycelium.blockID) {
          //System.out.println("Attempting to spread mycellium from "+Util.xyzString(x+dx, y, z+dz));
          /* Found a mycelium block */
          
          /*if(FysiksFun.rand.nextInt(10) == 0 && !checkForWater(w,x+dx,y,z+dz,10,-3,1,100,true)) {
            // There's no water for the mycellium to drink. Elliminate it
            //System.out.println("Removing mycellium block that has no moisture");
            FysiksFun.setBlockIDandMetadata(w, c, x+dx, y, z+dz, Block.dirt.blockID, 0, id, 0, null);
          }*/
          
          /* Let the mycellium spread itself */
          int dir = FysiksFun.rand.nextInt(4);
          int x2 = x + dx + Util.dirToDx(dir);
          int z2 = z + dz + Util.dirToDz(dir);
          Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
          if(c2 == null) continue;
          for (int dy2 = -2; dy2 <= 2; dy2++) {
            if (y + dy2 <= 0 || y + dy2 > 255) continue;
            int id2 = c2.getBlockID(x2 & 15, y + dy2, z2 & 15);
            int meta2 = c2.getBlockMetadata(x2 & 15, y + dy2, z2 & 15);
            //System.out.println("ID at "+Util.xyzString(x+dx, y, z+dz)+" is "+id2);
            if (id2 == Block.grass.blockID || id2 == Block.dirt.blockID) {
              int light = c2.getBlockLightValue(x2 & 15, y + dy2+1, z2 & 15, 0);
              //System.out.println("Light at "+Util.xyzString(x+dx, y+1, z+dz)+" is "+light);
              if (light > 0 && checkForWater(w,x2,y+dy2,z2,8,-3,1,10,true)) {
                /*  Spread to this block */
                FysiksFun.setBlockIDandMetadata(w, c2, x2, y+dy2, z2, Block.mycelium.blockID, 0, id2, meta2, null);                
              }
            }
          } 
        }
      }
    }
  }

  private static void doOtherPlants(World w, int x, int z) {
    Chunk c = ChunkCache.getChunk(w, x>>4, z>>4, false);
    if(c == null) return;

    //if (FysiksFun.rand.nextInt(10) > 5) return;
    boolean isDay = w.isDaytime();

    for (int tries = 0; tries < FysiksFun.settings.plantsThirst; tries++) {
      int dx = FysiksFun.rand.nextInt(16);
      int dz = FysiksFun.rand.nextInt(16);
      int x0 = x + dx;
      int z0 = z + dz;
      for (int y = 0; y < 256; y++) {
        int id = c.getBlockID(dx, y, dz);
        if (id == 0) continue;
        Block b = Block.blocksList[id];
        if (b == null) continue;
        int meta = c.getBlockMetadata(dx, y, dz);
        if (b instanceof BlockFlower && !(b instanceof BlockCrops)) {
          // b == Block.plantRed || b == Block.plantYellow || b ==
          // Block.tallGrass) {

          /** Determine radius in which these plants should consume stuff */
          int drinkRange = 4;
          /** Chances in 10 of skipping this plant (to make for slower plants) */
          int skipChance = 0;
          /** Probabilities to NOT spread when drinking (will still consume) */
          int spreadFailures = 4;
          /** Probabilities to NOT spread when drinking */
          int spreadRange = 12;
          /**
           * How many plants of this type is allowed in the same 5x5 area
           * (roughly)
           */
          int plantDensity = 4;
          /** Chance NOT to die when failing to find water */
          int droughtDeath = 4;

          int minLightLevelNeeded = 8;
          int maxLightLevelNeeded = 15;

          int yMin = -16, yMax = +1;
          /** Fraction of a content level that will be drunk by the plant */
          int drinkQuantityInv = 4;

          if (id == Block.tallGrass.blockID) {
            drinkRange = 8;
            yMin = -4;
            yMax = +2;
            drinkQuantityInv = 8;
            // Spreads faster than flowers, but not as far in one go
            spreadFailures = 1;
            spreadRange = 3;
            plantDensity = 8;
            droughtDeath = 16;
            // Can grow in slightly darker places than flowers
            minLightLevelNeeded = 4;
          }

          int lightValue = c.getBlockLightValue(dx, y, dz, 0);
          if (isDay) {
            if (lightValue < minLightLevelNeeded || lightValue > maxLightLevelNeeded) {
              //System.out.println("Elliminating plant due to bad light conditions");
              FysiksFun.setBlockWithMetadataAndPriority(w, x + dx, y, z + dz, 0, 0, 0);
              continue;
            }
          }

          // Skip doing anything for some slow plants
          if (FysiksFun.rand.nextInt(10) < skipChance) continue;
          boolean shouldConsume = (FysiksFun.rand.nextInt(drinkQuantityInv) == 0);
          if (!checkForWater(w, x0, y, z0, drinkRange, yMin, yMax, 64, shouldConsume)) {
            /*
             * No water was found, eliminate the plant and remove it from the
             * world (no seeds dropped)
             */
            if (FysiksFun.rand.nextInt(droughtDeath) == 0) {
              BiomeGenBase g = w.getBiomeGenForCoords(x, y);
              if (FysiksFun.rand.nextInt((int) Math.round(g.rainfall * 10.0 + 1.0)) <= 5) {
                Counters.cropsDie++;
                FysiksFun.setBlockWithMetadataAndPriority(w, x + dx, y, z + dz, 0, 0, 0);
                // System.out.println("Crop died cause of lack of water: "+x+" "+z);
              }
            }
          } else {
            /* Let them spread naturally */
            if (FysiksFun.rand.nextInt(spreadFailures) == 0) {
              for (int spreadTry = 0; spreadTry < 4; spreadTry++) {
                /* Make a random walk until we find a place where it can grow */
                int x2 = x0;
                int z2 = z0;
                int y2 = y;
                for (int steps = 0; steps < spreadRange; steps++) {
                  int dir = FysiksFun.rand.nextInt(4);
                  x2 += Util.dirToDx(dir);
                  z2 += Util.dirToDz(dir);
                  int dy2;
                  for (dy2 = -2; dy2 < 3; dy2++)
                    if (y2 + dy2 > 0 && w.getBlockId(x2, y2 + dy2, z2) == 0) {
                      int id2 = w.getBlockId(x2, y2 + dy2 - 1, z2);
                      int light2 = w.getBlockLightValue(x2, y2 + dy2 - 1, z2);
                      if (light2 < minLightLevelNeeded || light2 > maxLightLevelNeeded) continue;
                      if (id2 != 0) break;
                    }
                  y2 += dy2;
                }
                if (w.getBlockId(x2, y2, z2) == 0 && b.canBlockStay(w, x2, y2, z2)) {
                  /* Count how many neighbours have the same blockID as me... */
                  int neighbourMe = 0;
                  for (int dx2 = -2; dx2 <= 2; dx2++)
                    for (int dz2 = -2; dz2 <= 2; dz2++)
                      for (int dy3 = -1; dy3 <= 1; dy3++)
                        if (y2 + dy3 > 0 && w.getBlockId(x2 + dx2, y2 + dy3, z2 + dz2) == id) neighbourMe++;
                  if (neighbourMe < plantDensity &&
                  /* Check that there actually is nearby water so we don't spread to an area where we will die immediately */
                  checkForWater(w, x2, y2, z2, drinkRange, yMin, yMax, 16, false)) {
                    FysiksFun.setBlockWithMetadataAndPriority(w, x2, y2, z2, id, meta, 0);
                    Counters.cropsSpread++;
                    break;
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  /** Checks if there is any water within the given radius in XZ direction and within the yMin,yMax (given as deltas) vertical range. Returns true if water has 
   * been found and consumes 1/16 of a block if found. Will check water in non-existant chunks. */ 
  private static boolean checkForWater(World w, int x2, int y2, int z2, int drinkRange, int yMin, int yMax, int attempts, boolean consumeWater) {
    IChunkProvider chunkProvider = w.getChunkProvider();

    for (int drinkChance = 0; drinkChance < attempts; drinkChance++) {
      int dx2 = FysiksFun.rand.nextInt(drinkRange) + FysiksFun.rand.nextInt(drinkRange + 1) - drinkRange;
      int dz2 = FysiksFun.rand.nextInt(drinkRange) + FysiksFun.rand.nextInt(drinkRange + 1) - drinkRange;
      int x3 = x2 + dx2, z3 = z2 + dz2;
      int chunkX = x3 >> 4;
      int chunkZ = z3 >> 4;
      Chunk c = ChunkCache.getChunk(w, chunkX, chunkZ, false);
      if(c == null) continue;

      for (int dy2 = yMin; dy2 <= yMax; dy2++) {
        if (y2 + dy2 < 0) continue;
        int id2 = c.getBlockID(x3 & 15, y2 + dy2, z3 & 15);
        if (id2 == Fluids.stillWater.blockID || id2 == Fluids.flowingWater.blockID) {
          if (consumeWater) {
            // BlockFluid.preventSetBlockLiquidFlowover = true;
            Fluids.flowingWater.consume(w, c, x3, y2 + dy2, z3, minDrink);
            // int amount = Fluids.flowingWater.getBlockContent(w, x3, y2 + dy2,
            // z3) - 1;
            // Fluids.flowingWater.setBlockContent(w, x3, y2 + dy2, z3, amount);
            // BlockFluid.preventSetBlockLiquidFlowover = false;
            Counters.cropsDrink++;
          }
          return true;
        }
      }
    }
    return false;
  }

  private static void doCrops(World w, int x, int z) {
    Chunk c = ChunkCache.getChunk(w, x>>4, z>>4, false);
    if(c == null) return; // This should not realy happen, but in case someone adds extra calls refuse to check non-loaded chunks
    if (FysiksFun.rand.nextInt(10) >= 2) return;
    for (int tries = 0; tries < FysiksFun.settings.cropsThirst; tries++) {
      int dx = FysiksFun.rand.nextInt(16);
      int dz = FysiksFun.rand.nextInt(16);
      for (int y = 2; y < 192; y++) {
        int id = c.getBlockID(dx, y, dz);
        if (id == 0) continue;
        Block b = Block.blocksList[id];
        if (b == null) continue;
        if (!(b instanceof BlockCrops)) continue;
        /* Matches both crops, carrots and perhaps blocks added by other mods? */
        int meta = c.getBlockMetadata(dx, y, dz);
        if (meta == 7 && !FysiksFun.settings.cropsDrinkContinously) continue;
        int dirOffset = FysiksFun.rand.nextInt(4);
        boolean foundWater = false;
        for (int dx2 = -2; dx2 <= 2; dx2++)
          for (int dz2 = -2; dz2 <= 2; dz2++)
            for (int dy = -3; dy <= 0; dy++) {
              int x2 = x + dx + dx2, z2 = z + dz + dz2;
              int id2;
              Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
              if (c2 == null) continue;
              id2 = c2.getBlockID(x2 & 15, y + dy, z2 & 15);
              if (id2 == Fluids.stillWater.blockID || id2 == Fluids.flowingWater.blockID) {
                Fluids.flowingWater.consume(w, c, x2, y + dy, z2, minDrink);
                // int amount = Fluids.flowingWater.getBlockContent(w, x2, y -
                // 1,
                // z2) - 1;
                // Fluids.flowingWater.setBlockContent(w, x + dx + dx2, y - 1, z
                // +
                // dz + dz2, amount);
                Counters.cropsDrink++;
                foundWater = true;
                break;
              }
            }

        /* Makes plants that drink also grow 
        if (foundWater) {
          if (meta < 7) {
            meta = meta + 1;
            FysiksFun.setBlockIDandMetadata(w, c, x + dx, y, z + dz, id, meta, id, meta - 1, null);
          }
        }*/

        if (!foundWater) {
          /* No water was found, eliminate the crops */
          /* reducing the meta by one to avoid dropping anything too good */
          Counters.cropsDie++;
          FysiksFun.setBlockWithMetadataAndPriority(w, x + dx, y, z + dz, 0, 0, 0);
          System.out.println("Killing crop at: " + Util.xyzString(x + dx, y, z + dz));
          // w.setBlock(x + dx, y, z + dz, 0, 0, 0x01 + 0x02);
          // if(meta > 0) b.dropBlockAsItem(w, x + dx, y, z + dz, meta -
          // 1,
          // 0);
        }
        continue;
      }
    }
  }

}
