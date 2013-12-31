package mbrx.ff;

import java.util.EnumSet;

import net.minecraft.world.World;

import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;

public class ServerTickHandler implements IScheduledTickHandler {
  
  @Override
  public void tickStart(EnumSet<TickType> type, Object... tickData) {
    //System.out.println("TickType: "+type+" object: "+tickData);
    FysiksFun.tickServer();
  }
  
  @Override
  public void tickEnd(EnumSet<TickType> type, Object... tickData) {    
  }
  
  @Override
  public EnumSet<TickType> ticks() {
    return EnumSet.of(TickType.SERVER); //TickType.WORLD);    
  }
  
  @Override
  public String getLabel() {
    return "FlowingFluids::worldTickHandler";
  }
  
  @Override
  public int nextTickSpacing() {
    return 1;
  }
  
}
