package mbrx.ff;

import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldInfo;

public class Volcanoes {

  static int smear(int x) {
    x = ((x >> 16) ^ x) * 0x45d9f3b;
    x = ((x >> 16) ^ x) * 0x45d9f3b;
    x = ((x >> 16) ^ x);
    return x;

    // hashCode ^= (hashCode >>> 20) ^ (hashCode >>> 12);
    // return hashCode ^ (hashCode >>> 7) ^ (hashCode >>> 4);
  }

  public static void doWorldTick(World w) {
    WorldInfo winfo = w.getWorldInfo();
    int seed = smear(Objects.hashCode((int) winfo.getSeed()) + Objects.hashCode(winfo.getWorldName()));
    int volcanoFillY = 40;

    for (Object o : w.activeChunkSet) {
      ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
      // Chunk c = w.getChunkFromChunkCoords(xz.chunkXPos, xz.chunkZPos);
      Chunk chunk0 = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, false);
      ChunkTempData tempData0 = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);

      int startX = (xz.chunkXPos << 4) + 8;
      int startZ = (xz.chunkZPos << 4) + 8;
      // ChunkTempData tempData0 = ChunkTempData.getChunk(w, x, 64, z);
      int r = smear(smear(seed + xz.chunkXPos) + xz.chunkZPos) + startX;
      // boolean hasVolcano = (r % 311 == 0);
      // boolean hasVolcano = ((r / 17) % 47113 <
      // FysiksFun.settings.volcanoFrequency);
      // System.out.println("r: "+r);
      boolean hasVolcano = ((r >> 3) % 472135 < FysiksFun.settings.volcanoFrequency * 25);
      int radius = 1 + ((r / 11) % (FysiksFun.settings.volcanoRadius));

      // radius=0;
      if (!hasVolcano) radius = -1;

      solidifySurfaceLava(w, xz, chunk0, tempData0, startX, startZ, radius);

