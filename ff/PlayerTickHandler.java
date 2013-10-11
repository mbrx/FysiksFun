package mbrx.ff;

import java.util.EnumSet;

import net.minecraft.world.World;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.Player;

public class PlayerTickHandler implements IScheduledTickHandler {

  @Override
  /** Specify that we are only listening for player ticks */
  public EnumSet<TickType> ticks() {
    return EnumSet.of(TickType.PLAYER);
  }

  @Override
  public void tickStart(EnumSet<TickType> type, Object... tickData) {
    if(tickData[0] instanceof Player && tickData[1] instanceof World) {
      Player player = (Player) tickData[0];
      World w = (World) tickData[1];
    //  FysiksFun.tickPlayer(player, w);
    }   
  }

  @Override
  public void tickEnd(EnumSet<TickType> type, Object... tickData) {
  }


  @Override
  public String getLabel() {    
    return "FysiksFun.PlayerTickHandler";
  }

  @Override
  public int nextTickSpacing() { 
    return 1;
  }
}
