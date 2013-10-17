package mbrx.ff;

import java.util.Hashtable;
import java.util.logging.Level;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import cpw.mods.fml.common.registry.GameRegistry;

public class Fluids {
  public static BlockFluid flowingWater, stillWater;
  public static BlockFluid flowingLava, stillLava;

  public static int        nLiquids;
  public static int        liquidIDs[] = new int[256];
  public static boolean    isLiquid[]  = new boolean[4096];
  public static BlockFluid fluid[]     = new BlockFluid[4096];

  private static class WorldUpdateState {
    int sweepY;
  };

  private static Hashtable<World, WorldUpdateState> worldUpdateState = new Hashtable<World, WorldUpdateState>();

  public static void registerLiquidBlock(BlockFluid block) {
    liquidIDs[nLiquids++] = block.blockID;
    isLiquid[block.blockID] = true;
    fluid[block.blockID] = block;
  }

  public static void load() {
    for (int i = 0; i < 4096; i++) {
      isLiquid[i] = false;
      fluid[i] = null;
    }
  }

  public static void postInit() {

    /**** Water ****/
    // Remove old water blocks (still/moving)
    Block.blocksList[Block.waterMoving.blockID] = null;
    Block.blocksList[Block.waterStill.blockID] = null;

    // Register new blocks
    flowingWater = new BlockFluid(Block.waterMoving.blockID, Material.water, Block.waterStill.blockID, Block.waterMoving.blockID, "water");
    stillWater = new BlockFluid(Block.waterStill.blockID, Material.water, Block.waterStill.blockID, Block.waterMoving.blockID, "water");
    flowingWater.setLiquidUpdateRate(20); // was: 10);
    flowingWater.setTickRandomly(false);
    stillWater.setLiquidUpdateRate(20); // was: 10);
    stillWater.setTickRandomly(false);
    flowingWater.canCauseErosion = true;
    stillWater.canCauseErosion = true;
    flowingWater.canSeepThrough = true;
    stillWater.canSeepThrough = true;

    GameRegistry.registerBlock(flowingWater, "waterFlowing");
    GameRegistry.registerBlock(stillWater, "waterStill");

    /**** Lava ****/
    // Remove old lava blocks (still/moving)
    Block.blocksList[Block.lavaMoving.blockID] = null;
    Block.blocksList[Block.lavaStill.blockID] = null;

    // Register new blocks
    flowingLava = new BlockFluid(Block.lavaMoving.blockID, Material.lava, Block.lavaStill.blockID, Block.lavaMoving.blockID, "lava");
    stillLava = new BlockFluid(Block.lavaStill.blockID, Material.lava, Block.lavaStill.blockID, Block.lavaMoving.blockID, "lava");
    flowingLava.setLiquidUpdateRate(60);
    stillLava.setLiquidUpdateRate(60);
    flowingLava.setTickRandomly(false);
    stillLava.setTickRandomly(false);
    flowingLava.canCauseErosion = true;
    stillLava.canCauseErosion = true;
    flowingLava.canSeepThrough = false;
    stillLava.canSeepThrough = false;
    GameRegistry.registerBlock(flowingLava, "lavaFlowing");
    GameRegistry.registerBlock(stillLava, "lavaStill");

    registerLiquidBlock(stillWater);
    registerLiquidBlock(flowingWater);
    registerLiquidBlock(stillLava);
    registerLiquidBlock(flowingLava);

    if (FysiksFun.settings.flowingLiquidOil) {
      patchModLiquid("oilStill", "oilMoving", 20, false, false);
    }
    if (FysiksFun.settings.flowingHydrochloricAcid) {
      patchModLiquid("Still Hydrochloric Acid", "Flowing Hydrochloric Acid", 20, true, true);
    }

  }

  private static void patchModLiquid(String stillName, String flowingName, int updateRate, boolean causesErosion, boolean leaksThrough) {

    Block oldBlockFlowing = null;
    Block oldBlockStill = null;
    for (Block b : Block.blocksList) {
      if (b != null && b.getUnlocalizedName() != null && b.getUnlocalizedName().matches("tile." + flowingName)) oldBlockFlowing = b;
      if (b != null && b.getUnlocalizedName() != null && b.getUnlocalizedName().matches("tile." + stillName)) oldBlockStill = b;
    }
    if (oldBlockFlowing == null) {
      FysiksFun.logger.log(Level.WARNING, "Cannot patch behaviour of block:" + "tile." + flowingName + " since it was not found (is the mod installed?)");
    }
    if (oldBlockStill == null) {
      FysiksFun.logger.log(Level.WARNING, "Cannot patch behaviour of block:" + "tile." + stillName + " since it was not found (is the mod installed?)");
    }
    if (oldBlockFlowing != null && oldBlockStill != null && FysiksFun.settings.flowingLiquidOil) {
      Block.blocksList[oldBlockFlowing.blockID] = null;
      Block.blocksList[oldBlockStill.blockID] = null;

      // Register new blocks
      BlockFluid blockFlowing = new BlockFluid(oldBlockFlowing, oldBlockFlowing.blockID, oldBlockFlowing.blockMaterial, oldBlockStill.blockID,
          oldBlockFlowing.blockID, "oil");
      BlockFluid blockStill = new BlockFluid(oldBlockStill, oldBlockStill.blockID, oldBlockStill.blockMaterial, oldBlockStill.blockID, oldBlockFlowing.blockID,
          "oil");
      blockFlowing.setLiquidUpdateRate(updateRate);
      blockStill.setLiquidUpdateRate(updateRate);
      blockFlowing.setTickRandomly(false);
      blockStill.setTickRandomly(false);
      blockFlowing.canCauseErosion = causesErosion;
      blockStill.canCauseErosion = causesErosion;
      blockFlowing.canSeepThrough = leaksThrough;
      blockStill.canSeepThrough = leaksThrough;
      GameRegistry.registerBlock(blockFlowing, flowingName);
      GameRegistry.registerBlock(blockStill, stillName);

      registerLiquidBlock(blockStill);
      registerLiquidBlock(blockFlowing);
    }

  }

  /**
   * Performs random expensive ticks on every block in a randomly selected layer
   * of each chunk it is called for (chunk center XZ).
   */
  public static void doWorldTick(World w) {
    WorldUpdateState wstate = worldUpdateState.get(w);
    if (wstate == null) {
      wstate = new WorldUpdateState();
      worldUpdateState.put(w, wstate);
    }
    for (int step = 0; step < 20; step++) {
      wstate.sweepY--;
      if (wstate.sweepY < 2) wstate.sweepY = 254;
      int y = wstate.sweepY;
      for (Object o : w.activeChunkSet) {
        ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
        Chunk c = w.getChunkFromChunkCoords(xz.chunkXPos, xz.chunkZPos);
        int x = xz.chunkXPos<<4;
        int z = xz.chunkZPos<<4;

        for (int dx = 0; dx < 16; dx++)
          for (int dz = 0; dz < 16; dz++) {
            int id = c.getBlockID(dx, y, dz);
            if (isLiquid[id]) {
              BlockFluid b = (BlockFluid) Block.blocksList[id];
              b.updateTickSafe(w, c, x + dx, y, z+dz, FysiksFun.rand);
            }
          }
      }
    }
  }

}
