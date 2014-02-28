package mbrx.ff.util;

import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

public class Settings {
  public int     clayToDirtChance;

  public int     maxUpdatesPerTick;

  public double  waterEvaporationRate;
  public double  waterRainRate;
  public int     erosionRate;
  // public int erosionThreshold;
  // public boolean rainInOceans;
  public boolean alwaysRaining      = false;
  // public boolean liquidMetal;
  public boolean netherrackCanMelt;
  public boolean undergroundWater;
  // public boolean repairOceans;
  // public boolean infiniteOceans;
  public boolean flowingLiquidOil;
  public boolean flowingHydrochloricAcid;

  public boolean visualizeVolcanoes = false;
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
  public int     worldYOffset;

  public int     blockSteamDefaultID;
  public int     blockPyroclasticDefaultID;
  /* TODO, reduce usage of blockID's by merging different forms of turbines? */
  public int     blockWoodTurbineDefaultID;
  public int     blockIronTurbineDefaultID;
  public int     blockGoldTurbineDefaultID;
  public int     blockDiamondTurbineDefaultID;
  public int     blockIronGasTurbineDefaultID;
  public int     blockGoldGasTurbineDefaultID;
  public int     blockDiamondGasTurbineDefaultID;
  public int blockWoodSensorDefaultID;
  public int blockIronSensorDefaultID;
  public int blockGoldSensorDefaultID;
  public int blockDiamondSensorDefaultID;

  private String categoryModules    = "modules";
  private String categoryGeneric    = "generic";
  private String categoryFluids     = "fluids";
  private String categoryEcology    = "ecology";
  private String categoryOther      = "other";
  private String categoryGases      = "gases";
  private String categoryVolcanoes  = "volcanoes";
  private String categoryPhysics    = "physics";

  public boolean doVolcanoes;
  public boolean doDynamicPlants;
  public boolean doTreeFalling;
  public boolean doTreeConsumptions;
  public boolean doAnimalAI;
  public boolean doRain;
  public boolean doEvaporation;
  public boolean doErosions;
  public boolean doFluids;
  public boolean doGases;
  public boolean doNetherfun;
  public boolean doExtraFire;
  public boolean doPhysics;
  public boolean doEnergy;

  public boolean canPlaceStone;

  public boolean stonesShatter;
  public boolean leavesAreSoft;


