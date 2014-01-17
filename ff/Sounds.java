package mbrx.ff;

import java.util.logging.Level;

import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.event.ForgeSubscribe;

public class Sounds {

  public void Sounds() {    
  }
  @ForgeSubscribe
  public void onSound(SoundLoadEvent event) {
    for(int i=1;i<26;i++)
      event.manager.soundPoolSounds.addSound("fysiksfun:rubble"+i+".ogg");
    event.manager.soundPoolSounds.addSound("fysiksfun:earthquake.ogg");
  }
}
