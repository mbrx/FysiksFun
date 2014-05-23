package mbrx.ff;

import java.util.Random;
import java.util.logging.Level;

import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.Counters;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import cpw.mods.fml.common.IWorldGenerator;

public class WorldSupportGenerator implements IWorldGenerator {

  public WorldSupportGenerator() {}

  @Override
  public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
    Chunk c;
    synchronized (FysiksFun.vanillaMutex) {
      c = chunkGenerator.provideChunk(chunkX, chunkZ);
    }
    switch (world.provider.dimensionId) {
    case 1:
      // No physics and no support pillars needed
      break;
    case -1:
      generateFlyingSupports(world, c, chunkX << 4, chunkZ << 4);
      break;
    case 0:
      generatePillarSupports(world, c, chunkX << 4, chunkZ << 4);
      break;
    default:
      // This is most likely Mythcraft or other such worlds. Assume that they
      // are "normal"
      generatePillarSupports(world, c, chunkX << 4, chunkZ << 4);
      break;
    }
  }

  private void generatePillarSupports(World world, Chunk c, int x, int z) {
    if (FysiksFun.settings.worldSupportBlockID <= 0) {
      // System.out.println("warning... worldSupportBlockID: "+FysiksFun.settings.worldSupportBlockID);
      return;
    }

    for (int tries = 0; tries < 4; tries++) {
      int maxY = 10 + FysiksFun.rand.nextInt(92);
      int dx = FysiksFun.rand.nextInt(16);
      int dz = (FysiksFun.rand.nextInt(16) + Counters.tick) % 16;
      int y;
      for (y = 20; y < maxY; y++) {
        int id = c.getBlockID(dx, y, dz);
        if (id == 0) break;
        if (id == Block.dirt.blockID || id == Block.stone.blockID || id == Block.grass.blockID || id == Block.bedrock.blockID) continue;
        if (Block.blocksList[id] == null) {
          FysiksFun.logger.log(Level.SEVERE, "Bad block id=" + id + " found @" + Util.xyzString(x + dx, y, z + dz) + " while generating world supports");
          break;
        }
        if (Block.blocksList[id] instanceof BlockOre) continue;
        break;
      }
      if (y == 20) continue; // This is a flatworld, don't do any pillars

      y = y - FysiksFun.rand.nextInt(6) - 2;
      if (y <= 8) continue;
      for (int tmp = -FysiksFun.rand.nextInt(8); tmp <= 0; tmp++)
        world.setBlock(x + dx, y + tmp, z + dz, FysiksFun.settings.worldSupportBlockID);
      // c.setBlockIDWithMetadata(dx, tmp, dz, Block.bedrock.blockID, 0);
      // System.out.println("Bedrock support @" + Util.xyzString(x + dx,
      // y, z + dz));
    }
  }

  private void generateFlyingSupports(World world, Chunk c, int x, int z) {
    // FysiksFun.logger.log(Level.SEVERE,
    // "No support for the end/nether in world support generator yet.");

    for (int tries = 0; tries < 8; tries++) {
      int dx = FysiksFun.rand.nextInt(16);
      int dz = (FysiksFun.rand.nextInt(16) + Counters.tick) % 16;
      int y;
      for (y = 8; y < 128; y++) {
        int id = c.getBlockID(dx, y, dz);
        if (id == 0 || Fluids.isLiquid[id]) {
          world.setBlock(x + dx, y - 1, z + dz, Block.bedrock.blockID);
          while (y < 128) {
            y = y + 1;
            id = c.getBlockID(dx, y, dz);
            if (id != 0 && !Fluids.isLiquid[id]) break;
          }
          int tmp = FysiksFun.rand.nextInt(3);
          if (c.getBlockID(dx, y + tmp, dz) == 0) world.setBlock(x + dx, y + tmp, z + dz, Block.bedrock.blockID);
        }
      }
    }

  }

}
