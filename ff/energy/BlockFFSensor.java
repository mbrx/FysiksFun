package mbrx.ff.energy;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mbrx.ff.FysiksFun;
import mbrx.ff.energy.BlockTurbine.Quality;
import mbrx.ff.energy.BlockTurbine.Type;
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

public class BlockFFSensor extends Block implements ITileEntityProvider {

  public static BlockFFSensor woodLiquidSensor;
  public static BlockFFSensor ironLiquidSensor;
  public static BlockFFSensor goldLiquidSensor;
  public static BlockFFSensor diamondLiquidSensor;

  private Quality             quality;
  private Type                type;
  private String              iconName;

  enum Quality {
    WOOD, IRON, GOLD, DIAMOND
  };

  enum Type {
    LIQUID, GAS
  };

  public BlockFFSensor(int par1, Material par2Material, Quality quality, Type type) {
    super(par1, par2Material);
    this.type = type;
    this.quality = quality;
  }

  public static void init() {
    woodLiquidSensor = new BlockFFSensor(FysiksFun.settings.blockWoodSensorDefaultID, Material.iron, Quality.WOOD, Type.LIQUID);
    woodLiquidSensor.setUnlocalizedName("woodLiquidSensor");
    woodLiquidSensor.setCreativeTab(CreativeTabs.tabBlock);
    woodLiquidSensor.setHardness(2.0F);
    woodLiquidSensor.setIconName("fysiksfun:woodLiquidSensor");
    ItemStack woodLiquidSensorStack = new ItemStack(woodLiquidSensor);
    GameRegistry.addRecipe(woodLiquidSensorStack, "wbw", "rrw", "www", 'w', Block.planks, 'b', Block.fenceIron, 'r', Item.redstone);
    GameRegistry.registerBlock(woodLiquidSensor, "woodLiquidSensor");
    LanguageRegistry.addName(woodLiquidSensor, "woodLiquidSensor");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityWoodLiquidSensor.class, "TileEntityWoodLiquidSensor");

    ironLiquidSensor = new BlockFFSensor(FysiksFun.settings.blockIronSensorDefaultID, Material.iron, Quality.WOOD, Type.LIQUID);
    ironLiquidSensor.setUnlocalizedName("ironLiquidSensor");
    ironLiquidSensor.setCreativeTab(CreativeTabs.tabBlock);
    ironLiquidSensor.setHardness(2.0F);
    ironLiquidSensor.setIconName("fysiksfun:ironLiquidSensor");
    ItemStack ironLiquidSensorStack = new ItemStack(ironLiquidSensor);
    GameRegistry.addRecipe(ironLiquidSensorStack, "ibi", "rri", "iii", 'i', Item.ingotIron, 'b', Block.fenceIron, 'r', Item.redstone);
    GameRegistry.registerBlock(ironLiquidSensor, "ironLiquidSensor");
    LanguageRegistry.addName(ironLiquidSensor, "ironLiquidSensor");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityIronLiquidSensor.class, "TileEntityIronLiquidSensor");
    
    goldLiquidSensor = new BlockFFSensor(FysiksFun.settings.blockGoldSensorDefaultID, Material.iron, Quality.WOOD, Type.LIQUID);
    goldLiquidSensor.setUnlocalizedName("goldLiquidSensor");
    goldLiquidSensor.setCreativeTab(CreativeTabs.tabBlock);
    goldLiquidSensor.setHardness(2.0F);
    goldLiquidSensor.setIconName("fysiksfun:goldLiquidSensor");
    ItemStack goldLiquidSensorStack = new ItemStack(goldLiquidSensor);
    GameRegistry.addRecipe(goldLiquidSensorStack, "gbg", "rrg", "ggg", 'w', Item.ingotGold, 'b', Block.fenceIron, 'r', Item.redstone);
    GameRegistry.registerBlock(goldLiquidSensor, "goldLiquidSensor");
    LanguageRegistry.addName(goldLiquidSensor, "goldLiquidSensor");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityGoldLiquidSensor.class, "TileEntityGoldLiquidSensor");

    diamondLiquidSensor = new BlockFFSensor(FysiksFun.settings.blockDiamondSensorDefaultID, Material.iron, Quality.WOOD, Type.LIQUID);
    diamondLiquidSensor.setUnlocalizedName("diamondLiquidSensor");
    diamondLiquidSensor.setCreativeTab(CreativeTabs.tabBlock);
    diamondLiquidSensor.setHardness(2.0F);
    diamondLiquidSensor.setIconName("fysiksfun:diamondLiquidSensor");
    ItemStack diamondLiquidSensorStack = new ItemStack(diamondLiquidSensor);
    GameRegistry.addRecipe(diamondLiquidSensorStack, "dbd", "rrd", "ddd", 'd', Item.diamond, 'b', Block.fenceIron, 'r', Item.redstone);
    GameRegistry.registerBlock(diamondLiquidSensor, "diamondLiquidSensor");
    LanguageRegistry.addName(diamondLiquidSensor, "diamondLiquidSensor");
    GameRegistry.registerTileEntity(mbrx.ff.energy.TileEntityDiamondLiquidSensor.class, "TileEntityDiamondLiquidSensor");

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

  @Override
  public TileEntity createNewTileEntity(World world) {
    if (type == Type.LIQUID) switch (quality) {
    case WOOD:
      return new TileEntityWoodLiquidSensor();
    case IRON:
      return new TileEntityIronLiquidSensor();
    case GOLD:
      return new TileEntityGoldLiquidSensor();
    case DIAMOND:
      return new TileEntityDiamondLiquidSensor();
    default:
      return null;
    }
    else if (type == Type.GAS) switch (quality) {
    case WOOD:
      return null;
    case IRON:
      return null;
    case GOLD:
      return null;
    case DIAMOND:
      return null;
    default:
      return null;
    }
    else return null;
  }

  @Override
  public boolean canConnectRedstone(IBlockAccess world, int x, int y, int z, int side) {
    return true;
  }

  /*@Override
  public int isProvidingStrongPower(IBlockAccess par1IBlockAccess, int par2, int par3, int par4, int par5) {
    return 15;
  }*/
  public int isProvidingWeakPower(IBlockAccess w, int x, int y, int z, int side) {
    /* Remember to call neighbours when tile change */
    TileEntity entity = w.getBlockTileEntity(x, y, z);
    TileEntityLiquidSensor sensor = (TileEntityLiquidSensor) entity;
    if(sensor == null) return 0;
    return sensor.signal;
  }

  @Override
  public boolean canProvidePower() {
    return true;
  }

}
