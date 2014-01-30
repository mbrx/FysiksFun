package mbrx.ff.util;
import net.minecraft.world.World;

import com.google.common.base.Objects;

public class CoordinateWXYZ {
  private int x, y, z;
  private World w;
  int hash;

  public World getWorld() { return w; }
  public int getX() { return x; }
  public int getY() { return y; }
  public int getZ() { return z; }

  /** Doesn't initialize private fields - you must use the 'set' function after creating objects. */
  public CoordinateWXYZ() {
  }
  
  public CoordinateWXYZ(World w, int x, int y, int z) {
    this.w = w;
    this.x = x;
    this.y = y;
    this.z = z;
    recomputeHash();
  }
  public CoordinateWXYZ(CoordinateWXYZ copy) {
    this.w = copy.w;
    this.x = copy.x;
    this.y = copy.y;
    this.z = copy.z;
    recomputeHash();
  }    
  public void set(World w, int x, int y, int z) {
    this.w = w;
    this.x = x;
    this.y = y;
    this.z = z;
    recomputeHash();
  }
  private void recomputeHash() {
    // this.hash = Objects.hashCode(w, x, y, z);
    this.hash = (y&0x1ff) | ((x & 0xfff)<<9) | ((z & 0xfff)<<21);
  }
  @Override
  public int hashCode() {return hash;}
  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof CoordinateWXYZ) {
      final CoordinateWXYZ other = (CoordinateWXYZ) obj;
      return w.equals(other.w) && x == other.x && y == other.y && z == other.z;
    } else return false;
  }    
};

