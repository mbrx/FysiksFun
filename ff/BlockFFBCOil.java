package mbrx.ff;

import java.util.Random;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import buildcraft.BuildCraftEnergy;
import buildcraft.energy.BlockBuildcraftFluid;

public class BlockFFBCOil extends BlockBuildcraftFluid {
	public static BlockFFBCOil oil;
	public static BlockFFBCOil fuel;
	/**
	 * The corresponding FysikFun BlockFluid object wrapped by this (Buildcraft
	 * extended) oil
	 */
	public static BlockFluid oilFFWrapped;
	/**
	 * The corresponding FysikFun BlockFluid object wrapped by this (Buildcraft
	 * extended) fuel
	 */
	public static BlockFluid fuelFFWrapped;

	public BlockFluid ffFluid;

	public static void patchBC() {

		{
		BlockBuildcraftFluid origOil = (BlockBuildcraftFluid) BuildCraftEnergy.blockOil;
		Block.blocksList[origOil.blockID] = null;
		oilFFWrapped = new BlockFluid(origOil.blockID, origOil.blockMaterial,
				origOil.blockID, origOil.blockID, "blockOil");
		Fluids.registerLiquidBlock(oilFFWrapped);
		Block.blocksList[origOil.blockID] = null;
		oil = new BlockFFBCOil(origOil.blockID, origOil.getFluid(),
				origOil.blockMaterial, oilFFWrapped);
		oil.setUnlocalizedName("blockOil");
		GameRegistry.registerBlock(oil, "blockOil");
		}
		
		{
		BlockBuildcraftFluid origFuel = (BlockBuildcraftFluid) BuildCraftEnergy.blockFuel;
		Block.blocksList[origFuel.blockID] = null;
		fuelFFWrapped = new BlockFluid(origFuel.blockID, origFuel.blockMaterial,
				origFuel.blockID, origFuel.blockID, "blockFuel");
		Fluids.registerLiquidBlock(fuelFFWrapped);
		Block.blocksList[origFuel.blockID] = null;
		fuel = new BlockFFBCOil(origFuel.blockID, origFuel.getFluid(),
				origFuel.blockMaterial, fuelFFWrapped);
		fuel.setUnlocalizedName("blockFuel");
		GameRegistry.registerBlock(fuel, "blockFuel");
		}

	}

	public BlockFFBCOil(int id, Fluid fluid, Material material,
			BlockFluid toBeWrapped) {
		super(id, fluid, material);
		ffFluid = toBeWrapped;
	}

	/*
	 * All the wrapped functions (FF's BlockFluid functions that should be
	 * called instead of BC's)
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
	public void breakBlock(World w, int x, int y, int z, int oldId,
			int oldMetaData) {
		ffFluid.breakBlock(w, x, y, z, oldId, oldMetaData);
	}

	@Override
	public void velocityToAddToEntity(World w, int x, int y, int z,
			Entity entity, Vec3 velocity) {
		ffFluid.velocityToAddToEntity(w, x, y, z, entity, velocity);
	}

	@Override
	public void onBlockExploded(World world, int x, int y, int z,
			Explosion explosion) {
		ffFluid.onBlockExploded(world, x, y, z, explosion);
		super.onBlockExploded(world, x, y, z, explosion);
	}
}
