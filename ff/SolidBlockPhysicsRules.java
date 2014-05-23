package mbrx.ff;

import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWood;
import net.minecraft.block.ITileEntityProvider;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;
import buildcraft.factory.BlockFrame;
import buildcraft.transport.BlockGenericPipe;

/** Static class containing all SolidBlock physics parameters */
public class SolidBlockPhysicsRules {

  public static int                          blockStrength[]         = new int[4096];
  /**
   * The weight of each blockID, negative weights corresponds to fractional
   * (stochastic) weights
   */
  public static int                          blockWeight[]           = new int[4096];
  public static boolean                      blockDoPhysics[]        = new boolean[4096];
  /**
   * 0 all normal blocks, 1+ blocks not affected by full (breakable) physics.
   * Lower numbers can support higher numbers.
   */
  public static int                          blockDoSimplePhysics[]  = new int[4096];
  public static boolean                      blockIsFragile[]        = new boolean[4096];
  public static boolean                      blockIsSink[]           = new boolean[4096];

  public static int                         elasticStrengthConstant = 120;              // 240;

  
  public static void postInit(Configuration physicsRuleConfig) {

    /* Setup a default value for all blocks */
    setupDefaultPhysicsRules();
    
    /* Initialize the physics-rules file */
    String cat = "physics";
    physicsRuleConfig.addCustomCategoryComment(cat, "All entries in here modify the rules for physics blocks");
    /*
     * For each block, if it exists in the config file use it otherwise add the
     * default value
     */
    WorkerPhysicsSweep.maxChunkDist = physicsRuleConfig.get(cat, "0-maxDistanceForPhysics", "" + WorkerPhysicsSweep.maxChunkDist, "Radius around player in which physics are computed",
        Property.Type.INTEGER).getInt(WorkerPhysicsSweep.maxChunkDist);
    elasticStrengthConstant = physicsRuleConfig.get(cat, "0-elasticStrengthConstant", "" + elasticStrengthConstant,
        "only change this if you know what you do, it effects the total time before physics kick in", Property.Type.INTEGER).getInt(elasticStrengthConstant);

    physicsRuleConfig.get(cat, "1-example-do-full", "true",
        "If true, the full physics is run for this type of blocks, if false the simplified physics may still be applied", Property.Type.BOOLEAN);
    physicsRuleConfig
        .get(
            cat,
            "1-example-do-simplified",
            "0",
            "Integer order of execution of simplified physics. 0 disables simplified physics. Lower numbers can support higher numbers (but never blocks of full-physics). See leaves and vines for example",
            Property.Type.INTEGER);
    physicsRuleConfig.get(cat, "1-example-is-fragile", "true", "If true, the block will break (like glass) when falling", Property.Type.BOOLEAN);
    physicsRuleConfig.get(cat, "1-example-strength", "16",
        "Strength of block, must be less than elasticStrenghtConstant. Typical values 5 - 30 times the weight of the block.", Property.Type.INTEGER);
    physicsRuleConfig.get(cat, "1-example-weight", "4", "Weight of this block, typical values 1 - 16", Property.Type.INTEGER);
    physicsRuleConfig.get(cat, "1-example-is-sink", "false", "Sinks for forces that prevents all connected blocks from falling", Property.Type.BOOLEAN);

    physicsRuleConfig.get(cat, "2-start-of-rules", "", "Here comes the rules for each block in the game", Property.Type.STRING);

    for (int i = 0; i < 4096; i++) {
      Block b = Block.blocksList[i];
      if (i == 0 || b == null || b.blockID == 0) continue;
      String name = b.getUnlocalizedName().replace("tile.", "");
      if (i == Block.cobblestone.blockID) name = "cobbleStone";
      if (i == Block.stoneBrick.blockID) name = "stoneBrick";

      SolidBlockPhysicsRules.blockDoPhysics[i] = physicsRuleConfig.get(cat, name + "-do-full", SolidBlockPhysicsRules.blockDoPhysics[i] ? "true" : "false", null, Property.Type.BOOLEAN).getBoolean(
          SolidBlockPhysicsRules.blockDoPhysics[i]);
      if (!SolidBlockPhysicsRules.blockDoPhysics[i])
        SolidBlockPhysicsRules.blockDoSimplePhysics[i] = physicsRuleConfig.get(cat, name + "-do-simplified", "" + SolidBlockPhysicsRules.blockDoSimplePhysics[i], null, Property.Type.INTEGER).getInt(
            SolidBlockPhysicsRules.blockDoSimplePhysics[i]);
      if (SolidBlockPhysicsRules.blockDoPhysics[i] || SolidBlockPhysicsRules.blockDoSimplePhysics[i] != 0) {
        blockIsFragile[i] = physicsRuleConfig.get(cat, name + "-is-fragile", blockIsFragile[i] ? "true" : "false", null, Property.Type.BOOLEAN).getBoolean(
            blockIsFragile[i]);
        blockStrength[i] = physicsRuleConfig.get(cat, name + "-strength", "" + blockStrength[i], null, Property.Type.INTEGER).getInt(blockStrength[i]);
        blockWeight[i] = physicsRuleConfig.get(cat, name + "-weight", "" + blockWeight[i], null, Property.Type.INTEGER).getInt(blockWeight[i]);
        blockIsSink[i] = physicsRuleConfig.get(cat, name + "-is-sink", blockIsSink[i] ? "true" : "false", null, Property.Type.BOOLEAN).getBoolean(
            SolidBlockPhysicsRules.blockDoPhysics[i]);
      }
    }
    blockIsSink[FysiksFun.settings.blockSupportBlockDefaultID] = true;
  }

