package mbrx.ff;

import mbrx.ff.ecology.EntityAICoward;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public class EventListener {
	
	@ForgeSubscribe
	public void someEvent(ChunkEvent.Load event) {
		//System.out.println("Chunk loaded: "+event.getChunk().xPosition+ " "+event.getChunk().zPosition);
	}
	
	@ForgeSubscribe
	public void animalIsDamagedAtLocationEvent(LivingAttackEvent event) {
	  EntityLivingBase entity = event.entityLiving;
	  if(!(entity instanceof EntityAnimal)) return;
	  if(!FysiksFun.settings.doAnimalAI) return;
	  EntityAICoward.registerStaticThreat(entity.worldObj,entity.posX, entity.posY, entity.posZ, event.source);	  
	}
    @ForgeSubscribe
    public void animalIsDamagedByEntity(LivingSetAttackTargetEvent event) {
      EntityLivingBase entity = event.entityLiving;
      if(!(entity instanceof EntityAnimal)) return;
      if(!FysiksFun.settings.doAnimalAI) return;
      EntityAICoward.registerEntityThreat(event.target);   
    }

	//return MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(entity, src, amount));

	//MinecraftForge.EVENT_BUS.post(new LivingSetAttackTargetEvent(entity, target));

	
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
