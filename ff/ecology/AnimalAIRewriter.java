package mbrx.ff.ecology;

import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITaskEntry;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.item.Item;
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
           EntityAICoward cowardAI = new EntityAICoward(animal, 2.0F);
           insertAI(animal, 2, cowardAI); 

           EntityAISleepAtNight sleepAI = new EntityAISleepAtNight(animal);
           insertAI(animal, 3, sleepAI); 

           float foodPerEating=1.0F;
           if(animal instanceof EntityChicken) foodPerEating = 2.0F;
           EntityAIFeeding ai=new EntityAIFeeding(animal, 0.75f, foodPerEating );
           ai.addFoodtype(Block.tallGrass.blockID);
           ai.addFoodtype(Block.crops.blockID);
           ai.addFoodtype(Block.plantYellow.blockID);
           ai.addFoodtype(Block.plantRed.blockID);
           if(o instanceof EntityCow || o instanceof EntitySheep || o instanceof EntityPig)
             ai.addFoodtype(Item.wheat.itemID);
           if(o instanceof EntityChicken)
             ai.addFoodtype(Item.seeds.itemID);
           if(o instanceof EntityPig) {
             /* This may make it very hard to find carrots/potatoes...  have to test it */ 
             ai.addFoodtype(Block.carrot.blockID);
             ai.addFoodtype(Block.potato.blockID);
           }
           insertAI(animal, 4, ai);
           EntityAIHerd herdAi = new EntityAIHerd(animal, 1.0F);
           insertAI(animal, 5, herdAi); 
         }
       }                     
     }
    
  }

  /** All AI's with higher or equal priority value (less valued) is incremented by one before this AI is inserted. Thus it comes before an old AI with same priority value */ 
  private static void insertAI(EntityAnimal animal, int priority, EntityAIBase aitask) {
    // System.out.println("Rewriting the animal: "+cow);    
    // First increment the priority of every taskEntry that has same-or-higher priority as the new one
    for(Object o2 : animal.tasks.taskEntries) {
      EntityAITaskEntry taskEntry = (EntityAITaskEntry) o2;
      if(taskEntry.priority >= priority) {
        taskEntry.priority++;              
      }      
    }
    animal.tasks.addTask(priority, aitask);
  }

}