  public void loadFromConfig(Configuration config) {

    config.addCustomCategoryComment(categoryModules, "Enables different modules of the mod, some dependencies within modules");
    config.addCustomCategoryComment(categoryFluids, "Settings for fluid dynamics and world liquid budgets (evaporation, rainfall, lava generation)");
    config.addCustomCategoryComment(categoryPhysics,
        "Everything regarding the physics of solid blocks, except for the set of block rules (see FysiksFun-rules.cfg)");
    config.addCustomCategoryComment(categoryGeneric, "All high-level settings governing generic aspects of FysiksFun");
    config.addCustomCategoryComment(categoryEcology, "Settings for the natural ecology such as plants and animals");
    config.addCustomCategoryComment(categoryGases, "Settings for all gas dynamics and interaction between gases and liquids");
    config.addCustomCategoryComment(categoryOther, "All other (unsorted) settings, may move in the future");
    config.addCustomCategoryComment(categoryVolcanoes, "Settings related to dynamically generated volcanoes");

    doVolcanoes = config.get(categoryModules, "enable-volcanoes", "true", "Enables dynamically generated volcanoes").getBoolean(true);
    doDynamicPlants = config.get(categoryModules, "enable-dynamic-plants", "true", "Enables dynamic growth/death/consumptions of plants").getBoolean(true);
    doTreeFalling = config.get(categoryModules, "enable-tree-felling", "true", "Enables trees that fall when cut down").getBoolean(true);
    doAnimalAI = config.get(categoryModules, "enable-new-animal-ai", "true", "Rewrites the AI/game rules for animals to eat, breed and die").getBoolean(true);
    doRain = config.get(categoryModules, "enable-rain", "true", "Enables rainfall as partial liquids").getBoolean(true);
    doEvaporation = config.get(categoryModules, "enable-evaporation", "true", "Enables water to evaporate from oceans and deep underground, and humidity to fill up mid-range underground (eg. for wells)").getBoolean(true);
    doErosions = config.get(categoryModules, "enable-erosion", "true", "Enables erosion of terrain based on water flows (not working)").getBoolean(true);
    doFluids = config.get(categoryModules, "enable-fluid-dynamics", "true", "Enables fluid dynamics. Most features require this.").getBoolean(true);
    doGases = config.get(categoryModules, "enable-gases", "true", "Enables dynamic gases.").getBoolean(true);
    doNetherfun = config.get(categoryModules, "enable-netherfun", "true", "Enables Fun in the nether").getBoolean(true);
    doExtraFire = config.get(categoryModules, "extra-fires", "true", "Increases the danger and spread of large fires").getBoolean(true);
    doTreeConsumptions = config.get(categoryModules, "enable-dynamic-trees", "true", "Makes trees consume water or die").getBoolean(true);
    doPhysics = config.get(categoryModules, "enable-physics", "true", "Enables physics of all solid blocks. ").getBoolean(true);
    doEnergy = config.get(categoryModules, "enable-energy", "true",
        "Allows construction of energy producing machines (turbines) based on liquids&gases. Requires buildcraft.").getBoolean(true);
    
    maxUpdatesPerTick = config
        .get(
            categoryGeneric,
            "max-client-updates-per-tick",
            "300",
            "Maximum workload of chunks/blocks updates transmitted to client each tick. Default 300, higher cause more lag but shows more world updates to the client. For servers use a lower value if too much bandwidth is used",
            Property.Type.INTEGER).getInt(300);

    waterEvaporationRate = config.get(categoryFluids, "water-evaporation", "10.0",
        "Maximum rate for water evaporation, default 10, higher is more evaporation", Property.Type.DOUBLE).getDouble(10.0);
    waterRainRate = config.get(categoryFluids, "water-rain", "10.0", "Maximum intensity of rain, default 10, higher is more rain", Property.Type.DOUBLE)
        .getDouble(10.0);
    flowingLiquidOil = config.get(categoryFluids, "flowing-oil", "true", "Let buildcraft oil (if found) be treated as a flowing liquid", Property.Type.BOOLEAN)
        .getBoolean(true);
    flowingHydrochloricAcid = config.get(categoryFluids, "flowing-hydrochloric-acid", "true",
        "Let hydrochloric acid (from the Factorization mod) be treated as a flowing liquid", Property.Type.BOOLEAN).getBoolean(true);
    netherrackCanMelt = config.get(categoryFluids, "netherrack-can-melt", "true", "If true, burning netherrack slowly turn into lava", Property.Type.BOOLEAN)
        .getBoolean(true);
    erosionRate = config.get(categoryFluids, "erosion-rate", "10", "How quickly terrain can errode, 0 is off, default 10, larger values for faster erosions",
        Property.Type.INTEGER).getInt(10);
    // erosionThreshold = config.get(categoryFluids, "erosion-threshold",
    // "0",
    // "Threshold for when errosion can occur, 0 for any water movement, 1 (or higher) for only larger streams of water",
    // Property.Type.INTEGER).getInt(0);

    // liquidMetal = config.get(categoryFluids, "flowing-metal", "false",
    // "NOT WORKING! Let metals from the mod Tinkers construct (if found) be treated as a flowing liquid",
    // Property.Type.BOOLEAN).getBoolean(false);
    // liquidMetal = false;
    // repairOceans = config.get(categoryFluids, "repair-oceans", "true",
    // "If true attempt to repair ocean floors to not connect underground caves",
    // Property.Type.BOOLEAN)
    // .getBoolean(true);
    // infiniteOceans = config.get(categoryFluids, "infinite-oceans",
    // "true",
    // "If true, oceans contain infinite water sources after a certain depth",
    // Property.Type.BOOLEAN)
    // .getBoolean(true);

    // alwaysRaining = config.get(categoryFluids, "always-raining", "false",
    // "Íf there is continous raining (regardless of actual world rain animation)",
    // Property.Type.BOOLEAN).getBoolean(false);
    // rainInOceans = config.get(categoryFluids, "rain-in-oceans", "false",
    // "If rain falling in oceans will actually contribute to water level (false is efficient, true is realisitc)",
    // Property.Type.BOOLEAN).getBoolean(false);

    treeThirst = config.get(categoryEcology, "tree-thirst", "10", "Amount of water consumed by trees when enable-tree-consumption is true",
        Property.Type.INTEGER).getInt(10);
    treesFall = config.get(categoryEcology, "trees-fall", "true", "Trees fall in a semi-natural way when chopped down, in addition to the normal physics",
        Property.Type.BOOLEAN).getBoolean(true);
    cropsThirst = config.get(categoryEcology, "crops-thirst", "10", "Amount of water consumed by crops (default 10, zero disables need for water)",
        Property.Type.INTEGER).getInt(10);
    cropsDrinkContinously = config.get(categoryEcology, "crops-continously-thirsty", "true", "If true, also full-grown crops consumes water",
        Property.Type.BOOLEAN).getBoolean(true);
    plantsThirst = config.get(categoryEcology, "plants-thirst", "10",
        "Speed with which plants and crops drink water and plants spread themselves. (default 10)", Property.Type.INTEGER).getInt(10);
    weatherSpeed = config.get(categoryEcology, "weather-speed", "10", "Multiplier (> 1) for how often weather changes", Property.Type.INTEGER).getInt(10);
    clayToDirtChance = config.get(categoryEcology, "clay-creation", "0",
        "Multiplier (normal rate 10) for how quickly underwater dirt can be converted to clay. Use zero to disable.", Property.Type.INTEGER).getInt(0);

    undergroundWater = config.get(categoryEcology, "underground-water", "true", "Generates humidity and evaporation at different depths underground",
        Property.Type.BOOLEAN).getBoolean(true);
    plantGrowth = config.get(categoryEcology, "plant-activity", "10", "Overall speed at which plants will grow", Property.Type.INTEGER).getInt(10);

    blockSteamDefaultID = config.getBlock("steam-id", 2250, "ID for steam blocks").getInt();
    blockPyroclasticDefaultID = config.getBlock("pyroclastic-id", 2251, "ID for pyroclastic cloud blocks").getInt();
    blockWoodTurbineDefaultID = config.getBlock("wood-turbine-id", 2252, "ID for water turbine blocks").getInt();
    blockIronTurbineDefaultID = config.getBlock("iron-turbine-id", 2253, "ID for water turbine blocks").getInt();
    blockGoldTurbineDefaultID = config.getBlock("gold-turbine-id", 2254, "ID for water turbine blocks").getInt();
    blockDiamondTurbineDefaultID = config.getBlock("diamond-turbine-id", 2255, "ID for water turbine blocks").getInt();
    blockIronGasTurbineDefaultID = config.getBlock("iron-gas-turbine-id", 2256, "ID for gas turbine blocks").getInt();
    blockGoldGasTurbineDefaultID = config.getBlock("gold-gas-turbine-id", 2256, "ID for gas turbine blocks").getInt();
    blockDiamondGasTurbineDefaultID = config.getBlock("diamond-gas-turbine-id", 2256, "ID for gas turbine blocks").getInt();
    blockWoodSensorDefaultID = config.getBlock("wood-sensor-id", 2257, "ID for liquid sensor blocks").getInt();
    blockIronSensorDefaultID = config.getBlock("iron-sensor-id", 2258, "ID for liquid sensor blocks").getInt();
    blockGoldSensorDefaultID = config.getBlock("gold-sensor-id", 2259, "ID for liquid sensor blocks").getInt();
    blockDiamondSensorDefaultID = config.getBlock("diamond-sensor-id", 2260, "ID for liquid sensor blocks").getInt();

    
    worldYOffset = config.get(categoryGases, "y-offset", "0",
        "Offset of sealevel for computation of gas/volcano behaviours. Default assume sea-level at y=64. Use +80 for TFC", Property.Type.INTEGER).getInt(0);

    // visualizeVolcanoes = config.get(categoryVolcanoes,
    // "visualize-volcanoes", "false",
    // "If true, places a pillar of glas high over volcanoes for finding them before developed",
    // Property.Type.BOOLEAN)
    // .getBoolean(false);
    volcanoFrequency = config.get(categoryVolcanoes, "volcano-freqency", "100", "Frequency of volcanoes in percent (default 100)", Property.Type.INTEGER)
        .getInt(100);
    volcanoRadius = config.get(categoryVolcanoes, "volcano-radius", "5",
        "Maximum radius of the plume feeding a volcano (default 5, safe max 8, absolute max 16)", Property.Type.INTEGER).getInt(5);
    volcanoFeeding = config.get(categoryVolcanoes, "volcano-feeding", "20", "Odds for a plume to feed lava / pressure (default 20)", Property.Type.INTEGER)
        .getInt(20);

    canPlaceStone = config.get(categoryGeneric, "can-place-stone", "false", "If ordinary smooth stone can be placed by user", Property.Type.BOOLEAN)
        .getBoolean(false);
    stonesShatter = config.get(categoryGeneric, "stone-shatters", "true", "Stones that break may create a chain reaction of shattering stones, potentially causing cave-ins", Property.Type.BOOLEAN)
        .getBoolean(true);
    stonesShatter = config.get(categoryGeneric, "leaves-are-soft", "true", "Makes leaves act somewhat like spider webs, lettings the player move through at reduced speed. (Avoids exploit by building leaf bridges)", Property.Type.BOOLEAN)
        .getBoolean(true);

  }

}
