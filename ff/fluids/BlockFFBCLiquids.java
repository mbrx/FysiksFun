package mbrx.ff.fluids;

import java.util.Random;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.util.Icon;
import net.minecraft.util.Vec3;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import buildcraft.BuildCraftEnergy;
import buildcraft.energy.BlockBuildcraftFluid;

public class BlockFFBCLiquids extends BlockBuildcraftFluid {
  public static BlockFFBCLiquids  oil;
  public static BlockFFBCLiquids  fuel;
  /**
   * The corresponding FysikFun BlockFluid object wrapped by this (Buildcraft
   * extended) oil
   */
  public static BlockFFFluid    oilFFWrapped;
  /**
   * The corresponding FysikFun BlockFluid object wrapped by this (Buildcraft
   * extended) fuel
   */
  public static BlockFFFluid    fuelFFWrapped;

  public BlockFFFluid           ffFluid;
  public BlockBuildcraftFluid bcFluidBlock;

  public static void patchBC() {

    {
      BlockBuildcraftFluid origOil = (BlockBuildcraftFluid) BuildCraftEnergy.blockOil;
      Block.blocksList[origOil.blockID] = null;
      oilFFWrapped = new BlockFFFluid(origOil.blockID, origOil.blockMaterial, origOil.blockID, origOil.blockID, "blockOil",origOil);
      Fluids.registerLiquidBlock(oilFFWrapped);
      Block.blocksList[origOil.blockID] = null;
      oil = new BlockFFBCLiquids(origOil.blockID, origOil.getFluid(), origOil.blockMaterial, oilFFWrapped, origOil);
      // oil.setFlammable(canOilBurn).setFlammability(0);
      oil.setUnlocalizedName("blockOil");
      GameRegistry.registerBlock(oil, "blockOil");
    }

    {
      BlockBuildcraftFluid origFuel = (BlockBuildcraftFluid) BuildCraftEnergy.blockFuel;
      Block.blocksList[origFuel.blockID] = null;
      fuelFFWrapped = new BlockFFFluid(origFuel.blockID, origFuel.blockMaterial, origFuel.blockID, origFuel.blockID, "blockFuel",origFuel);
      Fluids.registerLiquidBlock(fuelFFWrapped);
      Block.blocksList[origFuel.blockID] = null;
      fuel = new BlockFFBCLiquids(origFuel.blockID, origFuel.getFluid(), origFuel.blockMaterial, fuelFFWrapped, origFuel);
      fuel.setFlammable(true).setFlammability(5).setParticleColor(0.7F, 0.7F, 0.0F);
      fuel.setUnlocalizedName("blockFuel");
      GameRegistry.registerBlock(fuel, "blockFuel");
    }

    BuildCraftEnergy.blockFuel = fuel;
    BuildCraftEnergy.blockOil = oil;
  }

  public BlockFFBCLiquids(int id, Fluid fluid, Material material, BlockFFFluid toBeWrapped, BlockBuildcraftFluid bcFluidBlock) {
    super(id, fluid, material);
    this.ffFluid = toBeWrapped;
    this.bcFluidBlock = bcFluidBlock;
  }

  public void registerIcons(IconRegister iconRegister) {
    super.registerIcons(iconRegister);
    // Not realy needed, but safe in case a mod somehow retains the old original
    // BCBlock
    bcFluidBlock.registerIcons(iconRegister);
  }

  /*
   * All the wrapped functions (FF's BlockFluid functions that should be called
   * instead of BC's)
   */
  @Override
  public void updateTick(World w, int x, int y, int z, Random r) {
    ffFluid.updateTick(w, x, y, z, r);
  }

  @Override
  public void onNeighborBlockChange(World w, int x, int y, int z, int someId) {
    ffFluid.onNeighborBlockChange(w, x, y, z, someId);
  }

  @Override
  public void onBlockAdded(World w, int x, int y, int z) {
    ffFluid.onBlockAdded(w, x, y, z);
  }

  @Override
  public void breakBlock(World w, int x, int y, int z, int oldId, int oldMetaData) {
    ffFluid.breakBlock(w, x, y, z, oldId, oldMetaData);
  }

  @Override
  public void velocityToAddToEntity(World w, int x, int y, int z, Entity entity, Vec3 velocity) {
    ffFluid.velocityToAddToEntity(w, x, y, z, entity, velocity);
  }

  @Override
  public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
    ffFluid.onBlockExploded(world, x, y, z, explosion);
    super.onBlockExploded(world, x, y, z, explosion);
  }

  @Override
  public boolean isSourceBlock(IBlockAccess world, int x, int y, int z) {
    //System.out.println("isSourceBlock @" + Util.xyzString(x, y, z) + " meta: " + world.getBlockMetadata(x, y, z));
    return world.getBlockId(x, y, z) == blockID && world.getBlockMetadata(x, y, z) == 0;
  }

}
