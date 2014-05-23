package mbrx.ff.fluids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.IPlantable;
import cpw.mods.fml.common.registry.GameRegistry;

public class Fluids {
  public static BlockFFFluid                 flowingWater, stillWater;
  public static BlockFFFluid                 flowingLava, stillLava;

  public static int                          nLiquids;
  public static int                          liquidIDs[]    = new int[256];
  public static boolean                      isLiquid[]     = new boolean[4096];
  public static boolean[]                    canFlowThrough = new boolean[4096];

  public static BlockFFFluid                 asFluid[]      = new BlockFFFluid[4096];
  public static BlockFFFluid                 fluid[]        = new BlockFFFluid[4096];
  public static LinkedList<FluidInteraction> interactions   = new LinkedList<FluidInteraction>();

  public static void registerLiquidBlock(BlockFFFluid block) {
    liquidIDs[nLiquids++] = block.blockID;
    isLiquid[block.blockID] = true;
    fluid[block.blockID] = block;
    asFluid[block.blockID] = block;
  }

  public static void registerFluidInteraction(FluidInteraction interact) {
    interactions.add(interact);
  }

  public static void load() {
    for (int i = 0; i < 4096; i++) {
      isLiquid[i] = false;
      fluid[i] = null;
      asFluid[i] = null;
      canFlowThrough[i] = false;
    }

  }

  public static void postInit() {

    for (int i = 0; i < 4096; i++) {
      Block b = Block.blocksList[i];
      if (b instanceof IPlantable) canFlowThrough[i] = true;
      if (b instanceof BlockDoor) canFlowThrough[i] = true;
    }

    /**** Water ****/
    // Remove old water blocks (still/moving)
    Block.blocksList[Block.waterMoving.blockID] = null;
    Block.blocksList[Block.waterStill.blockID] = null;

    // Register new blocks
    flowingWater = new BlockFFWater(Block.waterMoving.blockID, Material.water, Block.waterStill.blockID, Block.waterMoving.blockID, "water", Block.waterMoving);
    stillWater = new BlockFFWater(Block.waterStill.blockID, Material.water, Block.waterStill.blockID, Block.waterMoving.blockID, "water", Block.waterStill);
    flowingWater.setLiquidUpdateRate(1); // was: 10);
    flowingWater.setTickRandomly(false);
    stillWater.setLiquidUpdateRate(1); // was: 10);
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
    flowingLava = new BlockFFLava(Block.lavaMoving.blockID, Material.lava, Block.lavaStill.blockID, Block.lavaMoving.blockID, "lava", Block.lavaMoving);
    stillLava = new BlockFFLava(Block.lavaStill.blockID, Material.lava, Block.lavaStill.blockID, Block.lavaMoving.blockID, "lava", Block.lavaStill);
    flowingLava.setLiquidUpdateRate(2);
    stillLava.setLiquidUpdateRate(2);
    flowingLava.setTickRandomly(false);
    stillLava.setTickRandomly(false);
    flowingLava.canCauseErosion = true;
    stillLava.canCauseErosion = true;
    flowingLava.erodeMultiplier = 10;
    stillLava.erodeMultiplier = 10;
    flowingLava.canSeepThrough = false;
    stillLava.canSeepThrough = false;
    GameRegistry.registerBlock(flowingLava, "lavaFlowing");
    GameRegistry.registerBlock(stillLava, "lavaStill");

    stillLava.relativeWeight = +10;
    flowingLava.relativeWeight = +10;
    stillLava.viscosity = BlockFFFluid.maximumContent / 4;
    flowingLava.viscosity = BlockFFFluid.maximumContent / 4;

    registerLiquidBlock(stillWater);
    registerLiquidBlock(flowingWater);
    registerLiquidBlock(stillLava);
    registerLiquidBlock(flowingLava);

    if (FysiksFun.hasBuildcraft && FysiksFun.settings.flowingLiquidOil) {
      BlockFFBCLiquids.patchBC();
      // patchModLiquid("blockOil", "blockOil", false, 1, false, false);
      // patchModLiquid("blockFuel", "blockFuel", false, 1, false, false);
    }

    /*
     * if (FysiksFun.settings.flowingHydrochloricAcid) {
     * patchModLiquid("Still Hydrochloric Acid", "Flowing Hydrochloric Acid",
     * true, 1, true, true); }
     */

    registerFluidInteraction(new WaterLavaInteraction());
    registerFluidInteraction(new CombustibleFluidAndLavaInteraction());
  }

