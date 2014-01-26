package mbrx.ff.util;
import net.minecraft.world.World;

import com.google.common.base.Objects;

public class CoordinateWXZ {
  private int x, z;
  private World w;
  int hash;

  public World getWorld() { return w; }
  public int getX() { return x; }
  public int getZ() { return z; }
  
  public CoordinateWXZ(World w, int x, int z) {
    this.w = w;
    this.x = x;
    this.z = z;
    recomputeHash();
  }
  public CoordinateWXZ(CoordinateWXZ copy) {
    this.w = copy.w;
    this.x = copy.x;
    this.z = copy.z;
    recomputeHash();
  }    
  public void set(World w, int x, int z) {
    this.w = w;
    this.x = x;
    this.z = z;
    recomputeHash();
  }
  private void recomputeHash() {
    //this.hash = Objects.hashCode(w, x, z);
    this.hash = ((x & 0xffff)<<16) | (z&0xffff);
  }
  @Override
  public int hashCode() {
    return hash;
  }
  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof CoordinateWXZ) {
      final CoordinateWXZ other = (CoordinateWXZ) obj;
      return w.equals(other.w) && x == other.x && z == other.z;
    } else return false;
  }    
};
