package mbrx.ff;

import java.util.HashSet;
import java.util.Map;

import mbrx.ff.ecology.Evaporation;
import mbrx.ff.ecology.NetherFun;
import mbrx.ff.ecology.Plants;
import mbrx.ff.ecology.Rain;
import mbrx.ff.ecology.Trees;
import mbrx.ff.ecology.Volcanoes;
import mbrx.ff.util.ChunkMarkUpdateTask;
import mbrx.ff.util.Settings;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

class WorkerUpdateChunks implements Runnable {
  World                                      world;
  Chunk                                      chunk;
  Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets;
  ChunkCoordIntPair                          xz;

  public WorkerUpdateChunks(World w, Chunk c, ChunkCoordIntPair xz, Map<Integer, HashSet<ChunkMarkUpdateTask>> delayedBlockMarkSets) {
    this.world = w;
    this.chunk = c;
    this.xz = xz;
    this.delayedBlockMarkSets = delayedBlockMarkSets;
  }

  @Override
  public void run() {
    try {

      int x = xz.chunkXPos << 4;
      int z = xz.chunkZPos << 4;
      Settings settings = FysiksFun.settings;

      
      if (settings.doVolcanoes) Volcanoes.doChunkTick(world, xz);
      if (settings.doRain) Rain.doPrecipation(world, x, z);
      if (settings.doEvaporation) Evaporation.doEvaporation(world, x, z);
      if (settings.doTreeFalling) Trees.doTrees(world, x, z);
      if (settings.doDynamicPlants) Plants.doPlants(world, x, z);
      ExtraBlockBehaviours.doChunkTick(world, xz);
      if (world.provider.dimensionId == -1 && settings.doNetherfun) NetherFun.doNetherFun(world, x, z);   
      
    } catch (Exception e) {
      System.out.println("WorkerUpdateChunks got an exception" + e);
      e.printStackTrace();
    }
  }
}