  public static void setupDefaultPhysicsRules() {
    int rubWood = Util.findBlockIdFromName("blockRubWood");

    for (int i = 1; i < 4096; i++) {
      Block b = Block.blocksList[i];
      blockIsSink[i] = false;
      blockStrength[i] = 16;
      blockWeight[i] = 4;
      SolidBlockPhysicsRules.blockDoSimplePhysics[i] = 0;
      SolidBlockPhysicsRules.blockDoPhysics[i] = false;
      blockIsFragile[i] = false;
      if (b == null) continue;
      if (b.isOpaqueCube()) SolidBlockPhysicsRules.blockDoPhysics[i] = true;

      /* Default value for all ores */
      if (b instanceof BlockOre) {
        blockStrength[i] = 40;
        blockWeight[i] = 8;
      } else if (FysiksFun.hasBuildcraft && b instanceof BlockGenericPipe) {
        blockStrength[i] = 20;
        blockWeight[i] = 2;
        SolidBlockPhysicsRules.blockDoPhysics[i] = true;
      } else if (FysiksFun.hasBuildcraft && b instanceof BlockFrame) {
        blockStrength[i] = 120;
        blockWeight[i] = 2;
        SolidBlockPhysicsRules.blockDoPhysics[i] = true;
      } else if (b instanceof ITileEntityProvider) {
        blockStrength[i] = 40;
        blockWeight[i] = 4;
        SolidBlockPhysicsRules.blockDoPhysics[i] = true;
      } else if (b instanceof BlockStairs) {
        blockStrength[i] = 20;
        blockWeight[i] = 4;
        SolidBlockPhysicsRules.blockDoPhysics[i] = true;
      } else if (b instanceof BlockFence) {
        blockStrength[i] = 20;
        blockWeight[i] = 3;
        SolidBlockPhysicsRules.blockDoPhysics[i] = true;
        blockIsFragile[i] = true;
      } else if (b instanceof BlockLog || i == rubWood) {
        blockStrength[i] = 120; // 30 times weight, needed to avoid trees from
                                // breaking!
        blockWeight[i] = 4;
      } else if (b instanceof BlockWood) { // Poor name in vanilla, this is the
                                           // planks!!
        blockStrength[i] = 50; // 25 times weight
        blockWeight[i] = 2;
      }
      if (b instanceof BlockBreakable) blockIsFragile[i] = true;

      if (Fluids.isLiquid[i] || Gases.isGas[i] || i == 0) SolidBlockPhysicsRules.blockDoPhysics[i] = false;
      // if (!SolidBlockPhysicsRules.blockDoPhysics[i]) continue;
    }

    /*
     * SolidBlockPhysicsRules.blockDoPhysics[Block.leaves.blockID] = true;
     * blockStrength[Block.leaves.blockID] = 10; // 100 times weight!
     * blockWeight[Block.leaves.blockID] = -10;
     */

    SolidBlockPhysicsRules.blockDoPhysics[Block.bedrock.blockID] = true;
    blockWeight[Block.bedrock.blockID] = 0;
    blockIsSink[Block.bedrock.blockID] = true;

    SolidBlockPhysicsRules.blockDoPhysics[Block.leaves.blockID] = false;
    SolidBlockPhysicsRules.blockDoSimplePhysics[Block.leaves.blockID] = 1;
    /*blockWeight[Block.leaves.blockID] = 1;
    blockStrength[Block.leaves.blockID] = 10;*/

    SolidBlockPhysicsRules.blockDoPhysics[Block.vine.blockID] = false;
    SolidBlockPhysicsRules.blockDoSimplePhysics[Block.vine.blockID] = 2;

    blockStrength[Block.gravel.blockID] = 4;
    blockWeight[Block.gravel.blockID] = 4;

    blockStrength[Block.dirt.blockID] = 4;
    blockWeight[Block.dirt.blockID] = 1;

    blockStrength[Block.sand.blockID] = 4;
    blockStrength[Block.cobblestone.blockID] = 80; // 10 times weight
    blockWeight[Block.cobblestone.blockID] = 8;

    blockStrength[Block.stone.blockID] = 200; // 50 times weight
    blockWeight[Block.stone.blockID] = 4; // stone is unplaceable, low weight
                                          // for now!
    blockStrength[Block.stoneBrick.blockID] = 180; // 30 times weight
    blockWeight[Block.stoneBrick.blockID] = 6;
    blockStrength[Block.brick.blockID] = 160; // 40 times weight
    blockWeight[Block.brick.blockID] = 4;

    SolidBlockPhysicsRules.blockDoPhysics[Block.thinGlass.blockID] = true;
    blockIsFragile[Block.thinGlass.blockID] = true;
    blockStrength[Block.thinGlass.blockID] = 5; // 5 times weight
    blockWeight[Block.thinGlass.blockID] = 1;
    SolidBlockPhysicsRules.blockDoPhysics[Block.glass.blockID] = true;
    blockStrength[Block.glass.blockID] = 20; // 10 times weight
    blockWeight[Block.glass.blockID] = 2;
    blockIsFragile[Block.glass.blockID] = true;
    blockStrength[Block.glowStone.blockID] = 40; // 20 times weight (it's
    // anyway too expensive
    // to use for this?)
    blockWeight[Block.glowStone.blockID] = 2;
    blockIsFragile[Block.glowStone.blockID] = true;

    blockStrength[Block.fence.blockID] = 10; // 5 times weight
    blockWeight[Block.fence.blockID] = 2;

    blockStrength[Block.blockLapis.blockID] = 180; // 30 times weight
    blockWeight[Block.blockLapis.blockID] = 6;
    blockStrength[Block.blockNetherQuartz.blockID] = 90; // 30 times weight
    blockWeight[Block.blockNetherQuartz.blockID] = 3;
    blockStrength[Block.blockIron.blockID] = 160; // 40 times weight
    blockWeight[Block.blockIron.blockID] = 4;
    blockStrength[Block.blockGold.blockID] = 240; // 30 times weight
    blockWeight[Block.blockGold.blockID] = 8;
    blockStrength[Block.blockDiamond.blockID] = 240; // 80 times weight (!)
    blockWeight[Block.blockDiamond.blockID] = 3;
    blockStrength[Block.blockEmerald.blockID] = 240; // 80 times weight (!)
    blockWeight[Block.blockEmerald.blockID] = 3;

    blockStrength[Block.obsidian.blockID] = 240; // 15 times weight
    blockWeight[Block.obsidian.blockID] = 16;

    blockStrength[Block.blockSnow.blockID] = 4; // 4 times weight
    blockWeight[Block.blockSnow.blockID] = 1;
    blockStrength[Block.ice.blockID] = 15; // 5 times weight
    blockWeight[Block.ice.blockID] = 3;

    blockStrength[Block.hay.blockID] = 8; // 4 times weight
    blockWeight[Block.hay.blockID] = 2;
    blockStrength[Block.cloth.blockID] = 8; // 4 times weight
    blockWeight[Block.cloth.blockID] = 2;

    blockStrength[Block.hardenedClay.blockID] = 120; // 30 times weight
    blockWeight[Block.hardenedClay.blockID] = 4;

    /* Hell */
    // Netherrack is special, modified inside the 'run' method
    // blockStrength[Block.netherrack.blockID] = 16;
    // blockWeight[Block.netherrack.blockID] = 4;
    blockStrength[Block.slowSand.blockID] = 4;
    blockWeight[Block.slowSand.blockID] = 4;
    blockStrength[Block.netherBrick.blockID] = 120; // 30 times weight
    blockWeight[Block.netherBrick.blockID] = 4;

    /* IC2 specifics */
    /* Misc furniture */

    /* Aliases */
    blockStrength[Block.grass.blockID] = blockStrength[Block.dirt.blockID];
    blockWeight[Block.grass.blockID] = blockWeight[Block.dirt.blockID];
    blockStrength[Block.cobblestoneMossy.blockID] = blockStrength[Block.cobblestone.blockID];
    blockWeight[Block.cobblestoneMossy.blockID] = blockWeight[Block.cobblestone.blockID];
    blockStrength[Block.cobblestoneWall.blockID] = blockStrength[Block.cobblestone.blockID];
    blockWeight[Block.cobblestoneWall.blockID] = blockWeight[Block.cobblestone.blockID];
    blockStrength[Block.bookShelf.blockID] = blockStrength[Block.planks.blockID];
    blockWeight[Block.bookShelf.blockID] = blockWeight[Block.planks.blockID];
  }

}
