package mbrx.ff;

import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

public class Settings {
  public int     maxUpdatesPerTick;
  
  public double  waterEvaporationRate;
  public double  waterRainRate;
  public int     erosionRate;
  public int     erosionThreshold;
  public boolean rainInOceans;
  public boolean alwaysRaining;
  public boolean liquidMetal;
  public boolean netherrackCanMelt;
  public boolean undergroundWater;
  public boolean repairOceans;
  public boolean infiniteOceans;
  public boolean flowingLiquidOil;
  public boolean flowingHydrochloricAcid;
  
  public boolean visualizeVolcanoes;
  public int     volcanoFrequency;
  public int     volcanoRadius;
  public int     volcanoFeeding;
  
  public int     treeThirst;
  public int     cropsThirst;
  public boolean cropsDrinkContinously;
  public int     plantsThirst;
  public int     weatherSpeed;
  public int     plantGrowth;
  public boolean treesFall;
  
  public boolean easterEgg;

  public int blockSteamDefaultID;
  
  private String categoryModules = "modules";
  private String categoryGeneric = "generic";
  private String categoryFluids = "fluids";
  private String categoryEcology = "ecology";
  private String categoryOther = "other";
  private String categoryGases = "gases";
  private String categoryVolcanoes = "volcanoes";
  
  public boolean doVolcanoes;
  public boolean doDynamicPlants;
  public boolean doTreeFalling;
  public boolean doAnimalAI;
  public boolean doRain;
  public boolean doEvaporation;
  public boolean doErosions;
  public boolean doFluids;
  public boolean doGases;
  public boolean doNetherfun;

  public boolean doExtraFire;
  
