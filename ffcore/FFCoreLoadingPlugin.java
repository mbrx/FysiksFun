package mbrx.ffcore;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

public class FFCoreLoadingPlugin implements IFMLLoadingPlugin {

  @Override
  public String[] getLibraryRequestClass() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String[] getASMTransformerClass() {
    return new String[]{FFCoreClassTransformer.class.getName()};
  }

  @Override
  public String getModContainerClass() { 
    return FFDummyModContainer.class.getName(); 
  }

  @Override
  public String getSetupClass() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void injectData(Map<String, Object> data) {
    
    // TODO Auto-generated method stub
    
  }

}
