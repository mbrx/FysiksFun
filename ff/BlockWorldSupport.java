package mbrx.ff;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.world.World;

public class BlockWorldSupport extends BlockStone {

  public static BlockWorldSupport worldSupport;

  public BlockWorldSupport(int par1) {
    super(par1);
  }

  public boolean canPlaceBlockAt(World par1World, int x, int y, int z) {
    return true; // It's anyway impossible to aquire the blocks without cheating. Let creative/cheating guys actually use it...
  }

  public static void init() {
    worldSupport = new BlockWorldSupport(FysiksFun.settings.blockSupportBlockDefaultID);
    worldSupport.setHardness(1.5F).setResistance(10.0F).setStepSound(soundStoneFootstep).setUnlocalizedName("stone").setTextureName("stone");
    worldSupport.setUnlocalizedName("worldSupport");
    //worldSupport.setHardness(2.0F);
    GameRegistry.registerBlock(worldSupport, "worldSupport");
    LanguageRegistry.addName(worldSupport, "worldSupport");
  }

}