      if (hasVolcano) {
        // System.out.println("Active volcanoe at: " + startX + " " + startZ);
        createLavaSource(w, volcanoFillY, chunk0, startX, startZ, radius);
        if(FysiksFun.settings.visualizeVolcanoes)
          visualizeVolcano(chunk0, startX, startZ, radius);
        feedVolcano(w, volcanoFillY, startX, startZ, radius);
      }
    }
  }

  public final static int maximumVolcanoHeight=192;
  
  private static void feedVolcano(World w, int volcanoFillY, int startX, int startZ, int radius) {
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if (dx * dx + dz * dz <= radius * radius) {
          if (FysiksFun.rand.nextInt(FysiksFun.settings.volcanoFeeding) != 0) continue;

          int x0 = startX + dx;
          int z0 = startZ + dz;
          /* Walk straight up from the start of the plume. If air is encountered, make it lava and stop. If a block is encountered, make an explosion */
          Chunk chunk0 = ChunkCache.getChunk(w, x0>>4, z0>>4, true);
          ChunkTempData tempData0 = ChunkCache.getTempData(w, x0>>4, z0>>4);
          for(int y0=volcanoFillY;y0<maximumVolcanoHeight;y0++) {
            int id0 = chunk0.getBlockID(x0&15, y0, z0&15);
            if(id0 == 0 || Gases.isGas[id0] ||id0 == Block.fire.blockID) {
              /* Air above the plume, fill it with lava */
              Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFluid.maximumContent, "[Lava feed]", null);
              /* Random chance to make a mini-explosion column of lava */
              if(FysiksFun.rand.nextInt(100) == 0) {
                int height=FysiksFun.rand.nextInt(5)+1;
                for(int dy=1;dy<height;dy++) {
                  int id1 = chunk0.getBlockID(x0&15, y0+dy, z0&15);
                  if(id1 == 0 || Fluids.stillLava.isSameLiquid(id1) || Gases.isGas[id1]) {
                    Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFluid.maximumContent, "[Lava feed]", null);
                  } else
                    break;
                }
              }
              break;              
            } else if(Fluids.stillLava.isSameLiquid(id0)) {
              /* Lava above, top it it up or keep moving to next layer */
              int content0 = Fluids.stillLava.getBlockContent(chunk0, tempData0, x0, y0, z0);
              if(content0 >= BlockFluid.maximumContent) continue;
              else {
                Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFluid.maximumContent, "[Lava feed]", null);
                break;
              }
            } else if(Fluids.stillWater.isSameLiquid(id0)) {
              /* Water above it, turn into lava instead - and continue! */
              Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFluid.maximumContent, "[Lava feed]", null);
            } else {
              /* Non-lava, non-air block. Plume is blocked, make an explosion */
              int offsetY = radius/3+1;
              float explodeStrength = 0.5f + (1.f * radius) * FysiksFun.rand.nextFloat();
              w.newExplosion(null, x0, y0 + offsetY, z0, explodeStrength, true, true);
              int explodeRadius = ((int) explodeStrength) + 2;
              
              /* Fill exploded area with lava */
              for (int dy2 = -explodeRadius; dy2 <= explodeRadius; dy2++)
                for (int dz2 = -explodeRadius; dz2 <= explodeRadius; dz2++)
                  for (int dx2 = -explodeRadius; dx2 <= explodeRadius; dx2++) {
                    if (y0 + dy2 + offsetY > 0 && y0 + dy2 + offsetY < 255 && dx2 * dx2 + dy2 * dy2 + dz2 * dz2 <= explodeRadius * explodeRadius) {
                      Chunk chunkE = ChunkCache.getChunk(w, (x0 + dx2) >> 4, (z0 + dz2) >> 4, true);
                      ChunkTempData tempDataE = ChunkCache.getTempData(w, (x0 + dx2) >> 4, (z0 + dz2) >> 4);
                      int idE = chunkE.getBlockID((x0 + dx2) & 15, y0 + dy2, (z0 + dz2) & 15);
                      int content = 0;
                      if (idE == 0) {} 
                      else if (Fluids.stillLava.isSameLiquid(idE)) 
                        content = Fluids.stillLava.getBlockContent(chunkE, tempDataE, x0 + dx2, y0 + dy2, z0 + dz2);
                      else continue;
                      if (content < BlockFluid.maximumContent) {
                        Fluids.stillLava.setBlockContent(w, x0 + dx2, y0 + dy2 + offsetY, z0 + dz2, BlockFluid.maximumContent);
                      }
                      Fluids.stillLava.updateTick(w, x0 + dx2, y0 + offsetY + dy2, z0 + dz2, FysiksFun.rand);
                    }
                  }
              
              
            }            
          }
        }
      }
    }

  }

  private static void feedVolcanoOld(World w, int volcanoFillY, int startX, int startZ, int radius) {
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if (dx * dx + dz * dz <= radius * radius) {
          int x0 = startX + dx;
          int z0 = startZ + dz;

          // Make a random walk towards lower pressure that may trigger
          // explosions if the lava finds no way out
          for (int feedingChance = 0; feedingChance < 1; feedingChance++)
            if (FysiksFun.rand.nextInt(FysiksFun.settings.volcanoFeeding) == 0 || true) {
              // System.out.println("Trying to feed volcano");

              /* Find a point in which we either (a) add lava, or (b) make an explosion. 
               * The point should be the lowest pressure reachable point, or a point that has a free neighbour
               * We do this by making multiple random walks (biased towards lower pressures) out from the magma source.  
               */

              int sourceX = x0, sourceY = volcanoFillY, sourceZ = z0;
              int sourcePressure = Fluids.stillLava.getBlockContent(w, sourceX, sourceY, sourceZ);
              int targetPressure = sourcePressure;
              int targetX = sourceX, targetY = sourceY, targetZ = sourceZ;
              // System.out.println("Source: "+Util.xyzString(sourceX,
              // sourceY, sourceZ)+" with pressure: "+sourcePressure);

              for (int randomWalk = 0; randomWalk < 5; randomWalk++) {
                int currentX = sourceX, currentY = sourceY, currentZ = sourceZ;
                int currentPressure = sourcePressure;

                for (int steps = 0; steps < 128; steps++) {
                  int testX = currentX, testY = currentY, testZ = currentZ;

                  for (int dy2 = +1; dy2 >= -1; dy2--)
                    for (int dz2 = -1; dz2 <= 1; dz2++)
                      for (int dx2 = -1; dx2 <= 1; dx2++) {
                        // Limit to the same 10 neighbourhood as pressure
                        // updates??
                        // if(dy2 != 0 && (dx2 != 0 || dz2 != 0)) continue;
                        if (testY + dy2 <= 0 || testY + dy2 > 255) continue;
                        // if (dy2 != +1) continue; // DEBUG

                        Chunk testChunk = ChunkCache.getChunk(w, (testX + dx2) >> 4, (testZ + dz2) >> 4, false);
                        if (testChunk == null) continue;
                        ChunkTempData testTempData = ChunkCache.getTempData(w, (testX + dx2) >> 4, (testZ + dz2) >> 4);
                        int testId = testChunk.getBlockID((testX + dx2) & 15, (testY + dy2), (testZ + dz2) & 15);
                        if (testId != 0 && testId != Fluids.stillLava.blockID && testId != Fluids.flowingLava.blockID) continue;
                        int testPressure = 0;
                        if (testId == 0) {
                          steps = 256;
                          break;
                        }
                        if (testId != 0)
                          testPressure = Fluids.stillLava.getBlockContent(testChunk, testTempData, (testX + dx2) & 15, (testY + dy2), (testZ + dz2) & 15);
                        if (true) { // if (testPressure -
                                    // FysiksFun.rand.nextInt(10) - dy2 <
                                    // currentPressure) {
                          currentPressure = testPressure;
                          currentX = testX + dx2;
                          currentY = testY + dy2;
                          currentZ = testZ + dz2;
                          // System.out.println("  current: "+Util.xyzString(currentX,
                          // currentY, currentZ));
                        }
                      }
                  if (currentX == testX && currentY == testY && currentZ == testZ) break;
                }
                if (currentPressure < targetPressure) {
                  targetX = currentX;
                  targetY = currentY;
                  targetZ = currentZ;
                  targetPressure = currentPressure;
                  // System.out.println("  New target @"+targetX+","+targetY+","+targetZ+" with pressure: "+targetPressure);
                }
              }

              // int pressureHere = Fluids.stillLava.getBlockContent(w,
              // targetX, targetY, targetZ);
              // int pressureAbove = Fluids.stillLava.getBlockContent(w,
              // targetX, targetY+1, targetZ);
              // System.out.println("Final target @"+targetX+","+targetY+","+targetZ+" with pressure: "+targetPressure+"("+pressureHere+")"+" above: "+pressureAbove);
              /* target X/Y/Z is now the best local minima we have found in the pressure field */
              if (targetPressure < BlockFluid.maximumContent) {
                for (int dy2 = +1; dy2 >= -1; dy2--)
                  for (int dz2 = -1; dz2 <= 1; dz2++)
                    for (int dx2 = -1; dx2 <= 1; dx2++) {
                      int id = w.getBlockId(targetX + dx2, targetY + dy2, targetZ + dz2);
                      if (id == 0) {
                        Fluids.stillLava.setBlockContent(w, targetX + dx2, targetY + dy2, targetZ + dz2, BlockFluid.maximumContent);
                        // System.out.println("  added lava to "+dx2+","+dy2+","+dz2+" relative");
                      }
                    }
                int contentBefore = Fluids.stillLava.getBlockContent(w, targetX, targetY, targetZ);
                Fluids.stillLava.setBlockContent(w, targetX, targetY, targetZ, BlockFluid.maximumContent + BlockFluid.pressurePerY * 2);
                Fluids.stillLava.updateTick(w, targetX, targetY, targetZ, FysiksFun.rand);
                // System.out.println("Adding "+(BlockFluid.maximumContent-lowestPressure)+" lava to "+x1+" "+y1+" "+z1);
                int contentAfter = Fluids.stillLava.getBlockContent(w, targetX, targetY, targetZ);
                // System.out.println("Lava before update: " + contentBefore
                // + " after update: "+contentAfter);
              } else { // if (targetPressure > BlockFluid.maximumContent +
                       // BlockFluid.pressurePerY * 16) {
                /* See if it has no free neighbour to flow to, if not make an explosion */
                boolean makeExplosion = true;
                for (int dy2 = +1; dy2 >= -1; dy2--)
                  for (int dz2 = -1; dz2 <= 1; dz2++)
                    for (int dx2 = -1; dx2 <= 1; dx2++) {
                      if (dy2 != 0 && (dz2 != 0 || dx2 != 0)) continue;

                      if (targetY + dy2 <= 0 || targetY + dy2 > 255) continue;
                      Chunk targetChunk1 = ChunkCache.getChunk(w, (targetX + dx2) >> 4, (targetZ + dz2) >> 4, false);
                      if (targetChunk1 == null) continue;
                      ChunkTempData targetTempData1 = ChunkCache.getTempData(w, (targetX + dx2) >> 4, (targetZ + dz2) >> 4);
                      if (targetChunk1.getBlockID((targetX + dx2) & 15, targetY + dy2, (targetZ + dz2) & 15) == 0) {
                        Fluids.stillLava.setBlockContent(w, targetChunk1, targetTempData1, targetX + dx2, targetY + dy2, targetZ + dz2,
                            BlockFluid.maximumContent, "", null);
                        makeExplosion = false;
                      }
                      int content2 = Fluids.stillLava.getBlockContent(targetChunk1, targetTempData1, targetX + dx2, targetY + dy2, targetZ + dz2);
                      if (content2 < BlockFluid.maximumContent) makeExplosion = false;
                    }

                // System.out.println(" explode: "+makeExplosion);
                int explodeRadius = 2;
                int offsetY = +2;
                if (makeExplosion) {
                  System.out.println("Explosion at: " + targetX + " " + targetY + " " + targetZ);

                  float explodeStrength = 1.0f + (1.f * radius) * FysiksFun.rand.nextFloat();
                  w.newExplosion(null, targetX, targetY + offsetY, targetZ, explodeStrength, true, true);
                  explodeRadius = ((int) explodeStrength) + 1;
                }
                for (int dy2 = -explodeRadius; dy2 <= explodeRadius; dy2++)
                  for (int dz2 = -explodeRadius; dz2 <= explodeRadius; dz2++)
                    for (int dx2 = -explodeRadius; dx2 <= explodeRadius; dx2++) {
                      if (targetY + dy2 + offsetY > 0 && targetY + dy2 + offsetY < 255 && dx2 * dx2 + dy2 * dy2 + dz2 * dz2 <= explodeRadius * explodeRadius) {
                        Chunk chunkE = ChunkCache.getChunk(w, (targetX + dx2) >> 4, (targetZ + dz2) >> 4, true);
                        ChunkTempData tempDataE = ChunkCache.getTempData(w, (targetX + dx2) >> 4, (targetZ + dz2) >> 4);
                        int idE = chunkE.getBlockID((targetX + dx2) & 15, targetY + dy2, (targetZ + dz2) & 15);
                        int content = 0;
                        if (idE == 0) {} else if (Fluids.stillLava.isSameLiquid(idE)) content = Fluids.stillLava.getBlockContent(chunkE, tempDataE, targetX
                            + dx2, targetY + dy2, targetZ + dz2);
                        else continue;
                        if (content < BlockFluid.maximumContent) {
                          Fluids.stillLava.setBlockContent(w, targetX + dx2, targetY + dy2 + offsetY, targetZ + dz2, BlockFluid.maximumContent);
                        }
                        Fluids.stillLava.updateTick(w, targetX + dx2, targetY + offsetY + dy2, targetZ + dz2, FysiksFun.rand);
                      }
                    }

              }
            }
        }

      }
    }
  }

  private static void visualizeVolcano(Chunk chunk0, int startX, int startZ, int radius) {
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if (dx * dx + dz * dz <= radius * radius) {
          int x0 = startX + dx;
          int z0 = startZ + dz;
          // Debugging - visualizes where the volcanoes are
          if (FysiksFun.settings.visualizeVolcanoes) {
            for (int y0 = 96; y0 < 255; y0++) {
              chunk0.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, Block.glass.blockID, 0);
            }
          }
        }
      }
    }
  }

  private static void createLavaSource(World w, int volcanoFillY, Chunk chunk0, int startX, int startZ, int radius) {
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if (dx * dx + dz * dz <= radius * radius) {
          int x0 = startX + dx;
          int z0 = startZ + dz;

          // Fill up lava in the volcano area
          for (int y0 = 1; y0 <= volcanoFillY; y0++) {
            Chunk chunkF = ChunkCache.getChunk(w, x0 >> 4, z0 >> 4, false);
            int prevId = chunkF.getBlockID(x0 & 15, y0, z0 & 15);
            if (prevId != Fluids.stillLava.blockID && prevId != Fluids.flowingLava.blockID)
              chunk0.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, Fluids.stillLava.blockID, 0);
            Fluids.stillLava.setBlockContent(w, x0, y0, z0, BlockFluid.pressureMaximum);
          }
        }
      }
    }
  }

  private static void solidifySurfaceLava(World w, ChunkCoordIntPair xz, Chunk chunk0, ChunkTempData tempData0, int startX, int startZ, int radius) {
    /* Cool down lava that is exposed to the open air, creating stone or gravel */
    for (int cooldownAttempt = 0; cooldownAttempt < 1; cooldownAttempt++) {
      int x0 = (xz.chunkXPos << 4) + FysiksFun.rand.nextInt(16);
      int z0 = (xz.chunkZPos << 4) + FysiksFun.rand.nextInt(16);
      // If this is a chunk with a volcano, avoid cooling stone in the caldera
      // of the volcano
      if (radius >= 0) {
        int dx = x0 - startX;
        int dz = z0 - startZ;
        if (dx * dx + dz * dz <= radius * radius) continue;
      }
      for (int y0 = 255; y0 >= 1; y0--) {
        int id = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
        if (id == 0) continue;
        if (Fluids.stillLava.isSameLiquid(id)) {
          int content = Fluids.stillLava.getBlockContent(chunk0, tempData0, x0, y0, z0);
          Fluids.stillLava.setBlockContent(w, x0, y0, z0, 0);
          if (FysiksFun.rand.nextInt(BlockFluid.maximumContent) < content) {
            /* Go downwards as long as there is air, gas or water below. */
            for(;y0>0;y0--) {
              id = chunk0.getBlockID(x0&15, y0-1,z0&15);
              if(id != 0 && !Fluids.stillWater.isSameLiquid(id) && !Gases.isGas[id]) break;
            }
            BlockFluid.preventSetBlockLiquidFlowover = true;
            int r2 = FysiksFun.rand.nextInt(10);
            if (r2 < 2) w.setBlock(x0, y0, z0, Block.stone.blockID);
            else if (r2 < 4) w.setBlock(x0, y0, z0, Block.cobblestone.blockID);
            else w.setBlock(x0, y0, z0, Block.gravel.blockID);
            BlockFluid.preventSetBlockLiquidFlowover = false;
          }
          // System.out.println("Turning lava into stone: "+x0+" "+y0+" "+z0);
        }
        break;
      }
    }
  }
}
