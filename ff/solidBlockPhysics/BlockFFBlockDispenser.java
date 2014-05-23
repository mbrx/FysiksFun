package mbrx.ff.solidBlockPhysics;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mbrx.ff.FysiksFun;
import mbrx.ff.TileEntityFFBlockDispenser;
import mbrx.ff.energy.BlockTurbine;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockSourceImpl;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeDirection;

public class BlockFFBlockDispenser extends BlockDispenser {

  public static BlockFFBlockDispenser blockBlockDispenser;
  public String                       iconName;
  private Icon sideIcon;
  private Icon headIcon;

  public static void init() {

    blockBlockDispenser = new BlockFFBlockDispenser(FysiksFun.settings.blockBlockDispenserDefaultID);
    blockBlockDispenser.setUnlocalizedName("blockDispenser");
    blockBlockDispenser.setCreativeTab(CreativeTabs.tabInventory);
    blockBlockDispenser.setHardness(2.0F);
    // blockBlockDispenser.setIconName("fysiksfun:blockDispenser");
    ItemStack blockBlockDispenserStack = new ItemStack(blockBlockDispenser);
    GameRegistry.addRecipe(blockBlockDispenserStack, "mmm", "cpm", "mmm", 'm', Block.cobblestoneMossy, 'c', Block.chest, 'p', Block.pistonBase);
    GameRegistry.registerBlock(blockBlockDispenser, "blockDispenser");
    LanguageRegistry.addName(blockBlockDispenserStack, "blockDispenser");

    GameRegistry.registerTileEntity(mbrx.ff.TileEntityFFBlockDispenser.class, "TileEntityFFBlockDispenser");
  }

  public BlockFFBlockDispenser(int par1) {
    super(par1);
  }

  public Icon getIcon(int side, int metaData)
  {
      int k = metaData & 7;
      return side == k ? this.headIcon : this.sideIcon;
  }

  public void registerIcons(IconRegister par1IconRegister)
  {
      this.sideIcon = par1IconRegister.registerIcon("fysiksfun:blockDispenserSide");
      this.headIcon = par1IconRegister.registerIcon("fysiksfun:blockDispenserHead");
  }
  
  @Override
  public TileEntity createNewTileEntity(World world) {
      return (TileEntity) new TileEntityFFBlockDispenser();
  }
  
  /*
   * 
  @Override
  public TileEntity createNewTileEntity(World world) {
    return null;
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
  */

  /*
  public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer ply, int side, float xOffset, float yOffset, float zOffset) {
      if (w.isRemote) {
          return true;
      } else {
          TileEntityDispenser tileentitydispenser = (TileEntityDispenser)w.getBlockTileEntity(x, y, z);

          if (tileentitydispenser != null)
          {
              ply.displayGUIDispenser(tileentitydispenser);
          }

          return true;
      }
  }*/

  protected void dispense(World w, int x, int y, int z) {}

  protected void doDispense(World w, int x, int y, int z) {
    TileEntityFFBlockDispenser tileentitydispenser = (TileEntityFFBlockDispenser) w.getBlockTileEntity(x, y, z);

    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    int direction = c.getBlockMetadata(x & 15, y, z & 15);

    if (tileentitydispenser != null) {      
      //int l = tileentitydispenser.getRandomStackFromInventory();
      int l = tileentitydispenser.nextPosition;
      int tries;
      for(tries=0;tries<9;tries++) {
        if(tileentitydispenser.getStackInSlot(l) != null) break;
        tileentitydispenser.nextPosition = (tileentitydispenser.nextPosition+1)%9;
        l = tileentitydispenser.nextPosition;
      }
      if (tries == 9) {
        w.playAuxSFX(1001, x, y, z, 0);
      } else {
        ItemStack itemstack = tileentitydispenser.getStackInSlot(l);
        int id = itemstack.getItem().itemID;
        int meta = itemstack.getItemDamage();
        if (Block.blocksList[id] != null && Block.blocksList[id].blockID == id) {
          EnumFacing dir = getFacing(direction & 7);
          int x1 = x + dir.getFrontOffsetX();
          int y1 = y + dir.getFrontOffsetY();
          int z1 = z + dir.getFrontOffsetZ();
          if (y1 < 0 || y1 > 255) return;
          Chunk c1 = ChunkCache.getChunk(w, x1 >> 4, z1 >> 4, false);
          if (c1 == null) return;
          int id1 = c1.getBlockID(x1 & 15, y1, z1 & 15);
          int meta1 = c1.getBlockID(x1 & 15, y1, z1 & 15);
          if (id1 != 0) {
            w.playAuxSFX(1001, x, y, z, 0);
            return;
          }
          FysiksFun.setBlockIDandMetadata(w, c1, x1, y1, z1, id, meta, id1, meta1, null);
          itemstack.stackSize = itemstack.stackSize - 1;
          if (itemstack.stackSize <= 1) tileentitydispenser.setInventorySlotContents(l, null);
        }
      }
    }
  }

