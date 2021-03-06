package mbrx.ff.ecology;

import java.util.Objects;

import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import mbrx.ff.solidBlockPhysics.BlockFFStone;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkTempData;
import mbrx.ff.util.Counters;
import mbrx.ff.util.SoundQueue;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldInfo;

public class Volcanoes {

  static int smear(int x) {
    x = ((x >> 16) ^ x) * 0x45d9f3b;
    x = ((x >> 16) ^ x) * 0x45d9f3b;
    x = ((x >> 16) ^ x);
    return x;
  }

  public static void doChunkTick(World w, ChunkCoordIntPair xz) {
    if (w.provider.dimensionId == -1) return; // No volcanos in hell

    /* All get functions from vanilla MC are thread safe so no synchronized here */

    WorldInfo winfo = w.getWorldInfo();
    int seed = smear(Objects.hashCode((int) winfo.getSeed()) + Objects.hashCode(winfo.getWorldName()));
    int volcanoFillY = 3;

    Chunk chunk0 = ChunkCache.getChunk(w, xz.chunkXPos, xz.chunkZPos, false);
    ChunkTempData tempData0 = ChunkCache.getTempData(w, xz.chunkXPos, xz.chunkZPos);

    int startX = (xz.chunkXPos << 4) + 8;
    int startZ = (xz.chunkZPos << 4) + 8;
    int r = smear(smear(seed + xz.chunkXPos) + xz.chunkZPos) + startX;

    int volcanoFrequency = FysiksFun.settings.volcanoFrequency;
    BiomeGenBase g = w.getBiomeGenForCoords(startX, startZ);
    if (g == BiomeGenBase.extremeHills || g == BiomeGenBase.iceMountains) volcanoFrequency *= 4;
    if (g == BiomeGenBase.forestHills || g == BiomeGenBase.desertHills || g == BiomeGenBase.taigaHills) volcanoFrequency *= 2;

    // Using prime numbers for shaping the randomness, don't touch the basic
    // volcano constant!
    boolean hasVolcano = (((r >> 3) % 1028569) < volcanoFrequency * 11);
    int radius = 1 + ((r / 11) % (FysiksFun.settings.volcanoRadius));

    BlockFFStone.overrideShatter = true;

    /*
     * Compute the activity and intensity of volcanoes based on their "zone"
     * coordinates. A zone is defined as a region of 4x4 chunks. A volcanoes
     * activities is the sum of the phase/intensity of it's own zone and the
     * neighbouring zones. (With a lower weight for neighbouring zones).
     */
    int zx = xz.chunkXPos / 16;
    int zz = xz.chunkZPos / 16;
    double activity = 0.5;
    if (hasVolcano) {
      for (int dx = -2; dx <= 2; dx++)
        for (int dz = -2; dz <= 2; dz++) {
          double weight = 0.5 / (dx * dx + dz * dz + 3);
          // int r2 = smear(r + dx * 17 + dz * 11);
          int r2 = smear((zx + dx) * 17 + smear((zz + dz) * 471));
          /* Each zone has two parts to the sinosoid, a fast part */
          double phase = ((smear(r2 + 7) / 17) % 100) / 50.0 * 3.14159265;
          double freq = ((smear(r2 + 9) / 17) % 200) / 100.0 + 0.5; // From 0.5
                                                                    // to 2.5
          double inten = ((smear(r2 + 11) / 17) % 100) / 100.0 + 1.0; // From
                                                                      // 100% to
                                                                      // 200%
          activity += weight * inten * Math.sin(w.getTotalWorldTime() / 9600.0 * freq + phase);
          /* And a slow part (5 times slower) */
          phase = ((smear(r2 + 13) / 17) % 100) / 50.0 * 3.14159265;
          freq = ((smear(r2 + 17) / 17) % 200) / 100.0 + 0.5;
          inten = ((smear(r2 + 19) / 17) % 100) / 100.0 + 1.0;
          activity += weight * inten * 1.5 * Math.sin(w.getTotalWorldTime() / 48000.0 * freq + phase);
        }
    }
    if (!hasVolcano) radius = -1;

    solidifySurfaceLava(w, xz, chunk0, tempData0, startX, startZ, radius);

    if (hasVolcano) {
      // if (Counters.tick % 20 == 0) System.out.println("Active volcano at: " +
      // startX + " " + startZ + " Activity: " + activity);
      // retractLavaSource(w, volcanoFillY, chunk0, startX, startZ, radius,
      // activity);
      if (FysiksFun.settings.visualizeVolcanoes) visualizeVolcano(chunk0, startX, startZ, radius);
      feedVolcano(w, volcanoFillY, startX, startZ, radius, activity);
    }
    BlockFFStone.overrideShatter = false;
  }

