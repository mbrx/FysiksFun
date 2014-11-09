package mbrx.ff;

import static net.minecraftforge.common.EnumPlantType.Cave;
import static net.minecraftforge.common.EnumPlantType.Crop;
import static net.minecraftforge.common.EnumPlantType.Desert;
import static net.minecraftforge.common.EnumPlantType.Nether;
import static net.minecraftforge.common.EnumPlantType.Plains;
import static net.minecraftforge.common.EnumPlantType.Water;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PostInit;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;
import mbrx.ff.ecology.AnimalAIRewriter;
import mbrx.ff.ecology.EntityAICoward;
import mbrx.ff.ecology.ExtraFire;
import mbrx.ff.ecology.OceanRepairGenerator;
import mbrx.ff.ecology.Trees;
import mbrx.ff.ecology.Wind;
import mbrx.ff.energy.BlockFFSensor;
import mbrx.ff.energy.BlockTurbine;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.fluids.Gases;
import mbrx.ff.solidBlockPhysics.BlockFFBlockDispenser;
import mbrx.ff.solidBlockPhysics.SolidBlockPhysicsRules;
import mbrx.ff.util.BlockUpdateState;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkMarkUpdateTask;
import mbrx.ff.util.ChunkMarkUpdater;
import mbrx.ff.util.ChunkTempData;
import mbrx.ff.util.Counters;
import mbrx.ff.util.ObjectPool;
import mbrx.ff.util.Settings;
import mbrx.ff.util.SoundQueue;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Property;

import com.google.common.base.Objects;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

@Mod(modid = "FysiksFun", name = "FysiksFun", version = "0.6.0", dependencies = "")
@NetworkMod(clientSideRequired = true, serverSideRequired = false)
public class FysiksFun {
  // Singleton instance of mod class instansiated by Forge
  @Instance("FysiksFun")
  public static FysiksFun                    instance;

  // Says where the client and server proxy code is loaded.
  @SidedProxy(clientSide = "mbrx.ff.client.ClientProxy", serverSide = "mbrx.ff.CommonProxy")
  public static CommonProxy                  proxy;

  public static EventListener                eventListener;
  private static Configuration               config, physicsRuleConfig;
  public static Logger                       logger;

  private static WorldTickHandler            worldTickHandler       = new WorldTickHandler();
  private static ServerTickHandler           serverTickHandler      = new ServerTickHandler();
  private static ClientTickHandler           clientTickHandler      = new ClientTickHandler();
  
  public static Object                       blockTickQueueRing[]   = new Object[300];
  public static ArrayDeque<BlockUpdateState> blockTickQueueFreePool = new ArrayDeque<BlockUpdateState>(10);

  public static boolean                      inWorldTick;

  public static Random                       rand;

  public static Settings                     settings               = new Settings();
  public static ExecutorService              executor;

  public static boolean                      hasBuildcraft          = false;

  public static int totalWater;

  public static class WorldObserver {
    public World w;
    public double posX, posY, posZ;
  };
  public static ArrayList<WorldObserver> observers    = new ArrayList<WorldObserver>();

  /**
   * Mutex for locking the threads whenever a vanilla function is called.
   * Get-only functions can be run non synchronously, modifications to
   * ExtendedBlockStorages can also be run non-synchronosly. But every other
   * modifying call should be wrapped.
   */
  public static Object                   vanillaMutex = new Object();

  // @PreInit
  @EventHandler
  public void preInit(FMLPreInitializationEvent event) {
    ObjectPool.init();

    logger = event.getModLog();
    config = new Configuration(event.getSuggestedConfigurationFile());
    config.load();
    settings.loadFromConfig(config);
    if (config.hasChanged()) config.save();
    executor = Executors.newFixedThreadPool(settings.threadPoolSize);

    String physRulesName = event.getSuggestedConfigurationFile().toString().replace(".cfg", "-rules.cfg");
    physicsRuleConfig = new Configuration(new File(physRulesName));
  }

  // @Init
  @EventHandler
  public void load(FMLInitializationEvent event) {
    System.out.println("[FF] FysiksFun, by M. Broxvall ");
    System.out.println("[FF] Credits: Rubble sounds created by Dan Oberbaur; Earthquake/volcano sound created by Tim Kahn");

    proxy.registerRenderers();
    proxy.registerSounds();

    eventListener = new EventListener();
    MinecraftForge.EVENT_BUS.register(eventListener);

    /*
     * Let these modules load even when not used. Make it easier to not break
     * when disabled.
     */
    Fluids.load();
    Gases.load();

    // TickRegistry.registerTickHandler(worldTickHandler, Side.CLIENT);
    TickRegistry.registerTickHandler(worldTickHandler, Side.SERVER);
    TickRegistry.registerTickHandler(serverTickHandler, Side.SERVER);
    TickRegistry.registerTickHandler(clientTickHandler, Side.CLIENT);

    rand = new Random(4711);
    for (int i = 0; i < 300; i++) {
      blockTickQueueRing[i] = (Object) new HashSet<BlockUpdateState>();
    }

    BlockWorldSupport.init();
    settings.worldSupportBlockID = Util.findBlockIdFromName(settings.worldSupportBlockString);
    if (settings.worldSupportBlockID == 0) {
      FysiksFun.logger.log(Level.SEVERE, "[FF] Cannot find block named '" + settings.worldSupportBlockString + "' as the world-support block");
    } else FysiksFun.logger.log(Level.SEVERE, "[FF] Found block named'" + settings.worldSupportBlockString + "' as the world-support block, ID:"
        + settings.worldSupportBlockID);

  }

