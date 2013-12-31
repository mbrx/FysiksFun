package mbrx.ff;

import static net.minecraftforge.common.EnumPlantType.Cave;
import static net.minecraftforge.common.EnumPlantType.Crop;
import static net.minecraftforge.common.EnumPlantType.Desert;
import static net.minecraftforge.common.EnumPlantType.Nether;
import static net.minecraftforge.common.EnumPlantType.Plains;
import static net.minecraftforge.common.EnumPlantType.Water;

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
import java.util.concurrent.Semaphore;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
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
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntitySquid;
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

@Mod(modid = "FysiksFun", name = "FysiksFun", version = "0.4.2")
@NetworkMod(clientSideRequired = true, serverSideRequired = false)
public class FysiksFun {
  // Singleton instance of mod class instansiated by Forge
  @Instance("FysiksFun")
  public static FysiksFun                    instance;

  // Says where the client and server proxy code is loaded.
  @SidedProxy(clientSide = "mbrx.ff.client.ClientProxy", serverSide = "mbrx.ff.CommonProxy")
  public static CommonProxy                  proxy;
  private static final String                ConfigCategory_Generic = "general";

  public static EventListener                eventListener;
  private static Configuration               config;
  public static Logger                       logger;
  private static WorldTickHandler            worldTickHandler       = new WorldTickHandler();
  private static ServerTickHandler           serverTickHandler      = new ServerTickHandler();

  public static Object                       blockTickQueueRing[]   = new Object[300];
  public static ArrayDeque<BlockUpdateState> blockTickQueueFreePool = new ArrayDeque<BlockUpdateState>(10);

  public static boolean                      inWorldTick;

  public static Random                       rand;

  public static Settings                     settings               = new Settings();
  public static ExecutorService              executor               = Executors.newFixedThreadPool(20);

  public static class WorldObserver {
    World w;
    double posX, posY, posZ;
  };

  public static ArrayList<WorldObserver> observers                = new ArrayList<WorldObserver>();

  public static Semaphore                globalWorldChangingMutex = new Semaphore(1);

  @PreInit
  public void preInit(FMLPreInitializationEvent event) {
    logger = event.getModLog();
    config = new Configuration(event.getSuggestedConfigurationFile());
  }

  @Init
  public void load(FMLInitializationEvent event) {
    proxy.registerRenderers();

    config.load();
    settings.loadFromConfig(config);
    if (config.hasChanged()) config.save();

    eventListener = new EventListener();
    MinecraftForge.EVENT_BUS.register(eventListener);

    /*
     * Let these modules load even when not used. Make it easier to not break when disabled.
     */
    Fluids.load();
    Gases.load();

    // TickRegistry.registerTickHandler(worldTickHandler, Side.CLIENT);
    TickRegistry.registerTickHandler(worldTickHandler, Side.SERVER);
    TickRegistry.registerTickHandler(serverTickHandler, Side.SERVER);

    rand = new Random(4711);
    for (int i = 0; i < 300; i++) {
      blockTickQueueRing[i] = (Object) new HashSet<BlockUpdateState>();
    }
  }

  @PostInit
  public void postInit(FMLPostInitializationEvent event) {
    // Add our water repair generator AFTER the other generators
    // if (settings.repairOceans)
    // GameRegistry.registerWorldGenerator(new OceanRepairGenerator());
    if (settings.doFluids) Fluids.postInit();
    if (settings.doGases) Gases.postInit();
    if (settings.doExtraFire) ExtraFire.postInit();
    BlockPhysicsSweepWorkerThread.postInit();
  }

  /**
   * Temporary variable for quickly creating a blockUpdateState without risking GC'ing
   */
  private static BlockUpdateState tempBlockUpdateState = new BlockUpdateState();

