package mbrx.ff;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public class TileEntityFFBlockDispenser extends TileEntity implements IInventory {

  public TileEntityFFBlockDispenser() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public int getSizeInventory() {
    return 9;
  }

  @Override
  public ItemStack getStackInSlot(int i) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ItemStack decrStackSize(int i, int j) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ItemStack getStackInSlotOnClosing(int i) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setInventorySlotContents(int i, ItemStack itemstack) {
    // TODO Auto-generated method stub

  }

  @Override
  public String getInvName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isInvNameLocalized() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public int getInventoryStackLimit() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public boolean isUseableByPlayer(EntityPlayer entityplayer) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void openChest() {
    // TODO Auto-generated method stub

  }

  @Override
  public void closeChest() {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean isItemValidForSlot(int i, ItemStack itemstack) {
    // TODO Auto-generated method stub
    return false;
  }

}
