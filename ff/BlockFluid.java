package mbrx.ff;

import java.security.Provider;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowing;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.util.Icon;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/*
 * Liquids can move in four ways:
 * 
 * (0) Liquid can fall into an empty cell just below it 
 * (1) Moving a single cell due a significant (> 1) difference in content between neighbours 
 * (2) Moving a single cell due to a slight (= 1) different in content but over
 * multiple cells path 
 * (3) Move directly into an empty or non-blocking cell neighbouring us
 * 
 *  Case 0,1,3 can be initiated only by the pushing cell since we know it will
 * have the lower cell trigger an update (direct neighbours) 
 * Case 2 above can be subdivided into two cases: 
 *   (2a) A push initiated by the cell with the higher content 
 *   (2b) A pull initiated by the cell with a lower content
 * Furthermore, for case 2 we will *so far* only consider paths that are in
 * a straight line of limited length
 */

/*
 * Erosion
 * 
 * A dirt/sand block can erode under the following circumstances:
 * 
 * A liquid is moving in the XZ plane in a dX, dZ direction
 *   - First check if either the block +dZ, +dX (sic!) can erode
 *   - If not, check if the block -dZ, -dX (sic!) can erode
 *   - Finally, check if the block below it Y-1 can erode
 *   
 *   For a block to be able to erode it must be able to move to some other 
 *   place where it would fit in at a LOWER y-level
 *     - Make a random walk from the block to be checked
 *     - Follow only water, up to 8 steps (redoing steps that would not be water)
 *     - If at any point during the walk a water cell is found with a free water cell BELOW it
 *       then perform the erosion
 * 
 */

public class BlockFluid extends BlockFlowing {
	public int									stillID, movingID;

	public static boolean				preventSetBlockLiquidFlowover	= false;
	public static boolean				preventExtraNotifications			= false;
	public Block								superWrapper;
	public boolean							canSeepThrough;
	public boolean							canCauseErosion;

	public int									liquidUpdateRate;
	public int									pressurizedLiquidUpdateRate;

	/**
	 * Liquids content/pressure are modelled in TWO places. In the metaData we
	 * store the values 0 - 8 (or 0-16) as a representation of the content. In the
	 * tempData we store the values 0 - maxPressure. The range 0 ...
	 * fullLiquidPressure (eg. 0 ... 4096) is used to represent the actual content
	 * value in a cell. The range fullLiquidPressure ... maxPressure represents a
	 * cell that has full content _and_ that is under pressure. The maximum
	 * pressure and the value 256 per Y gives maximum pressure under 240 blocks of
	 * liquid. This would correspond to 24 bar.
	 * 
	 * 
	 * 
	 */

	public final static int			pressureMaximum								= 65535;
	public final static int			pressureFullLiquid						= 4096;
	public final static int			minimumLiquidLevel						= pressureFullLiquid / 8;
	public final static int			maximumContent								= pressureFullLiquid;
	public final static int			pressureLossPerStep						= 1;
	public final static int			pressurePerY									= 256;
	public final static int			pressurePerContent						= pressurePerY / 8;

	public final static boolean	logExcessively								= false;

	public String								name;

	// Needs to scale with pressurePerY so total time for equilibriums do not
	// increase
	public final int						pressureLossPerUpdate					= pressurePerY / 16;
	// Rationalle: we need to have surplus of atleast one 'pressurePerContent' to
	// be pushed "up" from NN, plus we loose two pressureLossPerStep on the way
	public final int						pressureDiffToMoveSideways		= pressurePerContent + 2 * pressureLossPerStep;

	public BlockFluid(Block superWrapper, int id, Material par2Material, int stillID, int movingID, String n) {
		super(id, par2Material);
		// this.blockIndexInTexture = superWrapper.blockIndexInTexture;
		// this.setTextureFile(superWrapper.getTextureFile());

		name = n;
		this.superWrapper = superWrapper;
		liquidUpdateRate = 5; // Default tickrate for water
		pressurizedLiquidUpdateRate = 1; // Math.max(2, (liquidUpdateRate + 1) / 2);
		this.stillID = stillID;
		this.movingID = movingID;
		this.canSeepThrough = false;
		this.canCauseErosion = false;
		this.setTickRandomly(false);
	}

	public BlockFluid(int id, Material par2Material, int stillID, int movingID, String n) {
		super(id, par2Material);
		this.superWrapper = null;
		liquidUpdateRate = 5; // Default tickrate for water
		pressurizedLiquidUpdateRate = Math.max(2, (liquidUpdateRate + 1) / 2);
		this.stillID = stillID;
		this.movingID = movingID;
		this.canSeepThrough = false;
		this.canCauseErosion = false;
		this.name = n;
	}

	@SideOnly(Side.CLIENT)
	/**
	 * When this method is called, your block should register all the icons it needs with the given IconRegister. This
	 * is the only chance you get to register icons.
	 */
	public void registerIcons(IconRegister par1IconRegister) {
		if (superWrapper != null) {
			superWrapper.registerIcons(par1IconRegister);
		} else {
			super.registerIcons(par1IconRegister);
		}

	}

	@Override
	public Icon getBlockTexture(IBlockAccess par1IBlockAccess, int par2, int par3, int par4, int par5) {
		if (superWrapper != null)
			return superWrapper.getBlockTexture(par1IBlockAccess, par2, par3, par4, par5);
		else
			return super.getBlockTexture(par1IBlockAccess, par2, par3, par4, par5);
	}

	@Override
	public Icon getIcon(int par1, int par2) {
		if (superWrapper != null)
			return superWrapper.getIcon(par1, par2);
		else
			return super.getIcon(par1, par2);
	}

	public void setLiquidUpdateRate(int rate) {
		if (rate < 1)
			FysiksFun.logger.log(Level.SEVERE, "Error, setting liquid " + name + " to invalid update rate " + rate);
		liquidUpdateRate = rate;
		pressurizedLiquidUpdateRate = (liquidUpdateRate + 1) / 2;
	}

	public boolean canOverflowBlock(World w, int x, int y, int z) {
		return canOverflowBlock(w.getBlockId(x, y, z));
	}

	public boolean canOverflowBlock(int id) {
		Block b = Block.blocksList[id];
		if (b == null)
			return false;
		Material m = b.blockMaterial;
		return !m.blocksMovement() && !m.isLiquid();
	}

	public int getBlockContent(World w, int x, int y, int z) {
		IChunkProvider chunkProvider = w.getChunkProvider();
		return getBlockContent(chunkProvider.provideChunk(x >> 4, z >> 4), ChunkTempData.getChunk(w, x, y, z), x, y, z);
	}

	public int getBlockContent(World w, Chunk c, int x, int y, int z) {
		return getBlockContent(c, ChunkTempData.getChunk(w, x, y, z), x, y, z);
	}

	public int getBlockContent(Chunk chunk, ChunkTempData tempData, int x, int y, int z) {
		int temp = tempData.getTempData(x, y, z);
		// System.out.println("getBlockContent @" + Util.xyzString(x, y, z) +
		// " temp=" + temp);

		if (temp == 0) {
			// Reconstruct content from lores data
			int meta = chunk.getBlockMetadata(x & 15, y, z & 15);
			int content = (8 - meta) * (maximumContent / 8);
			tempData.setTempData(x, y, z, content);
			return content;
		} else
			return temp;
	}

	public void setBlockContent(World w, int x, int y, int z, int content) {
		IChunkProvider chunkProvider = w.getChunkProvider();
		Chunk chunk = chunkProvider.provideChunk(x >> 4, z >> 4);
		ChunkTempData tempData = ChunkTempData.getChunk(w, x, y, z);
		setBlockContent(w, chunk, tempData, x, y, z, content, "");
	}

