package mbrx.ff.util;

import java.util.ArrayList;
import net.minecraft.world.World;

public class SoundQueue {

  private static class SoundFxObject {
    public World w;
    public double x, y, z;
    public String name;
    public float  volume, pitch;

    public SoundFxObject(World w, double x, double y, double z, String name, float volume, float pitch) {
      this.w = w;
      this.x = x;
      this.y = y;
      this.z = z;
      this.name = name;
      this.volume = volume;
      this.pitch = pitch;
    }
  }

  private static ArrayList<SoundFxObject> fxQueue = new ArrayList<SoundFxObject>();

  /**
   * Queues a sound effect so that it can be added to the normal minecraft sound
   * effect system (paulscode.*) by the main thread. Avoiding race conditions.-
   */
  public static void queueSound(World w, double x, double y, double z, String name, float volume, float pitch) {
    fxQueue.add(new SoundFxObject(w, x, y, z, name, volume, pitch));
  }

  /** Sends all scheduled sound effects to main sound system */
  public static void doSoundEffects() {
    /*for (SoundFxObject obj : fxQueue) {
      obj.w.playSoundEffect(obj.x, obj.y, obj.z, obj.name, obj.volume, obj.pitch);
    }*/
    fxQueue.clear();
  }
}
