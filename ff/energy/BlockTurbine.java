package mbrx.ff.energy;

import java.util.logging.Level;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mbrx.ff.FysiksFun;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockTurbine extends Block implements ITileEntityProvider {

  public static BlockTurbine woodTurbine;
  public static BlockTurbine ironTurbine;
  public static BlockTurbine goldTurbine;
  public static BlockTurbine diamondTurbine;
  
  public static BlockTurbine ironGasTurbine;
  public static BlockTurbine goldGasTurbine;
  public static BlockTurbine diamondGasTurbine;

  
  public String              iconName;
  public Quality             quality;
  public Type                type;

  enum Quality {
    WOOD, IRON, GOLD, DIAMOND
  };

  enum Type {
    LIQUID, GAS
  };

  /** Registers all turbine block instances (iron/gold etc) that should exist */
  public static void init() {
    
    if(!FysiksFun.settings.doEnergy) return;
    
    /* Very poor perfomance, easy to aquire */
    woodTurbine = new BlockTurbine(FysiksFun.settings.blockWoodTurbineDefaultID, Material.iron, Quality.WOOD, Type.LIQUID);
    woodTurbine.setUnlocalizedName("woodTurbine");
    woodTurbine.setCreativeTab(CreativeTabs.tabBlock);
    woodTurbine.setHardness(2.0F);
    woodTurbine.setIconName("fysiksfun:woodTurbine");
    ItemStack woodTurbineStack = new ItemStack(woodTurbine);
    GameRegistry.addRecipe(woodTurbineStack, "wbw", "rpr", "wbw", 'w', Block.planks, 'b', Block.fenceIron, 'r', Item.redstone, 'p', Block.pistonBase);
    GameRegistry.registerBlock(woodTurbine, "woodTurbine");
    LanguageRegistry.addName(woodTurbine, "woodTurbine");

    /* Standard */
    ironTurbine = new BlockTurbine(FysiksFun.settings.blockIronTurbineDefaultID, Material.iron, Quality.IRON, Type.LIQUID);
    ironTurbine.setUnlocalizedName("ironTurbine");
    ironTurbine.setCreativeTab(CreativeTabs.tabBlock);
    ironTurbine.setHardness(2.0F);
    ironTurbine.setIconName("fysiksfun:ironTurbine");
    ItemStack ironTurbineStack = new ItemStack(ironTurbine);
    GameRegistry.addRecipe(ironTurbineStack, "ibi", "rpr", "xbx", 'i', Item.ingotIron, 'x', Block.blockIron, 'b', Block.fenceIron, 'r', Item.redstone, 'p', Block.pistonBase);
    GameRegistry.registerBlock(ironTurbine, "ironTurbine");
    LanguageRegistry.addName(ironTurbine, "ironTurbine");

    /* Controllable and efficient */
    goldTurbine = new BlockTurbine(FysiksFun.settings.blockGoldTurbineDefaultID, Material.iron, Quality.GOLD, Type.LIQUID);
    goldTurbine.setUnlocalizedName("goldTurbine");
    goldTurbine.setCreativeTab(CreativeTabs.tabBlock);
    goldTurbine.setHardness(2.0F);
    goldTurbine.setIconName("fysiksfun:goldTurbine");
    ItemStack goldTurbineStack = new ItemStack(goldTurbine);
    GameRegistry.addRecipe(goldTurbineStack, "gbg", "rpr", "GbG", 'g', Item.ingotGold, 'G', Block.blockGold, 'b', Block.fenceIron, 'r', Item.redstone, 'p', Block.pistonBase);
    GameRegistry.registerBlock(goldTurbine, "goldTurbine");
    LanguageRegistry.addName(goldTurbine, "goldTurbine");

    /* Robust */
    diamondTurbine = new BlockTurbine(FysiksFun.settings.blockDiamondTurbineDefaultID, Material.iron, Quality.DIAMOND, Type.LIQUID);
    diamondTurbine.setUnlocalizedName("diamondTurbine");
    diamondTurbine.setCreativeTab(CreativeTabs.tabBlock);
    diamondTurbine.setHardness(2.0F);
    diamondTurbine.setIconName("fysiksfun:diamondTurbine");
    ItemStack diamondTurbineStack = new ItemStack(diamondTurbine);
    GameRegistry.addRecipe(diamondTurbineStack, "dbd", "rpr", "DbD", 'd', Item.diamond, 'b', Block.fenceIron, 'r', Item.redstone, 'p', Block.pistonBase, 'D', Block.blockDiamond);
    GameRegistry.registerBlock(diamondTurbine, "diamondTurbine");
    LanguageRegistry.addName(diamondTurbine, "diamondTurbine");

    /* Standard */
    ironGasTurbine = new BlockTurbine(FysiksFun.settings.blockIronGasTurbineDefaultID, Material.iron, Quality.IRON, Type.GAS);
    ironGasTurbine.setUnlocalizedName("ironGasTurbine");
    ironGasTurbine.setCreativeTab(CreativeTabs.tabBlock);
    ironGasTurbine.setHardness(2.0F);
    ironGasTurbine.setIconName("fysiksfun:ironGasTurbine");
    ItemStack ironGasTurbineStack = new ItemStack(ironGasTurbine);
    GameRegistry.addRecipe(ironGasTurbineStack, "IbI", "rpr", "QbQ", 'd', Block.blockIron, 'b', Block.fenceIron, 'r', Item.redstone, 'p', Block.pistonBase, 'D', Block.blockDiamond, 'Q', Block.blockNetherQuartz);
    GameRegistry.registerBlock(ironGasTurbine, "ironGasTurbine");
    LanguageRegistry.addName(ironGasTurbine, "ironGasTurbine");

    /* Controllable, highest efficiency. Cannot survive highest pressure. */
    goldGasTurbine = new BlockTurbine(FysiksFun.settings.blockGoldGasTurbineDefaultID, Material.iron, Quality.GOLD, Type.GAS);
    goldGasTurbine.setUnlocalizedName("goldGasTurbine");
    goldGasTurbine.setCreativeTab(CreativeTabs.tabBlock);
    goldGasTurbine.setHardness(2.0F);
    goldGasTurbine.setIconName("fysiksfun:goldGasTurbine");
    ItemStack goldGasTurbineStack = new ItemStack(goldGasTurbine);
    GameRegistry.addRecipe(goldGasTurbineStack, "GbG", "rpr", "QbQ", 'G', Block.blockGold, 'b', Block.fenceIron, 'r', Item.redstone, 'p', Block.pistonBase, 'D', Block.blockDiamond, 'Q', Block.blockNetherQuartz);
    GameRegistry.registerBlock(goldGasTurbine, "goldGasTurbine");
    LanguageRegistry.addName(goldGasTurbine, "goldGasTurbine");
    
    /* Survives highest pressure */
    diamondGasTurbine = new BlockTurbine(FysiksFun.settings.blockDiamondGasTurbineDefaultID, Material.iron, Quality.DIAMOND, Type.GAS);
    diamondGasTurbine.setUnlocalizedName("diamondGasTurbine");
    diamondGasTurbine.setCreativeTab(CreativeTabs.tabBlock);
    diamondGasTurbine.setHardness(2.0F);
    diamondGasTurbine.setIconName("fysiksfun:diamondGasTurbine");
    ItemStack diamondGasTurbineStack = new ItemStack(diamondGasTurbine);
    GameRegistry.addRecipe(diamondGasTurbineStack, "DbD", "rpr", "QbQ", 'd', Item.diamond, 'b', Block.fenceIron, 'r', Item.redstone, 'p', Block.pistonBase, 'D', Block.blockDiamond, 'Q', Block.blockNetherQuartz);
    GameRegistry.registerBlock(diamondGasTurbine, "diamondGasTurbine");
    LanguageRegistry.addName(diamondGasTurbine, "diamondGasTurbine");
    
    
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityTurbineBase.class, "TileEntityTurbineBase");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityLiquidTurbine.class, "TileEntityLiquidTurbine");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityWoodTurbine.class, "TileEntityWoodTurbine");    
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityIronTurbine.class, "TileEntityIronTurbine");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityGoldTurbine.class, "TileEntityGoldTurbine");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityDiamondTurbine.class, "TileEntityDiamondTurbine");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityIronGasTurbine.class, "TileEntityIronGasTurbine");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityGoldGasTurbine.class, "TileEntityGoldGasTurbine");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityDiamondGasTurbine.class, "TileEntityDiamondGasTurbine");
  }
  

  public BlockTurbine(int par1, Material par2Material, Quality quality, Type type) {
    super(par1, par2Material);
    this.quality = quality;
    this.type = type;
  }

  public void registerIcons(IconRegister iconRegister) {
    blockIcon = iconRegister.registerIcon(iconName);
  }

  @SideOnly(Side.CLIENT)
  @Override
  public Icon getIcon(int side, int metaData) {
    return blockIcon;
  }

  private void setIconName(String name) {
    this.iconName = name;
  }

  public boolean canConnectRedstone(IBlockAccess world, int x, int y, int z, int side) {
    if(quality == Quality.GOLD) return true; 
    return false;
  }

  @Override
  public TileEntity createNewTileEntity(World world) {
    if (type == Type.LIQUID) switch (quality) {
    case WOOD:
      return new TileEntityWoodTurbine();
    case IRON:
      return new TileEntityIronTurbine();
    case GOLD:
      return new TileEntityGoldTurbine();
    case DIAMOND:
      return new TileEntityDiamondTurbine();
    default:
      return null;
    }
    else if (type == Type.GAS) switch (quality) {
    case WOOD:
      return null;
    case IRON:
      return new TileEntityIronGasTurbine();
    case GOLD:
      return new TileEntityGoldGasTurbine();
    case DIAMOND:
      return new TileEntityDiamondGasTurbine();
    default:
      return null;
    }
    else return null;

  }
}
