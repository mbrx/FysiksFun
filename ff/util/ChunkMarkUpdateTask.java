package mbrx.ff.util;

import net.minecraft.world.World;

/**
 * Class used for creating sets of markupdates to be done at a later time. The
 * origId and origMeta is not hashed, thus you cannot have multiple instances
 * with the coordinate but different id/meta values in the same set. This is a
 * feature, not a bug.
 */
public class ChunkMarkUpdateTask extends CoordinateWXYZ {
  public int origId, origMeta;

  /**
   * Doesn't initialize private fields - you must use the 'set' function after
   * creating objects.
   */
  public ChunkMarkUpdateTask() {}

  public ChunkMarkUpdateTask(World w, int x, int y, int z, int origId, int origMeta) {
    super(w, x, y, z);
    this.origId = origId;
    this.origMeta = origMeta;
  }

  public void set(World w, int x, int y, int z, int origId, int origMeta) {
    super.set(w, x, y, z);
    this.origId = origId;
    this.origMeta = origMeta;
  }

  public int getOrigId() {
    return origId;
  }

  public int getOrigMeta() {
    return origMeta;
  }
}
