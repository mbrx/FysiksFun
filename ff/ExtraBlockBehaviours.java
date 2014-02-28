package mbrx.ff;

import cpw.mods.fml.common.registry.GameRegistry;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Counters;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Implements a few extra behaviours for various blocks, such as grass under water becoming dirt, and dirt under a
 * pillar of water becoming clay.
 */
public class ExtraBlockBehaviours {

  public static void postInit() {
    if(FysiksFun.settings.leavesAreSoft) {
    for (int leavesId = 0; leavesId < 4096; leavesId++) {
      if (!(Block.blocksList[leavesId] instanceof BlockLeaves)) continue;
      BlockLeaves leaves = (BlockLeaves) Block.blocksList[leavesId];
      Block.blocksList[leavesId]=null;
      BlockFFLeaves ffleaves = new BlockFFLeaves(leavesId,leaves);
      Block.blocksList[leavesId]=ffleaves;
      //System.out.println("Attempting to register leaf: "+ffleaves.blockID);
      GameRegistry.registerBlock(ffleaves, "modified-"+leaves.getUnlocalizedName());
    }
    }
    
    if(FysiksFun.settings.stonesShatter) {
    Block stone = Block.stone;
    Block.blocksList[stone.blockID] = null;
    BlockFFStone ffstone = new BlockFFStone(stone.blockID);
    ffstone.setHardness(1.5F).setResistance(10.0F).setStepSound(Block.soundStoneFootstep).setUnlocalizedName("stone").setTextureName("stone");    
    Block.blocksList[stone.blockID] = ffstone;
    GameRegistry.registerBlock(ffstone, "modified-stone");
    }
  }

  public static void doChunkTick(World world, ChunkCoordIntPair xz) {
    int x0 = xz.chunkXPos << 4;
    int z0 = xz.chunkZPos << 4;
    Chunk c = ChunkCache.getChunk(world, xz.chunkXPos, xz.chunkZPos, false);
    if (c == null) return;
    for (int tries = 0; tries < 8; tries++) {
      int dx = (FysiksFun.rand.nextInt(16) + Counters.tick) % 16;
      int dz = (FysiksFun.rand.nextInt(16) + Counters.tick / 16) % 16;
      int y = FysiksFun.rand.nextInt(200) + 1;
      int id = c.getBlockID(dx, y, dz);
      int idAbove = c.getBlockID(dx, y + 1, dz);
      boolean waterAbove = false;
      if(FysiksFun.settings.doFluids) waterAbove = Fluids.stillWater.isSameLiquid(idAbove);
      else waterAbove = idAbove == Block.waterMoving.blockID || idAbove == Block.waterStill.blockID;
      if (id == Block.grass.blockID && waterAbove) {
        FysiksFun.setBlockWithMetadataAndPriority(world, x0 + dx, y, z0 + dz, Block.dirt.blockID, 0, 0);
      } else if (FysiksFun.rand.nextInt(5000) < FysiksFun.settings.clayToDirtChance && id == Block.dirt.blockID && Fluids.stillWater.isSameLiquid(idAbove)) {

        makeClay:
        {
          for (int dy = 2; dy < 5; dy++)
            if (!Fluids.stillWater.isSameLiquid(c.getBlockID(dx, y + dy, dz))) break makeClay;
          FysiksFun.setBlockWithMetadataAndPriority(world, x0 + dx, y, z0 + dz, Block.blockClay.blockID, 0, 0);
        }

      }
    }
  }

}
