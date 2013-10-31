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
    int seed = 0; // Objects.hashCode((int)winfo.getSeed()) +
                  // Objects.hashCode(winfo.getWorldName());
    // if(w != null) return;
    int volcanoFillY = 3;

    for (Object o : w.activeChunkSet) {
      ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
      Chunk c = w.getChunkFromChunkCoords(xz.chunkXPos, xz.chunkZPos);
      int startX = (xz.chunkXPos << 4) + 8;
      int startZ = (xz.chunkZPos << 4) + 8;
      // ChunkTempData tempData0 = ChunkTempData.getChunk(w, x, 64, z);
      int r = smear(smear(seed + xz.chunkXPos) + xz.chunkZPos);
      // boolean hasVolcano = (r % 311 == 0);
      boolean hasVolcano = ((r / 17) % 17113 < FysiksFun.settings.volcanoFrequency);
      int radius = 1 + ((r / 11) % (FysiksFun.settings.volcanoRadius));

      /* Cool down lava that is exposed to the open air, creating stone or gravel */
      for (int samples = 0; samples < 1; samples++) {
        int x0 = (xz.chunkXPos << 4) + FysiksFun.rand.nextInt(16);
        int z0 = (xz.chunkZPos << 4) + FysiksFun.rand.nextInt(16);
        // If this is a chunk with a volcano, avoid cooling stone in the caldera
        // of the volcano
        if (hasVolcano) {
          int dx = x0 - startX;
          int dz = z0 - startZ;
          if (dx * dx + dz * dz <= radius * radius) continue;
        }
        for (int y0 = 255; y0 >= 1; y0--) {
          int id = c.getBlockID(x0 & 15, y0, z0 & 15);
          if (id == 0) continue;
          if (Fluids.stillLava.isSameLiquid(id)) {
            int content = Fluids.stillLava.getBlockContent(w, x0, y0, z0, 0);
            Fluids.stillLava.setBlockContent(w, x0, y0, z0, 0);
            if (FysiksFun.rand.nextInt(BlockFluid.maximumContent) < content) {
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

      if (hasVolcano) {
        // if (Counters.tick % 30 == 0)
        // System.out.println("Active volcanoe at: " + startX + " " + startZ);
        for (int dx = -radius; dx <= radius; dx++) {
          for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz <= radius * radius) {

              // Fill up lava in the volcano area
              int x0 = startX + dx;
              int z0 = startZ + dz;
              for (int y0 = 1; y0 <= volcanoFillY; y0++) {
                int prevId = w.getBlockId(x0, y0, z0);
                if (prevId != Fluids.stillLava.blockID && prevId != Fluids.flowingLava.blockID) {
                  Fluids.stillLava.setBlockContent(w, x0, y0, z0, BlockFluid.maximumContent);
                } else {
                  int content = Fluids.stillLava.getBlockContent(w, x0, y0, z0);
                  content = Math.min(content + BlockFluid.pressurePerY * 1, BlockFluid.pressureMaximum);
                  content = BlockFluid.pressureMaximum;
                  Fluids.stillLava.setBlockContent(w, x0, y0, z0, content);
                }
              }

              // Debugging - visualizes where the volcanoes are
              if (FysiksFun.settings.visualizeVolcanoes) {
                for (int y0 = 96; y0 < 255; y0++) {
                  w.setBlock(x0, y0, z0, Block.glass.blockID);
                }
              }

              // Make a random walk towards lower pressure that may trigger
              // explosions if the lava finds no way out
              for (int tries = 0; tries < 10; tries++)
                if (FysiksFun.rand.nextInt(FysiksFun.settings.volcanoFeeding) == 0) {
                  int x1 = x0, y1 = volcanoFillY, z1 = z0;
                  int lowestPressure = Fluids.stillLava.getBlockContent(w, x1, y1, z1);

                  for (int steps = 0; steps < 256; steps++) {
                    int x2 = x1, y2 = y1, z2 = z1;
                    for (int dy2 = +1; dy2 >= -1; dy2--)
                      for (int dz2 = -1; dz2 <= 1; dz2++)
                        for (int dx2 = -1; dx2 <= 1; dx2++) {
                          // Limit to the same 10 neighbourhood as pressure
                          // updates
                          // if(dy2 != 0 && (dx2 != 0 || dz2 != 0)) continue;
                          if (y1 + dy2 <= 0 || y1 + dy2 > 255) continue;
                          int id2 = w.getBlockId(x1 + dx2, y1 + dy2, z1 + dz2);
                          if (!Fluids.stillLava.isSameLiquid(id2)) continue;
                          int p = Fluids.stillLava.getBlockContent(w, x1 + dx2, y1 + dy2, z1 + dz2);
                          if (p + FysiksFun.rand.nextInt(3) < lowestPressure) {
                            lowestPressure = p;
                            x2 = x1 + dx2;
                            y2 = y1 + dy2;
                            z2 = z1 + dz2;
                          }
                        }
                    // if(x2 == x1 && y2 == y1 && z2 == z1) /* We have found a
                    // local minima in pressure */
                    // break;
                    // System.out.println("XYZ2: "+x2+" "+y2+" "+z2+" Pressure: "+lowestPressure);
                    x1 = x2;
                    y1 = y2;
                    z1 = z2;
                  }

                  // System.out.println("Volcano flow from "+x0+" "+volcanoFillY+" "+z0+" to "+x1+" "+y1+" "+z1+" pressure here: "+lowestPressure);

                  /* x1,y1,z1 is now the best local minima we have found in the pressure field */
                  if (lowestPressure <= BlockFluid.maximumContent) {
                    Fluids.stillLava.setBlockContent(w, x1, y1, z1, BlockFluid.maximumContent + BlockFluid.pressurePerY * 2);
                    Fluids.stillLava.updateTick(w, x1, y1, z1, FysiksFun.rand);
                    // System.out.println("Adding "+(BlockFluid.maximumContent-lowestPressure)+" lava to "+x1+" "+y1+" "+z1);
                  } else if (lowestPressure > BlockFluid.maximumContent + BlockFluid.pressurePerY * 16) {
                    /* See if it has no free neighbour to flow to, if not make an explosion */
                    boolean makeExplosion = true;
                    for (int dy2 = +1; dy2 >= -1; dy2--)
                      for (int dz2 = -1; dz2 <= 1; dz2++)
                        for (int dx2 = -1; dx2 <= 1; dx2++) {
                          if (y1 + dy2 <= 0 || y1 + dy2 > 255) continue;
                          if (w.getBlockId(x1 + dx2, y1 + dy2, z1 + dz2) == 0) makeExplosion = false;
                          int content2 = Fluids.stillLava.getBlockContent(w, x1 + dx2, y1 + dy2, z1 + dz2);
                          if (content2 < BlockFluid.maximumContent) makeExplosion = false;
                        }

                    // System.out.println(" explode: "+makeExplosion);
                    if (makeExplosion) {
                      // System.out.println("Volcanoe flow from " + x0 + " " +
                      // volcanoFillY + " " + z0 + " to " + x1 + " " + y1 + " "
                      // +
                      // z1 + " pressure here: "
                      // + lowestPressure);
                      w.newExplosion(null, x1, y1 + 1, z1, 1.5f + 1.5f * FysiksFun.rand.nextFloat(), true, true);
                      int explodeRadius = 5;
                      for (int dy2 = -explodeRadius + 1; dy2 <= explodeRadius + 1; dy2++)
                        for (int dz2 = -explodeRadius; dz2 <= explodeRadius; dz2++)
                          for (int dx2 = -explodeRadius; dx2 <= explodeRadius; dx2++) {
                            if (y1 + dy2 > 0 && y1 + dy2 < 255 && dx2 * dx2 + dy2 * dy2 + dz2 * dz2 <= explodeRadius * explodeRadius) {
                              int id2 = w.getBlockId(x1 + dx2, y1 + dy2, z1 + dz2);
                              if (id2 == 0 || Fluids.stillLava.isSameLiquid(id2) || Fluids.stillWater.isSameLiquid(id2))
                                Fluids.stillLava.setBlockContent(w, x1 + dx2, y1 + dy2, z1 + dz2, BlockFluid.maximumContent);
                            }
                          }
                    }
                  }
                }

            }
          }
        }
      }
    }

  }
}
