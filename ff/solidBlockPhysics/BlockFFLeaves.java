package mbrx.ff.solidBlockPhysics;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mbrx.ff.FysiksFun;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Icon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockFFLeaves extends BlockLeaves {

  /**
   * The "super block" that we are extending (even if it is not actually a
   * BlockLeaves)
   */
  BlockLeaves superSpecial;

  public BlockFFLeaves(int id, BlockLeaves leaves) {
    super(id);
    superSpecial = leaves;

    FysiksFun.logger.log(Level.INFO, "Replacing leaves with block id " + id);
    setHardness(leaves.blockHardness);
    setStepSound(leaves.stepSound);
    String unlocalizedName = leaves.getUnlocalizedName();
    String[] parts = unlocalizedName.split("\\.");
    setUnlocalizedName(parts[1]);
  }

  @Override
  public boolean getBlocksMovement(IBlockAccess w, int x, int y, int z) {
    return false;
  }

  @Override
  public boolean isOpaqueCube() {
    return false;
  }

  @Override
  public void registerIcons(IconRegister par1IconRegister) {
    superSpecial.registerIcons(par1IconRegister);
  }

  @SideOnly(Side.CLIENT)
  public Icon getIcon(int par1, int par2) {
    return superSpecial.getIcon(par1, par2);
  }

  public boolean isBlockSolid(IBlockAccess par1IBlockAccess, int par2, int par3, int par4, int par5) {
    return true;
  }

  @Override
  public boolean canCollideCheck(int par1, boolean par2) {
    return true;
  }

  /*
   * public MovingObjectPosition collisionRayTrace(World w, int x, int y, int z,
   * Vec3 start, Vec3 end) { return null; }
   */

  public AxisAlignedBB getCollisionBoundingBoxFromPool(World par1World, int par2, int par3, int par4) {
    return null;
  }

  public void onEntityCollidedWithBlock(World w, int x, int y, int z, Entity entity) {
    /*
     * if(FysiksFun.rand.nextInt(10) == 0) w.setBlockToAir(x, y, z);
     */
    if (FysiksFun.rand.nextInt(5) == 0) entity.setInWeb();
  }

  public int quantityDropped(Random rand) {
    return superSpecial.quantityDropped(rand);
  }
  public int idDropped(int par1, Random par2Random, int par3) {
    return superSpecial.idDropped(par1, par2Random, par3);
  }
  public ArrayList<ItemStack> getBlockDropped(World world, int x, int y, int z, int metadata, int fortune) {
    return superSpecial.getBlockDropped(world,x,y,z,metadata,fortune);
  }


}
