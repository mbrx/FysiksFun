package mbrx.ff.util;

import java.util.ArrayDeque;
import java.util.ArrayList;

import mbrx.ff.FysiksFun.WorldObserver;
import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.google.common.base.Objects;

public class BlockUpdateState {
  public World w;
  public Block block;
  public int   x, y, z;
  public int hash;
  
  /*
  static private ArrayDeque<BlockUpdateState> freeBlockUpdateStates = new ArrayDeque<BlockUpdateState>();
  public static BlockUpdateState alloc() {
  	if(freeBlockUpdateStates.size() > 0) return freeBlockUpdateStates.pop();
  	else return new BlockUpdateState();
  }
  public void free() {
  	w = null;
  	block = null;
  	freeBlockUpdateStates.push(this);
  }
  */
  
  public void set(World w, Block b, int x, int y, int z) {
    this.w = w;
    this.block = b;
    this.x = x;
    this.y = y;
    this.z = z;
   // this.hash = Objects.hashCode(w, x, y, z);
    this.hash = (y&0x1ff) | ((x & 0xfff)<<9) | ((z & 0xfff)<<21);
  }

  @Override
  public int hashCode() { return hash; }

  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof BlockUpdateState) {
      final BlockUpdateState other = (BlockUpdateState) obj;
      return w.equals(other.w) && x == other.x && y == other.y && z == other.z;
    } else return false;
  }
};