  /**
   * Utility function for removing a specific block-update from the queue. Currently not used.
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
   * Schedules a block for updates, with the given explanation (printed only in debug mode)
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
     * Check if block has already been scheduled for an earlier update, if so ignore this one
     */
    /*
     * This may seem like a less efficient way as compared to a priority heap - however it should be more efficient
     * where it is needed (for liquids with small tick rates)
     */
    for (int d = 1; d <= delay; d++)
      if (((Set<BlockUpdateState>) blockTickQueueRing[(Counters.tick + d) % 300]).contains(state)) {
        blockTickQueueFreePool.push(state);
        return;
      }
    q.add(state);
  }

  /**
   * Performs the ticks that should be done once per server tick loop, including the scheduling of all block updates and
   * mark TO client calls
   */
  public static void tickServer() {
    /* Update world tick and print statistics */
    Counters.tick++;
    if (Counters.tick % 50 == 0) {
      Counters.printStatistics();
    }
    Fluids.checkBlockOverwritten();

    /*if (Counters.tick == 500) {
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
     * Clear the observers so we don't accidentally cache worlds, they will be repopulated before next tick anyway
     */
    observers.clear();

  }

  /**
   * Performs the ticks that should happen for each world on the SERVER and the CLIENT
   */
  public static void doWorldTick(World w) {

    if (settings.doAnimalAI) AnimalAIRewriter.rewriteAnimalAIs(w);
    if (settings.doGases) Gases.doWorldTick(w);
    if (settings.doTreeFalling) Trees.doTick(w);
    ExtraEntityBehaviours.doTick(w);
    
    if (!w.isRemote) {      
      List allEntities = w.loadedEntityList;
      
      for (Object o : allEntities) {
        if (o instanceof Player) {
          Entity e = (Entity) o;
          WorldObserver observer = new WorldObserver();
          observer.w = w;
          observer.posX = e.posX;
          observer.posY = e.posY;
          observer.posZ = e.posZ;
          observers.add(observer);
        }
        /* Kill all squids! */
        /*if (o instanceof EntitySquid) {
          EntitySquid e = (EntitySquid) o;
          if(e.posY < 60) {
            System.out.println("Killing a squid: "+e);
            w.removeEntity(e);
          }
        }*/
      }
      
    }
       
    // System.out.println("Players found: "+observers.size());

    int rainTime = w.getWorldInfo().getRainTime();
    if (rainTime > settings.weatherSpeed - 1) w.getWorldInfo().setRainTime(rainTime + 1 - settings.weatherSpeed);

    Wind.doTick(w);
    MPWorldTicker.doBlockSweeps(w);
    MPWorldTicker.doUpdateChunks(w);
    // System.out.println("Active chunks: "+w.activeChunkSet.size());

  }

  /**
   * Priority MAY be used for prioritization of when the mark messages are sent, but currently is not. Use 0 for now.
   */
  public static synchronized void setBlockWithMetadataAndPriority(World w, int x, int y, int z, int id, int meta, int pri) {

    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    if (c == null) return;
    c.setBlockIDWithMetadata(x & 15, y, z & 15, id, meta);
    ChunkMarkUpdater.scheduleBlockMark(w, x, y, z);
  }

  public static void tickPlayer(Player player, World w) {}

  /** A thread safe mechanism for (a) assigning the ID and meta to a block, and (b) to schedule a block mark update. */
  public static void setBlockIDandMetadata(World w, Chunk c, int x, int y, int z, int id, int meta, int oldId, int oldMeta,
      HashSet<ChunkMarkUpdateTask> delayedBlockMarkSet) {

    ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();
    ExtendedBlockStorage ebs = blockStorage[y >> 4];
    if(ebs == null) 
      c.setBlockIDWithMetadata(x & 15, y, z & 15, id, meta);
    else {
      ebs.setExtBlockID(x & 15, y & 15, z & 15, id);
      ebs.setExtBlockMetadata(x & 15, y & 15, z & 15, meta);
    }

    if (delayedBlockMarkSet == null) ChunkMarkUpdater.scheduleBlockMark(w, x, y, z, oldId, oldMeta);
    else delayedBlockMarkSet.add(new ChunkMarkUpdateTask(w, x, y, z, oldId, oldMeta));

  }

}
