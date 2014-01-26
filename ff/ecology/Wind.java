package mbrx.ff.ecology;

import mbrx.ff.FysiksFun;
import net.minecraft.world.World;

public class Wind {
  private static double windX=0.2, windZ=0.0;
  
  public static float getWindX(World world, int x, int y, int z) {
    return (float) windX;
  }
  public static float getWindZ(World world, int x,int y, int z) {
    return (float) windZ;
  } 
  
  public static void doTick(World world) {
    if(FysiksFun.rand.nextInt(400) == 0) {
      windX = windX * 0.99f + 0.15f*(FysiksFun.rand.nextDouble()-0.5);
      windZ = windZ * 0.99f + 0.15f*(FysiksFun.rand.nextDouble()-0.5);
      //System.out.println("Change of wind: "+windX+" "+windZ);
    }
  }
}
