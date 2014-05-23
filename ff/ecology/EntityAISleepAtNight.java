package mbrx.ff.ecology;

import mbrx.ff.FysiksFun;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;

public class EntityAISleepAtNight extends EntityAIBase {
  int sleepOffset;
  EntityAnimal theAnimal;

  public EntityAISleepAtNight(EntityAnimal target) {
    theAnimal = target;
    this.setMutexBits(3);
    sleepOffset = FysiksFun.rand.nextInt(1000)-500;
  }

  @Override
  public boolean shouldExecute() {
    long timeNow = (theAnimal.worldObj.getWorldTime()+sleepOffset + 24000) % 24000;
    if(timeNow > 23000 || (timeNow > 0 && timeNow < 12000)) // Be awake from 6am to 8pm, sleep otherwise. 
      return false;
    else
      return true;
  }
  
  public boolean continueExecuting() {
    int destX = (int)(theAnimal.posX + 0.5f);
    int destY = (int)(theAnimal.posY + 0.5f) - 1; // Look straight down
    int destZ = (int)(theAnimal.posZ + 0.5f);    
    
      
    theAnimal.getLookHelper().setLookPosition(destX, destY+FysiksFun.rand.nextFloat()*1.0, destZ, 10.0f, (float) this.theAnimal.getVerticalFaceSpeed());
    return shouldExecute();
  }

}
