package mbrx.ff;

import java.security.Provider;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowing;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Icon;
import net.minecraft.util.Vec3;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

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
  public int                  stillID, movingID;

  public static boolean       preventSetBlockLiquidFlowover = false;
  public Block                superWrapper;
  public boolean              canSeepThrough;
  public boolean              canCauseErosion;

  public int                  liquidUpdateRate;

  /**
   * Liquids content/pressure are modelled in TWO places. In the metaData we store the values 0 - 8 (or 0-16) as a
   * representation of the content. In the tempData we store the values 0 - maxPressure. The range 0 ...
   * fullLiquidPressure (eg. 0 ... 4096) is used to represent the actual content value in a cell. The range
   * fullLiquidPressure ... maxPressure represents a cell that has full content _and_ that is under pressure. The
   * maximum pressure and the value 256 per Y gives maximum pressure under 240 blocks of liquid. This would correspond
   * to 24 bar.
   * 
   */

  public final static int     pressureMaximum               = 65535;
  public final static int     pressureFullLiquid            = 4096;
  public final static int     minimumLiquidLevel            = pressureFullLiquid / 8;
  public final static int     maximumContent                = pressureFullLiquid;
  public final static int     pressureLossPerStep           = 1;
  public final static int     pressurePerY                  = 256;
  public final static int     pressurePerContent            = pressurePerY / 8;

  public final static boolean logExcessively                = false;

  public String               name;

  // Needs to scale with pressurePerY so total time for equilibriums do not
  // increase
  // public final int pressureLossPerUpdate = pressurePerY / 16;
  // Rationalle: we need to have surplus of atleast one 'pressurePerContent' to
  // be pushed "up" from NN, plus we loose two pressureLossPerStep on the way
  public final int            pressureDiffToMoveSideways    = pressurePerContent + 2 * pressureLossPerStep;

  public BlockFluid(Block superWrapper, int id, Material par2Material, int stillID, int movingID, String n) {
    super(id, par2Material);
    // this.blockIndexInTexture = superWrapper.blockIndexInTexture;
    // this.setTextureFile(superWrapper.getTextureFile());

    name = n;
    this.superWrapper = superWrapper;
    liquidUpdateRate = 1;
    this.stillID = stillID;
    this.movingID = movingID;
    this.canSeepThrough = false;
    this.canCauseErosion = false;
    this.setTickRandomly(false);
  }

  public BlockFluid(int id, Material par2Material, int stillID, int movingID, String n) {
    super(id, par2Material);
    this.superWrapper = null;
    liquidUpdateRate = 1; // Default tickrate for water
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
    if (superWrapper != null) return superWrapper.getBlockTexture(par1IBlockAccess, par2, par3, par4, par5);
    else return super.getBlockTexture(par1IBlockAccess, par2, par3, par4, par5);
  }

  @Override
  public Icon getIcon(int par1, int par2) {
    if (superWrapper != null) return superWrapper.getIcon(par1, par2);
    else return super.getIcon(par1, par2);
  }

  public void setLiquidUpdateRate(int rate) {
    if (rate < 1) FysiksFun.logger.log(Level.SEVERE, "Error, setting liquid " + name + " to invalid update rate " + rate);
    liquidUpdateRate = rate;
  }

  public boolean canOverflowBlock(World w, int x, int y, int z) {
    return canOverflowBlock(w.getBlockId(x, y, z));
  }

  public boolean canOverflowBlock(int id) {
    Block b = Block.blocksList[id];
    if (b == null) return false;
    Material m = b.blockMaterial;
    return !m.blocksMovement() && !m.isLiquid();
  }

  public int getBlockContent(World w, int x, int y, int z) {
    IChunkProvider chunkProvider = w.getChunkProvider();
    return getBlockContent(chunkProvider.provideChunk(x >> 4, z >> 4), ChunkTempData.getChunk(w, x, y, z), x, y, z);
  }

  public int getBlockContent(World w, int x, int y, int z, int oldMetaData) {
    IChunkProvider chunkProvider = w.getChunkProvider();
    Chunk c = chunkProvider.provideChunk(x >> 4, z >> 4);
    ChunkTempData tempData = ChunkTempData.getChunk(w, x, y, z);
    int temp = tempData.getTempData(x, y, z);
    /*
     * if(temp == 0) return (8 - oldMetaData) * (maximumContent / 8); else
     */// DEBUG
    return temp;
  }

  public int getBlockContent(World w, Chunk c, int x, int y, int z) {
    return getBlockContent(c, ChunkTempData.getChunk(w, x, y, z), x, y, z);
  }

  public int getBlockContent(Chunk chunk, ChunkTempData tempData, int x, int y, int z) {
    int temp = tempData.getTempData(x, y, z);
    if (temp == 0) {
      // Reconstruct content from lores data
      int meta = chunk.getBlockMetadata(x & 15, y, z & 15);
      int content = (8 - meta) * (maximumContent / 8);
      tempData.setTempData(x, y, z, content);
      return content;
    } else return temp;
  }

  public void setBlockContent(World w, int x, int y, int z, int content) {
    Chunk chunk = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    ChunkTempData tempData = ChunkCache.getTempData(w, x >> 4, z >> 4);
    // IChunkProvider chunkProvider = w.getChunkProvider();
    // Chunk chunk = chunkProvider.provideChunk(x >> 4, z >> 4);
    // ChunkTempData tempData = ChunkTempData.getChunk(w, x, y, z);
    setBlockContent(w, chunk, tempData, x, y, z, content, "", null);
  }

  /**
   * Sets the content/pressure level of the block to the given amount
   */

  public void setBlockContent(World w, Chunk c, ChunkTempData tempData, int x, int y, int z, int content, String explanation,
      Set<ChunkMarkUpdateTask> delayedBlockMarkSet) {

    ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();
    ExtendedBlockStorage ebs = blockStorage[y >> 4];
    int oldId, oldActualMetadata;
    if (ebs != null) {
      oldId = ebs.getExtBlockID(x & 15, y & 15, z & 15);
      oldActualMetadata = ebs.getExtBlockMetadata(x&15, y & 15, z&15);
    }    
    else {
      oldId = 0;
      oldActualMetadata = 0;
    }
    int newId = (content == 0 ? 0 : (content < maximumContent ? movingID : stillID));
    int oldContent = (isSameLiquid(oldId) ? getBlockContent(c, tempData, x, y, z) : 0);

    int oldMetaData = oldContent < maximumContent ? 8 - oldContent / (maximumContent / 8) : 0;
    int newMetaData = content < maximumContent ? 8 - content / (maximumContent / 8) : 0;
    if (oldMetaData > 7) oldMetaData = 7;
    if (newMetaData > 7) newMetaData = 7;

    if (logExcessively) {
      String message = "Setting " + Util.xyzString(x, y, z) + " to " + content;
      FysiksFun.logger.log(Level.INFO, Util.logHeader() + message + " " + explanation);
    }

    // ebs=null;
    if (newId != oldId || newMetaData != oldMetaData) {
      if (ebs != null) {
        ebs.setExtBlockID(x & 15, y & 15, z & 15, newId);
        ebs.setExtBlockMetadata(x & 15, y & 15, z & 15, newMetaData);
      } else {
        c.setBlockIDWithMetadata(x & 15, y, z & 15, newId, newMetaData);
        // oldMetaData = newMetaData;
      }
    }

    // if (newMetaData != oldMetaData || newId != oldId)
    // c.setBlockIDWithMetadata(x & 15, y, z & 15, newId, newMetaData);

    /*
     * The following is lost by not calling the "proper" setBlockIDWithMetaData:
     * 
     * precipationHeighmap is not updated not calling breakBlock / preBlockDestroy on any blocks not calling any
     * blockTileEntities that was on this block not scheduling a recalculation of the skylightmap not scheduling (?) the
     * relightBlock not creating a tileentity (duh!) not calling onAdded not setting the isModified flag on the chunk
     */

    tempData.setTempData(x, y, z, content);

    if (oldId != newId || oldMetaData != newMetaData) {
      // ChunkMarkUpdater.scheduleBlockMark(w, x, y, z);
      if (delayedBlockMarkSet == null) ChunkMarkUpdater.scheduleBlockMark(w, x, y, z, oldId, oldActualMetadata);
      else delayedBlockMarkSet.add(new ChunkMarkUpdateTask(w, x, y, z, oldId, oldActualMetadata));
    }
  }

  /**
   * Assumption - we cannot trust that the block instance we are called as is the actual block that is present at the
   * given coordinates (MC Vanilla guarantees it, but our custom scheduling system does not)
   */
  @Override
  public void updateTick(World w, int x, int y, int z, Random r) {
    IChunkProvider chunkProvider = w.getChunkProvider();
    if (!chunkProvider.chunkExists(x >> 4, z >> 4)) return;
    ChunkTempData tempData0 = ChunkTempData.getChunk(w, x, y, z);
    updateTickSafe(w, chunkProvider.provideChunk(x >> 4, z >> 4), tempData0, x, y, z, r, 0, null);
  }

  /** Called only when we KNOW that the original chunk is loaded */
  public void updateTickSafe(World world, Chunk chunk0, ChunkTempData tempData0, int x0, int y0, int z0, Random r, int sweep,
      Set<ChunkMarkUpdateTask> delayedBlockMarkSet) {
    Counters.fluidUpdates++;
    // if (sweep % liquidUpdateRate != 0) return;
    boolean moveNormally = (r.nextInt(liquidUpdateRate) == 0);

    // if(x0 == -568 && z0 == 424) logExcessively = true;
    // else logExcessively = false;

    int oldIndent = Util.loggingIndentation;

    int chunkX0 = x0 >> 4, chunkZ0 = z0 >> 4;
    IChunkProvider chunkProvider = world.getChunkProvider();

    int id0 = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
    if (!isSameLiquid(id0)) return;

    try {
      preventSetBlockLiquidFlowover = true;

      // Variable naming conventions
      // abc0 : is the node currently under consideration
      // abc1 : is a direct neighbour node
      // abc2 : is a node two steps away
      // abcN : is a node multiple steps away, where N is decided by a loop
      // variable
      // abc11 : is a direct neighbour of abc1
      // abc12 : is a node two steps away from abc1
      // Invariant: abc10 is the same as abc1

      int content0 = getBlockContent(chunk0, tempData0, x0, y0, z0);
      int oldContent0 = content0;
      if (logExcessively) FysiksFun.logger.log(Level.INFO, Util.logHeader() + "Updating " + Util.xyzString(x0, y0, z0) + " content0: " + content0);
      Util.loggingIndentation++;

      final int directions[][] = { { -1, -1 }, { 0, -1 }, { 1, -1 }, { -1, 0 }, { 1, 0 }, { -1, +1 }, { 0, +1 }, { 1, +1 } };

      /* Special test for free falling water */
      if (content0 <= maximumContent / 8) {
        int dy;
        for (dy = 1; dy < 4; dy++) {
          if (y0 - dy < 0) break;
          if (chunk0.getBlockID(x0 & 15, y0 - dy, z0 & 15) != 0) break;
        }
        if (dy > 1) {
          setBlockContent(world, chunk0, tempData0, x0, y0 - dy + 1, z0, content0, "[Falling fast]", delayedBlockMarkSet);
          setBlockContent(world, chunk0, tempData0, x0, y0, z0, 0, "[Falling fast]", delayedBlockMarkSet);
          return;
        }
      }

      /* Recompute our own pressure before moving blocks */
      if (content0 >= maximumContent) {
        content0 = maximumContent;
        for (int dir0 = 0; dir0 < 10; dir0++) {
          int dX, dY, dZ;
          boolean isDiagonal = false;
          if (dir0 == 0) {
            dX = 0;
            dY = -1;
            dZ = 0;
          } else if (dir0 == 9) {
            dX = 0;
            dY = +1;
            dZ = 0;
          } else {
            int dir = dir0 - 1;
            dX = directions[dir][0];
            dY = 0;
            dZ = directions[dir][1];
            isDiagonal = !(dX == 0 || dZ == 0);
          }
          int x1 = x0 + dX;
          int y1 = y0 + dY;
          int z1 = z0 + dZ;
          if (y1 < 0 || y1 >= 256) continue;

          Chunk chunk1 = ChunkCache.getChunk(world, x1 >> 4, z1 >> 4, false);
          if (chunk1 == null) continue;

          // if ((x1 >> 4) == chunkX0 && (z1 >> 4) == chunkZ0) chunk1 = chunk0;
          // else if (chunkProvider.chunkExists(x1 >> 4, z1 >> 4)) chunk1 =
          // chunkProvider.provideChunk(x1 >> 4, z1 >> 4);
          // else continue;

          int id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
          if (!Fluids.isLiquid[id1]) continue;
          ChunkTempData tempData1 = ChunkCache.getTempData(world, x1 >> 4, z1 >> 4);

          // Get new tempData
          // if ((x1 >> 4) == (x0 >> 4) && (z1 >> 4) == (z0 >> 4)) tempData1 =
          // tempData0;
          // else tempData1 = ChunkTempData.getChunk(world, x1, y1, z1);

          int content1 = getBlockContent(chunk1, tempData1, x1, y1, z1);
          if (content1 < maximumContent) continue;
          int pressure1 = content1 + dY * pressurePerY - pressureLossPerStep;
          if (isDiagonal) pressure1 -= pressureLossPerStep / 2;
          if (pressure1 > content0) content0 = pressure1;
        }
      }

      /* Move liquids */
      /*
       * Iterate over all neighbours and check if there is a significant enough pressure difference to move fluids. If
       * so, perform the moves and schedule new ticks.
       */

      int dirOffset = FysiksFun.rand.nextInt(8);
      for (int dir0 = 0; dir0 < 10; dir0++) {
        // Compute a direction of interest such that we
        // (a) First consider the down direction
        // (b) Next consider the horizontal directions (including diagonals) in
        // a SEMI-random order
        // (c) Last consider the "up" direction
        // int dir1 = dir0 % 6;
        // int dir = dir1 < 4 ? (dir1 ^ dirOffset) & 3 : dir1;
        int dX, dY, dZ;
        boolean isDiagonal = false;
        if (dir0 == 0) {
          dX = 0;
          dY = -1;
          dZ = 0;
        } else if (dir0 == 9) {
          dX = 0;
          dY = +1;
          dZ = 0;
        } else {
          int dir = (dir0 - 1 + dirOffset) % 8;
          dX = directions[dir][0];
          dY = 0;
          dZ = directions[dir][1];
          isDiagonal = !(dX == 0 || dZ == 0);
        }
        // if ((!moveNormally) && dir0 != 0) continue;

        int x1 = x0 + dX;
        int y1 = y0 + dY;
        int z1 = z0 + dZ;
        if (y1 < 0) {
          // Flow out of the world
          System.out.println("Flowing out of the world: "+Util.xyzString(x1, y1, z1));
          content0 = 0;
          continue;
        } else if (y1 >= 256) continue;

        Chunk chunk1;
        if ((x1 >> 4) == chunkX0 && (z1 >> 4) == chunkZ0) chunk1 = chunk0;
        else if (chunkProvider.chunkExists(x1 >> 4, z1 >> 4)) chunk1 = chunkProvider.provideChunk(x1 >> 4, z1 >> 4);
        else continue;

        int id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
        int content1 = 0;

        /* Check if this is a block we can flow over and if we have enough liquid left. */
        if (id1 != 0 && !Fluids.isLiquid[id1]) {
          if (Gases.isGas[id1]) continue;
          if (Block.blocksList[id1] == null) continue;
          Material m = Block.blocksList[id1].blockMaterial;
          if (m.blocksMovement()) continue;
          else if (content0 > minimumLiquidLevel || dY == -1) {
            /* Flow over this block, dropping the contents */
            int metaData1 = chunk1.getBlockMetadata(x1 & 15, y1, z1 & 15);
            if(id1 != Block.snow.blockID && id1 != Block.tallGrass.blockID)
              Block.blocksList[id1].dropBlockAsItem(world, x1, y1, z1, metaData1, 0);
            chunk1.setBlockIDWithMetadata(x1 & 15, y1, z1 & 15, 0, 0);
            id1 = 0;
            content1 = 0;
          } else continue;
        }

        // Get new tempData and get the content of this neighbour
        ChunkTempData tempData1 = ChunkCache.getTempData(world, x1 >> 4, z1 >> 4);
        // if ((x1 >> 4) == (x0 >> 4) && (z1 >> 4) == (z0 >> 4)) tempData1 =
        // tempData0;
        // else tempData1 = ChunkTempData.getChunk(world, x1, y1, z1);
        if (id1 != 0) {
          content1 = getBlockContent(chunk1, tempData1, x1, y1, z1);
        }

        // Check for special interactions
        if (id1 != 0 && !isSameLiquid(id1)) {
          // Allow fluid interactions, but only sideways and down; or up when
          // pressurized
          if (Fluids.liquidCanInteract(movingID, id1) && (dY < 1 || content0 > maximumContent)) {
            content0 = Fluids.liquidInteract(world, x1, y1, z1, movingID, content0, id1, content1);
          }
          continue;
        }

        if (logExcessively)
          FysiksFun.logger.log(Level.INFO, Util.logHeader() + "considering " + Util.xyzString(x1, y1, z1) + " id1: " + id1 + " content1: " + content1);

        int prevContent0 = content0;
        if (dY < 0) {
          if (content1 < maximumContent) {
            // Move liquid downwards
            content0 = Math.max(0, Math.min(maximumContent, content0) + content1 - maximumContent);
            content1 = Math.min(maximumContent, content1 + prevContent0);
            setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Fall down]", delayedBlockMarkSet);
          }
        } else if (dY > 0 && content0 >= maximumContent + pressurePerY + pressureLossPerStep) {
          if (content1 < maximumContent) {
            content0 = content1;
            content1 = Math.min(maximumContent, prevContent0);
            setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Flowing up]", delayedBlockMarkSet);
          }
        } else if (dY == 0 && content0 > content1) {
          if (content1 < maximumContent) {
            content0 = Math.min(maximumContent, content0);
            int toMove = Math.min((content0 - content1) / 2, maximumContent - content1);
            if (isDiagonal) toMove -= toMove / 3;
            if (content1 + toMove >= minimumLiquidLevel) {
              content0 -= toMove;
              content1 += toMove;
              setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Flowing sideways]", delayedBlockMarkSet);
            } else if (id1 == 0) {
              /*
               * Special case to make sure we can move the last drop of liquid over an edge
               */
              int id1b = chunk1.getBlockID(x1 & 15, y1 - 1, z1 & 15);
              int content1b = 0;
              ChunkTempData tempData1b = tempData1;
              if (id1b == 0 || isSameLiquid(id1b)) {
                // if ((y1 - 1) >> 8 == y1 >> 8) tempData1b = tempData1;
                // else tempData1b = ChunkTempData.getChunk(world, x1, y1 - 1,
                // z1);
                content1b = (id1b == 0 ? 0 : getBlockContent(chunk1, tempData1b, x1, y1 - 1, z1));
                if (content1b < maximumContent - content0) {
                  content1b += content0;
                  content0 = 0;
                  // System.out.println("Flowing diagonally down... checking for erosion");
                  setBlockContent(world, chunk1, tempData1, x1, y1 - 1, z1, content1b, "[Flowing diagonally down]", delayedBlockMarkSet);
                  // Possibly trigger an erosion event
                  // DEBUG
                  if (r.nextInt(1000) < FysiksFun.settings.erosionRate && false) {
                    int id0b = chunk0.getBlockID(x0 & 15, y0 - 1, z0 & 15);
                    if (canErodeBlock(id0b)) {
                      int cnt = 0;
                      for (int dx0 = -1; dx0 <= 1; dx0++)
                        for (int dz0 = -1; dz0 <= 1; dz0++) {
                          int sideId = world.getBlockId(x0 + dx0, y0, z0 + dz0);
                          if (sideId != 0 && !Fluids.isLiquid[sideId]) cnt++;
                        }
                      if (cnt <= 2) {
                        // System.out.println("Erosion A: "+x0+" "+(y0-1)+" "+z0);
                        world.setBlockToAir(x0, y0 - 1, z0);

                        /* If this was a diagnonal movement, also remove a straight neighbour */
                        if (dX != 0 && dZ != 0) {
                          int idA = world.getBlockId(x0 + dX, y0 - 1, z0 + 0);
                          if (idA != 0 && !Fluids.isLiquid[idA]) world.setBlockToAir(x0 + dX, y0 - 1, z0 + 0);
                          idA = world.getBlockId(x0 + 0, y0 - 1, z0 + dZ);
                          if (idA != 0 && !Fluids.isLiquid[idA]) world.setBlockToAir(x0 + 0, y0 - 1, z0 + dZ);
                        }

                        // setBlockContent(world, x0, y0-1, z0, 0);
                      } /*
                         * else System.out.println("Cnt="+cnt+" so no erosion");
                         */
                    } /*
                       * else System.out.println("Block below cannot erode id="+id0b);
                       */
                  }
                }
              }
            }
          }
        }
        // We have moved sideways to some extent, see if should remove any
        // surrounding dirt wall as erosion
        int erodeChance = FysiksFun.settings.erosionRate;
        if (dY == 0 && dZ != 0 && dX != 0) erodeChance *= 2; // Higher chance to
                                                             // erode so we get
                                                             // rid or diagonal
                                                             // movements
        // DEBUG
        if (content0 != prevContent0 && dY == 0 && r.nextInt(1000) < erodeChance && false) {
          int idSideA = world.getBlockId(x0 + dZ, y0, z0 + dX);
          if (canErodeBlock(idSideA)) world.setBlockToAir(x0 + dZ, y0, z0 + dX);
          int idSideB = world.getBlockId(x0 - dZ, y0, z0 - dX);
          if (canErodeBlock(idSideB)) world.setBlockToAir(x0 - dZ, y0, z0 - dX);
          if (dX != 0 && dZ != 0) {
            int idSideC = world.getBlockId(x0 + dX, y0, z0 + 0);
            if (canErodeBlock(idSideC)) world.setBlockToAir(x0 + dX, y0, z0 + 0);
            int idSideD = world.getBlockId(x0 + 0, y0, z0 + dZ);
            if (canErodeBlock(idSideD)) world.setBlockToAir(x0 + 0, y0, z0 + dZ);
          }
        }
      }

      /*
       * If we have made a pressurized move, make a random walk towards lower pressures until we find a node we can
       * steal liquid from
       */
      if (oldContent0 >= maximumContent && content0 < maximumContent) {
        int steps;
        int xN = x0, yN = y0, zN = z0;
        int currPressure = maximumContent;
        Chunk chunkN = chunk0;
        ChunkTempData tempDataN = tempData0;
        Chunk bestChunkM = null;
        ChunkTempData bestTempDataM = null;

        for (steps = 0; steps < 16; steps++) {
          int bestDir = -1;
          int bestPressure = 0;
          for (int dir = 0; dir < 6; dir++) {
            // TODO, use a 10 neighbourhood here instead
            int dX = Util.dirToDx(dir);
            int dY = Util.dirToDy(dir);
            int dZ = Util.dirToDz(dir);
            int xM = xN + dX;
            int yM = yN + dY;
            int zM = zN + dZ;
            if (yM < 0 || yM > 255) continue;

            Chunk chunkM = ChunkCache.getChunk(world, xM >> 4, zM >> 4, false);
            if (chunkM == null) continue;

            /*
             * if (xM >> 4 == xN >> 4 && zM >> 4 == zN >> 4) chunkM = chunkN; else { if (!chunkProvider.chunkExists(xM
             * >> 4, zM >> 4)) continue; chunkM = chunkProvider.provideChunk(xM >> 4, zM >> 4); }
             */
            int idM = chunkM.getBlockID(xM & 15, yM, zM & 15);
            if (!isSameLiquid(idM)) continue;

            ChunkTempData tempDataM = ChunkCache.getTempData(world, xM >> 4, zM >> 4);
            // if (xM >> 4 == xN >> 4 && zM >> 4 == zN >> 4) tempDataM =
            // tempDataN;
            // else tempDataM = ChunkTempData.getChunk(world, xM, yM, zM);
            int contentM = getBlockContent(chunkM, tempDataM, xM, yM, zM);
            int modifiedPressure = contentM + pressurePerY * dY - 2 * pressureLossPerStep;
            if (modifiedPressure > bestPressure) {
              bestDir = dir;
              bestPressure = modifiedPressure;
              bestChunkM = chunkM;
              bestTempDataM = tempDataM;
            }
          }
          // WAS if (bestPressure < currPressure) break;
          if (bestPressure < currPressure) break;
          xN += Util.dirToDx(bestDir);
          yN += Util.dirToDy(bestDir);
          zN += Util.dirToDz(bestDir);
          chunkN = bestChunkM;
          tempDataN = bestTempDataM;
        }
        /* Steal as much as possible from N */
        int contentN = getBlockContent(chunkN, tempDataN, xN, yN, zN);
        contentN = Math.min(maximumContent, contentN);
        int toMove = Math.min(contentN, maximumContent - content0);
        contentN -= toMove;
        content0 += toMove;

        setBlockContent(world, chunkN, tempDataN, xN, yN, zN, contentN, "[Propagated pressurized liquid]", delayedBlockMarkSet);
        if (content0 == maximumContent) content0 = oldContent0;
      }

      /* Write our updated content to the world if it has changed */
      if (content0 != oldContent0) {
        if (logExcessively) FysiksFun.logger.log(Level.INFO, Util.logHeader() + "self update, oldContent0: " + oldContent0);
        setBlockContent(world, chunk0, tempData0, x0, y0, z0, content0, "[Final self update]", delayedBlockMarkSet);
      }
    } finally {
      preventSetBlockLiquidFlowover = false;
      Util.loggingIndentation = oldIndent;
      if (logExcessively) FysiksFun.logger.log(Level.INFO, Util.logHeader() + "Finished " + Util.xyzString(x0, y0, z0));

    }
  }

  /**
   * Called less regularly to give the fluids a chance to do any expensive tick updates (checking larger chunk areas
   * etc)
   **/
  public void expensiveTick(World world, Chunk chunk0, ChunkTempData tempData0, int x0, int y0, int z0, Random r) {}

  /**
   * Perform a random walk that will eliminate puddles by moving them in the general direction of free areas
   */
  public void updateRandomWalk(World world, Chunk chunk0, ChunkTempData tempData0, int x0, int y0, int z0, Random r) {
    int oldIndent = Util.loggingIndentation;
    int chunkX0 = x0 >> 4, chunkZ0 = z0 >> 4;
    IChunkProvider chunkProvider = world.getChunkProvider();
    int id0 = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
    if (!isSameLiquid(id0)) return;
    int content0 = getBlockContent(chunk0, tempData0, x0, y0, z0);

    // This amount can flow by itself, no need for random walks
    if (content0 > 2 * minimumLiquidLevel) return;

    try {
      preventSetBlockLiquidFlowover = true;
      double bestDx = -1.d, bestDz = -1d;
      int bestDist = 12;
      int dirOffset = r.nextInt(8);
      for (int dir = 0; dir < 8; dir++) {
        // Rotation around Y-axis for X movement
        double dx = Math.sin((dir + dirOffset) / 4. * 3.141);
        double dz = Math.cos((dir + dirOffset) / 4. * 3.141);
        for (int dist = 1; dist < bestDist; dist++) {
          int x1 = (int) (x0 + 0.5d + dist * dx);
          int y1 = y0;
          int z1 = (int) (z0 + 0.5d + dist * dz);
          if (x1 == x0 && z1 == z0) continue;

          Chunk c = ChunkCache.getChunk(world, x1 >> 4, z1 >> 4, false);
          if (c == null) continue;
          int id1 = c.getBlockID(x1 & 15, y1, z1 & 15);
          Block b = Block.blocksList[id1];
          Material m1 = b == null ? null : b.blockMaterial;
          if (id1 != 0 && (m1 != null && m1.blocksMovement())) break;
          int id1b = c.getBlockID(x1 & 15, y1 - 1, z1 & 15);

          int content1b = 0;
          Material m1b = id1b == 0 ? null : Block.blocksList[id1b].blockMaterial;
          if (Fluids.isLiquid[id1b]) {
            ChunkTempData tempData = ChunkCache.getTempData(world, x1 >> 4, z1 >> 4);
            content1b = getBlockContent(c, tempData, x1, y1 - 1, z1);
          } else if (id1b != 0 && m1b.blocksMovement()) continue;
          if (content1b < maximumContent - minimumLiquidLevel) {
            bestDist = dist;
            bestDx = dx;
            bestDz = dz;
            break;
          }
        }
      }
      /*
       * if(bestDist == 1) { // && r.nextInt(10) < FysiksFun.settings.erosionRate) { System.out.println("Erosion B");
       * int id0b = world.getBlockId(x0, y0-1, z0); if(Block.blocksList[id0b] == Block.dirt || Block.blocksList[id0b] ==
       * Block.sand) { setBlockContent(world, x0, y0-1, z0, 0); } }
       */
      if (bestDist < 12) {
        // We have found a direction to flow towards
        for (int dist = 1; dist <= bestDist; dist++) {
          int x1 = (int) (x0 + 0.5d + dist * bestDx);
          int z1 = (int) (z0 + 0.5d + dist * bestDz);
          int y1 = y0;

          Chunk c = ChunkCache.getChunk(world, x1 >> 4, z1 >> 4, false);
          ChunkTempData tempData = ChunkCache.getTempData(world, x1 >> 4, z1 >> 4);
          if (c == null) break;
          int id1 = c.getBlockID(x1 & 15, y1, z1 & 15);
          // Material m1 = id1 == 0 ? null :
          // Block.blocksList[id1].blockMaterial;
          if (id1 == 0) {
            setBlockContent(world, c, tempData, x1, y1, z1, content0, "", null);
            setBlockContent(world, c, tempData, x0, y0, z0, 0, "", null);
            break;
          }
        }
      }
    } finally {
      preventSetBlockLiquidFlowover = false;
      Util.loggingIndentation = oldIndent;
      if (logExcessively) FysiksFun.logger.log(Level.INFO, Util.logHeader() + "Finished " + Util.xyzString(x0, y0, z0));
    }
  }

  public boolean canErodeBlock(int blockId) {
    Block b = Block.blocksList[blockId];
    if (b == Block.dirt || b == Block.grass || b == Block.sand) return true;
    else return false;
  }

  /*
   * // If we are in an ocean biome and have a pillar of water atleast DY high // above us, and are below Y=60 - // then
   * we are probably at the bottom of the ocean and treats this as a // special case of infinite water boolean
   * isInfinite = false; if (FysiksFun.settings.infiniteOceans) { if (y < 62 && newLiquidContent != oldLiquidContent &&
   * (this == Fluids.stillWater || this == Fluids.flowingWater)) { if (isOceanic(w, x, y)) { isInfinite = true; for (int
   * dy = 1; dy <= 2; dy++) { int idYY = origChunk.getBlockID(x & 15, y + dy, z & 15); if (idYY != movingID && idYY !=
   * stillID) { isInfinite = false; break; } } } } }
   */
  /* Let water flow out of the world if we are at the bottom most layer */
  /*
   * if (y == 0 && !isInfinite) { setBlockContent(w, origChunk, x, y, z, 0); // FysiksFun.scheduleBlockTick(w, this, x,
   * y, z, 1); notifyFeeders(w, origChunk, x, y, z, 0, liquidUpdateRate, pressurizedLiquidUpdateRate); return; }
   */

  static public boolean isOceanic(World w, int x, int y) {
    BiomeGenBase g = w.getBiomeGenForCoords(x, y);
    if (g == BiomeGenBase.ocean || g == BiomeGenBase.frozenOcean || g == BiomeGenBase.river) return true;
    else return false;
  }

  /* Full blocks of water with nowhere else to go, MAY leak through dirt */
  // TODO - increase chance or effect exponentially depending on pressure?
  /*
   * if (canSeepThrough && FysiksFun.settings.erosionRate > 0 && newLiquidContent >= 2 && (blockIdNN ==
   * Block.dirt.blockID || blockIdNN == Block.sand.blockID)) { boolean doSeep = false; int range2;
   * 
   * chunk2 = origChunk; int chunk2x = x >> 4; int chunk2z = z >> 4; int x3 = x, z3 = z; for (range2 = 2; range2 < 16;
   * range2++) { x3 = x + range2 * dx; z3 = z + range2 * dz; if (x3 >> 4 != chunk2x || z3 >> 4 != chunk2z) { if
   * (!chunkProvider.chunkExists(x3 >> 4, z3 >> 4)) continue; else { chunk2 = chunkProvider.provideChunk(x3 >> 4, z3 >>
   * 4); chunk2x = x3 >> 4; chunk2z = z3 >> 4; } } int blockIdThrough = chunk2.getBlockID(x3 & 15, y, z3 & 15);
   * 
   * if (blockIdThrough == 0) { doSeep = true; break; } else if (blockIdThrough != Block.dirt.blockID && blockIdThrough
   * != Block.sand.blockID) break; } if (doSeep) { newLiquidContent -= 1; setBlockContent(w, chunk2, x3 & 15, y, z3 &
   * 15, 1, "[Seeping liquids]"); FysiksFun.scheduleBlockTick(w, this, x3, y, z3, liquidUpdateRate,
   * "[Seeping liquids]"); if (FysiksFun.rand.nextInt(1 + 200 / FysiksFun.settings.erosionRate) == 0) {
   * FysiksFun.setBlockWithMetadataAndPriority(w, x2, y, z2, 0, 0, 0); } } }
   */
  // Let liquids flow through open doors
  // Note: this may cause us to leak into an unloaded chunk, but since the
  // only effect is a slight efficiency cost
  // I consider this to be too rare to fix
  /*
   * boolean isOpenDoorNN = canFlowThrough(blockIdNN, blockMetaNN); if (newLiquidContent >= 2 && isOpenDoorNN) { // The
   * door is open, see if we can flow through it int x3 = x + dx * 2, z3 = z + dz * 2; Chunk chunk3 = origChunk; if (x3
   * >> 4 != x >> chunkX || z3 >> 4 != chunkZ) { if (chunkProvider.chunkExists(x3 >> 4, z3 >> 4)) chunk3 =
   * chunkProvider.provideChunk(x3 >> 4, z3 >> 4); else chunk3 = null; } if (chunk3 != null) { int id3 =
   * chunk3.getBlockID(x3 & 15, y, z3 & 15); if (id3 == 0 || id3 == movingID || id3 == stillID) { int contentThrough =
   * 0; if (id3 == movingID || id3 == stillID) contentThrough = getBlockContent(chunk3, x3, y, z3); int toMove =
   * (newLiquidContent - contentThrough) / 2; if (toMove > 0) { newLiquidContent -= toMove; setBlockContent(w, chunk3,
   * x3, y, z3, contentThrough + toMove, "[Through open door]"); FysiksFun.scheduleBlockTick(w, this, x + dx * 2, y, z +
   * dz * 2, liquidUpdateRate); } } } } else if (y > 1 && blockIdNN != this.blockID && newLiquidContent > 2 &&
   * this.canOverflowBlock(blockIdNN) && newLiquidContent >= 2) { // Again: this may cause loading of another chunk, but
   * it should be rare // enough that we can ignore it...
   * 
   * // If the cell NN is an item that can be destroyed by the liquid, then // drop it. And in next step of THIS tick
   * move into it Block b = Block.blocksList[blockIdNN]; if (b != null && b != Block.snow && b != Block.grass)
   * b.dropBlockAsItem(w, x2, y, z2, chunk2.getBlockMetadata(x2 & 15, y, z2 & 15), 0); blockIdNN = 0; }
   * 
   * if (blockIdNN == 0 || (newLiquidContent == 1 && isOpenDoorNN)) { // Case 3: move into empty neighbouring cell int
   * toMove = newLiquidContent / 2;
   * 
   * if (newPressure > 0 && blockIdNN == 0) { toMove = 0; // We will make the full movement directly here, rather //
   * than in the test below setBlockContent(w, chunk2, x2, y, z2, newLiquidContent, "[Is empty neighbour]");
   * newLiquidContent = 0; notifyFeeders(w, origChunk, x, y, z, 1, liquidUpdateRate - 1, pressurizedLiquidUpdateRate);
   * // Remove any earlier ticks from the target, so we have time to refill // before he runs
   * FysiksFun.removeBlockTick(w, this, x2, y, z2, liquidUpdateRate - 1); FysiksFun.scheduleBlockTick(w, this, x2, y,
   * z2, liquidUpdateRate); newPressure = 0; }
   */

  /*
   * // Do erosion for the case when we move into an empty neighbour that // will be able to fall down on the next tick
   * if (canCauseErosion && chunk2.getBlockID(x2 & 15, y - 1, z2 & 15) == 0 && FysiksFun.settings.erosionRate != 0 &&
   * (this == Fluids.stillWater || this == Fluids.flowingWater) && toMove > FysiksFun.settings.erosionThreshold) {
   * boolean canErodeA = canErode(w, x + dz, y, z + dx); boolean canErodeB = canErode(w, x - dz, y, z - dx);
   * 
   * if (!canErodeA && !canErodeB) { if (FysiksFun.rand.nextInt(3000 / FysiksFun.settings.erosionRate) < toMove * 4 - 3)
   * doErode(w, x, y - 1, z, x2, y - 1, z2); } else { // Count how "surrounded" the block that can erode is int cntA =
   * 0, cntB = 0; for (int dx2 = -1; dx2 <= 1; dx2++) for (int dz2 = -1; dz2 <= 1; dz2++) { if
   * (isSameLiquid(w.getBlockId(x + dz + dx2, y, z + dx + dz2))) cntA++; if (isSameLiquid(w.getBlockId(x - dz + dx2, y,
   * z - dx + dz2))) cntB++; } // Finally, attempt to erode the target block, boosting the // probability if it is very
   * surrounded by the liquid if (canErodeA && FysiksFun.rand.nextInt(3000 / FysiksFun.settings.erosionRate) < toMove *
   * cntA - 3) maybeErode(w, x + dz, y, z + dx); if (canErodeB && FysiksFun.rand.nextInt(3000 /
   * FysiksFun.settings.erosionRate) < toMove * cntB - 3) maybeErode(w, x - dz, y, z - dx); } } } else {
   * 
   * // Treat cells with very little (1) water specially to make sure they // can eventually flow over an edge, if
   * within reach int maxRange = 8; // FysiksFun.rand.nextInt(15) + 3; Chunk chunkCache = origChunk; int chunkCacheX = x
   * >> 4, chunkCacheZ = z >> 4; toMove = 0;
   * 
   * for (int range = (isOpenDoorNN ? 2 : 1); range < maxRange && toMove == 0; range++) for (int side = 0; side < 3 &&
   * toMove == 0; side++) { int x3 = range * dx + (side == 0 ? 0 : (side == 1 ? -1 : 1)) * dz * range + x; int z3 =
   * range * dz + (side == 0 ? 0 : (side == 1 ? -1 : 1)) * dx * range + z;
   * 
   * if (x3 >> 4 != chunkCacheX || z3 >> 4 != chunkCacheZ) { if (!chunkProvider.chunkExists(x3 >> 4, z3 >> 4)) break;
   * chunkCache = chunkProvider.provideChunk(x3 >> 4, z3 >> 4); chunkCacheX = x3 >> 4; chunkCacheZ = z3 >> 4; } int idMM
   * = chunkCache.getBlockID(x3 & 15, y, z3 & 15); if (idMM != Block.tallGrass.blockID && idMM != 0) break;
   * 
   * int idBelowMM = chunkCache.getBlockID(x3 & 15, y - 1, z3 & 15); if (idBelowMM == 0 || idBelowMM == movingID) {
   * toMove = 1; // Erosion that eats away from under it and carries the block // along way along these micro-flows.
   * This is needed to start // rivers/lakes int x2x1 = x2 - x, z2z1 = z2 - z; if (canCauseErosion &&
   * FysiksFun.settings.erosionThreshold == 0 && FysiksFun.settings.erosionRate != 0 && FysiksFun.rand.nextInt(1000 /
   * FysiksFun.settings.erosionRate) == 0) { boolean canErodeLeft = canErode(w, x + z2z1, y, z + x2x1); boolean
   * canErodeRight = canErode(w, x - z2z1, y, z - x2x1); boolean canErodeBelow = canErode(w, x, y - 1, z); if
   * (canErodeLeft || canErodeRight || canErodeBelow) { int cnt = -3 * range; for (int dx2 = -2; dx2 <= 2; dx2++) for
   * (int dy2 = -1; dy2 <= 0; dy2++) for (int dz2 = -2; dz2 <= 2; dz2++) if (isSameLiquid(w.getBlockId(x + dx2, y + dy2,
   * z + dz2))) cnt++; // TODO - use the chunkCache here if (!canErode(w, x3 - 1, y - 1, z3)) cnt += 3; if (!canErode(w,
   * x3 + 1, y - 1, z3)) cnt += 3; if (!canErode(w, x3, y - 1, z3 - 1)) cnt += 3; if (!canErode(w, x3, y - 1, z3 + 1))
   * cnt += 3; // Erosion of the block below can occur if there are // atleast X neighbours of us that also have liquid
   * // since this means that we are in a large shallow pool // TODO: use a stochastic method depending on cnt // TODO:
   * move the second to last block? // TODO: set counter higher if the TARGET block is // surrounded by other non-liquid
   * blocks?? // If a block is surrounded by 4 water blocks (in the XZ // plane) then it should be carried away. (Hmm,
   * also count // on Y plane?) if (cnt >= 3 && FysiksFun.rand.nextInt(100) < cnt) { int dy; for (dy = 1; dy < 256;
   * dy++) { int idMM4 = chunkCache.getBlockID(x3 & 15, y - dy, z3 & 15); if (idMM4 != 0 && !isSameLiquid(idMM4)) break;
   * // if (w.getBlockId(x3, y - dy, z3) != 0 && // !isSameLiquid(w.getBlockId(x3, y - dy, z3))) break; } if
   * (canErodeLeft) doErode(w, x + z2z1, y, z + x2x1, x3, y - dy, z3); else if (canErodeRight) doErode(w, x - z2z1, y, z
   * - x2x1, x3, y - dy, z3); else if (canErodeBelow) doErode(w, x, y - 1, z, x3, y - dy, z3); } } } // Extra
   * notification to make sure that a single water level // will immediately fall down if (range == 1)
   * FysiksFun.scheduleBlockTick(w, this, x2, y, z2, 1); break; } } }
   */
  /*
   * if (toMove > 0) { if (isOpenDoorNN && newLiquidContent == 1) { // Move one step further - so we can flow through
   * open doors if (chunkProvider.chunkExists((x + dx * 2) >> 4, (z + dz * 2) >> 4)) { setBlockContent(w, x + dx * 2, y,
   * z + dz * 2, toMove, "[Moving into empty neighbour through door]"); FysiksFun.scheduleBlockTick(w, this, x + dx * 2,
   * y, z + dz * 2, liquidUpdateRate / toMove + 1); newLiquidContent -= toMove; } } else { /// Move to NN if
   * (chunkProvider.chunkExists(x2 >> 4, z2 >> 4)) { setBlockContent(w, chunk2, x2, y, z2, toMove,
   * "[Moving into empty neighbour]"); FysiksFun.scheduleBlockTick(w, this, x2, y, z2, liquidUpdateRate / toMove + 1);
   * // It should not be needed to notify anyone at x2,y,z2 since we // _grew_ in content //
   * notifyFeeders(w,chunk2,x2,y,z2, 1, liquidUpdateRate, // pressurizedLiquidUpdateRate); //
   * notifySameLiquidNeighbours(w, x2, y, z2, 1); newLiquidContent -= toMove; } } } }
   */

  // Check for erosions
  /*
   * if (canCauseErosion && FysiksFun.settings.erosionRate != 0 && (this == Fluids.stillWater || this ==
   * Fluids.flowingWater) && toMove > FysiksFun.settings.erosionThreshold) { boolean canErodeA = canErode(w, x + dz, y,
   * z + dx); boolean canErodeB = canErode(w, x - dz, y, z - dx);
   * 
   * if (!canErodeA && !canErodeB) { if (canErode(w, x, y - 1, z)) maybeErode(w, x, y - 1, z); } else { // Count how
   * "surrounded" the block that can erode is int cntA = 0, cntB = 0; for (int dx2 = -1; dx2 <= 1; dx2++) for (int dz2 =
   * -1; dz2 <= 1; dz2++) { if (isSameLiquid(w.getBlockId(x + dz + dx2, y, z + dx + dz2))) cntA++; if
   * (isSameLiquid(w.getBlockId(x - dz + dx2, y, z - dx + dz2))) cntB++; } // Finally, attempt to erode the target
   * block, boosting the // probability if it is very surrounded by the liquid if (canErodeA &&
   * FysiksFun.rand.nextInt(6000 / FysiksFun.settings.erosionRate) < toMove * cntA - 3) maybeErode(w, x + dz, y, z +
   * dx); if (canErodeB && FysiksFun.rand.nextInt(6000 / FysiksFun.settings.erosionRate) < toMove * cntB - 3)
   * maybeErode(w, x - dz, y, z - dx); }
   * 
   * } } }
   */

  private boolean canFlowThrough(int blockIdNN, int blockMetaNN) {

    if ((blockIdNN == Block.doorWood.blockID || blockIdNN == Block.doorIron.blockID) && (blockMetaNN & 4) != 0) return true;
    else if (blockIdNN == Block.fence.blockID || blockIdNN == Block.fenceIron.blockID || blockIdNN == Block.fenceGate.blockID) return true;

    return false;
  }

  // Move into air/gas cell below us
  /*
   * int swappedId = origChunk.getBlockID(x & 15, y - dy, z & 15); int swappedMeta = swappedId == 0 ? 0 :
   * origChunk.getBlockMetadata(x & 15, y - dy, z & 15); int swappedTemp = swappedId == 0 ? 0 :
   * ChunkTempData.getTempData(w, x, y - dy, z); // Set the content of the block below, zero pressure, update client, //
   * schedule tick System.out.println(Counters.tick + ": Falling " + newLiquidContent + " into " + Util.xyzString(x, y -
   * dy, z)); // Inherit our old pressure into this new position - it will dissipate // slowly if no more water comes
   * after this one setBlockContentAndPressure(w, origChunk, x, y - dy, z, newLiquidContent, newLiquidPressure,
   * "[Falling into]"); FysiksFun.scheduleBlockTick(w, this, x, y - dy, z, liquidUpdateRate, "[Falling into]"); int foo
   * = getBlockPressure(w, x, y - dy, z); System.out.println("*foo*: " + foo);
   * 
   * if (dy != 1 || infiniteSource) FysiksFun.scheduleBlockTick(w, this, x, y - dy, z, liquidUpdateRate); if
   * (!infiniteSource) { // Set block here as content of old block, schedule a GAS tick, notify // neighbouring liquids,
   * update client origChunk.setBlockIDWithMetadata(x & 15, y, z & 15, swappedId, swappedMeta);
   * ChunkTempData.setTempData(w, x, y, z, swappedTemp); if (isSameLiquid(swappedId)) {
   * FysiksFun.logger.log(Level.SEVERE, "Swapping gases with something that is not a gas"); } notifyFeeders(w,
   * origChunk, x, y, z, 0, liquidUpdateRate, pressurizedLiquidUpdateRate); if (Gases.isGas[swappedId]) {
   * FysiksFun.scheduleBlockTick(w, Block.blocksList[swappedId], x, y, z, 1, "[Gas swapped with liquid]"); }
   * ChunkMarkUpdater.scheduleBlockMark(w, x, y, z); }
   */

  /* Leak through dirt cavities roof */
  /*
   * if (canSeepThrough && FysiksFun.settings.erosionRate > 0 && newLiquidContent >= 1 && (blockBelowId ==
   * Block.dirt.blockID || blockBelowId == Block.sand.blockID || blockBelowId == Block.gravel.blockID)) { for (dy = 2;
   * dy < 5 && dy < y; dy++) { int blockId2 = origChunk.getBlockID(x & 15, y - dy, z & 15); if (blockId2 == 0) {
   * newLiquidContent = newLiquidContent - 1; setBlockContent(w, origChunk, x, y - 2, z, 1, "[Through roof]");
   * FysiksFun.scheduleBlockTick(w, this, x, y - 2, z, liquidUpdateRate); if (FysiksFun.rand.nextInt(1 + 100 /
   * FysiksFun.settings.erosionRate) == 0) { FysiksFun.setBlockWithMetadataAndPriority(w, x, y - 1, z, 0, 0, 0); // See
   * where the new dirt block can fall int dy2; for (dy2 = 0; dy2 < 64; dy2++) { int tmpId = origChunk.getBlockID(x &
   * 15, y - dy - dy2 - 1, z & 15); if (tmpId != 0 && tmpId != stillID && tmpId != movingID) break; }
   * FysiksFun.setBlockWithMetadataAndPriority(w, x, y - dy - dy2, z, blockBelowId, 0, 0); Counters.erosionCounter++; }
   * break; } else if (blockId2 != Block.dirt.blockID && blockId2 != Block.sand.blockID && blockId2 !=
   * Block.gravel.blockID) break; } }
   */
  /* Interact with liquid below us */
  /*
   * if (FysiksFun.liquidsCanInteract(this.blockID, blockBelowId)) { newLiquidContent = FysiksFun.liquidInteract(w, x, y
   * - 1, z, this.blockID, newLiquidContent, blockBelowId, getBlockContent(w, x, y - 1, z)); }
   * 
   * return newLiquidContent;
   */

  /** True if the given blockId matches our liquid type (either still or moving) */
  public boolean isSameLiquid(int blockId) {
    return blockId == stillID || blockId == movingID;
  }

  private boolean canErode(World w, int x, int y, int z) {
    int idHere = w.getBlockId(x, y, z);
    if (idHere != Block.dirt.blockID && idHere != Block.grass.blockID && idHere != Block.sand.blockID && idHere != Block.cobblestone.blockID
        && idHere != Block.gravel.blockID) return false;
    // Gravel is _slightly_ more efficient than dirt against erosion
    if (idHere == Block.gravel.blockID && FysiksFun.rand.nextInt(2) != 0) return false;
    // Cobblestone MAY erode, but not very likely
    if (idHere == Block.cobblestone.blockID && FysiksFun.rand.nextInt(10) != 0) return false;
    /*
     * If this block is adjacent to a tree (in any direction) then refuse to erode it
     */
    if (w.getBlockId(x, y + 1, z) == Block.wood.blockID) return false;
    for (int dir = 0; dir < 4; dir++) {
      int dx = Util.dirToDx(dir), dz = Util.dirToDz(dir);
      if (w.getBlockId(x + dx, y + 1, z + dz) == Block.wood.blockID && w.getBlockId(x + dx, y + 2, z + dz) == Block.wood.blockID) return false;
    }

    return true;
  }

  private boolean maybeErode(World w, int x, int y, int z) {

    /*
     * Perform a random walk and see if we can walk into any water cell that has free (liquid) below it
     */
    int idHere = w.getBlockId(x, y, z);
    if (idHere != Block.grass.blockID && idHere != Block.sand.blockID) return false;
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
          if (idMM == stillID || idMM == movingID) break;
        }
        if (dirOffset == 4) break;
        x2 += dX;
        z2 += dZ;
        if (x2 == x && z2 == z) continue;
        int idBelowMM = w.getBlockId(x2, y - 1, z2);
        if (idBelowMM == stillID || idBelowMM == movingID || idBelowMM == 0) {
          /* We have found a place to deposit this block! */
          /* See if we can move it further downwards */
          int dY;
          for (dY = 1; dY < 256; dY++) {
            int id2 = w.getBlockId(x2, y - dY - 1, z2);
            if (id2 != stillID && id2 != movingID && id2 != 0) break;
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
      if (id != 0 && id != stillID && id != movingID) break;
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
   * Lets the block know when one of its neighbor changes. Doesn't know which neighbor changed (coordinates passed are
   * their own) Args: x, y, z, neighbor blockID
   */
  public void onNeighborBlockChange(World w, int x, int y, int z, int someId) {
    // Don't trigger any extra block updates
  }

  public void onBlockAdded(World w, int x, int y, int z) {
    // Override superclass so that checkOnHarden is not called when the block is
    // added. We want to do our own liquid update calculations.
    // ChunkTempData tempData = ChunkTempData.getChunk(w, x, y, z);
    // tempData.liquidHistogram(y,+1);

    // System.out.println("A fluid was added! @"+Util.xyzString(x,y,z)+" chunk is:"+(x>>4)+","+(z>>4));
  }

  /** Called when this block is OVERWRITTEN by another world.setBlock */
  @Override
  public void breakBlock(World w, int x, int y, int z, int oldId, int oldMetaData) {

    // ChunkTempData tempData = ChunkTempData.getChunk(w, x, y, z);
    // tempData.liquidHistogram(y,+1);

    if (w != null) return;

    int x2, y2, z2;

    if (preventSetBlockLiquidFlowover) {
      // ChunkTempData.setTempData(w, x, y, z, 0);
      return;
    }

    try {
      preventSetBlockLiquidFlowover = true;

      IChunkProvider chunkProvider = w.getChunkProvider();
      if (!chunkProvider.chunkExists(x >> 4, z >> 4)) return;
      Chunk chunk = chunkProvider.provideChunk(x >> 4, z >> 4);

      int newIdHere = chunk.getBlockID(x & 15, y, z & 15);
      if (newIdHere == 0 || isSameLiquid(newIdHere)) return;
      // Overwriting AIR into a block means that the block should be deleted...

      int thisContent = getBlockContent(w, x, y, z, oldMetaData);
      if (thisContent > maximumContent) thisContent = maximumContent;

      for (int dy = 1; dy < 50 && y + dy < 255 && thisContent > 0; dy++) {
        int id = w.getBlockId(x, y + dy, z);
        if (id == stillID) continue;
        else if (id == 0 || id == movingID) {
          int newContent = Math.min(maximumContent, getBlockContent(w, x, y, z));
          if (id == 0) {
            newContent = thisContent;
            thisContent = 0;
          } else {
            newContent += thisContent;
            if (newContent > maximumContent) {
              thisContent = newContent - maximumContent;
              newContent = maximumContent;
            } else thisContent = 0;
          }

          ChunkTempData tempData = ChunkTempData.getChunk(w, x, y, z);
          setBlockContent(w, chunk, tempData, x, y + dy, z, newContent, "[From displaced block]", null);

          if (thisContent == 0) break;
        } else break;
      }
    } finally {
      preventSetBlockLiquidFlowover = false;

      // Remove any temp data. Note that other blocks should not add new temp
      // data until _after_ they have written their ID into the cell. */
      // ChunkTempData.setTempData(w, x, y, z, 0);
    }
  }

  @Override
  public int getRenderType() {
    if (superWrapper != null) return superWrapper.getRenderType();
    else return super.getRenderType();
  }

  @Override
  @SideOnly(Side.CLIENT)
  public int getRenderBlockPass() {
    if (superWrapper != null) return superWrapper.getRenderBlockPass();
    else return super.getRenderBlockPass();
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void randomDisplayTick(World par1World, int par2, int par3, int par4, Random par5Random) {
    if (superWrapper != null) superWrapper.randomDisplayTick(par1World, par2, par3, par4, par5Random);
    else super.randomDisplayTick(par1World, par2, par3, par4, par5Random);
  }

  @Override
  public void velocityToAddToEntity(World w, int x, int y, int z, Entity entity, Vec3 velocity) {
    Vec3 vec = this.getFFFlowVector(w, x, y, z);
    Vec3 vec2;
    // Let the direction of flow also be affected by streams of water one step
    // below us
    if (isSameLiquid(w.getBlockId(x, y - 1, z))) {
      vec2 = this.getFFFlowVector(w, x, y - 1, z);
      vec.xCoord += vec2.xCoord;
      vec.yCoord += vec2.yCoord;
    }
    // Let the direction of flow also be affected by streams of water above us
    for (int dy = 1; dy < 3; dy++) {
      if (!isSameLiquid(w.getBlockId(x, y + dy, z))) break;
      vec2 = this.getFFFlowVector(w, x, y + dy, z);
      vec.xCoord += vec2.xCoord;
      vec.yCoord += vec2.yCoord;
    }

    if (vec.lengthVector() > 0.0D && entity.isPushedByWater()) {
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
     * If the liquid can fall straight down, return a vector straight down at "full" strength
     */
    int idBelow = w.getBlockId(x, y - 1, z);
    int contentHere = getBlockContent(w, x, y, z);

    if (idBelow == 0 || idBelow == movingID || Gases.isGas[idBelow]) {
      int belowContent = (idBelow == movingID) ? getBlockContent(w, x, y - 1, z) : 0;
      if (contentHere < belowContent) myvec.yCoord = 0.0;
      else myvec.yCoord = -2.0 * (contentHere - belowContent) / (1.d * BlockFluid.maximumContent);

      myvec.xCoord = 0.0;
      myvec.zCoord = 0.0;
      return myvec;
    }

    /*
     * Otherwise, see how much can flow into neighbours and sum the level differences as strengths
     */

    for (int dir = 0; dir < 4; dir++) {
      int dx = Util.dirToDx(dir);
      int dz = Util.dirToDz(dir);
      int id2 = w.getBlockId(x + dx, y, z + dz);
      if (id2 == 0 || id2 == stillID || id2 == movingID) {
        int content2 = id2 == 0 ? 0 : getBlockContent(w, x + dx, y, z + dz);
        if (Math.abs(contentHere - content2) > 1) {
          int delta = contentHere - content2;
          if (Math.abs(delta) < BlockFluid.maximumContent / 4) delta = 0;
          myvec.xCoord += 0.3d * ((double) dx * delta) * contentHere / (1.d * BlockFluid.maximumContent * BlockFluid.maximumContent);
          myvec.zCoord += 0.3d * ((double) dz * delta) * contentHere / (1.d * BlockFluid.maximumContent * BlockFluid.maximumContent);
        }
      }
    }
    return myvec;
  }

  /* Removes a part of the liquid through evaporation and adds gas instead */
  public void evaporate(World w, Chunk c, int x, int y, int z, int amount) {
    int content = getBlockContent(w, x, y, z);
    content = Math.max(0, content - amount);
    setBlockContent(w, x, y, z, content);
    if (this == Fluids.stillWater || this == Fluids.flowingWater) {
      /* Attempt to create steam from this */
      Gases.steam.produceSteam(w, c, x, y, z, amount / (maximumContent / 16));
    }
  }

  /* Removes a part of the liquid through consumption */
  public void consume(World w, Chunk chunk, int x, int y, int z, int amount) {
    int content = getBlockContent(w, x, y, z);
    content = Math.max(0, content - amount);
    setBlockContent(w, x, y, z, content);
  }

  @Override
  public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
    double dx = explosion.explosionX - x - 0.5d;
    double dy = explosion.explosionX - y - 0.5d;
    double dz = explosion.explosionX - z - 0.5d;
    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
    dx /= len;
    dy /= len;
    dz /= len;
    int maxSteps = (int) (explosion.explosionSize * 5.0);

    Chunk c0 = ChunkCache.getChunk(world, x >> 4, z >> 4, true);
    ChunkTempData tempData0 = ChunkCache.getTempData(world, x >> 4, z >> 4);
    int content = getBlockContent(c0, tempData0, x, y, z);

    /* Red-neck fishing */
    LinkedList allEntities = new LinkedList();
    allEntities.addAll(world.loadedEntityList);
    for (Object o : allEntities) {
      if (o instanceof Entity) {
        Entity e = (Entity) o;
        if (e instanceof EntityLiving) {
          EntityLiving living = (EntityLiving) e;
          int xe = (int) e.posX;
          int ye = (int) e.posY;
          int ze = (int) e.posZ;

          double dex = xe - x;
          double dey = ye - y;
          double dez = ze - z;
          if (dex * dex + dey * dey + dez * dez < 5.0 * 5.0) {
            Chunk c1 = ChunkCache.getChunk(world, xe >> 4, ze >> 4, false);
            if (c1 == null) continue;
            int id = c1.getBlockID(xe & 15, ye, ze & 15);
            if (isSameLiquid(id)) {
              living.attackEntityFrom(DamageSource.inWall, 4);
              System.out.println("Redneck fishing afflicted " + living);
            }
          }
        }
      }
    }

    for (int step = 0; step < maxSteps; step++) {
      int x1 = x + (int) (0.5 + dx * step);
      int y1 = y + (int) (0.5 + dy * step);
      int z1 = z + (int) (0.5 + dz * step);
      if (y1 < 0 || y1 > 255) break;
      Chunk c1 = ChunkCache.getChunk(world, x1 >> 4, z1 >> 4, false);
      ChunkTempData tempData1 = ChunkCache.getTempData(world, x1 >> 4, z1 >> 4);
      if (c1 == null) break;
      int id = c1.getBlockID(x1 & 15, y1, z1 & 15);
      if (id == 0) {
        setBlockContent(world, x1, y1, z1, content);
        setBlockContent(world, x, y, z, 0);
        break;
      } else if (isSameLiquid(id)) continue;
      else break;
    }
  }

}
