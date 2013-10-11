package mbrx.ff;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public class EntityAIFeeding extends EntityAIBase {
  World              theWorld;
  EntityAnimal       theAnimal;

  /** The speed the creature moves at during feeding behavior. */
  float              moveSpeed;

  float              foodLevel;
  int                attemptCounter;
  int                walkingTime;
  int                eatingTime;
  ArrayList<Integer> eatingList = new ArrayList<Integer>();

  private int        destX, destY, destZ;

  public EntityAIFeeding(EntityAnimal cow, float d) {
    this.theAnimal = cow;
    this.theWorld = cow.worldObj;
    this.moveSpeed = d;
    this.setMutexBits(3);

    foodLevel = 8;
    attemptCounter = 0;
    walkingTime = 0;
    eatingTime = 0;
  }

  public void addFoodtype(int id) {
    eatingList.add(new Integer(id));
  }

  @Override
  public boolean shouldExecute() {

    foodLevel = foodLevel - 1 / 500.f;
    if (foodLevel < 0.0) {
      foodLevel = 0.0f;
      if (FysiksFun.rand.nextInt(100) == 0) {
        // System.out.println("Animal is starving");
        theAnimal.attackEntityFrom(DamageSource.generic, 1);
      }
    }
    
    if (attemptCounter != 0) {
      attemptCounter = (attemptCounter + 1) % 100;
      return false;
    }
    if (foodLevel >= 10.0) return false;
    return true;
  }

  public void resetTask() {    
    attemptCounter = 0;
    int x = (int) Math.round(theAnimal.posX);
    int y = (int) Math.round(theAnimal.posY);
    int z = (int) Math.round(theAnimal.posZ);
    Integer intObject;
    
    boolean success = false;
    for (int tries = 0; tries < 200 && !success; tries++) {
      int range = 1 + (tries / 10);
      int dx = FysiksFun.rand.nextInt(range * 2 + 1) - range;
      int dz = FysiksFun.rand.nextInt(range * 2 + 1) - range;
      for (int dy = +range / 3; dy >= -range / 3 && !success; dy--) {
        int id = theWorld.getBlockId(x + dx, y + dy, z + dz);        
        if(id == 0) continue;
        intObject=id;
        if (eatingList.contains(intObject)) { 
          //if (id == Block.tallGrass.blockID || id == Block.crops.blockID) //             
          if (theAnimal.getNavigator().tryMoveToXYZ(x + dx, y + dy, z + dz, moveSpeed)) {
            destX = x + dx;
            destY = y + dy;
            destZ = z + dz;
            walkingTime = 0;
            eatingTime=0;
            success = true;            
            this.theAnimal.getLookHelper().setLookPosition(destX, destY, destZ, 10.0f, (float) this.theAnimal.getVerticalFaceSpeed());            
            //System.out.println(" "+theAnimal+" starting to eat at "+destX+", "+destZ);
            break;
          } else {
            tries += 50;  // We will not make too many tries to use the navigator... too expensive!
          }
        }
      }
      if (!success) {
        attemptCounter = 1;
      }
    }
  }

  public void updateTask() {
    double dx = destX - this.theAnimal.posX;
    double dy = destY - this.theAnimal.posY;
    double dz = destZ - this.theAnimal.posZ;
    
    double dist;
    if (dy * dy < 4.0) dist = dx * dx + dz * dz;
    else dist = dx * dx + dz * dz + dy * dy;
        
    walkingTime++;
    if (walkingTime >= 600) {
      // System.out.println("Giving up walking");
      attemptCounter = 1;
      return;
    }

    int id = theWorld.getBlockId(destX, destY, destZ);
    if (id != Block.tallGrass.blockID && id != Block.crops.blockID) {
      // Food has disappeared, try to find something else
      attemptCounter = -2;
    }
    
    if (dist < 3.0) {
      if((eatingTime % 4) == 0) {
        // this.theAnimal.getLookHelper().setLookPosition(destX, destY - 1.0 + Math.sin(eatingTime / 9.0), destZ, 10.0f, (float) this.theAnimal.getVerticalFaceSpeed());
        // TODO - only animate the eating if someone actually is looking at the animal
      }
      if (++eatingTime >= 30) {
        // We are now close enough to eat and finish this task
        FysiksFun.setBlockWithMetadataAndPriority(theWorld, destX, destY, destZ, 0, 0, 0);
        //theWorld.setBlock(destX, destY, destZ, 0, 0, 0x01 + 0x02);
        foodLevel = foodLevel + 1.0f;
        eatingTime = 0;
        //System.out.println(" "+theAnimal+" finished eating at "+destX+", "+destZ);
        if(foodLevel >= 9.0) { 
          theAnimal.inLove = 100;
          //System.out.println(" and is feeling amorous!");
        }
      }
    } else {
      if(eatingTime != 0)
        this.theAnimal.getLookHelper().setLookPosition(destX, destY, destZ, 10.0f, (float) this.theAnimal.getVerticalFaceSpeed());
      eatingTime = 0;      
      /*if ((walkingTime % 200) == 0 && !theAnimal.getNavigator().tryMoveToXYZ(destX, destY, destZ, moveSpeed)) {
        attemptCounter = 1;
      }*/
    }
  }
  
}
