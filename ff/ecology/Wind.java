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
    if(FysiksFun.rand.nextInt(1000) == 0) {
      windX = windX * 0.9f + 0.25f*(FysiksFun.rand.nextDouble()+FysiksFun.rand.nextDouble()-1.0+0.1);
      windZ = windZ * 0.9f + 0.25f*(FysiksFun.rand.nextDouble()+FysiksFun.rand.nextDouble()-1.0);
      System.out.println("Change of wind: "+windX+" "+windZ);
    }
  }
}