  /**
   * Verifies that the liquid blocks are still correct, otherwise writes them
   * back again.
   */
  public static void checkBlockOverwritten() {
    if (Block.blocksList[stillWater.blockID] != stillWater) {
      Block.blocksList[stillWater.blockID] = null;
      GameRegistry.registerBlock(stillWater, "waterStill");
      Block.blocksList[stillWater.blockID] = stillWater;
      FysiksFun.logger.log(Level.INFO,
          "Re-installing water since another mod have overwritten it. If you don't want this, bug me about it and i'll make an option");
    }
    if (Block.blocksList[stillLava.blockID] != stillLava) {
      Block.blocksList[stillLava.blockID] = null;
      GameRegistry.registerBlock(stillLava, "lavaStill");
      Block.blocksList[stillLava.blockID] = stillLava;
      FysiksFun.logger.log(Level.INFO,
          "Re-installing lava since another mod have overwritten it. If you don't want this, bug me about it and i'll make an option");
    }
  }

  private static void patchModLiquid(String stillName, String flowingName, boolean hasTwoNames, int updateRate, boolean causesErosion, boolean leaksThrough) {

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
      BlockFFFluid blockFlowing = new BlockFFFluid(oldBlockFlowing, oldBlockFlowing.blockID, oldBlockFlowing.blockMaterial, oldBlockStill.blockID,
          oldBlockFlowing.blockID, flowingName);
      blockFlowing.setLiquidUpdateRate(updateRate);
      blockFlowing.setTickRandomly(false);
      blockFlowing.canCauseErosion = causesErosion;
      blockFlowing.canSeepThrough = leaksThrough;
      Block.blocksList[oldBlockFlowing.blockID] = blockFlowing;
      GameRegistry.registerBlock(blockFlowing, flowingName);
      registerLiquidBlock(blockFlowing);

      if (hasTwoNames) {
        Block.blocksList[oldBlockStill.blockID] = null;
        BlockFFFluid blockStill = new BlockFFFluid(oldBlockStill, oldBlockStill.blockID, oldBlockStill.blockMaterial, oldBlockStill.blockID,
            oldBlockFlowing.blockID, stillName);
        blockStill.setLiquidUpdateRate(updateRate);
        blockStill.setTickRandomly(false);
        blockStill.canCauseErosion = causesErosion;
        blockStill.canSeepThrough = leaksThrough;
        GameRegistry.registerBlock(blockStill, stillName);
        registerLiquidBlock(blockStill);
      }
    }

  }

  /**
   * Returns true if the two liquids given by blockID's can mix and cause an
   * interaction
   */
  public static boolean liquidCanInteract(int block1, int block2) {    
    if (Block.blocksList[block1] == null || Block.blocksList[block2] == null) return false;
    for (FluidInteraction interaction : interactions) {
      if (interaction.canInteract(Block.blocksList[block1], Block.blocksList[block2])) return true;
    }
    return false;
  }

  /**
   * Create the effect of interaction between the two liquids mixing in the
   * given cell. Returns the amount of incoming liquid should be LEFT after the
   * interaction
   */
  public static int liquidInteract(World w, int x, int y, int z, int incomingBlockID, int incommingAmount, int targetBlockID, int targetAmount) {
    for (FluidInteraction interaction : interactions) {
      if (!interaction.canInteract(Block.blocksList[targetBlockID], Block.blocksList[incomingBlockID])) continue;
      return interaction.liquidInteract(w, x, y, z, incomingBlockID, incommingAmount, targetBlockID, targetAmount);
    }
    FysiksFun.logger.log(Level.WARNING, "Fluids.liquidInteract called for liquids: " + incomingBlockID + " " + targetBlockID
        + " and they have no registered interaction");
    return targetAmount;
  }

}
