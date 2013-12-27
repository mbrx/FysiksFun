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

  public static void doChunkTick(World w, ChunkCoordIntPair xz) {
    WorldInfo winfo = w.getWorldInfo();
    int seed = smear(Objects.hashCode((int) winfo.getSeed()) + Objects.hashCode(winfo.getWorldName()));
    int volcanoFillY = 8;

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
    // Using prime numbers for shaping the randomness
    boolean hasVolcano = (((r >> 3) % 1028569) < FysiksFun.settings.volcanoFrequency * 17);
    int radius = 1 + ((r / 11) % (FysiksFun.settings.volcanoRadius));

    /*
     * Compute the activity and intensity of volcanoes based on their "zone" coordinates. A zone is defined as a region
     * of 4x4 chunks. A volcanoes activities is the sum of the phase/intensity of it's own zone and the neighbouring
     * zones. (With a lower weight for neighbouring zones).
     */
    int zx = xz.chunkXPos / 16;
    int zz = xz.chunkZPos / 16;
    double activity = 0.2;
    if (hasVolcano) {
      for (int dx = -2; dx <= 2; dx++)
        for (int dz = -2; dz <= 2; dz++) {
          double weight = 0.35 / (dx * dx + dz * dz + 1);
          int r2 = smear(r + dx * 17 + dz * 11);
          double phase = ((smear(r2 + 7) / 17) % 100) / 50.0 * 3.14159265;
          double freq = ((smear(r2 + 9) / 17) % 200) / 100.0 + 1.0;
          double inten = ((smear(r2 + 11) / 17) % 100) / 100.0 + 1.0;
          activity += weight * inten * Math.sin(w.getTotalWorldTime() / 4800.0 * freq + phase);
        }
    }
    // DEBUG
    // activity += 1.0;
    // radius=0;
    if (!hasVolcano) radius = -1;

    solidifySurfaceLava(w, xz, chunk0, tempData0, startX, startZ, radius);

    if (hasVolcano) {
      //System.out.println("Active volcanoe at: " + startX + " " + startZ + "Activity: " + activity);
      retractLavaSource(w, volcanoFillY, chunk0, startX, startZ, radius, activity);
      if (FysiksFun.settings.visualizeVolcanoes) visualizeVolcano(chunk0, startX, startZ, radius);
      feedVolcano(w, volcanoFillY, startX, startZ, radius, activity);
    }
  }

  public final static int maximumVolcanoHeight = 200;

  private static void feedVolcano(World w, int volcanoFillY, int startX, int startZ, int radius, double activity) {
    if (activity <= 0.0) return;
    // if(w != null) return;

    int worldYOffset = FysiksFun.settings.worldYOffset;
    
    for (int tries = 0; tries < 3; tries++) {
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
          if (dx * dx + dz * dz <= radius * radius) {
            if (FysiksFun.rand.nextInt((int) (FysiksFun.settings.volcanoFeeding / (activity + 0.1))) != 0) continue;
            int maxY = 1+FysiksFun.rand.nextInt(maximumVolcanoHeight);
            
            int x0 = startX + dx;
            int z0 = startZ + dz;
            /*
             * Walk straight up from the start of the plume. If air is encountered, make it lava and stop. If a block is
             * encountered, make an explosion
             */
            Chunk chunk0 = ChunkCache.getChunk(w, x0 >> 4, z0 >> 4, false);
            // If lava plume extends outside loaded area, stop feeding volcanoe and wait until it is loaded
            if(chunk0 == null) return; 
            ChunkCoordIntPair testXZ = new ChunkCoordIntPair(x0>>4, z0>>4);
            if(!w.activeChunkSet.contains(testXZ)) return;             
            
            ChunkTempData tempData0 = ChunkCache.getTempData(w, x0 >> 4, z0 >> 4);
            int seed = smear(smear(startX * 17) + startZ * 311);
            for (int y0 = volcanoFillY, cnt = 0; y0 < maxY && cnt < 300; y0++, cnt++) {

              int r0 = smear(seed + (cnt / 5) * 4711);
              if ((r0 % 17) < 9) cnt++;
              switch ((r0 % 13) + 4*(y0/22)) {
              case 0:
                x0++;
                break;
              case 1:
                x0--;
                break;
              case 2:
                z0++;
                break;
              case 3:
                z0--;
                break;
              case 4:
                x0++;
                if (y0 < 64+worldYOffset) y0--;
                break;
              case 5:
                x0--;
                if (y0 < 64+worldYOffset) y0--;
                break;
              case 6:
                z0++;
                if (y0 < 64+worldYOffset) y0--;
                break;
              case 7:
                z0--;
                if (y0 < 64+worldYOffset) y0--;
                break;                             
              case 8:
                x0++;
                break;
              case 9:
                x0--;
                break;
              case 10:
                z0++;
                break;
              case 11:
                z0--;
                break;
              case 12:
                if (y0 < 64+worldYOffset) y0 = Math.max(volcanoFillY + 3, y0 - 2);
              default:
              }
              chunk0 = ChunkCache.getChunk(w, x0 >> 4, z0 >> 4, true);
              tempData0 = ChunkCache.getTempData(w, x0 >> 4, z0 >> 4);

              int id0 = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
              if (id0 == 0 || Gases.isGas[id0] || id0 == Block.fire.blockID) {
                /* Air above the plume, fill it with lava */
                Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFluid.maximumContent, "[Lava feed]", null);
                                     
                if(activity>0.6 && Counters.tick%1000 < activity*300.0) {
                int idAbove = chunk0.getBlockID(x0 & 15, y0+1, z0 & 15);
                if (idAbove == 0 || Gases.isGas[idAbove]) {
                  /* Add some pyroclastic gases */
                  Gases.pyroclastic.setBlockContent(w, chunk0, x0, y0+1, z0, 15);
                }
                }
                
                /*
                 * Random chance to make a mini-column of lava
                 */
                if (FysiksFun.rand.nextInt((int) (1000 / (0.1 + activity))) == 0) {
                  int height = FysiksFun.rand.nextInt((int) (1 + 0.5 * activity));
                  for (int dy = 1; dy < height; dy++) {
                    int id1 = chunk0.getBlockID(x0 & 15, y0 + dy, z0 & 15);
                    if (id1 == 0 || Fluids.stillLava.isSameLiquid(id1) || Gases.isGas[id1]) {
                      Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFluid.maximumContent, "[Lava feed]", null);
                    } else break;
                  }
                }
                break;
              } else if (Fluids.stillLava.isSameLiquid(id0)) {
                /*
                 * Lava above, top it it up or keep moving to next layer
                 */
                int content0 = Fluids.stillLava.getBlockContent(chunk0, tempData0, x0, y0, z0);
                if (content0 >= BlockFluid.maximumContent) continue;
                else {
                  Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFluid.maximumContent, "[Lava feed]", null);
                  break;
                }
              } else if (Fluids.stillWater.isSameLiquid(id0)) {
                /*
                 * Water above it, turn into lava instead - and continue!
                 */
                Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFluid.maximumContent, "[Lava feed]", null);
              } else {
                /*
                 * Non-lava, non-air block. Plume is blocked, make an explosion
                 */
                // System.out.println("Explode @" + Util.xyzString(x0, y0, z0) + " id: " + id0);
                int offsetY = 1; // radius / 3 + 1;
                float explodeStrength = 0.5f + (1.f * radius) * FysiksFun.rand.nextFloat();
                if (FysiksFun.rand.nextInt(10) == 0) {
                  FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0, 0, 0, 0);
                  FysiksFun.globalWorldChangingMutex.acquireUninterruptibly();
                  w.newExplosion(null, x0, y0 + offsetY, z0, explodeStrength, true, true);
                  FysiksFun.globalWorldChangingMutex.release();
                }
                int explodeRadius = ((int) explodeStrength) + 2;

                /* Fill exploded area with lava */
                for (int dy2 = -explodeRadius; dy2 <= explodeRadius; dy2++)
                  for (int dz2 = -explodeRadius; dz2 <= explodeRadius; dz2++)
                    for (int dx2 = -explodeRadius; dx2 <= explodeRadius; dx2++) {
                      if (y0 + dy2 < 0) continue;
                      int radSq = dx2 * dx2 + dy2 * dy2 + dz2 * dz2;
                      if (y0 + dy2 + offsetY > 0 && y0 + dy2 + offsetY < 255 && radSq <= explodeRadius * explodeRadius) {
                        int x2 = x0 + dx2;
                        int y2 = y0 + dy2 + offsetY;
                        int z2 = z0 + dz2;

                        Chunk chunkE = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, true);
                        ChunkTempData tempDataE = ChunkCache.getTempData(w, x2 >> 4, z2 >> 4);
                        int idE = chunkE.getBlockID((x0 + dx2) & 15, y0 + dy2, (z0 + dz2) & 15);
                        int content = 0;
                        if (idE == 0) {} else if (Fluids.stillLava.isSameLiquid(idE)) content = Fluids.stillLava.getBlockContent(chunkE, tempDataE, x0 + dx2,
                            y0 + dy2, z0 + dz2);
                        else {
                          /* Chance to create new ores */
                          if (idE == Block.stone.blockID && radSq < (int) ((explodeStrength + 2) * (explodeStrength + 2))) {
                            int newId = 0;
                            int v = FysiksFun.rand.nextInt(3500);
                            if (v == 0 && y2 < 35) newId = Block.oreDiamond.blockID;
                            else if (v == 1 && y2 < 55) newId = Block.oreEmerald.blockID;
                            else if (v > 1 && v < 6 && y2 < 200) newId = Block.oreIron.blockID;
                            if (newId != 0) {
                              FysiksFun.setBlockWithMetadataAndPriority(w, x2, y2, z2, newId, 0, 0);
                              FysiksFun.setBlockWithMetadataAndPriority(w, x2 + 1, y2, z2, newId, 0, 0);
                              FysiksFun.setBlockWithMetadataAndPriority(w, x2 - 1, y2, z2, newId, 0, 0);
                              FysiksFun.setBlockWithMetadataAndPriority(w, x2, y2 + 1, z2, newId, 0, 0);
                              FysiksFun.setBlockWithMetadataAndPriority(w, x2, y2 - 1, z2, newId, 0, 0);
                              FysiksFun.setBlockWithMetadataAndPriority(w, x2, y2 + 1, z2 + 1, newId, 0, 0);
                              FysiksFun.setBlockWithMetadataAndPriority(w, x2, y2 - 1, z2 - 1, newId, 0, 0);
                            }
                          }
                          continue;
                        }
                        if (content < BlockFluid.maximumContent && radSq < explodeStrength*explodeStrength) {
                          Fluids.stillLava.setBlockContent(w, x0 + dx2, y0 + dy2 + offsetY, z0 + dz2, BlockFluid.maximumContent);
                        }
                        // Fluids.stillLava.updateTick(w, x0 + dx2, y0 + offsetY + dy2, z0 + dz2, FysiksFun.rand);
                      }
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

          // Make a random walk towards lower pressure that may
          // trigger
          // explosions if the lava finds no way out
          for (int feedingChance = 0; feedingChance < 1; feedingChance++)
            if (FysiksFun.rand.nextInt(FysiksFun.settings.volcanoFeeding) == 0 || true) {
              // System.out.println("Trying to feed volcano");

              /*
               * Find a point in which we either (a) add lava, or (b) make an explosion. The point should be the lowest
               * pressure reachable point, or a point that has a free neighbour We do this by making multiple random
               * walks (biased towards lower pressures) out from the magma source.
               */

              int sourceX = x0, sourceY = volcanoFillY, sourceZ = z0;
              int sourcePressure = Fluids.stillLava.getBlockContent(w, sourceX, sourceY, sourceZ);
              int targetPressure = sourcePressure;
              int targetX = sourceX, targetY = sourceY, targetZ = sourceZ;
              // System.out.println("Source: "+Util.xyzString(sourceX,
              // sourceY,
              // sourceZ)+" with pressure: "+sourcePressure);

              for (int randomWalk = 0; randomWalk < 5; randomWalk++) {
                int currentX = sourceX, currentY = sourceY, currentZ = sourceZ;
                int currentPressure = sourcePressure;

                for (int steps = 0; steps < 128; steps++) {
                  int testX = currentX, testY = currentY, testZ = currentZ;

                  for (int dy2 = +1; dy2 >= -1; dy2--)
                    for (int dz2 = -1; dz2 <= 1; dz2++)
                      for (int dx2 = -1; dx2 <= 1; dx2++) {
                        // Limit to the same 10
                        // neighbourhood as pressure
                        // updates??
                        // if(dy2 != 0 && (dx2 != 0 ||
                        // dz2 != 0)) continue;
                        if (testY + dy2 <= 0 || testY + dy2 > 255) continue;
                        // if (dy2 != +1) continue; //
                        // DEBUG

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
                        if (true) { // if (testPressure
                          // -
                          // FysiksFun.rand.nextInt(10)
                          // - dy2 <
                          // currentPressure)
                          // {
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

              // int pressureHere =
              // Fluids.stillLava.getBlockContent(w,
              // targetX, targetY, targetZ);
              // int pressureAbove =
              // Fluids.stillLava.getBlockContent(w,
              // targetX, targetY+1, targetZ);
              // System.out.println("Final target @"+targetX+","+targetY+","+targetZ+" with pressure: "+targetPressure+"("+pressureHere+")"+" above: "+pressureAbove);
              /*
               * target X/Y/Z is now the best local minima we have found in the pressure field
               */
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
                // Fluids.stillLava.updateTick(w, targetX, targetY, targetZ, FysiksFun.rand);
                // System.out.println("Adding "+(BlockFluid.maximumContent-lowestPressure)+" lava to "+x1+" "+y1+" "+z1);
                int contentAfter = Fluids.stillLava.getBlockContent(w, targetX, targetY, targetZ);
                // System.out.println("Lava before update: " +
                // contentBefore
                // + " after update: "+contentAfter);
              } else { // if (targetPressure >
                // BlockFluid.maximumContent +
                // BlockFluid.pressurePerY * 16) {
                /*
                 * See if it has no free neighbour to flow to, if not make an explosion
                 */
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
            for (int y0 = 100+FysiksFun.settings.worldYOffset; y0 < 255; y0++) {
              chunk0.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, Block.glass.blockID, 0);
            }
          }
        }
      }
    }
  }

  private static void retractLavaSource(World w, int volcanoFillY, Chunk chunk0, int startX, int startZ, int radius, double activity) {
    if(activity >= 0.0) return;
    if(FysiksFun.rand.nextInt(20) != 0) return;
    
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if (dx * dx + dz * dz <= radius * radius) {
          int x0 = startX + dx;
          int z0 = startZ + dz;

          // Empty lava source in the volcano area
          for (int y0 = 1; y0 <= volcanoFillY; y0++) {
            Chunk chunkF = ChunkCache.getChunk(w, x0 >> 4, z0 >> 4, false);
            int prevId = chunkF.getBlockID(x0 & 15, y0, z0 & 15);

            if (prevId == Fluids.stillLava.blockID || prevId == Fluids.flowingLava.blockID)
              if(FysiksFun.rand.nextInt(10) == 0)
                FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0, 0, 0, 0);
            
            /*
            if (activity > 0.0) {
              if (prevId != Fluids.stillLava.blockID && prevId != Fluids.flowingLava.blockID)
                chunk0.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, Fluids.stillLava.blockID, 0);
              Fluids.stillLava.setBlockContent(w, x0, y0, z0, BlockFluid.pressureMaximum);
            } else if (activity < 0.0) {
              // if (prevId == Fluids.stillLava.blockID || prevId ==
              // Fluids.flowingLava.blockID) w.setBlockToAir(x0, y0, z0);
            }
            */
          }
        }
      }
    }
  }

  private static void solidifySurfaceLava(World w, ChunkCoordIntPair xz, Chunk chunk0, ChunkTempData tempData0, int startX, int startZ, int radius) {
    /*
     * Cool down lava that is exposed to the open air, creating stone or gravel
     */
    if(FysiksFun.rand.nextInt(4) != 0) return;
    for (int cooldownAttempt = 0; cooldownAttempt < 8; cooldownAttempt++) {
      int rx = (FysiksFun.rand.nextInt(16) + (Counters.tick) / 3) % 16;
      int rz = (FysiksFun.rand.nextInt(16) + (Counters.tick) / (3 * 16)) % 16;
      int x0 = (xz.chunkXPos << 4) + rx;
      int z0 = (xz.chunkZPos << 4) + rz;
      // A tweak factor to reshape the growth of the volcanoes, max altitude=128
      int lowestY = 1 + FysiksFun.rand.nextInt(64) + FysiksFun.rand.nextInt(64);
      
      for (int y0 = 255; y0 >= lowestY; y0--) {
        int id = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
        if (id == 0) continue;
        if (Gases.isGas[id]) continue;
        if (Fluids.stillLava.isSameLiquid(id)) {
          int content = Fluids.stillLava.getBlockContent(chunk0, tempData0, x0, y0, z0);
          Fluids.stillLava.setBlockContent(w, x0, y0, z0, 0);
          if (FysiksFun.rand.nextInt(BlockFluid.maximumContent) < content) {

            /*
             * Go downwards as long as there is air, gas or water or lava below
             */
            int nNeighbours = 0;
            int adjacentLava=0;
            for (; y0 > 0; y0--) {
              nNeighbours=0;
              for (int dir = 0; dir < 6; dir++) {
                int dx = Util.dirToDx(dir);
                int dy = Util.dirToDy(dir);
                int dz = Util.dirToDz(dir);                
                Chunk c = ChunkCache.getChunk(w, (x0 + dx) >> 4, (z0 + dz) >> 4, false);
                if (c == null) continue;
                id = c.getBlockID((x0 + dx) & 15, y0+dy, (z0 + dz) & 15);
                if (id != 0 && !Fluids.isLiquid[id] && !Gases.isGas[id]) nNeighbours++;
                if(Fluids.stillLava.isSameLiquid(id)) adjacentLava++;
              }
              if (nNeighbours >= 2 && adjacentLava < 3) break; // Allow solidifying close to other structures

              id = chunk0.getBlockID(x0 & 15, y0 - 1, z0 & 15);
              // if (id != 0 && !Fluids.stillWater.isSameLiquid(id) &&
              // !Gases.isGas[id]) break;
              if (id == 0) continue;              
              if (Fluids.isLiquid[id]) continue;
              if (Gases.isGas[id]) continue;
              Block b = Block.blocksList[id];
              if(b != null && !b.blockMaterial.isSolid()) continue;
              break;
            }
            /* Count neighbours in the same plane */
            /*int neighbours = 0;
            for (int dx = -1; dx <= 1; dx++)
              for (int dz = -1; dz <= 1; dz++) {
                Chunk c2 = ChunkCache.getChunk(w, (x0 + dx) >> 4, (z0 + dz) >> 4, false);
                if (c2 == null) continue;
                int id2 = c2.getBlockID((x0 + dx) & 15, y0, (z0 + dz) & 15);
                if (id2 == 0 || Fluids.isLiquid[id2] || Gases.isGas[id2]) continue;
                neighbours++;
              }
            if (neighbours <= 4 && cooldownAttempt != 0) break;
*/
            if(cooldownAttempt != 0 && nNeighbours < 2 && adjacentLava < 3) break;
            
            BlockFluid.preventSetBlockLiquidFlowover = true;
            int r2 = (FysiksFun.rand.nextInt(101) + Counters.tick * 4711) % 10;
            if (r2 < 3) FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0, Block.stone.blockID, 0, 0);
            else if (r2 < 6) FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0, Block.cobblestone.blockID, 0, 0);
            else FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0, Block.gravel.blockID, 0, 0);
            BlockFluid.preventSetBlockLiquidFlowover = false;
          }
          // System.out.println("Turning lava into stone: "+x0+" "+y0+" "+z0);
        } else if (FysiksFun.rand.nextInt(10) == 0) break;
      }
    }
  }
}
