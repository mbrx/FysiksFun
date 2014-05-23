package mbrx.ff.ecology;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import mbrx.ff.FysiksFun;
import mbrx.ff.util.Counters;
import mbrx.ff.util.Util;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public class EntityAICoward extends EntityAIBase {

  int          randOffset;
  World        theWorld;
  EntityAnimal theAnimal;
  float        moveSpeed;
  int          destX, destY, destZ;

  static class StaticThreat {
    World world;
    double posX, posY, posZ, radius;
    int    creationTick;

    public StaticThreat(World w, double x, double y, double z, double r) {
      world = w;
      posX = x;
      posY = y;
      posZ = z;
      radius = r;
      creationTick = Counters.tick;
    }
  }
  static class EntityThreat {
    Entity entity;
    double radius;
    int    creationTick;

    public EntityThreat(Entity e, double r) {
      entity = e;
      radius = r;
      creationTick = Counters.tick;
    }
  }
  
  private static LinkedList<StaticThreat> staticThreats = new LinkedList<StaticThreat>();
  private static LinkedList<EntityThreat> entityThreats = new LinkedList<EntityThreat>();

  public static boolean isPositionSafe(World w, double x, double y, double z) {
    for (StaticThreat t : staticThreats) {
      if (t.world != w) continue;
      double dx = x - t.posX;
      double dy = y - t.posY;
      double dz = z - t.posZ;
      if (dx * dx + dy * dy + dz * dz < t.radius * t.radius) return false;
    }
    for (EntityThreat t : entityThreats) {
      if (t.entity.worldObj != w) continue;
      double dx = x - t.entity.posX;
      double dy = y - t.entity.posY;
      double dz = z - t.entity.posZ;
      if (dx * dx + dy * dy + dz * dz < t.radius * t.radius) return false;
    }
    return true;
  }

  public static void cleanup() {
    LinkedList<StaticThreat> staticThreatsToRemove = new LinkedList<StaticThreat>();
    for (StaticThreat t : staticThreats) {
      if(t.creationTick + 20*60*3 < Counters.tick) staticThreatsToRemove.add(t);
    }
    staticThreats.removeAll(staticThreatsToRemove);

    LinkedList<EntityThreat> entityThreatsToRemove = new LinkedList<EntityThreat>();
    for (EntityThreat t : entityThreats) {
      if(t.creationTick + 20*60*3 < Counters.tick) entityThreatsToRemove.add(t);
    }
    entityThreats.removeAll(entityThreatsToRemove);    
  }

  public EntityAICoward(EntityAnimal animal, float movementSpeed) {
    randOffset = FysiksFun.rand.nextInt(200);
    this.theAnimal = animal;
    this.theWorld = animal.worldObj;
    this.moveSpeed = movementSpeed;
    this.setMutexBits(3);
  }

  @Override
  public boolean shouldExecute() {
    return !isPositionSafe(theAnimal.worldObj, theAnimal.posX, theAnimal.posY, theAnimal.posZ);
  }

  @Override
  public void resetTask() {
    System.out.println("A coward animal: "+theAnimal+" is trying to flee");
    for (int tries = 0; tries < 50; tries++) {
      int radius = 2 + tries/2;
      destX = (int) (theAnimal.posX + 0.5f + (FysiksFun.rand.nextFloat() - 0.5) * 2. * radius);
      destZ = (int) (theAnimal.posZ + 0.5f + (FysiksFun.rand.nextFloat() - 0.5) * 2. * radius);
      int oy = (int) theAnimal.posY;
      boolean foundAir = false;
      for (destY = oy + 20; destY > oy - 20; destY--) {
        int id = theWorld.getBlockId(destX, destY, destZ);
        if (id == 0) {
          foundAir = true;
          continue;
        } else if (foundAir) {
          break;
        }
      }
      if (!foundAir) continue;
      if(!isPositionSafe(theAnimal.worldObj,destX,destY,destZ)) continue;
      if (theAnimal.getNavigator().tryMoveToXYZ(destX, destY, destZ, moveSpeed)) return;
    }
  }

  public boolean continueExecuting() {
    double dx = destX - this.theAnimal.posX;
    double dy = destY - this.theAnimal.posY;
    double dz = destZ - this.theAnimal.posZ;
    double lookX = destX + 0.5;
    double lookY = destY + 1.0;
    double lookZ = destZ + 0.5;
    
    /* Figure out whom to look at. The closest threatening entity IF they are within the threat radius  */
    LinkedList<EntityThreat> entityThreatsToRemove = new LinkedList<EntityThreat>();
    double minDist = 1e6;
    for (EntityThreat t : entityThreats) {
      if (t.entity.worldObj != theAnimal.worldObj) continue;
      double dx2 = theAnimal.posX - t.entity.posX;
      double dy2 = theAnimal.posY - t.entity.posY;
      double dz2 = theAnimal.posZ - t.entity.posZ;
      double dist = dx2 * dx2 + dy2 * dy2 + dz2 * dz2; 
      if (dist < t.radius * t.radius && dist < minDist) {
        minDist = dist;
        lookX = t.entity.posX;
        lookY = t.entity.posY + 0.5;
        lookZ = t.entity.posZ;
      }
    }    
    theAnimal.getLookHelper().setLookPosition(lookX, lookY, lookZ, 10.0f, (float) this.theAnimal.getVerticalFaceSpeed());

    double dist;
    dist = dx * dx + dz * dz;
    if(!isPositionSafe(theAnimal.worldObj,destX,destY,destZ)) {
      return false;
    }
    if (dist < 2.0 * 2.0) return false;
    return !this.theAnimal.getNavigator().noPath();
  }

  public static void registerStaticThreat(World world, double posX, double posY, double posZ, DamageSource source) {
    if(source == DamageSource.starve) return;
    staticThreats.add(new StaticThreat(world, posX, posY, posZ, 15.0));
  }
  public static void registerEntityThreat(Entity e) {
    if(e == null) {
      FysiksFun.logger.log(Level.SEVERE,"Called registerEntityThreat with null entity");
      Util.printStackTrace();
      return;
    }
    for (EntityThreat t : entityThreats) {
      if(t.entity == e) { 
        System.out.println("Adding entity "+e+" as a REFRESHED threat");
        t.creationTick = Counters.tick;
        return;
      }
    }
    System.out.println("Adding entity "+e+" as a NEW threat");
    entityThreats.add(new EntityThreat(e, 10.0));
  }
}
