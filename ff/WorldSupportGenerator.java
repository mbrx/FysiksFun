package mbrx.ff;

import java.util.Random;
import java.util.logging.Level;

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
    Chunk c = chunkGenerator.provideChunk(chunkX, chunkZ);
    switch (world.provider.dimensionId) {
    case 1:
    case -1:
      generateFlyingSupports(world, c, chunkX << 4, chunkZ << 4);
      break;
    case 0:
      generatePillarSupports(world, c, chunkX << 4, chunkZ << 4);
      break;

    }
  }

  private void generatePillarSupports(World world, Chunk c, int x, int z) {
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
      y = y - FysiksFun.rand.nextInt(6) - 2;
      if (y <= 8) continue;
      for (int tmp = -FysiksFun.rand.nextInt(8); tmp <= 0; tmp++)
        world.setBlock(x + dx, y + tmp, z + dz, Block.bedrock.blockID);
      //c.setBlockIDWithMetadata(dx, tmp, dz, Block.bedrock.blockID, 0);
      //System.out.println("Bedrock support @" + Util.xyzString(x + dx, y, z + dz));
    }
  }

  private void generateFlyingSupports(World world, Chunk c, int i, int j) {
    FysiksFun.logger.log(Level.SEVERE, "No support for the end/nether in world support generator yet.");
  }

}
