package mbrx.ff;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;

public class TileEntityFFBlockDispenser extends TileEntityDispenser {

  public int nextPosition;
  
  public TileEntityFFBlockDispenser() {
    super();
    nextPosition=0;
  }

  public void readFromNBT(NBTTagCompound par1NBTTagCompound)
  {
      super.readFromNBT(par1NBTTagCompound);
      nextPosition = par1NBTTagCompound.getByte("nextBlockDispense");
      if(nextPosition<=0 || nextPosition>9) nextPosition=0;
  }

  /**
   * Writes a tile entity to NBT.
   */
  public void writeToNBT(NBTTagCompound par1NBTTagCompound)
  {
      super.writeToNBT(par1NBTTagCompound);
      par1NBTTagCompound.setByte("nextBlockDispense", (byte) nextPosition);
  }

}
