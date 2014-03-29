package mbrx.ff;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockPistonExtension;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Facing;
import net.minecraft.util.Icon;
import net.minecraft.world.World;

public class BlockFFPistonExtension extends BlockPistonExtension {

  public BlockFFPistonExtension(int par1) {
    super(par1);
  }
  
  public void breakBlock(World w, int x, int y, int z, int oldId, int oldMeta) {
    if(!FysiksFun.isCurrentlyMovingABlock) super.breakBlock(w,x,y,z,oldId,oldMeta); 
  }
  
}