  // @PostInit
  @EventHandler
  public void postInit(FMLPostInitializationEvent event) {
    // Add our water repair generator AFTER the other generators
    // if (settings.repairOceans)
    GameRegistry.registerWorldGenerator(new OceanRepairGenerator());

    /* Figure out if we have buildcraft registered */
    for (Block b : Block.blocksList) {
      if (b != null && b.getUnlocalizedName() != null && b.getUnlocalizedName().matches("tile.blockOil")) hasBuildcraft = true;
    }
    logger.log(Level.INFO, "Buildcraft found: " + hasBuildcraft);

    GameRegistry.registerWorldGenerator(new WorldSupportGenerator());

    if (settings.doFluids) Fluids.postInit();
    if (settings.doGases) Gases.postInit();
    if (settings.doExtraFire) ExtraFire.postInit();

    if (hasBuildcraft) BlockTurbine.init();
    BlockFFSensor.init();
    BlockFFBlockDispenser.init();

    physicsRuleConfig.load();
    SolidBlockPhysicsRules.postInit(physicsRuleConfig);
    if (physicsRuleConfig.hasChanged()) physicsRuleConfig.save();
    ExtraBlockBehaviours.postInit(event.getSide().isServer());
    // Must be after all other blocks (that may be related to trees) have been
    // created
    Trees.initTreePartClassification();
  }

  /**
   * Temporary variable for quickly creating a blockUpdateState without risking
   * GC'ing
   */
  private static BlockUpdateState tempBlockUpdateState = new BlockUpdateState();

  /**
   * Utility function for removing a specific block-update from the queue.
   * Currently not used.
   */
  public static void removeBlockTick(World w, Block block, int x, int y, int z, int maxDelay) {
    BlockUpdateState state = tempBlockUpdateState;
    state.set(w, block, x, y, z);

    for (int d = 1; d <= maxDelay; d++) {
      ((Set<BlockUpdateState>) blockTickQueueRing[(Counters.tick + d) % 300]).remove(state);
    }
  }

  /** Schedules a block for updates, without giving an explanation */
  public static void scheduleBlockTick(World w, Block block, int x, int y, int z, int delay) {
    scheduleBlockTick(w, block, x, y, z, delay, "");
  }

  /**
   * Schedules a block for updates, with the given explanation (printed only in
   * debug mode)
   */
  public static void scheduleBlockTick(World w, Block block, int x, int y, int z, int delay, String explanation) {
    if (delay <= 0 || delay >= 300) return;
    Set<BlockUpdateState> q = (Set<BlockUpdateState>) blockTickQueueRing[(Counters.tick + delay) % 300];
    int size = q.size();
    if (size > 50000) return;
    if (inWorldTick && size >= 40000) return;
    BlockUpdateState state;
    if (blockTickQueueFreePool.size() > 0) state = blockTickQueueFreePool.pop();
    else state = new BlockUpdateState();
    state.set(w, block, x, y, z);

    /*
     * Check if block has already been scheduled for an earlier update, if so
     * ignore this one
     */
    /*
     * This may seem like a less efficient way as compared to a priority heap -
     * however it should be more efficient where it is needed (for liquids with
     * small tick rates)
     */
    for (int d = 1; d <= delay; d++)
      if (((Set<BlockUpdateState>) blockTickQueueRing[(Counters.tick + d) % 300]).contains(state)) {
        blockTickQueueFreePool.push(state);
        return;
      }
    q.add(state);
  }

