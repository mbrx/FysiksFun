package mbrx.ff.ecology;

import java.util.LinkedList;
import java.util.Random;

import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.BlockFFFluid;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkTempData;
import mbrx.ff.util.CoordinateWXZ;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import cpw.mods.fml.common.IWorldGenerator;

public class OceanRepairGenerator implements IWorldGenerator {

	/*
	 * We use two lists of chunks to be repaired. The first one will be repaired
	 * first (any time from 0-99 ticks from now), the second one after that
	 * (atleast 100-199 ticks from now)
	 */
	public static LinkedList<CoordinateWXZ>	chunkRepairList1	= new LinkedList<CoordinateWXZ>();
	public static LinkedList<CoordinateWXZ>	chunkRepairList2	= new LinkedList<CoordinateWXZ>();

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
			IChunkProvider chunkProvider) {
		chunkRepairList2.add(new CoordinateWXZ(world, chunkX, chunkZ));
		// FysiksFun.repair(world,chunkX,chunkZ);
		doRepair(world, chunkX, chunkZ);
	}

	public static void doTick() {
		/*
		 * Every 100 ticks we will repair chunks an extra time, in case they where
		 * unstable when created
		 */
		/*if ((Counters.tick % 100) != 0)
			return;
		for (CoordinateWXZ coord : chunkRepairList1) {
			doRepair(coord.getWorld(), coord.getX(), coord.getZ());
		}
		chunkRepairList1.clear();
		LinkedList<CoordinateWXZ> tmp = chunkRepairList1;
		chunkRepairList1 = chunkRepairList2;
		chunkRepairList2 = tmp;
		*/
	}

	public static void doRepair(World world, int chunkX, int chunkZ) {
		int x, y, z;
		int repairCnt = 0;
	    Chunk c = ChunkCache.getChunk(world, chunkX, chunkZ, false);
	    if(c == null) return;

		for (x = 0; x < 16; x++)
			for (z = 0; z < 16; z++) {
				int waterCnt = 0;
				if (!BlockFFFluid.isOceanic(world, chunkX * 16 + x, chunkZ * 16 + z))
					continue;
				for (y = 64; y > 55; y--) {
					int idHere = c.getBlockID(x, y, z);
					if (idHere == Fluids.flowingWater.blockID || idHere == Fluids.stillWater.blockID)
						waterCnt++;
					if(waterCnt >= 4) break;
				}
				if(waterCnt >= 4) {
					/* Repair */
				    int stonesPlaced=0;
					for (; y > 1 && stonesPlaced<10; y--) { 
						int idHere = c.getBlockID(x, y, z);
						
						if(idHere == 0) {
						  stonesPlaced++;
						  FysiksFun.setBlockIDandMetadata(world, c, x+chunkX*16, y, z+chunkZ*16, Block.stone.blockID, 0, -1, -1, null);
							//Fluids.stillWater.setBlockContent(world, c, ChunkTempData.getChunk(world,x,y,z), x, y, z, BlockFFFluid.maximumContent,"[repair ocean]", null);
							repairCnt++;
						}
					}
				}
			}
	//if (repairCnt > 0)
	//	System.out.println("Repaired " + repairCnt + " blocks under water");
	}

}

/*
 * for(y=1;y<90;y++) { for(x=0;x<16;x++) for(z=0;z<16;z++) { int idHere =
 * c.getBlockID(x,y,z); if((idHere == Fluids.stillWater.blockID || idHere ==
 * Fluids.flowingWater.blockID) && c.getBlockID(x, y-1, z) == 0) { cnt++;
 * c.setBlockIDWithMetadata(x, y-1, z, Block.obsidian.blockID, 0); } if(idHere
 * == Fluids.stillWater.blockID && c.getBlockID(x, y-2, z) == 0) { cnt++;
 * c.setBlockIDWithMetadata(x, y-1, z, Block.obsidian.blockID, 0); } } }
 */
