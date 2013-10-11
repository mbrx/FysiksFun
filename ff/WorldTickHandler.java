package mbrx.ff;

import java.util.EnumSet;

import net.minecraft.world.World;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;

public class WorldTickHandler implements IScheduledTickHandler {

	@Override
	/** Specify that we are only listening for world update ticks */
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.WORLD);
	}
	@Override
	/** Run the fluid world updates */
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		if(tickData[0] instanceof World) {
			FysiksFun.doWorldTick((World) tickData[0]);
		}
	}
	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
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
