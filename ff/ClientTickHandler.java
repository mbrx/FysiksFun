package mbrx.ff;

import java.util.EnumSet;

import mbrx.ff.util.SoundQueue;
import net.minecraft.world.World;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.Player;

public class ClientTickHandler implements IScheduledTickHandler {

  @Override
  /** Specify that we are only listening for player ticks */
  public EnumSet<TickType> ticks() {
    return EnumSet.of(TickType.CLIENT);
  }

  @Override
  public void tickStart(EnumSet<TickType> type, Object... tickData) {
    //System.out.println("Thread "+Thread.currentThread().getName()+" triggers sound effects from ClientTickHandler");
    SoundQueue.doSoundEffects();      
  }

  @Override
  public void tickEnd(EnumSet<TickType> type, Object... tickData) {
  }


  @Override
  public String getLabel() {    
    return "FysiksFun.ClientTickHandler";
  }

  @Override
  public int nextTickSpacing() { 
    return 1;
  }
}
