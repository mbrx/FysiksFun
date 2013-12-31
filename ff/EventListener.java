package mbrx.ff;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public class EventListener {
	
	@ForgeSubscribe
	public void someEvent(ChunkEvent.Load event) {
		//System.out.println("Chunk loaded: "+event.getChunk().xPosition+ " "+event.getChunk().zPosition);
	}
	
	/*
   @ForgeSubscribe
   public void spawnEvent(LivingSpawnEvent event) {
     EntityLiving living = (EntityLiving) event.entity;
     System.out.println("Entity spawned: "+living);
     if(living instanceof EntitySquid) {
       System.out.println("Kill it with fire!");
       event.setResult(Event.Result.DENY);
     }         
   }*/

}