  public void loadFromConfig(Configuration config) {
    config.addCustomCategoryComment(categoryGeneric, "All high-level settings governing generic aspects of FysiksFun");
    config.addCustomCategoryComment(categoryFluids, "Settings for fluid dynamics and world liquid budgets (evaporation, rainfall, lava generation)");
    config.addCustomCategoryComment(categoryEcology, "Settings for the natural ecology such as plants and animals");
    config.addCustomCategoryComment(categoryGases, "Settings for all gas dynamics and interaction between gases and liquids");
    config.addCustomCategoryComment(categoryOther, "All other (unsorted) settings, may move in the future");
    config.addCustomCategoryComment(categoryVolcanoes, "Settings related to dynamically generated volcanoes");
    

    maxUpdatesPerTick = config.get(categoryGeneric, "max-client-updates-per-tick", "50",
        "Maximum workload of chunks/blocks updates transmitted to client each tick. Default 50, higher cause more lag but give faster reaction to world updates",
        Property.Type.INTEGER).getInt(50);

    waterEvaporationRate = config.get(categoryFluids, "water-evaporation", "10.0",
        "Maximum rate for water evaporation, default 10, higher is more evaporation", Property.Type.DOUBLE).getDouble(10.0);    
    waterRainRate = config.get(categoryFluids, "water-rain", "10.0", "Maximum intensity of rain, default 10, higher is more rain",
        Property.Type.DOUBLE).getDouble(10.0);    
    flowingLiquidOil = config.get(categoryFluids, "flowing-oil", "true", "Let oil (if found) be treated as a flowing liquid", Property.Type.BOOLEAN)
        .getBoolean(true);    
    flowingHydrochloricAcid = config.get(categoryFluids, "flowing-hydrochloric-acid", "true", "Let hydrochloric acid (from the Factorization mod) be treated as a flowing liquid", Property.Type.BOOLEAN)
        .getBoolean(true);    
    liquidMetal = config.get(categoryFluids, "flowing-metal", "false",
        "NOT WORKING! Let metals from the mod Tinkers construct (if found) be treated as a flowing liquid", Property.Type.BOOLEAN).getBoolean(false);    
    liquidMetal = false;   
    netherrackCanMelt = config.get(categoryFluids, "netherrack-can-melt", "true", "If true, burning netherrack slowly turn into lava", Property.Type.BOOLEAN)
        .getBoolean(true);
    repairOceans = config.get(categoryFluids, "repair-oceans", "true", "If true attempt to repair ocean floors to not connect underground caves", Property.Type.BOOLEAN)
        .getBoolean(true);
    infiniteOceans = config.get(categoryFluids, "infinite-oceans", "true", "If true, oceans contain infinite water sources after a certain depth", Property.Type.BOOLEAN)
        .getBoolean(true);
    
    alwaysRaining = config.get(categoryFluids, "always-raining", "false",
        "Íf there is continous raining (regardless of actual world rain animation)", Property.Type.BOOLEAN).getBoolean(false);
    rainInOceans = config.get(categoryFluids, "rain-in-oceans", "false",
        "If rain falling in oceans will actually contribute to water level (false is efficient, true is realisitc)", Property.Type.BOOLEAN).getBoolean(false);
    erosionRate = config.get(categoryFluids, "erosion-rate", "10",
        "How quickly terrain can errode, 0 is off, default 10, larger values for faster erosions", Property.Type.INTEGER).getInt(10);
    erosionThreshold = config.get(categoryFluids, "erosion-threshold", "0",
        "Threshold for when errosion can occur, 0 for any water movement, 1 (or higher) for only larger streams of water", Property.Type.INTEGER).getInt(0);
    
    treeThirst = config.get(categoryEcology, "tree-thirst", "10", "Amount of water consumed by trees", Property.Type.INTEGER).getInt(10);
    treesFall = config.get(categoryEcology, "trees-fall", "true", "Trees fall in a semi-natural way when chopped down", Property.Type.BOOLEAN).getBoolean(true);
    cropsThirst = config.get(categoryEcology, "crops-thirst", "10", "Amount of water consumed by crops (zero disables need for water)",
        Property.Type.INTEGER).getInt(10);
    cropsDrinkContinously = config.get(categoryEcology, "crops-continously-thirsty", "true", "If true, also full-grown crops consumes water",
        Property.Type.BOOLEAN).getBoolean(true);
    plantsThirst = config.get(categoryEcology, "plants-thirst", "10", "Speed with which plants drink water and spread themselves.r",
        Property.Type.INTEGER).getInt(10);
    weatherSpeed = config.get(categoryEcology, "weather-speed", "10", "Multiplier (> 1) for how often weather changes", Property.Type.INTEGER)
        .getInt(10);
    
    undergroundWater = config.get(categoryEcology, "underground-water", "true", "If true, generates humidity and evaporation at different depths underground", 
        Property.Type.BOOLEAN).getBoolean(true);
    plantGrowth = config.get(categoryEcology,"plant-activity", "10", "Overall speed at which plants will grow", 
        Property.Type.INTEGER).getInt(10);
    
    blockSteamDefaultID = config.get(categoryGases,"block-id-steam", "2250", "Initial attempt at ID for the steam block (may be reallocated to an empty block)", 
        Property.Type.INTEGER).getInt(2250);       

    visualizeVolcanoes = config.get(categoryVolcanoes, "visualize-volcanoes", "false", "If true, places a pillar of glas high over volcanoes for finding them before developed", Property.Type.BOOLEAN)
        .getBoolean(false);
    volcanoFrequency = config.get(categoryVolcanoes, "volcano-freqency", "100", "Frequency of volcanoes in percent of default occurance", Property.Type.INTEGER)
        .getInt(100);
    volcanoRadius = config.get(categoryVolcanoes, "volcano-radius", "5", "Maximum radius of the plume feeding a volcano (default 5, safe max 8)", Property.Type.INTEGER)
        .getInt(5);
    volcanoFeeding = config.get(categoryVolcanoes, "volcano-feeding", "20", "Odds for a plume to feed lava / pressure (default 20)", Property.Type.INTEGER)
        .getInt(20);
    
    
       
    doVolcanoes = config.get(categoryModules, "enable-volcanoes", "true", "Enables dynamically generated volcanoes").getBoolean(true);
    doDynamicPlants = config.get(categoryModules, "enable-dynamic-plants", "true", "Enables dynamic growth/death/consumptions of plants").getBoolean(true);
    doTreeFalling = config.get(categoryModules, "enable-tree-felling", "true", "Enables trees that fall when cut down").getBoolean(true);
    doAnimalAI = config.get(categoryModules, "enable-new-animal-ai", "true", "Rewrites the AI/game rules for animals to eat, breed and die").getBoolean(true);
    doRain = config.get(categoryModules, "enable-rain", "true", "Enables rainfall as partial liquids").getBoolean(true);
    doEvaporation = config.get(categoryModules, "enable-evaporation", "true", "Enables dynamically generated volcanoes").getBoolean(true);
    doErosions = config.get(categoryModules, "enable-erosion", "true", "Enables erosion of terrain based on water flows (not working)").getBoolean(true);
    doFluids = config.get(categoryModules, "enable-fluid-dynamics", "true", "Enables fluid dynamics. Most features require this.").getBoolean(true);
    doGases = config.get(categoryModules, "enable-gases", "true", "Enables dynamic gases.").getBoolean(true);
    doNetherfun = config.get(categoryModules, "enable-netherfun", "true", "Enables Fun in the nether").getBoolean(true);
    doExtraFire = config.get(categoryModules, "extra-fires", "true", "Increases the danger and spread of large fires").getBoolean(true);
  } 
}
