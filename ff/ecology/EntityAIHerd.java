package mbrx.ff.ecology;

import java.util.ArrayList;
import java.util.List;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.Counters;
import mbrx.ff.util.Util;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.world.World;

public class EntityAIHerd extends EntityAIBase {
  int          randOffset;
  World        theWorld;
  EntityAnimal theAnimal;
  float        moveSpeed;
  int destX, destY, destZ;
  
  public EntityAIHerd(EntityAnimal animal, float movementSpeed) {
    randOffset = FysiksFun.rand.nextInt(200);
    this.theAnimal = animal;
    this.theWorld = animal.worldObj;
    this.moveSpeed = movementSpeed;
    this.setMutexBits(3);

  }

  @Override
  public boolean shouldExecute() {
    return ((Counters.tick + randOffset) % 200) < 50;
  }

  @Override
  public void resetTask() {

    for (int tries = 0; tries < 10; tries++) {
      int range = 5 + tries * 5;
      List allEntities = new ArrayList(theWorld.loadedEntityList);      
      for (Object o : allEntities) {
        if(o == theAnimal) continue;
        if(o.getClass() == theAnimal.getClass()) {
          
        //if (o instanceof theAnimal.getClass()) {             
        //    EntityCow || o instanceof EntitySheep || o instanceof EntityPig || o instanceof EntityChicken) {
          EntityAnimal other = (EntityAnimal) o;
          double dx = other.posX - this.theAnimal.posX;
          double dy = other.posY - this.theAnimal.posY;
          double dz = other.posZ - this.theAnimal.posZ;
          double dist = dx * dx + dy * dy + dz * dz;
          if (dist < range * range) {
            //System.out.println(theAnimal + " is considering " + o);
            destX = (int) (other.posX + (FysiksFun.rand.nextFloat() - 0.5F) * 2.F * 2.F);
            destZ = (int) (other.posZ + (FysiksFun.rand.nextFloat() - 0.5F) * 2.F * 2.F);
            int oy = (int) other.posY;
            boolean foundAir = false;
            for (destY = oy+20; destY > oy-20; destY--) {
              int id = theWorld.getBlockId(destX, destY, destZ);
              if (id == 0) {
                foundAir = true;
                continue;
              } else if (foundAir) {
                //destY = destY + 1;
                break;
              }
            }
            //System.out.println("Target "+Util.xyzString(destX,destY,destZ)+" found air: "+foundAir);
            if (!foundAir) continue;
            // Don't try to go to places that are not safe (avoids infinite loops trying to go to unsafe place, aborting and then starting over) 
            if(!EntityAICoward.isPositionSafe(theAnimal.worldObj,destX,destY,destZ)) continue;
            if (theAnimal.getNavigator().tryMoveToXYZ(destX, destY, destZ, moveSpeed)) {
              //System.out.println("+0 Found path to " + Util.xyzString(destX, destY, destZ));
              return;
            
            }

          }
        }
      }
    }
    //System.out.println("Nope, couldn't find anyone to go to...");
  }

  public boolean continueExecuting() {
    double dx = destX - this.theAnimal.posX;
    double dy = destY - this.theAnimal.posY;
    double dz = destZ - this.theAnimal.posZ;
    
    double dist;
    dist = dx * dx + dz * dz;
    if(dist < 2.0*2.0) return false;
    //System.out.println(theAnimal+" continue executing?");
    return !this.theAnimal.getNavigator().noPath();
  }
}
