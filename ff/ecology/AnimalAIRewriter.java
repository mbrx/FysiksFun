package mbrx.ff.ecology;

import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITaskEntry;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.world.World;

public class AnimalAIRewriter {

  public static void rewriteAnimalAIs(World w) {
    
     List allEntities = w.loadedEntityList;
     for(Object o : allEntities) {
       if(o instanceof EntityCow || o instanceof EntitySheep || o instanceof EntityPig || o instanceof EntityChicken) {
         EntityAnimal animal = (EntityAnimal) o;
         boolean hasFeedingAI=false;
         for(Object o2 : animal.tasks.taskEntries) {
           EntityAITaskEntry taskEntry = (EntityAITaskEntry) o2;
           if(taskEntry.action instanceof EntityAIFeeding) hasFeedingAI=true;              
         }
         if(!hasFeedingAI) {
           EntityAIFeeding ai=new EntityAIFeeding(animal, 0.25f);
           ai.addFoodtype(Block.tallGrass.blockID);
           ai.addFoodtype(Block.crops.blockID);
           ai.addFoodtype(Block.plantYellow.blockID);
           ai.addFoodtype(Block.plantRed.blockID);
           insertAI(animal, 4, ai);
         }
       }                     
     }
    
  }

  private static void insertAI(EntityAnimal cow, int priority, EntityAIBase aitask) {
    // System.out.println("Rewriting the animal: "+cow);    
    // First increment the priority of every taskEntry that has same-or-higher priority as the new one
    for(Object o2 : cow.tasks.taskEntries) {
      EntityAITaskEntry taskEntry = (EntityAITaskEntry) o2;
      if(taskEntry.priority >= priority) {
        taskEntry.priority++;              
      }      
    }
    cow.tasks.addTask(priority, aitask);
  }

}