  /**
   * Performs the ticks that should be done once per server tick loop, including
   * the scheduling of all block updates and mark TO client calls
   */
  public static void tickServer() {
    // System.out.println("Start of tickServer");

    /* Update world tick and print statistics */
    Counters.tick++;
    if (Counters.tick % 50 == 0) {
      Counters.printStatistics();
    }
    if (settings.doFluids) Fluids.checkBlockOverwritten();

    /*if (Counters.tick % 47 == 0) {
      EntityAICoward.cleanup();
    }*/

    /*if (Counters.tick == 20) {
      System.out.println("[FF] Dumping list of all blocks");
      for (Block b : Block.blocksList) {
        if (b != null) System.out.println("Block " + b.blockID + " name: '" + b.getUnlocalizedName() + "'");
      }            
    }*/

    try {
      inWorldTick = true;
      int foo = blockTickQueueFreePool.size();
      // First tick all blocks
      int before = 0, after = -42;
      // Count the total size of all allocated BlockUpdate instances
      // before/after to detect leakage
      int totsize = blockTickQueueFreePool.size();
      for (int i = 0; i < 300; i++)
        totsize += ((Set<BlockUpdateState>) blockTickQueueRing[i]).size();

      for (BlockUpdateState s : (Set<BlockUpdateState>) blockTickQueueRing[Counters.tick % 300]) {
        BlockUpdateState s2 = s;
        if (s != null) {
          s.block.updateTick(s.w, s.x, s.y, s.z, rand);
          s.set(null, null, 0, 0, 0);
          before = blockTickQueueFreePool.size();
          blockTickQueueFreePool.push(s2);
          after = blockTickQueueFreePool.size();
          Counters.liquidQueueCounter++;
        }
      }
      ((HashSet<BlockUpdateState>) blockTickQueueRing[Counters.tick % 300]).clear();

      int totsize2 = blockTickQueueFreePool.size();
      for (int i = 0; i < 300; i++)
        totsize2 += ((Set<BlockUpdateState>) blockTickQueueRing[i]).size();
    } finally {
      inWorldTick = false;
    }

    /* Queue chunks/blocks to be send to the client */
    ChunkMarkUpdater.doTick();

    /*
     * Clear the observers so we don't accidentally cache worlds, they will be
     * repopulated before next tick anyway
     */
    observers.clear();

    /* Reset the cache regularly to make sure we get rid of old (swapped out) chunks and tempData objects. */
    if ((Counters.tick % 413) == 0) ChunkCache.resetCache();

    // System.out.println("End of tickServer");
    
    //SoundQueue.doSoundEffects();
  }

  /**
   * Performs the ticks that should happen for each world on the SERVER and the
   * CLIENT
   */
  public static void tickWorld(World w) {

    //if(w.isRemote) {
    //}
    
    //System.out.println("Start of world tick");

    /*if(!w.isRemote && w.provider.dimensionId == 0) {
      int id = w.getBlockId(239, 74, 61);
      System.out.println("w: "+w+" ID@239,74,61: "+id);
    }*/

    
    ChunkTempData.cleanup(w);

    // Fast forward the time until rain stops/starts
    int rainTime = w.getWorldInfo().getRainTime();
    if (rainTime > settings.weatherSpeed - 1) w.getWorldInfo().setRainTime(rainTime + 1 - settings.weatherSpeed);

    // Build list of observers used by chunk marking
    if (!w.isRemote) {
      List allEntities = w.loadedEntityList;
      for (Object o : allEntities) {
        if (o instanceof Player) {
          
          /*EntityPlayer ply = (EntityPlayer) o;
          if(ply != null) {
            System.out.println("Found SSP player: "+ply);
            InventoryPlayer inventory = ply.inventory;
            for(ItemStack stack : inventory.mainInventory) {
              if(stack == null) continue;
              System.out.println("Player item with id: "+stack.itemID+" damage/meta: "+stack.getItemDamage());
              if(stack.itemID == 2131) stack.setItemDamage(0);
            }
          }*/
          
          Entity e = (Entity) o;
          WorldObserver observer = new WorldObserver();
          observer.w = w;
          observer.posX = e.posX;
          observer.posY = e.posY;
          observer.posZ = e.posZ;
          observers.add(observer);
        }
      }

    }

    if (settings.doAnimalAI) AnimalAIRewriter.rewriteAnimalAIs(w);
    if (settings.doGases) Gases.doWorldTick(w);
    if (settings.doTreeFalling) Trees.doTick(w);
    Wind.doTick(w);
    totalWater=0;
    
    MPWorldTicker.doBlockSweeps(w);
    MPWorldTicker.doUpdateChunks(w);
    
    //if(w.provider.dimensionId == 0)
    //  System.out.println("Total water in world: "+(totalWater/(double) BlockFFFluid.maximumContent)+ "    "+w);
    
    // Do this last to remove any entites that are now inside any blocks
    ExtraEntityBehaviours.doTick(w);

    // System.out.println("Active chunks: "+w.activeChunkSet.size());
    // System.out.println("End of world tick");
  }

  /**
   * Priority MAY be used for prioritization of when the mark messages are sent,
   * but currently is not. Use 0 for now.
   */
  public static synchronized void setBlockWithMetadataAndPriority(World w, int x, int y, int z, int id, int meta, int pri) {

    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    if (c == null) return;
    synchronized (FysiksFun.vanillaMutex) {
      c.setBlockIDWithMetadata(x & 15, y, z & 15, id, meta);
    }
    ChunkMarkUpdater.scheduleBlockMark(w, x, y, z);
  }

