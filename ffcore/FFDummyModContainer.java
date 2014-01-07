package mbrx.ffcore;

import java.util.Arrays;

import com.google.common.eventbus.Subscribe;

import net.minecraftforge.event.EventBus;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class FFDummyModContainer extends DummyModContainer {

  public FFDummyModContainer() {
    super(new ModMetadata());
    
    ModMetadata meta = getMetadata();
    meta.modId = "FFCore";
    meta.name = "FFCore";
    meta.version = "0.4";
    meta.credits = "Mathias Broxvall";
    meta.authorList = Arrays.asList("Mathias Broxvall");
    meta.description = "";
    meta.url = "nope";
    meta.updateUrl = "";
    meta.screenshots = new String[0];
    meta.logoFile = "";   
  }

  
  @Subscribe
  public void preInit(FMLPreInitializationEvent evt) {

  }

  @Subscribe
  public void init(FMLInitializationEvent evt) {

  }


  @Subscribe
  public void postInit(FMLPostInitializationEvent evt) {

  }

}