  public final static int maximumVolcanoHeight = 128;

  private static void feedVolcano(World w, int volcanoFillY, int startX, int startZ, int radius, double activity) {
    if (activity <= 0.0) return;
    // if(w != null) return;

    int worldYOffset = FysiksFun.settings.worldYOffset;

    boolean canDoExpensiveSound = FysiksFun.rand.nextInt(200) == 0;
    for (int tries = 0; tries < 3; tries++) {
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
          if (dx * dx + dz * dz <= radius * radius) {
            if (FysiksFun.rand.nextInt((int) (FysiksFun.settings.volcanoFeeding / (activity + 0.1))) != 0) continue;
            int maxY = 1 + FysiksFun.rand.nextInt(maximumVolcanoHeight / 2) + FysiksFun.rand.nextInt(maximumVolcanoHeight / 4)
                + FysiksFun.rand.nextInt(maximumVolcanoHeight / 4);

            int x0 = startX + dx;
            int z0 = startZ + dz;
            /*
             * Walk straight up from the start of the plume. If air is
             * encountered, make it lava and stop. If a block is encountered,
             * make an explosion
             */
            Chunk chunk0 = ChunkCache.getChunk(w, x0 >> 4, z0 >> 4, false);
            // If lava plume extends outside loaded area, stop feeding volcano
            // and wait until it is loaded
            if (chunk0 == null) return;
            ChunkCoordIntPair testXZ = new ChunkCoordIntPair(x0 >> 4, z0 >> 4);
            if (!w.activeChunkSet.contains(testXZ)) return;

            ChunkTempData tempData0 = ChunkCache.getTempData(w, x0 >> 4, z0 >> 4);
            int seed = smear(smear(startX * 17) + startZ * 311);
            int y0, cnt;
            int leftToFill = 3;
            for (y0 = volcanoFillY, cnt = 0; y0 < maxY && cnt < 300; y0++, cnt++) {

              if (FysiksFun.rand.nextInt(100) == 0 && canDoExpensiveSound) {
                canDoExpensiveSound = false;
                float volume = (float) (4.0F + 4.0F * activity);
                float pitch = (float) (1.4 - FysiksFun.rand.nextFloat() * 0.4 - activity * 0.2);
                SoundQueue.queueSound(w,x0 + 0.5, y0 + 0.5, z0 + 0.5, "fysiksfun:earthquake", volume, pitch);
              }
              if (FysiksFun.rand.nextInt(1000) == 0) {
                float volume = (float) (activity * 1.0 + 1.0);
                float pitch = (float) (1.4 - FysiksFun.rand.nextFloat() * 0.4 - activity * 0.3) * 0.2F;
                SoundQueue.queueSound(w,x0 + 0.5, y0 + 0.5, z0 + 0.5, "random.explode", volume, pitch);
              }

              int r0 = smear(seed + (cnt / 5) * 4711);
              if ((r0 % 17) < 9) cnt++;
              switch ((r0 % 13) + 4 * (y0 / 22)) {
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
                if (y0 < 64 + worldYOffset) y0--;
                break;
              case 5:
                x0--;
                if (y0 < 64 + worldYOffset) y0--;
                break;
              case 6:
                z0++;
                if (y0 < 64 + worldYOffset) y0--;
                break;
              case 7:
                z0--;
                if (y0 < 64 + worldYOffset) y0--;
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
                if (y0 < 64 + worldYOffset) y0 = Math.max(volcanoFillY + 3, y0 - 2);
              default:
              }
              chunk0 = ChunkCache.getChunk(w, x0 >> 4, z0 >> 4, true);
              tempData0 = ChunkCache.getTempData(w, x0 >> 4, z0 >> 4);

              int id0 = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
              if (id0 == 0 || Gases.isGas[id0] || id0 == Block.fire.blockID) {
                /* Air above the plume, fill it with lava */
                Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFFFluid.maximumContent, "[Lava feed]", null);

                if (activity > 0.6 && Counters.tick % 1000 < activity * 300.0 && FysiksFun.rand.nextInt(11) == 0) {
                  int idAbove = chunk0.getBlockID(x0 & 15, y0 + 1, z0 & 15);
                  if (idAbove == 0 || Gases.isGas[idAbove]) {
                    /* Add some pyroclastic gases */
                    Gases.pyroclastic.setBlockContent(w, chunk0, x0, y0 + 1, z0, 15);
                  }
                }

                // Random chance to make a mini-column of lava
                // makeLavaBubble(w, activity, x0, z0, chunk0, tempData0, y0);
                if (--leftToFill < 0) break;
              } else if (Fluids.stillLava.isSameLiquid(id0)) {
                /*
                 * Lava above, top it it up or keep moving to next layer
                 */
                int content0 = Fluids.stillLava.getBlockContent(chunk0, tempData0, x0, y0, z0);
                if (content0 >= BlockFFFluid.maximumContent) continue;
                else {
                  /* Lava was not maximum content, fill up and stop propagating */
                  Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFFFluid.maximumContent, "[Lava feed]", null);
                  break;
                }
              } else if (Fluids.stillWater.isSameLiquid(id0)) {
                /*
                 * Water above it, turn into lava instead - and continue!
                 */
                Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFFFluid.maximumContent, "[Lava feed]", null);
              } else {

                if (FysiksFun.rand.nextInt(40) == 0) doVolcanoExplosion(w, radius, x0, z0, y0);
                else {
                  Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFFFluid.maximumContent, "[Lava feed]", null);
                }
                break;

                // Lower chance to explode at higher altitudes (to reduce the
                // insane growth up there)
                // if (y0 > 64 + FysiksFun.rand.nextInt(60)) break;
                // if (y0 > 64 + FysiksFun.rand.nextInt(60)) break;
                // doVolcanoExplosion(w, radius, x0, z0, y0);
                // break;

                /*
                 * if(--leftToFill <= 0) { //if(FysiksFun.rand.nextInt(10) == 0)
                 * doVolcanoExplosion(w, radius, x0, z0, y0); break; } else {
                 * int dir=FysiksFun.rand.nextInt(4); x0 += Util.dirToDx(dir);
                 * z0 += Util.dirToDz(dir); }
                 */
                // Non-lava, non-air block. Plume is blocked, make an explosion
                // doVolcanoExplosion(w, radius, x0, z0, y0);
                // break;
              }
            }

          }
        }
      }
    }

  }

  private static void makeLavaBubble(World w, double activity, int x0, int z0, Chunk chunk0, ChunkTempData tempData0, int y0) {
    if (y0 < 96 && FysiksFun.rand.nextInt((int) (1000 / (0.1 + activity))) == 0) {
      int height = FysiksFun.rand.nextInt((int) (1 + 0.5 * activity));
      for (int dy = 1; dy < height; dy++) {
        int id1 = chunk0.getBlockID(x0 & 15, y0 + dy, z0 & 15);
        if (id1 == 0 || Fluids.stillLava.isSameLiquid(id1) || Gases.isGas[id1]) {
          Fluids.stillLava.setBlockContent(w, chunk0, tempData0, x0, y0, z0, BlockFFFluid.maximumContent, "[Lava feed]", null);
        } else break;
      }
    }
  }

  private static void doVolcanoExplosion(World w, int radius, int x0, int z0, int y0) {
    {
      int offsetY = 1;
      float explodeStrength = 0.5f + (1.f * radius) * FysiksFun.rand.nextFloat();
      // float explodeStrength = 1.5f;
      if (FysiksFun.rand.nextInt(10) == 0) {
        FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0, 0, 0, 0);
        synchronized (FysiksFun.vanillaMutex) {
          w.newExplosion(null, x0, y0 + offsetY, z0, explodeStrength, true, true);
        }
      }
      int explodeRadius = ((int) explodeStrength) + 4;
      fillExplodedAreaWithLava(w, x0, z0, y0, offsetY, explodeStrength, explodeRadius);
    }
  }

  /*
   * Fills the exploded area with lava, may6 also turn neighbouring stones into
   * ores depending on the depth
   */
  private static void fillExplodedAreaWithLava(World w, int x0, int z0, int y0, int offsetY, float explodeStrength, int explodeRadius) {
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
            if (idE == 0) {} else if (Fluids.stillLava.isSameLiquid(idE)) content = Fluids.stillLava.getBlockContent(chunkE, tempDataE, x0 + dx2, y0 + dy2, z0
                + dz2);
            else {
              /* Chance to create new ores */
              if (idE == Block.stone.blockID && radSq < (int) ((explodeStrength + 2) * (explodeStrength + 2))) {
                int newId = 0;
                int v = FysiksFun.rand.nextInt(2000) + (y2 / 20);
                if (v == 0) newId = Block.oreDiamond.blockID;
                else if (v <= 1) newId = Block.oreEmerald.blockID;
                else if (v <= 2) newId = Block.oreRedstone.blockID;
                else if (v <= 5) newId = Block.oreIron.blockID;
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
            if (content < BlockFFFluid.maximumContent && radSq < explodeStrength * explodeStrength) {
              Fluids.stillLava.setBlockContent(w, x0 + dx2, y0 + dy2 + offsetY, z0 + dz2, BlockFFFluid.maximumContent);
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
            for (int y0 = 100 + FysiksFun.settings.worldYOffset; y0 < 255; y0++) {
              synchronized (FysiksFun.vanillaMutex) {
                chunk0.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15, Block.glass.blockID, 0);
              }
            }
          }
        }
      }
    }
  }

  private static void retractLavaSource(World w, int volcanoFillY, Chunk chunk0, int startX, int startZ, int radius, double activity) {
    if (activity >= 0.0) return;
    if (FysiksFun.rand.nextInt(5) != 0) return;

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
              if (FysiksFun.rand.nextInt(10) == 0) FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0, 0, 0, 0);

            /*
             * if (activity > 0.0) { if (prevId != Fluids.stillLava.blockID &&
             * prevId != Fluids.flowingLava.blockID)
             * chunk0.setBlockIDWithMetadata(x0 & 15, y0, z0 & 15,
             * Fluids.stillLava.blockID, 0); Fluids.stillLava.setBlockContent(w,
             * x0, y0, z0, BlockFluid.pressureMaximum); } else if (activity <
             * 0.0) { // if (prevId == Fluids.stillLava.blockID || prevId == //
             * Fluids.flowingLava.blockID) w.setBlockToAir(x0, y0, z0); }
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
    if (FysiksFun.rand.nextInt(4) != 0) return;
    for (int cooldownAttempt = 0; cooldownAttempt < 4; cooldownAttempt++) {
      int rx = (FysiksFun.rand.nextInt(16) + (Counters.tick) / 3) % 16;
      int rz = (FysiksFun.rand.nextInt(16) + (Counters.tick) / (3 * 16)) % 16;
      int x0 = (xz.chunkXPos << 4) + rx;
      int z0 = (xz.chunkZPos << 4) + rz;
      // A tweak factor to reshape the growth of the volcanoes, max altitude=128
      int highestY = 1 + FysiksFun.rand.nextInt(64) + FysiksFun.rand.nextInt(32) + FysiksFun.rand.nextInt(32);

      for (int y0 = 255; y0 >= 1; y0--) {
        int id = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
        if (id == 0) continue;
        if (Gases.isGas[id]) continue;
        if (Fluids.stillLava.isSameLiquid(id) && y0 < highestY) {
          int content = Fluids.stillLava.getBlockContent(chunk0, tempData0, x0, y0, z0);
          Fluids.stillLava.setBlockContent(w, x0, y0, z0, 0);
          if (FysiksFun.rand.nextInt(BlockFFFluid.maximumContent) < content) {

            /*
             * Go downwards as long as there is air, gas or water or lava below
             */
            int nNeighbours = 0;
            int adjacentLava = 0;
            for (; y0 > 0; y0--) {
              nNeighbours = 0;
              for (int dir = 0; dir < 6; dir++) {
                int dx = Util.dirToDx(dir);
                int dy = Util.dirToDy(dir);
                int dz = Util.dirToDz(dir);
                Chunk c = ChunkCache.getChunk(w, (x0 + dx) >> 4, (z0 + dz) >> 4, false);
                if (c == null) continue;
                id = c.getBlockID((x0 + dx) & 15, y0 + dy, (z0 + dz) & 15);
                if (id != 0 && !Fluids.isLiquid[id] && !Gases.isGas[id]) nNeighbours++;
                if (Fluids.stillLava.isSameLiquid(id)) adjacentLava++;
              }
              if (nNeighbours >= 2 && adjacentLava < 3) break; // Allow
                                                               // solidifying
                                                               // close to other
                                                               // structures

              id = chunk0.getBlockID(x0 & 15, y0 - 1, z0 & 15);
              // if (id != 0 && !Fluids.stillWater.isSameLiquid(id) &&
              // !Gases.isGas[id]) break;
              if (id == 0) continue;
              if (Fluids.isLiquid[id]) continue;
              if (Gases.isGas[id]) continue;
              Block b = Block.blocksList[id];
              if (b != null && !b.blockMaterial.isSolid()) continue;
              break;
            }
            /* Count neighbours in the same plane */
            /*
             * int neighbours = 0; for (int dx = -1; dx <= 1; dx++) for (int dz
             * = -1; dz <= 1; dz++) { Chunk c2 = ChunkCache.getChunk(w, (x0 +
             * dx) >> 4, (z0 + dz) >> 4, false); if (c2 == null) continue; int
             * id2 = c2.getBlockID((x0 + dx) & 15, y0, (z0 + dz) & 15); if (id2
             * == 0 || Fluids.isLiquid[id2] || Gases.isGas[id2]) continue;
             * neighbours++; } if (neighbours <= 4 && cooldownAttempt != 0)
             * break;
             */
            if (cooldownAttempt != 0 && nNeighbours < 2 && adjacentLava < 3) break;

            BlockFFFluid.preventSetBlockLiquidFlowover = true;
            int r2 = (FysiksFun.rand.nextInt(101) + Counters.tick * 4711) % 10;
            // if (r2 < 3)
            FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0, Block.stone.blockID, 0, 0);
            // else if (r2 < 6) FysiksFun.setBlockWithMetadataAndPriority(w, x0,
            // y0, z0, Block.cobblestone.blockID, 0, 0);
            // else FysiksFun.setBlockWithMetadataAndPriority(w, x0, y0, z0,
            // Block.gravel.blockID, 0, 0);
            BlockFFFluid.preventSetBlockLiquidFlowover = false;
          }
          // System.out.println("Turning lava into stone: "+x0+" "+y0+" "+z0);
        } else {
          if (FysiksFun.rand.nextInt(5) == 0) break;
          /* TODO: change these to include all forms of stone generated by the volcano itself */
          if (id == Block.dirt.blockID || id == Block.stone.blockID || id == Block.gravel.blockID) continue;
          else break;
        }
      }
    }
  }
}
