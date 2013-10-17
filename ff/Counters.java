package mbrx.ff;

import java.util.HashSet;

public class Counters {

  public static int tick                       = 0;
  public static int updateBlockCounter         = 0;
  public static int rewriteBlockCounter        = 0;
  public static int tickChangedBlocks          = 0;
  public static int genericCounter             = 0;
  public static int liquidQueueCounter         = 0;
  public static int markQueueCounter           = 0;
  public static int erosionCounter             = 0;
  public static int worldHeatEvaporation       = 0;
  public static int rainCounter                = 0;
  public static int smallLiquidExtraTicks      = 0;
  public static int directEvaporation   = 0;
  public static int indirectEvaporation = 0;
  public static int treeCounter                = 0;
  public static int fluidUpdates							 = 0;
  
  public static int treeDrink                  = 0;
  public static int treeKill                   = 0;
  public static int cropsDrink                 = 0;
  public static int cropsSpread                = 0;
  public static int cropsDie                   = 0;
  public static int earlyTick                  = 0;
  public static int humidification             = 0;

  public static int heatEvaporation            = 0;

  
  public static void printStatistics() {
       
  	int totsize=0;
  	for(int i=0;i<300;i++) {
  		HashSet set = (HashSet) FysiksFun.blockTickQueueRing[i];
  		totsize += set.size();
  	}
  	System.out.println("Liquid Tick Queue: " + (liquidQueueCounter) + " per tick (" + totsize + " used "+ FysiksFun.blockTickQueueFreePool.size() + " free pool) smallLiquidExtraTicks: "+smallLiquidExtraTicks);
  	System.out.println("Fluid updates: "+fluidUpdates);
  	fluidUpdates=0;  	
    System.out.println("Mark counter: " + (markQueueCounter) + " per tick");
    
    /*
    System.out.println("Liquid Tick Queue: " + (liquidQueueCounter / 300) + " per tick (" + totsize + " used "+ FysiksFun.blockTickQueueFreePool.size() + " free pool) smallLiquidExtraTicks: "+smallLiquidExtraTicks/300);
    System.out.println("Mark counter: " + (markQueueCounter / 300) + " per tick");
    */
    //System.out.println("Erosion events: " + erosionCounter);
    //System.out.println("Rain: +" + rainCounter + " evaporation: " + directEvaporation + "/" + worldHeatEvaporation + "/" + indirectEvaporation + " humidification: +" + humidification);
    //System.out.println("Trees processed: " + treeCounter + " drinks: " + treeDrink + " treeKill: " + treeKill);
    //System.out.println("Crops drink: " + cropsDrink + " spreads: " + cropsSpread + " dies: " + cropsDie);
    //System.out.println("Observers in world: " + FysiksFun.observers.size() + " early marks: " + earlyTick + " total");
    //System.out.println("Heat evaporation: "+ heatEvaporation);
    ChunkMarkUpdater.printStatistics();
    earlyTick = 0;
    cropsDrink = 0;
    cropsSpread = 0;
    cropsDie = 0;
    humidification = 0;
    heatEvaporation = 0;
    smallLiquidExtraTicks      = 0;
    
    treeCounter = 0;
    treeDrink = 0;
    treeKill = 0;

    updateBlockCounter = 0;
    tickChangedBlocks = 0;
    liquidQueueCounter = 0;
    genericCounter = 0;
    markQueueCounter = 0;
    erosionCounter = 0;
    rainCounter = 0;
    worldHeatEvaporation = 0;
    directEvaporation = 0;
  }
}
