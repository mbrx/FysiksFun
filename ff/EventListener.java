package mbrx.ff;

import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public class EventListener {
	
	@ForgeSubscribe
	public void someEvent(ChunkEvent.Load event) {
		//System.out.println("Chunk loaded: "+event.getChunk().xPosition+ " "+event.getChunk().zPosition);
	}
}
