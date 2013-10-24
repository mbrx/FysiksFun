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
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Property;

import com.google.common.base.Objects;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

@Mod(modid = "FysiksFun", name = "FysiksFun", version = "0.3.90")
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
  public static ExecutorService              executor               = Executors.newFixedThreadPool(4);

  public static class WorldObserver {
    World w;
    double posX, posY, posZ;
  };

  public static ArrayList<WorldObserver> observers = new ArrayList<WorldObserver>();

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
    Fluids.postInit();
    Gases.postInit();
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
    /*
     * logger.log(Level.INFO, Util.logHeader() + "Scheduling " +
     * Util.xyzString(x, y, z) + " for " + (Counters.tick + delay) + " (+" +
     * delay + ")" + " " + explanation);
     */

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
    /* Update world tick and print statistics */
    Counters.tick++;
    if (Counters.tick % 300 == 0 && false)  {
      Counters.printStatistics();
    }

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
        s.block.updateTick(s.w, s.x, s.y, s.z, rand);
        s.set(null, null, 0, 0, 0);
        before = blockTickQueueFreePool.size();
        blockTickQueueFreePool.push(s2);
        after = blockTickQueueFreePool.size();
        Counters.liquidQueueCounter++;
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

  }

  /**
   * Performs the ticks that should happen for each world on the SERVER and the
   * CLIENT
   */
  public static void doWorldTick(World w) {

    AnimalAIRewriter.rewriteAnimalAIs(w);
    Gases.doWorldTick(w);

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
      }
    }

    int rainTime = w.getWorldInfo().getRainTime();
    if (rainTime > settings.weatherSpeed - 1) w.getWorldInfo().setRainTime(rainTime + 1 - settings.weatherSpeed);

    for (Object o : w.activeChunkSet) {
      ChunkCoordIntPair xz = (ChunkCoordIntPair) o;
      Chunk c = w.getChunkFromChunkCoords(xz.chunkXPos, xz.chunkZPos);
      if (!c.isChunkLoaded) {
        System.out.println("ERROR - ticking a chunk that is not loaded?");
      }
      int x = xz.getCenterXPos() & 0xfffffff0;
      int z = xz.getCenterZPosition() & 0xfffffff0;

      // Gases.doChunkTick(w, x, z);
      Rain.doPrecipation(w, x, z);
      Evaporation.doEvaporation(w, x, z);
      Trees.doTrees(w, x, z);
      Plants.doPlants(w, x, z);
      if (w.provider.dimensionId == -1) NetherFun.doNetherFun(w, x, z);

    }

    Fluids.doWorldTick(w);
    // System.out.println("Active chunks: "+w.activeChunkSet.size());

  }

  public static synchronized void setBlockWithMetadataAndPriority(World w, int x, int y, int z, int id, int meta, int pri) {
    IChunkProvider chunkProvider = w.getChunkProvider();
    if (!chunkProvider.chunkExists(x >> 4, z >> 4)) return;

    Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
    c.setBlockIDWithMetadata(x & 15, y, z & 15, id, meta);
    ChunkMarkUpdater.scheduleBlockMark(w, x, y, z);
  }

  public static void tickPlayer(Player player, World w) {}

}
