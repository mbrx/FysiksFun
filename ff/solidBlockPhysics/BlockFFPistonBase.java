package mbrx.ff.solidBlockPhysics;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkMarkUpdater;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockPistonMoving;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class BlockFFPistonBase extends BlockPistonBase {

  boolean isSticky;

  public BlockFFPistonBase(int par1, boolean isSticky) {
    super(par1, isSticky);
    this.isSticky = isSticky;
  }

  /**
   * checks the block to that side to see if it is indirectly powered.
   */
  public boolean isIndirectlyPowered(World par1World, int x, int y, int z, int direction) {
    return direction != 0 && par1World.getIndirectPowerOutput(x, y - 1, z, 0) ? true
        : (direction != 1 && par1World.getIndirectPowerOutput(x, y + 1, z, 1) ? true
            : (direction != 2 && par1World.getIndirectPowerOutput(x, y, z - 1, 2) ? true
                : (direction != 3 && par1World.getIndirectPowerOutput(x, y, z + 1, 3) ? true : (direction != 5
                    && par1World.getIndirectPowerOutput(x + 1, y, z, 5) ? true : (direction != 4 && par1World.getIndirectPowerOutput(x - 1, y, z, 4) ? true
                    : (par1World.getIndirectPowerOutput(x, y, z, 0) ? true : (par1World.getIndirectPowerOutput(x, y + 2, z, 1) ? true : (par1World
                        .getIndirectPowerOutput(x, y + 1, z - 1, 2) ? true : (par1World.getIndirectPowerOutput(x, y + 1, z + 1, 3) ? true : (par1World
                        .getIndirectPowerOutput(x - 1, y + 1, z, 4) ? true : par1World.getIndirectPowerOutput(x + 1, y + 1, z, 5)))))))))));
  }

  public boolean onBlockEventReceived(World w, int x, int y, int z, int eventId, int direction) {
    if (!w.isRemote) {
      /* This hides/shows the head of the piston on client side */
      boolean flag = this.isIndirectlyPowered(w, x, y, z, direction);
      if (flag && eventId == 1) {
        w.setBlockMetadataWithNotify(x, y, z, direction | 8, 2);
        return false;
      }
      if (!flag && eventId == 0) { return false; }
    }
    /* Only reached on servers */
    if (eventId == 0) {
      /* Event wants to extend the piston out, see if it works */
      if (!this.tryExtend(w, x, y, z, direction, false)) { return false; }
      /* It worked (guess that tryExtend added the PistonExtension?) */
      w.setBlockMetadataWithNotify(x, y, z, direction | 8, 2);
      w.playSoundEffect((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D, "tile.piston.out", 0.5F, w.rand.nextFloat() * 0.25F + 0.6F);
    } else if (eventId == 1) {
      if (!this.tryRepel(w, x, y, z, direction, false)) { return false; }
      w.setBlockMetadataWithNotify(x, y, z, direction, 2);
      w.playSoundEffect((double) x + 0.5D, (double) y + 0.5D, (double) z + 0.5D, "tile.piston.in", 0.5F, w.rand.nextFloat() * 0.25F + 0.6F);
    }
    return false;
  }

  public boolean tryRepel(World w, int x, int y, int z, int direction, boolean simulate) {
    int dx = Facing.offsetsXForSide[direction];
    int dy = Facing.offsetsYForSide[direction];
    int dz = Facing.offsetsZForSide[direction];

    Chunk c0 = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    int meta = c0.getBlockMetadata(x & 15, y, z & 15);
    //if (!isExtended(meta)) return false;
    
    //System.out.println("Repelling: " + Util.xyzString(x, y, z));
    int x1 = x + dx, y1 = y + dy, z1 = z + dz;
    if (y1 < 0 || y1 > 255) return false;
    Chunk c1 = ChunkCache.getChunk(w, x1 >> 4, z1 >> 4, true);
    int id1 = c1.getBlockID(x1 & 15, y1, z1 & 15);
    int meta1 = c1.getBlockMetadata(x1 & 15, y1, z1 & 15);
    if (id1 != Block.pistonExtension.blockID) return false;
    c1.setBlockIDWithMetadata(x1 & 15, y1, z1 & 15, 0, 0);

    /* Calculate how many steps that will be pulled */
    // TODO use the power strength to decide an upper limit on how many steps
    // should be pulled
    int steps;
    if (!isSticky) steps = 1;
    else for (steps = 1; steps < 256; steps++) {
      int xN = x + dx * (steps + 1);
      int yN = y + dy * (steps + 1);
      int zN = z + dz * (steps + 1);
      Chunk cN = ChunkCache.getChunk(w, xN >> 4, zN >> 4, true);
      if(yN<0 || yN > 255) break;      
      int idN = cN.getBlockID(xN & 15, yN, zN & 15);
      if(idN == Block.bedrock.blockID) { steps--; break; }
      if (idN == 0 || Fluids.isLiquid[idN] || Gases.isGas[idN]) break;
      Block bN = Block.blocksList[idN];
      if(!bN.isOpaqueCube()) { steps++; break; }
    }
    System.out.println("Repell steps: " + steps);
    /* Next, pull in all these blocks */
    for (int step2 = 1; step2 < steps; step2++) {
      /** Step 2 is the destination block */
      int xD = x + dx * step2;
      int yD = y + dy * step2;
      int zD = z + dz * step2;
      int xS = x + dx * (step2 + 1);
      int yS = y + dy * (step2 + 1);
      int zS = z + dz * (step2 + 1);
      if (yS > 255 || yS < 0) continue;
      FysiksFun.moveBlock(w, xD, yD, zD, xS, yS, zS, true, step2 == steps - 1);
    }

    ChunkMarkUpdater.scheduleBlockMark(w, x1, y1, z1, id1, meta1);
    /*w.setBlock(x1, y1, z1, Block.pistonMoving.blockID, direction | (isSticky ? 8 : 0), 4);
    w.setBlockTileEntity(x1, y1, z1,
        BlockPistonMoving.getTileEntity(Block.pistonExtension.blockID, direction | (isSticky ? 8 : 0), direction, false, false));
     */
    return true;
  }

  public boolean tryExtend(World w, int x, int y, int z, int direction, boolean simulate) {
    Chunk c0 = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    int meta = c0.getBlockMetadata(x & 15, y, z & 15);
    if (isExtended(meta)) return false;

    //System.out.println("Extending: " + Util.xyzString(x, y, z));

    int dx = Facing.offsetsXForSide[direction];
    int dy = Facing.offsetsYForSide[direction];
    int dz = Facing.offsetsZForSide[direction];
    /** The number of blocks that will be pushed */
    int steps;
    for (steps = 0; steps < 256; steps++) {
      int x2 = x + dx * (steps + 1);
      int y2 = y + dy * (steps + 1);
      int z2 = z + dz * (steps + 1);
      if (y2 < 0 || y2 > 255) continue;
      Chunk c = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, true);
      int id = c.getBlockID(x2 & 15, y2, z2 & 15);
      if (id == 0 || Fluids.isLiquid[id] || Gases.isGas[id]) break;
    }
    if (steps == 256) return false;
    if (simulate) return true;
    /* Now, start shifting the blocks to the end */
    int step2;
    for (step2 = steps; step2 > 0; step2--) {
      int xD = x + dx * (step2 + 1);
      int yD = y + dy * (step2 + 1);
      int zD = z + dz * (step2 + 1);
      int xS = x + dx * step2;
      int yS = y + dy * step2;
      int zS = z + dz * step2;
      if (yD < 0 || yD > 255) return true;
      FysiksFun.moveBlock(w, xD, yD, zD, xS, yS, zS, true, false);
    }
    int x1 = x + dx;
    int y1 = y + dy;
    int z1 = z + dz;
    Chunk c1 = ChunkCache.getChunk(w, x1 >> 4, z1 >> 4, true);
    c1.setBlockIDWithMetadata(x1 & 15, y1, z1 & 15, Block.pistonMoving.blockID, direction | (isSticky ? 8 : 0));
    ChunkMarkUpdater.scheduleBlockMark(w, x1, y1, z1, -1, -1);
    // w.setBlock(x1, y1, z1, Block.pistonMoving.blockID, direction | (isSticky
    // ? 8 : 0), 4);
    w.setBlockTileEntity(x1, y1, z1, BlockPistonMoving.getTileEntity(Block.pistonExtension.blockID, direction | (isSticky ? 8 : 0), direction, true, true));
    return true;
  }

  /**
   * handles attempts to extend or retract the piston.
   */
  private void updatePistonState(World w, int x, int y, int z) {
    int meta = w.getBlockMetadata(x, y, z);
    int dir = getOrientation(meta);
    if (dir != 7) {
      boolean flag = this.isIndirectlyPowered(w, x, y, z, dir);

      if (flag && !isExtended(meta)) {
        if (tryExtend(w, x, y, z, dir, true)) {
          w.addBlockEvent(x, y, z, this.blockID, 0, dir);
        }
      } else if (!flag && isExtended(meta)) {
        w.setBlockMetadataWithNotify(x, y, z, dir, 2);
        w.addBlockEvent(x, y, z, this.blockID, 1, dir);
      }
    }
  }

  /**
   * Called when the block is placed in the world.
   */
  public void onBlockPlacedBy(World par1World, int par2, int par3, int par4, EntityLivingBase par5EntityLivingBase, ItemStack par6ItemStack) {
    int l = determineOrientation(par1World, par2, par3, par4, par5EntityLivingBase);
    par1World.setBlockMetadataWithNotify(par2, par3, par4, l, 2);

    if (!par1World.isRemote) {
      this.updatePistonState(par1World, par2, par3, par4);
    }
  }

  /**
   * Lets the block know when one of its neighbor changes. Doesn't know which
   * neighbor changed (coordinates passed are their own) Args: x, y, z, neighbor
   * blockID
   */
  public void onNeighborBlockChange(World par1World, int par2, int par3, int par4, int par5) {
    if (!par1World.isRemote) {
      this.updatePistonState(par1World, par2, par3, par4);
    }
  }

  /**
   * Called whenever the block is added into the world. Args: world, x, y, z
   */
  public void onBlockAdded(World par1World, int par2, int par3, int par4) {
    if (!par1World.isRemote && par1World.getBlockTileEntity(par2, par3, par4) == null) {
      this.updatePistonState(par1World, par2, par3, par4);
    }
  }

  @SideOnly(Side.CLIENT)
  public void registerIcons(IconRegister par1IconRegister) {
    Block.pistonBase.registerIcons(par1IconRegister);
    super.registerIcons(par1IconRegister);
  }

}
