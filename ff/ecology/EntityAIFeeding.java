package mbrx.ff.ecology;

import java.util.ArrayList;
import java.util.List;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class EntityAIFeeding extends EntityAIBase {
  World              theWorld;
  EntityAnimal       theAnimal;

  /** The speed the creature moves at during feeding behavior. */
  float              moveSpeed;

  float              foodLevel;
  int                attemptCounter;
  int                walkingTime;
  /** How many ticks have been spent eating. Starts at zero. Increases when in range of block */
  int                eatingTime;
  ArrayList<Integer> eatingList = new ArrayList<Integer>();

  private int        destX, destY, destZ;

  public EntityAIFeeding(EntityAnimal animal, float movementSpeed) {
    this.theAnimal = animal;
    this.theWorld = animal.worldObj;
    this.moveSpeed = movementSpeed;
    this.setMutexBits(3);

    foodLevel = 4.0F+FysiksFun.rand.nextFloat()*4.0F; // TODO add random amount of food when creature is created first time?
    attemptCounter = 0;
    walkingTime = 0;
    eatingTime = 0;
  }

  public void addFoodtype(int id) {
    eatingList.add(new Integer(id));
  }

  @Override
  public boolean shouldExecute() {

    //System.out.println("CheckExecute: "+theAnimal+"Food: "+foodLevel);
    foodLevel = foodLevel - 1 / 500.f;
    if (foodLevel < 0.0) {
      foodLevel = 0.0f;
      if (FysiksFun.rand.nextInt(200) == 0) {
        //System.out.println("Animal is starving");
        theAnimal.attackEntityFrom(DamageSource.generic, 1);
      }
    }
    
    if (attemptCounter != 0) {
      attemptCounter = (attemptCounter + 1) % 10;
      return false;
    }
    if(theAnimal.inLove > 0) return false;
    if (foodLevel >= 10.0) return false;
    return true;
  }

  public void resetTask() {    
    attemptCounter = 0;
    int x = (int) Math.round(theAnimal.posX);
    int y = (int) Math.round(theAnimal.posY);
    int z = (int) Math.round(theAnimal.posZ);
    Integer intObject;
    
    //System.out.println("reset: "+theAnimal+"");
    boolean success = false;
    for (int tries = 0; tries < 600 && !success; tries++) {
      int range = 2 + (tries / 20);
      int dx = FysiksFun.rand.nextInt(range * 2 + 1) - range;
      int dz = FysiksFun.rand.nextInt(range * 2 + 1) - range;
      int x0 = x+dx, z0=z+dz;
      Chunk c = ChunkCache.getChunk(theWorld, x0>>4, z0>>4, false);
      if(c == null) continue;
      
      for (int dy = 1+range/3; dy >= -1-range/3 && !success; dy--) {
        if(y+dy<0 || y+dy>255) continue;
        int id = c.getBlockID(x0&15,y+dy,z0&15);        
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
            attemptCounter=0;
            success = true;            
            this.theAnimal.getLookHelper().setLookPosition(destX, destY, destZ, 10.0f, (float) this.theAnimal.getVerticalFaceSpeed());            
            //System.out.println(" "+theAnimal+" starting to eat at "+destX+", "+destZ);
            break;
          } else {
            tries += 50;  // We will not make too many tries to use the navigator... too expensive!
          }
        }
      }
    }
      if (!success) {
        //System.out.println(theAnimal+" failed to find food, attemptCounter: "+attemptCounter);
        attemptCounter = 1;
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
    //if((walkingTime % 10) == 0)
    //  theAnimal.getNavigator().tryMoveToXYZ(destX, destY, destZ, moveSpeed);

    int id = theWorld.getBlockId(destX, destY, destZ);
    Integer intObject = new Integer(id);
    if(!eatingList.contains(intObject)) {
      //if (id != Block.tallGrass.blockID && id != Block.crops.blockID) {
      // Food has disappeared, try to find something else
      attemptCounter = -2;
    }
    //System.out.println("update: "+theAnimal+" dist: "+dist+" XYZ: "+Util.xyzString(destX, destY, destZ)+" eat: "+eatingTime);
    
    if (dist < 3.0) {
      if(eatingTime == 0)
        this.theWorld.setEntityState(this.theAnimal, (byte)10);
      if((eatingTime % 4) == 0) {
        this.theAnimal.getLookHelper().setLookPosition(destX, destY - 1.0 + Math.sin(eatingTime / 9.0), destZ, 10.0f, (float) this.theAnimal.getVerticalFaceSpeed());
        // TODO - only animate the eating if someone actually is looking at the animal
      }
      if (++eatingTime >= 60) {
        // We are now close enough to eat and finish this task
        FysiksFun.setBlockWithMetadataAndPriority(theWorld, destX, destY, destZ, 0, 0, 0);
        //theWorld.setBlock(destX, destY, destZ, 0, 0, 0x01 + 0x02);
        foodLevel = foodLevel + 1.0F; //0.75F; //0.5F;
        eatingTime = 0;
        //System.out.println(" "+theAnimal+" finished eating at "+destX+", "+destZ);
        if(foodLevel >= 9.0) { 
          theAnimal.inLove = 1000;
          //System.out.println(theAnimal+"is feeling amorous!");
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