	/**
	 * Sets the content level of the block to the given amount, and keeps pressure
	 * neutral
	 */

	public void setBlockContent(World w, Chunk c, ChunkTempData tempData, int x, int y, int z, int content,
			String explanation) {
		int oldId = c.getBlockID(x & 15, y, z & 15);
		int newId = (content == 0 ? 0 : (content < maximumContent ? movingID : stillID));
		int oldContent = (isSameLiquid(oldId) ? getBlockContent(w, c, x, y, z) : 0);

		if (content < 0) {
			FysiksFun.logger.log(Level.SEVERE, "Negative content (" + content + ") at " + Util.xyzString(x, y, z));
			content = 0;
		}
		if (content > pressureMaximum)
			content = pressureMaximum;

		int oldMetaData = oldContent < maximumContent ? 8 - oldContent / (maximumContent / 8) : 0;
		int newMetaData = content < maximumContent ? 8 - content / (maximumContent / 8) : 0;
		if (oldMetaData > 7)
			oldMetaData = 7;
		if (newMetaData > 7)
			newMetaData = 7;

		if (logExcessively) {
			String message = "Setting " + Util.xyzString(x, y, z) + " to " + content;
			FysiksFun.logger.log(Level.INFO, Util.logHeader() + message + " " + explanation);
		}

		if (newMetaData != oldMetaData || newId != oldId)
			c.setBlockIDWithMetadata(x & 15, y, z & 15, newId, newMetaData);
		tempData.setTempData(x, y, z, content);

		int foo = tempData.getTempData(x, y, z);
		if (foo != content) {
			FysiksFun.logger.log(Level.SEVERE, "Something is still fucked up with java and bytes");
		}

		if (oldId != newId || oldMetaData != newMetaData)
			ChunkMarkUpdater.scheduleBlockMark(w, x, y, z);
	}

	/**
	 * Assumption - we cannot trust that the block instance we are called as is
	 * the actual block that is present at the given coordinates (MC Vanilla
	 * guarantees it, but our custom scheduling system does not)
	 */
	@Override
	public void updateTick(World w, int x, int y, int z, Random r) {
		IChunkProvider chunkProvider = w.getChunkProvider();
		if (!chunkProvider.chunkExists(x >> 4, z >> 4))
			return;
		updateTickSafe(w, chunkProvider.provideChunk(x >> 4, z >> 4), x, y, z, r);
	}