  protected void undoDispense(World w, int x, int y, int z) {
    System.out.println("Undoing dispense");

    TileEntityFFBlockDispenser tileentitydispenser = (TileEntityFFBlockDispenser) w.getBlockTileEntity(x, y, z);
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    int direction = c.getBlockMetadata(x & 15, y, z & 15);
    EnumFacing dir = getFacing(direction & 7);
    int x1 = x + dir.getFrontOffsetX();
    int y1 = y + dir.getFrontOffsetY();
    int z1 = z + dir.getFrontOffsetZ();

    Chunk c1 = ChunkCache.getChunk(w, x1 >> 4, z1 >> 4, false);
    if (c1 == null) return;
    int id = c1.getBlockID(x1 & 15, y1, z1 & 15);
    int meta = c1.getBlockID(x1 & 15, y1, z1 & 15);
    if (id == 0 || Block.blocksList[id] == null || Block.blocksList[id].blockID != id) return;
    if(id == Block.fire.blockID) return;
    if(id == Block.pistonExtension.blockID) return;
    if (Fluids.isLiquid[id] || Gases.isGas[id]) return;
    
    Block b = Block.blocksList[id];
    id = b.idDropped(meta, FysiksFun.rand, 0);
    
    /* First attempt to add the corresponding block to an existing stack */
    ItemStack itemstack = null;
    boolean blockConsumed = false;

    int tries;
    for(tries=0;tries<9;tries++) {      
      int i;
      i=tileentitydispenser.nextPosition;
      itemstack = tileentitydispenser.getStackInSlot(i);
      if(itemstack == null) {
        /* Found an empty slot to place block in */
        itemstack = new ItemStack(id, 1, meta);
        tileentitydispenser.setInventorySlotContents(i, itemstack);
        blockConsumed=true;
        break;
      }
      if(itemstack.getItem().itemID == id && itemstack.getItemDamage() == meta && itemstack.stackSize <= itemstack.getMaxStackSize() - 1) {
        itemstack.stackSize = itemstack.stackSize + 1;
        blockConsumed = true;
        break;        
      }
      tileentitydispenser.nextPosition = (tileentitydispenser.nextPosition+1)%9;
    }    
    if (blockConsumed) {
      Block.blocksList[id].breakBlock(w, x1, y1, z1, id, meta);
      FysiksFun.setBlockIDandMetadata(w, c1, x1, y1, z1, 0, 0, id, meta, null);
    } else {
      w.playAuxSFX(1001, x, y, z, 0);
    }
  }

  /**
   * Lets the block know when one of its neighbor changes. Doesn't know which
   * neighbor changed (coordinates passed are their own) Args: x, y, z, neighbor
   * blockID
   */
  public void onNeighborBlockChange(World w, int x, int y, int z, int par5) {
    
    boolean flag = w.isBlockIndirectlyGettingPowered(x, y, z) || w.isBlockIndirectlyGettingPowered(x, y + 1, z);
    int i1 = w.getBlockMetadata(x, y, z);
    boolean flag1 = (i1 & 8) != 0;
    
    if (flag && !flag1) {
      w.setBlockMetadataWithNotify(x, y, z, i1 | 8, 4);
      doDispense(w, x, y, z);
    } else if (!flag && flag1) {
      w.setBlockMetadataWithNotify(x, y, z, i1 & -9, 4);
      undoDispense(w, x, y, z);
    }
  }

}