  public static void tickPlayer(Player player, World w) {}

  /**
   * A thread safe mechanism for (a) assigning the ID and meta to a block, and
   * (b) to schedule a block mark update. If delayedBlockMarkSet is given then
   * the updates are scheduled for later, otherwise they are done _now_ but in
   * another thread safe way.
   */
  public static void setBlockIDandMetadata(World w, Chunk c, int x, int y, int z, int id, int meta, int oldId, int oldMeta,
      Set<ChunkMarkUpdateTask> delayedBlockMarkSet) {

    ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();
    ExtendedBlockStorage ebs = blockStorage[y >> 4];
    ebs = null;
    if (ebs == null) {
      synchronized (FysiksFun.vanillaMutex) {
        c.setBlockIDWithMetadata(x & 15, y, z & 15, id, meta);
      }
    } else {
      // This code intentionally left dead...
      if (y > c.heightMap[(x & 15) + (z & 15) * 16]) c.generateSkylightMap();
      c.updateSkylightColumns[(x & 15) + (z & 15) * 16] = true;

      ebs.setExtBlockID(x & 15, y & 15, z & 15, id);
      ebs.setExtBlockMetadata(x & 15, y & 15, z & 15, meta);
    }

    if (delayedBlockMarkSet == null) ChunkMarkUpdater.scheduleBlockMark(w, x, y, z, oldId, oldMeta);
    else {
      ChunkMarkUpdateTask task = ObjectPool.poolChunkMarkUpdateTask.getObject();
      task.set(w, x, y, z, oldId, oldMeta);
      delayedBlockMarkSet.add(task);
      // delayedBlockMarkSet.add(new ChunkMarkUpdateTask(w, x, y, z, oldId,
      // oldMeta));
    }
  }

  public static float minXZDistSqToObserver(World w, float x, float z) {
    float minDist = 1e6F;
    for (FysiksFun.WorldObserver wo : FysiksFun.observers) {
      if (wo.w != w) continue;
      float dist = (float) ((wo.posX - x) * (wo.posX - x) + (wo.posZ - z) * (wo.posZ - z));
      if (dist < minDist) minDist = dist;
    }
    return minDist;
  }

  public static float minDistSqToObserver(World w, float x, float y, float z) {
    float minDist = 1e6F;
    for (FysiksFun.WorldObserver wo : FysiksFun.observers) {
      if (wo.w != w) continue;
      float dist = (float) ((wo.posX - x) * (wo.posX - x) + (wo.posY - y) * (wo.posY - y) + (wo.posZ - z) * (wo.posZ - z));
      if (dist < minDist) minDist = dist;
    }
    return minDist;
  }

  public static boolean isCurrentlyMovingABlock = false;

  public static void moveBlock(World w, int xD, int yD, int zD, int xS, int yS, int zS, boolean scheduleUpdate, boolean clearSource) {
    isCurrentlyMovingABlock = true;
    Chunk cD = ChunkCache.getChunk(w, xD >> 4, zD >> 4, true);
    Chunk cS = ChunkCache.getChunk(w, xS >> 4, zS >> 4, true);
    // System.out.println("Moving block from: "+Util.xyzString(xS,yS,zS)+" to "+Util.xyzString(xD,yD,zD));

    int idS = cS.getBlockID(xS & 15, yS, zS & 15);
    int metaS = cS.getBlockMetadata(xS & 15, yS, zS & 15);

    int idD = cD.getBlockID(xD & 15, yD, zD & 15);
    int metaD = cD.getBlockMetadata(xD & 15, yS, zD & 15);
    Block blockS = Block.blocksList[idS];

    synchronized (FysiksFun.vanillaMutex) {
      cD.setBlockIDWithMetadata(xD & 15, yD, zD & 15, idS, metaS);
    }
    if (scheduleUpdate) ChunkMarkUpdater.scheduleBlockMark(w, xD, yD, zD, idD, metaD);

    if (blockS instanceof ITileEntityProvider) {
      TileEntity entity = cS.getChunkBlockTileEntity(xS & 15, yS, zS & 15);
      cS.removeChunkBlockTileEntity(xS & 15, yS, zS & 15);
      entity.xCoord = xD;
      entity.yCoord = yD;
      entity.zCoord = zD;
      entity.validate();
      cD.addTileEntity(entity);
    }
    if (clearSource) {
      cS.setBlockIDWithMetadata(xS & 15, yS, zS & 15, 0, 0);
      if (scheduleUpdate) ChunkMarkUpdater.scheduleBlockMark(w, xS, yS, zS, idD, metaD);
    }
    isCurrentlyMovingABlock = false;
  }


  
}