	/** Called only when we KNOW that the original chunk is loaded */
	public void updateTickSafe(World world, Chunk chunk0, int x0, int y0, int z0, Random r) {

		int oldIndent = Util.loggingIndentation;

		int chunkX0 = x0 >> 4, chunkZ0 = z0 >> 4;
		IChunkProvider chunkProvider = world.getChunkProvider();

		int id0 = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
		/* Since we was asked to update, but now are empty. It might be that one of our neighbours needs to be updated instead. */
		if (!isSameLiquid(id0)) {
			for(int dir=0;dir<6;dir++) {
				int dX = Util.dirToDx(dir);
				int dY = Util.dirToDy(dir);
				int dZ = Util.dirToDz(dir);
				int x1 = x0 + dX;
				int y1 = y0 + dY;
				int z1 = z0 + dZ;
				if (y1 < 0 || y1 > 255)
					continue;

				Chunk chunk1;
				if (x1 >> 4 == chunkX0 && z1 >> 4 == chunkZ0)
					chunk1 = chunk0;
				else if (chunkProvider.chunkExists(x1 >> 4, z1 >> 4))
					chunk1 = chunkProvider.provideChunk(x1 >> 4, z1 >> 4);
				else
					continue;

				int id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
				if(id1 != 0 && isSameLiquid(id1)) updateTickSafe(world, chunk1, x1, y1, z1, r);				
			}
		
			return;
		}

		try {
			preventSetBlockLiquidFlowover = true;
			preventExtraNotifications = true;
			boolean delayAbove = false;

			// Variable naming conventions
			// abc0 : is the node currently under consideration
			// abc1 : is a direct neighbour node
			// abc2 : is a node two steps away
			// abcN : is a node multiple steps away, where N is decided by a loop
			// variable
			// abc11 : is a direct neighbour of abc1
			// abc12 : is a node two steps away from abc1
			// Invariant: abc10 is the same as abc1

			int content0 = getBlockContent(world, chunk0, x0, y0, z0);
			int oldContent0 = content0;

			if (logExcessively)
				FysiksFun.logger.log(Level.INFO, Util.logHeader() + "Updating " + Util.xyzString(x0, y0, z0) + " content0: "
						+ content0);
			Util.loggingIndentation++;

			ChunkTempData tempData0 = ChunkTempData.getChunk(world, x0, y0, z0);

			/* Recompute our own pressure before moving blocks */
			if (content0 >= maximumContent) {
				content0 = maximumContent;
				for (int dir = 0; dir < 6; dir++) {
					int dX = Util.dirToDx(dir);
					int dY = Util.dirToDy(dir);
					int dZ = Util.dirToDz(dir);
					int x1 = x0 + dX;
					int y1 = y0 + dY;
					int z1 = z0 + dZ;

					Chunk chunk1;
					ChunkTempData tempData1;
					if (x1 >> 4 == chunkX0 && z1 >> 4 == chunkZ0)
						chunk1 = chunk0;
					else if (chunkProvider.chunkExists(x1 >> 4, z1 >> 4))
						chunk1 = chunkProvider.provideChunk(x1 >> 4, z1 >> 4);
					else
						continue;
					// Check for correct fluid type
					int id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
					if (!isSameLiquid(id1))
						continue;
					// Get new tempData
					if ((x1 >> 4) == (x0 >> 4) && (y1 >> 4) == (y0 >> 4) && (z1 >> 4) == (z0 >> 4))
						tempData1 = tempData0;
					else
						tempData1 = ChunkTempData.getChunk(world, x1, y1, z1);

					int content1 = getBlockContent(chunk1, tempData1, x1, y1, z1);
					if (content1 < maximumContent)
						continue;
					int pressure1 = content1 + dY * pressurePerY - pressureLossPerStep;
					if (pressure1 > content0)
						content0 = pressure1;
				}
			}

			/* Move liquids and propagate any pressures if applicable */
			/*
			 * Iterate over all neighbours and check if there is a significant enough
			 * pressure difference to move fluids. If so, perform the moves and
			 * schedule new ticks.
			 */
			int dirOffset = FysiksFun.rand.nextInt(4);
			for (int dir0 = 5; dir0 < 5 + 6; dir0++) {
				// Compute a direction of interest such that we
				// (a) First consider the down direction
				// (b) Next consider the horizontal directions in a SEMI-random order
				// (c) Last consider the "up" direction
				int dir1 = dir0 % 6;
				int dir = dir1 < 4 ? (dir1 ^ dirOffset) & 3 : dir1;

				int dX = Util.dirToDx(dir);
				int dY = Util.dirToDy(dir);
				int dZ = Util.dirToDz(dir);
				int x1 = x0 + dX;
				int y1 = y0 + dY;
				int z1 = z0 + dZ;
				if (y1 < 0 || y1 > 255)
					continue;

				Chunk chunk1;
				if (x1 >> 4 == chunkX0 && z1 >> 4 == chunkZ0)
					chunk1 = chunk0;
				else if (chunkProvider.chunkExists(x1 >> 4, z1 >> 4))
					chunk1 = chunkProvider.provideChunk(x1 >> 4, z1 >> 4);
				else
					continue;

				int id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
				int content1 = 0;
				if (id1 != 0 && !isSameLiquid(id1))
					continue;

				// Get new tempData
				ChunkTempData tempData1;
				if ((x1 >> 4) == (x0 >> 4) && (y1 >> 4) == (y0 >> 4) && (z1 >> 4) == (z0 >> 4))
					tempData1 = tempData0;
				else
					tempData1 = ChunkTempData.getChunk(world, x1, y1, z1);

				if (id1 != 0) {
					content1 = getBlockContent(chunk1, tempData1, x1, y1, z1);
				}

				if (logExcessively)
					FysiksFun.logger.log(Level.INFO, Util.logHeader() + "considering " + Util.xyzString(x1, y1, z1) + " dir: "
							+ dir + " id1: " + id1 + " content1: " + content1);

				int prevContent0 = content0;
				if (dY < 0) {
					if (content1 < maximumContent) {
						// Move liquid downwards
						content0 = Math.max(0, Math.min(maximumContent, content0) + content1 - maximumContent);
						content1 = Math.min(maximumContent, content1 + prevContent0);
						setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Fall down]");
						FysiksFun.scheduleBlockTick(world, this, x1, y1, z1, liquidUpdateRate, "[Fall down]");
					} else if (content1 < content0 + pressurePerY - pressureLossPerStep) {
						content1 = content0 + pressurePerY - pressureLossPerStep;
						setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Pressure down]");
						FysiksFun.scheduleBlockTick(world, this, x1, y1, z1, liquidUpdateRate, "[Fall down]");
					}
				} else if (dY > 0 && content0 >= maximumContent + pressurePerY + pressureLossPerStep) {
					if (content1 < maximumContent) {
						content0 = content1;
						content1 = Math.min(maximumContent, prevContent0);
						setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Flowing up]");
						FysiksFun.removeBlockTick(world, this, x1, y1, z1, liquidUpdateRate);
						FysiksFun.scheduleBlockTick(world, this, x1, y1, z1, liquidUpdateRate, "[Flowing up]");
						delayAbove = true;
					} else if (content0 - pressurePerY - pressureLossPerStep > content1) {
						content1 = content0 - pressurePerY - pressureLossPerStep;
						setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Pressure up]");
						FysiksFun.scheduleBlockTick(world, this, x1, y1, z1, liquidUpdateRate, "[Pressure up]");
						delayAbove = true; // not needed?
					}
				} else if (dY == 0 && content0 > content1) {
					if (content1 < maximumContent) {
						int toMove = Math.min((content0 - content1) / 2, maximumContent - content1);
						if (content1 + toMove > minimumLiquidLevel) {
							content0 -= toMove;
							content1 += toMove;
							setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Flowing sideways]");
							FysiksFun.scheduleBlockTick(world, this, x1, y1, z1, liquidUpdateRate, "[Flowing sideways]");
						}
					} else if (content0 - pressureLossPerStep > content1) {
						content1 = content0 - pressureLossPerStep;
						setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Pressure sideways]");
						FysiksFun.scheduleBlockTick(world, this, x1, y1, z1, pressurizedLiquidUpdateRate, "[Pressure sideways]");
					}
				}
			}

			/*
			 * If we have made a pressurized move, make a random walk towards lower
			 * pressures until we find a node we can steal liquid from
			 */
			if (oldContent0 >= maximumContent && content0 < maximumContent) {
				int steps;
				int xN = x0, yN = y0, zN = z0;
				int currPressure = maximumContent;
				Chunk chunkN = chunk0;
				ChunkTempData tempDataN = tempData0;

				for (steps = 0; steps < 256; steps++) {
					int bestDir=-1;
					int bestPressure = 0;
					for (int dir = 0; dir < 6; dir++) {
						int dX = Util.dirToDx(dir);
						int dY = Util.dirToDy(dir);
						int dZ = Util.dirToDz(dir);
						int xM = xN + dX;
						int yM = yN + dY;
						int zM = zN + dZ;
						if (yM < 0 || yM > 255)
							continue;

						Chunk chunkM;
						ChunkTempData tempDataM;
						if (xM >> 4 == xN >> 4 && zM >> 4 == zN >> 4)
							chunkM = chunkN;
						else {
							if (!chunkProvider.chunkExists(xM >> 4, zM >> 4))
								continue;
							chunkM = chunkProvider.provideChunk(xM >> 4, zM >> 4);
						}
						int idM = chunkM.getBlockID(xM & 15, yM, zM & 15);
						if (!isSameLiquid(idM))
							continue;

						if (xM >> 4 == xN >> 4 && zM >> 4 == zN >> 4)
							tempDataM = tempDataN;
						else
							tempDataM = ChunkTempData.getChunk(world, xM, yM, zM);
						int contentM = getBlockContent(chunkM, tempDataM, xM, yM, zM);
						int modifiedPressure = contentM + pressurePerY * dY;
						if (modifiedPressure > bestPressure) {
							bestDir = dir;
							bestPressure = modifiedPressure;
						}
					}
					if (bestPressure < currPressure)
						break;
					xN += Util.dirToDx(bestDir);
					yN += Util.dirToDy(bestDir);
					zN += Util.dirToDz(bestDir);
				}
				/* Steal as much as possible from N */
				int contentN = getBlockContent(chunkN, tempDataN, xN, yN, zN);
				int toMove = Math.min(contentN, maximumContent - content0);
				contentN -= toMove;
				content0 += toMove;
				setBlockContent(world, chunkN, tempDataN, xN, yN, zN, contentN, "[Propagated pressurized liquid]");
				if(contentN > 0)
					FysiksFun.scheduleBlockTick(world, this, xN, yN, zN, liquidUpdateRate, "[Propagated pressurized liquid]");				
				for(int dir=0;dir<6;dir++)
					FysiksFun.scheduleBlockTick(world, this, xN+Util.dirToDx(dir), yN+Util.dirToDy(dir), zN+Util.dirToDz(dir), liquidUpdateRate, "[Neighbour of pressurized liquid]");
							 
				if(content0 == maximumContent) 
					content0 = oldContent0;   
			}

			/* Write our updated content to the world if it has changed */
			if (content0 != oldContent0) {
				if (logExcessively)
					FysiksFun.logger.log(Level.INFO, Util.logHeader() + "self update, oldContent0: " + oldContent0);
				setBlockContent(world, chunk0, tempData0, x0, y0, z0, content0, "[Final self update]");
				FysiksFun.scheduleBlockTick(world, this, x0, y0, z0, liquidUpdateRate, "[Final self update]");
			}

			/* Schedule updates to neighbours if our content has changed */
			if (content0 != oldContent0) {
				for (int dir = 0; dir < 6; dir++) {
					int dX = Util.dirToDx(dir);
					int dY = Util.dirToDy(dir);
					int dZ = Util.dirToDz(dir);
					int x1 = x0 + dX;
					int y1 = y0 + dY;
					int z1 = z0 + dZ;

					Chunk chunk1;
					if (x1 >> 4 == chunkX0 && z1 >> 4 == chunkZ0)
						chunk1 = chunk0;
					else if (chunkProvider.chunkExists(x1 >> 4, z1 >> 4))
						chunk1 = chunkProvider.provideChunk(x1 >> 4, z1 >> 4);
					else
						continue;
					int id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
					if (!isSameLiquid(id1))
						continue;

					// Get new tempData
					ChunkTempData tempData1;
					if (x1 >> 4 == x0 >> 4 && y1 >> 4 == y0 >> 4 && z1 >> 4 == z0 >> 4)
						tempData1 = tempData0;
					else
						tempData1 = ChunkTempData.getChunk(world, x1, y1, z1);
					int content1 = getBlockContent(chunk1, tempData1, x1, y1, z1);
					if (content1 > content0 + dY * pressurePerY + pressureLossPerStep)
						if (delayAbove && dY == +1) {
						} else {
							FysiksFun.scheduleBlockTick(world, this, x1, y1, z1, content1 < maximumContent ? liquidUpdateRate
									: pressurizedLiquidUpdateRate, "[Pressure updates] DA:" + delayAbove);
						}
				}
			}

		} finally {
			preventSetBlockLiquidFlowover = false;
			preventExtraNotifications = false;
			Util.loggingIndentation = oldIndent;
			if (logExcessively)
				FysiksFun.logger.log(Level.INFO, Util.logHeader() + "Finished " + Util.xyzString(x0, y0, z0));

		}
	}

	/*
	 * // If we are in an ocean biome and have a pillar of water atleast DY high
	 * // above us, and are below Y=60 - // then we are probably at the bottom of
	 * the ocean and treats this as a // special case of infinite water boolean
	 * isInfinite = false; if (FysiksFun.settings.infiniteOceans) { if (y < 62 &&
	 * newLiquidContent != oldLiquidContent && (this == Fluids.stillWater || this
	 * == Fluids.flowingWater)) { if (isOceanic(w, x, y)) { isInfinite = true; for
	 * (int dy = 1; dy <= 2; dy++) { int idYY = origChunk.getBlockID(x & 15, y +
	 * dy, z & 15); if (idYY != movingID && idYY != stillID) { isInfinite = false;
	 * break; } } } } }
	 */
	/* Let water flow out of the world if we are at the bottom most layer */
	/*
	 * if (y == 0 && !isInfinite) { setBlockContent(w, origChunk, x, y, z, 0); //
	 * FysiksFun.scheduleBlockTick(w, this, x, y, z, 1); notifyFeeders(w,
	 * origChunk, x, y, z, 0, liquidUpdateRate, pressurizedLiquidUpdateRate);
	 * return; }
	 */

	static public boolean isOceanic(World w, int x, int y) {
		BiomeGenBase g = w.getBiomeGenForCoords(x, y);
		if (g == BiomeGenBase.ocean || g == BiomeGenBase.frozenOcean || g == BiomeGenBase.river)
			return true;
		else
			return false;
	}

	/*
	 * Check if neighbour is a liquid, and we have a possible interaction with him
	 */
	/*
	 * if (FysiksFun.liquidsCanInteract(this.blockID, blockIdNN)) {
	 * newLiquidContent = FysiksFun.liquidInteract(w, x2, y, z2, this.blockID,
	 * newLiquidContent, blockIdNN, getBlockContent(chunk2, x2, y, z2)); }
	 */

	/* Full blocks of water with nowhere else to go, MAY leak through dirt */
	// TODO - increase chance or effect exponentially depending on pressure?
	/*
	 * if (canSeepThrough && FysiksFun.settings.erosionRate > 0 &&
	 * newLiquidContent >= 2 && (blockIdNN == Block.dirt.blockID || blockIdNN ==
	 * Block.sand.blockID)) { boolean doSeep = false; int range2;
	 * 
	 * chunk2 = origChunk; int chunk2x = x >> 4; int chunk2z = z >> 4; int x3 = x,
	 * z3 = z; for (range2 = 2; range2 < 16; range2++) { x3 = x + range2 * dx; z3
	 * = z + range2 * dz; if (x3 >> 4 != chunk2x || z3 >> 4 != chunk2z) { if
	 * (!chunkProvider.chunkExists(x3 >> 4, z3 >> 4)) continue; else { chunk2 =
	 * chunkProvider.provideChunk(x3 >> 4, z3 >> 4); chunk2x = x3 >> 4; chunk2z =
	 * z3 >> 4; } } int blockIdThrough = chunk2.getBlockID(x3 & 15, y, z3 & 15);
	 * 
	 * if (blockIdThrough == 0) { doSeep = true; break; } else if (blockIdThrough
	 * != Block.dirt.blockID && blockIdThrough != Block.sand.blockID) break; } if
	 * (doSeep) { newLiquidContent -= 1; setBlockContent(w, chunk2, x3 & 15, y, z3
	 * & 15, 1, "[Seeping liquids]"); FysiksFun.scheduleBlockTick(w, this, x3, y,
	 * z3, liquidUpdateRate, "[Seeping liquids]"); if (FysiksFun.rand.nextInt(1 +
	 * 200 / FysiksFun.settings.erosionRate) == 0) {
	 * FysiksFun.setBlockWithMetadataAndPriority(w, x2, y, z2, 0, 0, 0); } } }
	 */
	// Let liquids flow through open doors
	// Note: this may cause us to leak into an unloaded chunk, but since the
	// only effect is a slight efficiency cost
	// I consider this to be too rare to fix
	/*
	 * boolean isOpenDoorNN = canFlowThrough(blockIdNN, blockMetaNN); if
	 * (newLiquidContent >= 2 && isOpenDoorNN) { // The door is open, see if we
	 * can flow through it int x3 = x + dx * 2, z3 = z + dz * 2; Chunk chunk3 =
	 * origChunk; if (x3 >> 4 != x >> chunkX || z3 >> 4 != chunkZ) { if
	 * (chunkProvider.chunkExists(x3 >> 4, z3 >> 4)) chunk3 =
	 * chunkProvider.provideChunk(x3 >> 4, z3 >> 4); else chunk3 = null; } if
	 * (chunk3 != null) { int id3 = chunk3.getBlockID(x3 & 15, y, z3 & 15); if
	 * (id3 == 0 || id3 == movingID || id3 == stillID) { int contentThrough = 0;
	 * if (id3 == movingID || id3 == stillID) contentThrough =
	 * getBlockContent(chunk3, x3, y, z3); int toMove = (newLiquidContent -
	 * contentThrough) / 2; if (toMove > 0) { newLiquidContent -= toMove;
	 * setBlockContent(w, chunk3, x3, y, z3, contentThrough + toMove,
	 * "[Through open door]"); FysiksFun.scheduleBlockTick(w, this, x + dx * 2, y,
	 * z + dz * 2, liquidUpdateRate); } } } } else if (y > 1 && blockIdNN !=
	 * this.blockID && newLiquidContent > 2 && this.canOverflowBlock(blockIdNN) &&
	 * newLiquidContent >= 2) { // Again: this may cause loading of another chunk,
	 * but it should be rare // enough that we can ignore it...
	 * 
	 * // If the cell NN is an item that can be destroyed by the liquid, then //
	 * drop it. And in next step of THIS tick move into it Block b =
	 * Block.blocksList[blockIdNN]; if (b != null && b != Block.snow && b !=
	 * Block.grass) b.dropBlockAsItem(w, x2, y, z2, chunk2.getBlockMetadata(x2 &
	 * 15, y, z2 & 15), 0); blockIdNN = 0; }
	 * 
	 * if (blockIdNN == 0 || (newLiquidContent == 1 && isOpenDoorNN)) { // Case 3:
	 * move into empty neighbouring cell int toMove = newLiquidContent / 2;
	 * 
	 * if (newPressure > 0 && blockIdNN == 0) { toMove = 0; // We will make the
	 * full movement directly here, rather // than in the test below
	 * setBlockContent(w, chunk2, x2, y, z2, newLiquidContent,
	 * "[Is empty neighbour]"); newLiquidContent = 0; notifyFeeders(w, origChunk,
	 * x, y, z, 1, liquidUpdateRate - 1, pressurizedLiquidUpdateRate); // Remove
	 * any earlier ticks from the target, so we have time to refill // before he
	 * runs FysiksFun.removeBlockTick(w, this, x2, y, z2, liquidUpdateRate - 1);
	 * FysiksFun.scheduleBlockTick(w, this, x2, y, z2, liquidUpdateRate);
	 * newPressure = 0; }
	 */

	/*
	 * // Do erosion for the case when we move into an empty neighbour that //
	 * will be able to fall down on the next tick if (canCauseErosion &&
	 * chunk2.getBlockID(x2 & 15, y - 1, z2 & 15) == 0 &&
	 * FysiksFun.settings.erosionRate != 0 && (this == Fluids.stillWater || this
	 * == Fluids.flowingWater) && toMove > FysiksFun.settings.erosionThreshold) {
	 * boolean canErodeA = canErode(w, x + dz, y, z + dx); boolean canErodeB =
	 * canErode(w, x - dz, y, z - dx);
	 * 
	 * if (!canErodeA && !canErodeB) { if (FysiksFun.rand.nextInt(3000 /
	 * FysiksFun.settings.erosionRate) < toMove * 4 - 3) doErode(w, x, y - 1, z,
	 * x2, y - 1, z2); } else { // Count how "surrounded" the block that can erode
	 * is int cntA = 0, cntB = 0; for (int dx2 = -1; dx2 <= 1; dx2++) for (int dz2
	 * = -1; dz2 <= 1; dz2++) { if (isSameLiquid(w.getBlockId(x + dz + dx2, y, z +
	 * dx + dz2))) cntA++; if (isSameLiquid(w.getBlockId(x - dz + dx2, y, z - dx +
	 * dz2))) cntB++; } // Finally, attempt to erode the target block, boosting
	 * the // probability if it is very surrounded by the liquid if (canErodeA &&
	 * FysiksFun.rand.nextInt(3000 / FysiksFun.settings.erosionRate) < toMove *
	 * cntA - 3) maybeErode(w, x + dz, y, z + dx); if (canErodeB &&
	 * FysiksFun.rand.nextInt(3000 / FysiksFun.settings.erosionRate) < toMove *
	 * cntB - 3) maybeErode(w, x - dz, y, z - dx); } } } else {
	 * 
	 * // Treat cells with very little (1) water specially to make sure they //
	 * can eventually flow over an edge, if within reach int maxRange = 8; //
	 * FysiksFun.rand.nextInt(15) + 3; Chunk chunkCache = origChunk; int
	 * chunkCacheX = x >> 4, chunkCacheZ = z >> 4; toMove = 0;
	 * 
	 * for (int range = (isOpenDoorNN ? 2 : 1); range < maxRange && toMove == 0;
	 * range++) for (int side = 0; side < 3 && toMove == 0; side++) { int x3 =
	 * range * dx + (side == 0 ? 0 : (side == 1 ? -1 : 1)) * dz * range + x; int
	 * z3 = range * dz + (side == 0 ? 0 : (side == 1 ? -1 : 1)) * dx * range + z;
	 * 
	 * if (x3 >> 4 != chunkCacheX || z3 >> 4 != chunkCacheZ) { if
	 * (!chunkProvider.chunkExists(x3 >> 4, z3 >> 4)) break; chunkCache =
	 * chunkProvider.provideChunk(x3 >> 4, z3 >> 4); chunkCacheX = x3 >> 4;
	 * chunkCacheZ = z3 >> 4; } int idMM = chunkCache.getBlockID(x3 & 15, y, z3 &
	 * 15); if (idMM != Block.tallGrass.blockID && idMM != 0) break;
	 * 
	 * int idBelowMM = chunkCache.getBlockID(x3 & 15, y - 1, z3 & 15); if
	 * (idBelowMM == 0 || idBelowMM == movingID) { toMove = 1; // Erosion that
	 * eats away from under it and carries the block // along way along these
	 * micro-flows. This is needed to start // rivers/lakes int x2x1 = x2 - x,
	 * z2z1 = z2 - z; if (canCauseErosion && FysiksFun.settings.erosionThreshold
	 * == 0 && FysiksFun.settings.erosionRate != 0 && FysiksFun.rand.nextInt(1000
	 * / FysiksFun.settings.erosionRate) == 0) { boolean canErodeLeft =
	 * canErode(w, x + z2z1, y, z + x2x1); boolean canErodeRight = canErode(w, x -
	 * z2z1, y, z - x2x1); boolean canErodeBelow = canErode(w, x, y - 1, z); if
	 * (canErodeLeft || canErodeRight || canErodeBelow) { int cnt = -3 * range;
	 * for (int dx2 = -2; dx2 <= 2; dx2++) for (int dy2 = -1; dy2 <= 0; dy2++) for
	 * (int dz2 = -2; dz2 <= 2; dz2++) if (isSameLiquid(w.getBlockId(x + dx2, y +
	 * dy2, z + dz2))) cnt++; // TODO - use the chunkCache here if (!canErode(w,
	 * x3 - 1, y - 1, z3)) cnt += 3; if (!canErode(w, x3 + 1, y - 1, z3)) cnt +=
	 * 3; if (!canErode(w, x3, y - 1, z3 - 1)) cnt += 3; if (!canErode(w, x3, y -
	 * 1, z3 + 1)) cnt += 3; // Erosion of the block below can occur if there are
	 * // atleast X neighbours of us that also have liquid // since this means
	 * that we are in a large shallow pool // TODO: use a stochastic method
	 * depending on cnt // TODO: move the second to last block? // TODO: set
	 * counter higher if the TARGET block is // surrounded by other non-liquid
	 * blocks?? // If a block is surrounded by 4 water blocks (in the XZ // plane)
	 * then it should be carried away. (Hmm, also count // on Y plane?) if (cnt >=
	 * 3 && FysiksFun.rand.nextInt(100) < cnt) { int dy; for (dy = 1; dy < 256;
	 * dy++) { int idMM4 = chunkCache.getBlockID(x3 & 15, y - dy, z3 & 15); if
	 * (idMM4 != 0 && !isSameLiquid(idMM4)) break; // if (w.getBlockId(x3, y - dy,
	 * z3) != 0 && // !isSameLiquid(w.getBlockId(x3, y - dy, z3))) break; } if
	 * (canErodeLeft) doErode(w, x + z2z1, y, z + x2x1, x3, y - dy, z3); else if
	 * (canErodeRight) doErode(w, x - z2z1, y, z - x2x1, x3, y - dy, z3); else if
	 * (canErodeBelow) doErode(w, x, y - 1, z, x3, y - dy, z3); } } } // Extra
	 * notification to make sure that a single water level // will immediately
	 * fall down if (range == 1) FysiksFun.scheduleBlockTick(w, this, x2, y, z2,
	 * 1); break; } } }
	 */
	/*
	 * if (toMove > 0) { if (isOpenDoorNN && newLiquidContent == 1) { // Move one
	 * step further - so we can flow through open doors if
	 * (chunkProvider.chunkExists((x + dx * 2) >> 4, (z + dz * 2) >> 4)) {
	 * setBlockContent(w, x + dx * 2, y, z + dz * 2, toMove,
	 * "[Moving into empty neighbour through door]");
	 * FysiksFun.scheduleBlockTick(w, this, x + dx * 2, y, z + dz * 2,
	 * liquidUpdateRate / toMove + 1); newLiquidContent -= toMove; } } else { ///
	 * Move to NN if (chunkProvider.chunkExists(x2 >> 4, z2 >> 4)) {
	 * setBlockContent(w, chunk2, x2, y, z2, toMove,
	 * "[Moving into empty neighbour]"); FysiksFun.scheduleBlockTick(w, this, x2,
	 * y, z2, liquidUpdateRate / toMove + 1); // It should not be needed to notify
	 * anyone at x2,y,z2 since we // _grew_ in content //
	 * notifyFeeders(w,chunk2,x2,y,z2, 1, liquidUpdateRate, //
	 * pressurizedLiquidUpdateRate); // notifySameLiquidNeighbours(w, x2, y, z2,
	 * 1); newLiquidContent -= toMove; } } } }
	 */

	// Check for erosions
	/*
	 * if (canCauseErosion && FysiksFun.settings.erosionRate != 0 && (this ==
	 * Fluids.stillWater || this == Fluids.flowingWater) && toMove >
	 * FysiksFun.settings.erosionThreshold) { boolean canErodeA = canErode(w, x +
	 * dz, y, z + dx); boolean canErodeB = canErode(w, x - dz, y, z - dx);
	 * 
	 * if (!canErodeA && !canErodeB) { if (canErode(w, x, y - 1, z)) maybeErode(w,
	 * x, y - 1, z); } else { // Count how "surrounded" the block that can erode
	 * is int cntA = 0, cntB = 0; for (int dx2 = -1; dx2 <= 1; dx2++) for (int dz2
	 * = -1; dz2 <= 1; dz2++) { if (isSameLiquid(w.getBlockId(x + dz + dx2, y, z +
	 * dx + dz2))) cntA++; if (isSameLiquid(w.getBlockId(x - dz + dx2, y, z - dx +
	 * dz2))) cntB++; } // Finally, attempt to erode the target block, boosting
	 * the // probability if it is very surrounded by the liquid if (canErodeA &&
	 * FysiksFun.rand.nextInt(6000 / FysiksFun.settings.erosionRate) < toMove *
	 * cntA - 3) maybeErode(w, x + dz, y, z + dx); if (canErodeB &&
	 * FysiksFun.rand.nextInt(6000 / FysiksFun.settings.erosionRate) < toMove *
	 * cntB - 3) maybeErode(w, x - dz, y, z - dx); }
	 * 
	 * } } }
	 */

	private boolean canFlowThrough(int blockIdNN, int blockMetaNN) {

		if ((blockIdNN == Block.doorWood.blockID || blockIdNN == Block.doorIron.blockID) &&
				(blockMetaNN & 4) != 0)
			return true;
		else if (blockIdNN == Block.fence.blockID || blockIdNN == Block.fenceIron.blockID
				|| blockIdNN == Block.fenceGate.blockID)
			return true;

		return false;
	}

	// Move into air/gas cell below us
	/*
	 * int swappedId = origChunk.getBlockID(x & 15, y - dy, z & 15); int
	 * swappedMeta = swappedId == 0 ? 0 : origChunk.getBlockMetadata(x & 15, y -
	 * dy, z & 15); int swappedTemp = swappedId == 0 ? 0 :
	 * ChunkTempData.getTempData(w, x, y - dy, z); // Set the content of the block
	 * below, zero pressure, update client, // schedule tick
	 * System.out.println(Counters.tick + ": Falling " + newLiquidContent +
	 * " into " + Util.xyzString(x, y - dy, z)); // Inherit our old pressure into
	 * this new position - it will dissipate // slowly if no more water comes
	 * after this one setBlockContentAndPressure(w, origChunk, x, y - dy, z,
	 * newLiquidContent, newLiquidPressure, "[Falling into]");
	 * FysiksFun.scheduleBlockTick(w, this, x, y - dy, z, liquidUpdateRate,
	 * "[Falling into]"); int foo = getBlockPressure(w, x, y - dy, z);
	 * System.out.println("*foo*: " + foo);
	 * 
	 * if (dy != 1 || infiniteSource) FysiksFun.scheduleBlockTick(w, this, x, y -
	 * dy, z, liquidUpdateRate); if (!infiniteSource) { // Set block here as
	 * content of old block, schedule a GAS tick, notify // neighbouring liquids,
	 * update client origChunk.setBlockIDWithMetadata(x & 15, y, z & 15,
	 * swappedId, swappedMeta); ChunkTempData.setTempData(w, x, y, z,
	 * swappedTemp); if (isSameLiquid(swappedId)) {
	 * FysiksFun.logger.log(Level.SEVERE,
	 * "Swapping gases with something that is not a gas"); } notifyFeeders(w,
	 * origChunk, x, y, z, 0, liquidUpdateRate, pressurizedLiquidUpdateRate); if
	 * (Gases.isGas[swappedId]) { FysiksFun.scheduleBlockTick(w,
	 * Block.blocksList[swappedId], x, y, z, 1, "[Gas swapped with liquid]"); }
	 * ChunkMarkUpdater.scheduleBlockMark(w, x, y, z); }
	 */

	/* Leak through dirt cavities roof */
	/*
	 * if (canSeepThrough && FysiksFun.settings.erosionRate > 0 &&
	 * newLiquidContent >= 1 && (blockBelowId == Block.dirt.blockID ||
	 * blockBelowId == Block.sand.blockID || blockBelowId ==
	 * Block.gravel.blockID)) { for (dy = 2; dy < 5 && dy < y; dy++) { int
	 * blockId2 = origChunk.getBlockID(x & 15, y - dy, z & 15); if (blockId2 == 0)
	 * { newLiquidContent = newLiquidContent - 1; setBlockContent(w, origChunk, x,
	 * y - 2, z, 1, "[Through roof]"); FysiksFun.scheduleBlockTick(w, this, x, y -
	 * 2, z, liquidUpdateRate); if (FysiksFun.rand.nextInt(1 + 100 /
	 * FysiksFun.settings.erosionRate) == 0) {
	 * FysiksFun.setBlockWithMetadataAndPriority(w, x, y - 1, z, 0, 0, 0); // See
	 * where the new dirt block can fall int dy2; for (dy2 = 0; dy2 < 64; dy2++) {
	 * int tmpId = origChunk.getBlockID(x & 15, y - dy - dy2 - 1, z & 15); if
	 * (tmpId != 0 && tmpId != stillID && tmpId != movingID) break; }
	 * FysiksFun.setBlockWithMetadataAndPriority(w, x, y - dy - dy2, z,
	 * blockBelowId, 0, 0); Counters.erosionCounter++; } break; } else if
	 * (blockId2 != Block.dirt.blockID && blockId2 != Block.sand.blockID &&
	 * blockId2 != Block.gravel.blockID) break; } }
	 */
	/* Interact with liquid below us */
	/*
	 * if (FysiksFun.liquidsCanInteract(this.blockID, blockBelowId)) {
	 * newLiquidContent = FysiksFun.liquidInteract(w, x, y - 1, z, this.blockID,
	 * newLiquidContent, blockBelowId, getBlockContent(w, x, y - 1, z)); }
	 * 
	 * return newLiquidContent;
	 */

	/** True if the given blockId matches our liquid type (either still or moving) */
	private boolean isSameLiquid(int blockId) {
		return blockId == stillID || blockId == movingID;
	}

	private boolean canErode(World w, int x, int y, int z) {
		int idHere = w.getBlockId(x, y, z);
		if (idHere != Block.dirt.blockID && idHere != Block.grass.blockID && idHere != Block.sand.blockID
				&& idHere != Block.cobblestone.blockID && idHere != Block.gravel.blockID)
			return false;
		// Gravel is _slightly_ more efficient than dirt against erosion
		if (idHere == Block.gravel.blockID && FysiksFun.rand.nextInt(2) != 0)
			return false;
		// Cobblestone MAY erode, but not very likely
		if (idHere == Block.cobblestone.blockID && FysiksFun.rand.nextInt(10) != 0)
			return false;
		/*
		 * If this block is adjacent to a tree (in any direction) then refuse to
		 * erode it
		 */
		if (w.getBlockId(x, y + 1, z) == Block.wood.blockID)
			return false;
		for (int dir = 0; dir < 4; dir++) {
			int dx = Util.dirToDx(dir), dz = Util.dirToDz(dir);
			if (w.getBlockId(x + dx, y + 1, z + dz) == Block.wood.blockID
					&& w.getBlockId(x + dx, y + 2, z + dz) == Block.wood.blockID)
				return false;
		}

		return true;
	}

	private boolean maybeErode(World w, int x, int y, int z) {

		/*
		 * Perform a random walk and see if we can walk into any water cell that has
		 * free (liquid) below it
		 */
		int idHere = w.getBlockId(x, y, z);
		if (idHere != Block.grass.blockID && idHere != Block.sand.blockID)
			return false;
		for (int attempt = 0; attempt < 20; attempt++) {
			int x2 = x, z2 = z;
			for (int steps = 0; steps < 8; steps++) {
				int dirStart = FysiksFun.rand.nextInt(4);
				int dirOffset;
				int idMM, dX = 0, dZ = 0;
				for (dirOffset = 0; dirOffset < 4; dirOffset++) {
					dX = Util.dirToDx((dirStart + dirOffset) % 4);
					dZ = Util.dirToDz((dirStart + dirOffset) % 4);
					idMM = w.getBlockId(x2 + dX, y, z2 + dZ);
					if (idMM == stillID || idMM == movingID)
						break;
				}
				if (dirOffset == 4)
					break;
				x2 += dX;
				z2 += dZ;
				if (x2 == x && z2 == z)
					continue;
				int idBelowMM = w.getBlockId(x2, y - 1, z2);
				if (idBelowMM == stillID || idBelowMM == movingID || idBelowMM == 0) {
					/* We have found a place to deposit this block! */
					/* See if we can move it further downwards */
					int dY;
					for (dY = 1; dY < 256; dY++) {
						int id2 = w.getBlockId(x2, y - dY - 1, z2);
						if (id2 != stillID && id2 != movingID && id2 != 0)
							break;
					}
					doErode(w, x, y, z, x2, y - dY, z2);
					return true;
				}
			}
		}
		return false;
	}

	private void doErode(World w, int x, int y, int z, int x2, int y2, int z2) {
		boolean oldPreventSetBlockLiquidFlowover = preventSetBlockLiquidFlowover;
		int idHere = w.getBlockId(x, y, z);
		int metaHere = w.getBlockMetadata(x, y, z);
		preventSetBlockLiquidFlowover = true;
		for (; y2 > 0; y2--) {
			// Drop the block downwards on target location
			int id = w.getBlockId(x2, y2 - 1, z2);
			if (id != 0 && id != stillID && id != movingID)
				break;
		}
		if (y2 >= 0) {
			int targetId = w.getBlockId(x2, y2, z2);
			int targetMeta = w.getBlockMetadata(x2, y2, z2);
			FysiksFun.setBlockWithMetadataAndPriority(w, x2, y2, z2, idHere, metaHere, 0);
			FysiksFun.setBlockWithMetadataAndPriority(w, x, y, z, targetId, targetMeta, 0);
		} else
			// Special case to deal with erosions that go through the bottom of the
			// world, the block is lost then
			FysiksFun.setBlockWithMetadataAndPriority(w, x, y, z, 0, 0, 0);

		w.notifyBlocksOfNeighborChange(x, y, z, idHere);
		// setBlockContent(w,x,y,z,0);
		// w.setBlock(x2, y2, z2, idHere, metaHere, 0x01 + 0x02);
		preventSetBlockLiquidFlowover = oldPreventSetBlockLiquidFlowover;
		Counters.erosionCounter++;
	}

	@Override
	public int tickRate(World par1World) {
		return 30;
	}

	/**
	 * Lets the block know when one of its neighbor changes. Doesn't know which
	 * neighbor changed (coordinates passed are their own) Args: x, y, z, neighbor
	 * blockID
	 */
	public void onNeighborBlockChange(World w, int x, int y, int z, int someId) {
		// if oldId == this.blockID ||
		// if (w.isAirBlock(x + 1, y, z) || w.isAirBlock(x - 1, y, z) ||
		// w.isAirBlock(x, y, z - 1) || w.isAirBlock(x, y, z + 1) || w.isAirBlock(x,
		// y - 1, z))
		// w.scheduleBlockUpdate(x, y, z, this.blockID, liquidUpdateRate);
		if (!preventExtraNotifications)
			FysiksFun.scheduleBlockTick(w, this, x, y, z, liquidUpdateRate, "[onNeighbour changed]");
	}

	public void onBlockAdded(World w, int x, int y, int z) {
		// if(FysiksFun.inWorldTick)
		if (!preventExtraNotifications)
			FysiksFun.scheduleBlockTick(w, this, x, y, z, liquidUpdateRate, "[On block added]");
	}

	/** Called when this block is OVERWRITTEN by another world.setBlock */
	public void breakBlock(World w, int x, int y, int z, int oldId, int oldMetaData) {

		int x2, y2, z2;
		try {
			if (preventSetBlockLiquidFlowover)
				return;

			IChunkProvider chunkProvider = w.getChunkProvider();
			if (!chunkProvider.chunkExists(x >> 4, z >> 4))
				return;
			Chunk chunk = chunkProvider.provideChunk(x >> 4, z >> 4);

			int newIdHere = chunk.getBlockID(x & 15, y, z & 15);
			if (newIdHere == 0 || isSameLiquid(newIdHere))
				return;
			// Overwriting AIR into a block means that the block should be deleted...

			int thisContent = 8 - oldMetaData;
			for (int dy = 1; dy < 50 && y + dy < 255; dy++) {
				int id = w.getBlockId(x, y + dy, z);
				if (id == stillID)
					continue;
				if (id == 0 || id == movingID) {
					int newContent = getBlockContent(w, x, y, z);
					if (id == 0)
						newContent = thisContent;
					else {
						newContent += thisContent;
						if (newContent > 8) {
							thisContent = newContent - 8;
							newContent = 8;
						} else
							thisContent = 0;
					}

					ChunkTempData tempData = ChunkTempData.getChunk(w, x, y, z);
					setBlockContent(w, chunk, tempData, x, y + dy, z, newContent, "[From displaced block]");
					FysiksFun.scheduleBlockTick(w, this, x, y + dy, z, liquidUpdateRate, "[From displaced block]");
					break;
					/* Doesnt work */
					/*
					 * for(int i=0;i<7;i++) w.spawnParticle("suspended", (double) ((float)
					 * x + w.rand.nextFloat()), (double) ((float) y + w.rand.nextFloat() +
					 * dy), (double) ((float) z + w.rand.nextFloat()), 0.0D, 0.1D, 0.0D);
					 */

					// if (thisContent == 0) break;
				} else
					break;
			}
		} finally {
			// Remove any temp data. Note that other blocks should not add new temp
			// data until _after_ they have written their ID into the cell. */
			ChunkTempData.setTempData(w, x, y, z, 0);
		}
	}

	public void doExpensiveChecks(World w, Chunk origChunk, int x, int y, int z) {
		// Make a random walk to see if we should deposit one of our water points to
		// someone else
		int chunkX = x >> 4, chunkZ = z >> 4;
		IChunkProvider chunkProvider = w.getChunkProvider();
		// Chunk origChunk = chunkProvider.provideChunk(chunkX, chunkZ);

		int origId = origChunk.getBlockID(x & 15, y, z & 15);
		// int origId = w.getBlockId(x, y, z);
		if (origId != stillID && origId != movingID)
			return;

		/*
		 * 
		 * for (int range = 1; range < maxRange; range++) for (int side = 0; side <
		 * 3; side++) { int x3 = range * dx + (side==0?0:(side==1?-1:1))*dz + x; int
		 * z3 = range * dz + (side==0?0:(side==1?-1:1))*dx + z;
		 */

		int dir = w.rand.nextInt(4);
		int x2 = x, z2 = z;

		Chunk chunkCache = origChunk;
		int chunkCacheX = x2 >> 4, chunkCacheZ = z2 >> 4;

		try {
			preventSetBlockLiquidFlowover = true;

			switch (dir % 4) {
			case 0:
				x2++;
				break;
			case 1:
				x2--;
				break;
			case 2:
				z2++;
				break;
			case 3:
				z2--;
				break;
			}
			if (x2 >> 4 != chunkCacheX || z2 >> 4 != chunkCacheZ) {
				if (!chunkProvider.chunkExists(x2 >> 4, z2 >> 4))
					return;
				chunkCache = chunkProvider.provideChunk(x2 >> 4, z2 >> 4);
				chunkCacheX = x2 >> 4;
				chunkCacheZ = z2 >> 4;
			}
			/*
			 * if (!w.blockExists(x2, y, z2)) return;
			 */
			int id = chunkCache.getBlockID(x2 & 15, y, z2 & 15);
			// int id = w.getBlockId(x2, y, z2);
			if (id != stillID && id != movingID && id != 0)
				return;
			// int contentHere = getBlockContent(w, x, y, z);
			int contentHere = 8 - origChunk.getBlockMetadata(x & 15, y, z & 15);
			int contentNN;
			if (id == 0)
				contentNN = 0;
			else {
				contentNN = 8 - chunkCache.getBlockMetadata(x2 & 15, y, z2 & 15);
				// contentNN = getBlockContent(w, x2, y, z2);
			}
			if (contentNN > contentHere)
				return;
			int toMove = 0;

			int idNNBelow = chunkCache.getBlockID(x2 & 15, y - 1, z2 & 15);
			// int idNNBelow = w.getBlockId(x2, y - 1, z2);
			if (contentNN + 1 < contentHere || idNNBelow == 0 || idNNBelow == movingID)
				toMove = 1;
			if (toMove == 0 && toMove == -1) {
				/* Keep walking to see if we could support this liquid in another cell */
				int x3 = x2, z3 = z2;
				for (int i = 0; i < 4; i++) {
					int dir2 = w.rand.nextInt(4);
					int oldX3 = x3, oldZ3 = z3;
					int idMM = 0, j;
					for (j = 0; j < 4; j++) {
						x3 = oldX3;
						z3 = oldZ3;
						switch ((dir2 + j) % 4) {
						case 0:
							x3++;
							break;
						case 1:
							x3--;
							break;
						case 2:
							z3++;
							break;
						case 3:
							z3--;
							break;
						}
						if (x3 >> 4 != chunkCacheX || z3 >> 4 != chunkCacheZ) {
							if (!chunkProvider.chunkExists(x3 >> 4, z3 >> 4))
								return;
							chunkCache = chunkProvider.provideChunk(x3 >> 4, z3 >> 4);
							chunkCacheX = x3 >> 4;
							chunkCacheZ = z3 >> 4;
						}
						/*
						 * if (!w.blockExists(x3, y, z3)) return;
						 */
						idMM = chunkCache.getBlockID(x3 & 15, y, z3 & 15);
						// idMM = w.getBlockId(x3, y, z3);
						if (idMM == stillID || idMM == movingID || idMM == 0)
							break;
					}
					if (j == 4)
						return;
					if (idMM == 0 && chunkCache.getBlockID(x3 & 15, y - 1, z3 & 15) == 0) {
						// if (idMM == 0 && w.getBlockId(x3, y - 1, z3) == 0) {
						toMove = 1;
						break;
					}
					if (idMM == stillID || idMM == movingID) {
						int contentMM = 8 - chunkCache.getBlockMetadata(x3 & 15, y, z3 & 15);
						// int contentMM = getBlockContent(w, x3, y, z3);
						if (contentMM > contentHere)
							return;
						int idMMBelow = w.getBlockId(x3, y - 1, z3);
						if (contentMM + 1 < contentHere || idMMBelow == 0 || idMMBelow == movingID) {
							toMove = 1;
							break;
						}
					}
				}
			}
			if (toMove != 0) {
				contentHere = contentHere - toMove;
				contentNN = contentNN + toMove;
				FysiksFun.logger.log(Level.SEVERE, "We haven't finished rewriting doExpensiveChecks yet");
				// setBlockContent(w, x2, y, z2, contentNN, "[Expensive checks 1]"); //
				// id
				// setBlockContent(w, x, y, z, contentHere, "[Expensive checks 2]"); //
				// origId
			}
		} finally {
			preventSetBlockLiquidFlowover = false;
		}
	}

	@Override
	public int getRenderType() {
		if (superWrapper != null)
			return superWrapper.getRenderType();
		else
			return super.getRenderType();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public int getRenderBlockPass() {
		if (superWrapper != null)
			return superWrapper.getRenderBlockPass();
		else
			return super.getRenderBlockPass();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void randomDisplayTick(World par1World, int par2, int par3, int par4, Random par5Random) {
		if (superWrapper != null)
			superWrapper.randomDisplayTick(par1World, par2, par3, par4, par5Random);
		else
			super.randomDisplayTick(par1World, par2, par3, par4, par5Random);
	}

	@Override
	public void velocityToAddToEntity(World w, int x, int y, int z, Entity entity, Vec3 velocity) {
		Vec3 vec = this.getFFFlowVector(w, x, y, z);

		if (vec.lengthVector() > 0.0D && entity.func_96092_aw()) {
			double d1 = 0.005d; // 0.014D;
			entity.motionX += vec.xCoord * d1;
			entity.motionY += vec.yCoord * d1;
			entity.motionZ += vec.zCoord * d1;
			velocity.xCoord += vec.xCoord;
			velocity.yCoord += vec.yCoord;
			velocity.zCoord += vec.zCoord;
		}

	}

	private Vec3 getFFFlowVector(World w, int x, int y, int z) {
		Vec3 myvec = w.getWorldVec3Pool().getVecFromPool(0.0D, 0.0D, 0.0D);
		/*
		 * If the liquid can fall straight down, return a vector straight down at
		 * "full" strength
		 */
		int idBelow = w.getBlockId(x, y - 1, z);
		int contentHere = getBlockContent(w, x, y, z);

		if (idBelow == 0 || idBelow == movingID) {
			int belowContent = (idBelow == 0 ? 0 : getBlockContent(w, x, y - 1, z));
			if (contentHere < belowContent)
				myvec.yCoord = 0.0;
			else
				myvec.yCoord = -10.0 * (contentHere - belowContent) / 8.0;

			myvec.xCoord = 0.0;
			myvec.zCoord = 0.0;
			return myvec;
		}

		/*
		 * Otherwise, see how much can flow into neighbours and sum the level
		 * differences as strengths
		 */

		for (int dir = 0; dir < 4; dir++) {
			int dx = Util.dirToDx(dir);
			int dz = Util.dirToDz(dir);
			int id2 = w.getBlockId(x + dx, y, z + dz);
			if (id2 == 0 || id2 == stillID || id2 == movingID) {
				int content2 = id2 == 0 ? 0 : getBlockContent(w, x + dx, y, z + dz);
				if (Math.abs(contentHere - content2) > 1) {
					myvec.xCoord += ((double) dx * (contentHere - content2)) * contentHere / 16.d;
					myvec.zCoord += ((double) dz * (contentHere - content2)) * contentHere / 16.d;
				}
			}
		}
		return myvec;
	}

	/* Removes a part of the liquid through evaporation */
	public void evaporate(World w, Chunk c, int x, int y, int z, int amount) {
		int content = getBlockContent(w, x, y, z);
		content = Math.max(0, content - amount * (maximumContent / 8));
		setBlockContent(w, x, y, z, content);
		if (this == Fluids.stillWater || this == Fluids.flowingWater) {
			/* Attempt to create steam from this */
			Gases.steam.produceSteam(w, c, x, y, z, amount);
		}
	}

	/* Removes a part of the liquid through consumption */
	public void consume(World w, Chunk chunk, int x, int y, int z, int amount) {
		int content = getBlockContent(w, x, y, z);
		content = Math.max(0, content - amount * (maximumContent / 8));
		setBlockContent(w, x, y, z, content);
	}

}
