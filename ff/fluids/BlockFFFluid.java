package mbrx.ff.fluids;

import java.security.Provider;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mbrx.ff.FysiksFun;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.ChunkMarkUpdateTask;
import mbrx.ff.util.ChunkMarkUpdater;
import mbrx.ff.util.ChunkTempData;
import mbrx.ff.util.Counters;
import mbrx.ff.util.ObjectPool;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFlowing;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityWaterMob;
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
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.IPlantable;

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

public class BlockFFFluid extends BlockFlowing {
  public int                  stillID, movingID;

  public static boolean       preventSetBlockLiquidFlowover = false;
  public Block                superWrapper;
  // Only the registerIcons function is wrapped to this block
  public Block                replacedBlock;
  public boolean              canSeepThrough;
  public boolean              canCauseErosion;
  /** True for fluids that can sustain and spread a fire */
  public boolean              canBurn;
  /**
   * The strength of explosions when this fluid explodes. Set to zero if the
   * block should not be able to explode. Non-burnable blocks can only explode
   * if this value is true AND if they do a direct liquid interaction with Lava
   */
  public float          explodeStrength;
  /**
   * If liquid is burnable and atleast this number of neighbours in a 5x5 area
   * above is fire, then it will explode
   */
  public int                  burnNeighboursForExplosion;
  /**
   * Amount of fluids (cf. BlockFFFluid.maximumContent) that is consumed each
   * time a fire is noticed on this block
   */
  public int                  burnConsumeRate;

  public int                  liquidUpdateRate;
  public int                  erodeMultiplier;
  /**
   * Relative weight of this fluid as compared to water. Water has value 0,
   * heavier fluids positive values. Lighter fluids negative values.
   */
  public int                  relativeWeight;
  /**
   * Determines the minimum amount of fluid to leave in a cell when flowing
   * (sideways) out of or into an empty cell. Use fractions of
   * BlockFFFluid.maximumContent
   */
  public int                  viscosity;
  
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
   */

  public final static int     pressureMaximum               = 65535;
  public final static int     pressureFullLiquid            = 4096;
  public final static int     minimumLiquidLevel            = pressureFullLiquid / 8;
  public final static int     maximumContent                = pressureFullLiquid;
  public final static int     minimumLiquidToDestroyPlants  = pressureFullLiquid / 2;
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

  public BlockFFFluid(Block superWrapper, int id, Material par2Material, int stillID, int movingID, String n) {
    super(id, par2Material);
    name = n;
    this.superWrapper = superWrapper;
    liquidUpdateRate = 1;
    erodeMultiplier = 1;
    this.stillID = stillID;
    this.movingID = movingID;
    this.canSeepThrough = false;
    this.canCauseErosion = false;
    this.setTickRandomly(false);
    relativeWeight = 0;
    viscosity = maximumContent / 8;
    this.canBurn = false;
    this.burnConsumeRate = 0;
    this.burnNeighboursForExplosion = 26;
    this.explodeStrength=0;
    setUnlocalizedName(n);
  }

  public BlockFFFluid(int id, Material par2Material, int stillID, int movingID, String n, Block replacedBlock) {
    super(id, par2Material);
    this.superWrapper = null;
    liquidUpdateRate = 1; // Default tickrate for water
    erodeMultiplier = 1;
    this.stillID = stillID;
    this.movingID = movingID;
    this.canSeepThrough = false;
    this.canCauseErosion = false;
    this.name = n;
    this.replacedBlock = replacedBlock;
    relativeWeight = 0;
    viscosity = maximumContent / 8;
    this.canBurn = false;
    this.burnConsumeRate = 0;
    this.burnNeighboursForExplosion = 26;
    this.explodeStrength=0;
    setUnlocalizedName(n);
  }

  public void setErodeMultiplier(int v) {
    erodeMultiplier = v;
  }

  @SideOnly(Side.CLIENT)
  /**
   * When this method is called, your block should register all the icons it needs with the given IconRegister. This
   * is the only chance you get to register icons.
   */
  public void registerIcons(IconRegister par1IconRegister) {
    replacedBlock.registerIcons(par1IconRegister);
    if (superWrapper != null) {
      // System.out.println("*** Registering icons for my superWrapper");
      superWrapper.registerIcons(par1IconRegister);
      blockIcon = superWrapper.getIcon(0, 0);
    } else {
      super.registerIcons(par1IconRegister);
    }

  }

  @Override
  public Icon getBlockTexture(IBlockAccess par1IBlockAccess, int par2, int par3, int par4, int par5) {
    System.out.println("getBlockTexture " + name);

    if (name.equals("blockOil")) System.out.println("getting texture!");

    if (superWrapper != null) {
      System.out.println("getting texture from my superwrapper");
      return superWrapper.getBlockTexture(par1IBlockAccess, par2, par3, par4, par5);
    } else return super.getBlockTexture(par1IBlockAccess, par2, par3, par4, par5);
  }

  @Override
  public Icon getIcon(int par1, int par2) {
    if (name.equals("blockOil")) System.out.println("getting Icon!");
    if (superWrapper != null) {
      System.out.println("*** Getting icon from my superWrapper");
      return superWrapper.getIcon(par1, par2);
    } else return super.getIcon(par1, par2);
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
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    return getBlockContent(c, ChunkCache.getTempData(w, x >> 4, z >> 4), x, y, z);
  }

  public int getBlockContent(World w, int x, int y, int z, int oldMetaData) {
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    ChunkTempData tempData = ChunkCache.getTempData(w, x >> 4, z >> 4);
    return tempData.getTempData16(x, y, z);
  }

  public int getBlockContent(World w, Chunk c, int x, int y, int z) {
    return getBlockContent(c, ChunkCache.getTempData(w, x >> 4, z >> 4), x, y, z);
  }

  @Override
  public boolean isFireSource(World world, int x, int y, int z, int metadata, ForgeDirection side) {
    return canBurn;
  }

  /**
   * Override how this work, normally liquids never have a solid top surface...
   * BUT if we where called by the native fire handling mechanism then we should
   * pretend that we do... so that we can burn
   */
  @Override
  public boolean isBlockSolidOnSide(World world, int x, int y, int z, ForgeDirection side) {
    /* We inspect the stack to see if we have been called indirectly by any of the fire handling routines. If so pretend to have a solid top surface so that the fire can stay on this block.
     * Note the special circumstances that forces us to do this stack based hack, it would make most software engineers cry - but works well for our purpose (not modifying any base-classes).  
     */
    if (!canBurn) return false;
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    if (stackTraceElements.length < 7) return false;
    StackTraceElement caller = stackTraceElements[6];
    if (caller.getClassName().equals(Block.fire.getClass().getName())) {
      return true;
    }

    if (stackTraceElements.length < 7) return false;
    StackTraceElement caller8 = stackTraceElements[8];
    if (caller8.getClassName().equals(Block.fire.getClass().getName())) {
      return true;
    }
    return false;

  }

  /**
   * Gets the fluid content in this cell. XYZ expressed in world coordinates
   * (ie. >16 allowed)
   */
  public int getBlockContent(Chunk chunk, ChunkTempData tempData, int x, int y, int z) {
    int temp = tempData.getTempData16(x, y, z);
    if (temp == 0) {
      // if(((x+y+z) % 500) == 0)
      // System.out.println("Reconstructing hires water at: "+Util.xyzString(x,y,z));
      // Reconstruct content from lores data
      int meta = chunk.getBlockMetadata(x & 15, y, z & 15);
      int content = (8 - meta) * (maximumContent / 7); // Calculation was wrong by 1/8, Meta 7 -> Max content
      tempData.setTempData(x, y, z, content);
      return content;
    } else return temp;
  }

  public void setBlockContent(World w, int x, int y, int z, int content) {
    Chunk chunk = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    ChunkTempData tempData = ChunkCache.getTempData(w, x >> 4, z >> 4);
    setBlockContent(w, chunk, tempData, x, y, z, content, "", null);
  }

  /**
   * Sets the content/pressure level of the block to the given amount
   */

  public void setBlockContent(World w, Chunk c, ChunkTempData tempData, int x, int y, int z, int content, String explanation,
      Set<ChunkMarkUpdateTask> delayedBlockMarkSet) {

    // System.out.println("Set "+Util.xyzString(x,y,z)+" to "+content+" "+explanation);
    ExtendedBlockStorage blockStorage[] = c.getBlockStorageArray();
    ExtendedBlockStorage ebs = blockStorage[y >> 4];
    int oldId, oldActualMetadata;
    if (ebs != null) {
      oldId = ebs.getExtBlockID(x & 15, y & 15, z & 15);
      oldActualMetadata = ebs.getExtBlockMetadata(x & 15, y & 15, z & 15);
    } else {
      oldId = 0;
      oldActualMetadata = 0;
    }
    int newId = (content == 0 ? 0 : (content < maximumContent ? movingID : stillID));
    int oldContent = (isSameLiquid(oldId) ? getBlockContent(c, tempData, x, y, z) : 0);

    int oldMetaData = oldContent < maximumContent ? 8 - oldContent / (maximumContent / 8) : 0;
    int newMetaData = content < maximumContent ? 8 - content / (maximumContent / 8) : 0;
    if (oldMetaData > 7) oldMetaData = 7;
    if (newMetaData > 7) newMetaData = 7;

    Counters.fluidSetContent++;
    if (newId != oldId || newMetaData != oldMetaData) {
      if(newId != oldId)  ebs = null; // Use slow method only if the ID has changed, fast method can be used for meta data only changes
      if (ebs != null) {

        // if(y > c.heightMap[(x&15)+(z&15)*16]) c.generateSkylightMap();
        // c.updateSkylightColumns[(x&15)+(z&15)* 16] = true;
        // TODO: potential bug if we overwrite an existing block since it's
        // TileEntity might not be cleaned up
        ebs.setExtBlockID(x & 15, y & 15, z & 15, newId);
        ebs.setExtBlockMetadata(x & 15, y & 15, z & 15, newMetaData);
      } else {
        synchronized (FysiksFun.vanillaMutex) {
          c.setBlockIDWithMetadata(x & 15, y, z & 15, newId, newMetaData);
        }
        // oldMetaData = newMetaData;
      }
    }

    // if (newMetaData != oldMetaData || newId != oldId)
    // c.setBlockIDWithMetadata(x & 15, y, z & 15, newId, newMetaData);

    /*
     * The following is lost by not calling the "proper" setBlockIDWithMetaData:
     * 
     * precipationHeighmap is not updated not calling breakBlock /
     * preBlockDestroy on any blocks not calling any blockTileEntities that was
     * on this block not scheduling a recalculation of the skylightmap not
     * scheduling (?) the relightBlock not creating a tileentity (duh!) not
     * calling onAdded not setting the isModified flag on the chunk
     */

    tempData.setTempData16(x, y, z, content);

    if (oldId != newId || oldMetaData != newMetaData) {
      if (delayedBlockMarkSet == null) ChunkMarkUpdater.scheduleBlockMark(w, x, y, z, oldId, oldActualMetadata);
      else {
        ChunkMarkUpdateTask task = ObjectPool.poolChunkMarkUpdateTask.getObject();
        task.set(w, x, y, z, oldId, oldActualMetadata);
        delayedBlockMarkSet.add(task);
        // delayedBlockMarkSet.add(new ChunkMarkUpdateTask(w, x, y, z, oldId,
        // oldActualMetadata));
      }

    }
  }

  /**
   * Assumption - we cannot trust that the block instance we are called as is
   * the actual block that is present at the given coordinates (MC Vanilla
   * guarantees it, but our custom scheduling system does not)
   */
  @Override
  public void updateTick(World w, int x, int y, int z, Random r) {
    IChunkProvider chunkProvider = w.getChunkProvider();
    if (!chunkProvider.chunkExists(x >> 4, z >> 4)) return;
    ChunkTempData tempData0 = ChunkTempData.getChunk(w, x, y, z);
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, true);
    updateTickSafe(w, c, tempData0, x, y, z, r, null);
  }

  /** Called only when we KNOW that the original chunk is loaded */
  public void updateTickSafe(World world, Chunk chunk0, ChunkTempData tempData0, int x0, int y0, int z0, Random r, 
      Set<ChunkMarkUpdateTask> delayedBlockMarkSet) {
    Counters.fluidUpdates++;
    // if (sweep % liquidUpdateRate != 0) return;
    boolean moveNormally = (r.nextInt(liquidUpdateRate) == 0);

    int chunkX0 = x0 >> 4, chunkZ0 = z0 >> 4;

    int id0 = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
    // if (!isSameLiquid(id0)) return; Impossible to not be true, since we the
    // 'safe' function
        
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

      // System.out.println("Considering "+Util.xyzString(x0, y0, z0));

      int content0 = getBlockContent(chunk0, tempData0, x0, y0, z0);
      int oldContent0 = content0;

      final int directions[][] = { { -1, -1 }, { 0, -1 }, { 1, -1 }, { -1, 0 }, { 1, 0 }, { -1, +1 }, { 0, +1 }, { 1, +1 } };

      /* Special test for free falling water "small" amounts of water */
      /*if (content0 <= maximumContent / 8) {
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
      }*/

      // System.out.println(""+Util.xyzString(x0,y0,z0)+" initial content/pressure: "+content0);
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

          int id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
          if (!Fluids.isLiquid[id1]) continue;
          ChunkTempData tempData1 = ChunkCache.getTempData(world, x1 >> 4, z1 >> 4);

          int content1 = getBlockContent(chunk1, tempData1, x1, y1, z1);
          //if (content1 < maximumContent) continue;
          int pressure1 = content1 + dY * pressurePerY - pressureLossPerStep;
          if (isDiagonal) pressure1 -= pressureLossPerStep / 2;
          if (pressure1 > content0) content0 = pressure1;
        }
        // System.out.println(""+Util.xyzString(x0,y0,z0)+" after pressure check: "+content0);
      }

      /* Move liquids */
      /*
       * Iterate over all neighbours and check if there is a significant enough
       * pressure difference to move fluids. If so, perform the moves and
       * schedule new ticks.
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
        
        int x1 = x0 + dX;
        int y1 = y0 + dY;
        int z1 = z0 + dZ;
        if (y1 < 0) {
          // Flow out of the world
          System.out.println("Something is flowing out of the world....");
          content0 = 0;
          continue;
        } else if (y1 >= 256) continue;

        Chunk chunk1 = ChunkCache.getChunk(world, x1 >> 4, z1 >> 4, false);
        if (chunk1 == null) continue;

        int id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
        int content1 = 0;
                
        /*
         * Check if this is a block we can flow over and if we have enough
         * liquid left.
         */
        if (id1 != 0 && !Fluids.isLiquid[id1]) {
          if (Gases.isGas[id1]) continue;
          if (Block.blocksList[id1] == null) continue;
          Material m = Block.blocksList[id1].blockMaterial;
          if (!m.blocksMovement() && (dY == -1 || (content0 > minimumLiquidLevel && !Fluids.canFlowThrough[id1]))) {
            // Flow over this block, dropping the contents
            int metaData1 = chunk1.getBlockMetadata(x1 & 15, y1, z1 & 15);
            if (id1 == Block.fire.blockID && canBurn) {
              if (dY == -1) {
                setBlockContent(world, chunk0, tempData0, x0, y0 - 1, z0, content0, "", delayedBlockMarkSet);
                content0 = 0;
                FysiksFun.setBlockIDandMetadata(world, chunk0, x0, y0, z0, Block.fire.blockID, 0, -1, -1, delayedBlockMarkSet);
                return; // Important not to continue since we otherwise
                        // overwrite our old cell at x0,y0,z0 that is now fire
              } else {
                int idAbove = chunk0.getBlockID(x0 & 15, y0 + 1, z0 & 15);
                if (idAbove == 0) FysiksFun.setBlockIDandMetadata(world, chunk0, x0, y0 + 1, z0, Block.fire.blockID, 0, -1, -1, delayedBlockMarkSet);
              }
              continue;
            }
            if (id1 != Block.snow.blockID && id1 != Block.tallGrass.blockID) Block.blocksList[id1].dropBlockAsItem(world, x1, y1, z1, metaData1, 0);
            synchronized (FysiksFun.vanillaMutex) {
              FysiksFun.setBlockIDandMetadata(world, chunk1, x1, y1, z1, 0, 0, -1, -1, delayedBlockMarkSet);
            }
            id1 = 0;
            content1 = 0;
          } else if (dY == 0 && Fluids.canFlowThrough[id1]) {
            if (Block.blocksList[id1] instanceof BlockDoor) {
              int meta1 = chunk1.getBlockMetadata(x1 & 15, y1, z1 & 15);
              boolean doorState;
              if ((meta1 & 1) != 0) doorState = (meta1 & 4) != 0;
              else doorState = (meta1 & 4) == 0;
              if ((dX == 0 && !doorState) || (dX != 0 && doorState)) continue;
            }

            x1 = x0 + 2 * dX;
            z1 = z0 + 2 * dZ;
            chunk1 = ChunkCache.getChunk(world, x1 >> 4, z1 >> 4, false);
            if (chunk1 == null) continue;
            id1 = chunk1.getBlockID(x1 & 15, y1, z1 & 15);
            if (id1 != 0 && !Fluids.isLiquid[id1]) continue;
          } else continue;
        }

        // Get new tempData and get the content of this neighbour
        ChunkTempData tempData1 = ChunkCache.getTempData(world, x1 >> 4, z1 >> 4);
        if (id1 != 0) {
          content1 = getBlockContent(chunk1, tempData1, x1, y1, z1);
        }
        
        // Check for special interactions
        if (id1 != 0 && !isSameLiquid(id1)) {
          // Allow fluid interactions, but only sideways and down; or up when pressurized
          if (Fluids.liquidCanInteract(movingID, id1) && (dY < 1 || content0 > maximumContent)) {
            content0 = Fluids.liquidInteract(world, x1, y1, z1, movingID, content0, id1, content1);
          } else {
            /*if(content1 >= maximumContent && dY == -1) {
              // The liquid block below us is a full block. So we either swap positions or do nothing
              if(Fluids.asFluid[id1].relativeWeight < this.relativeWeight) {
                Fluids.asFluid[id1].setBlockContent(world, chunk0, tempData0, x0, y0, z0, content1, "swapping fluids", delayedBlockMarkSet);
                this.setBlockContent(world, chunk1, tempData1, x1, y1, z1, content0, "swapping fluids", delayedBlockMarkSet);
                return;
              }
            }*/
            /* not giving good results... maybe later
            else if(content1 < maximumContent && content0 > content1 + maximumContent/4) {
              int toMove = (content0 - content1)/2;
              content0 = content0 - toMove;
              // See if the target cell becomes one of our types of fluids... 
              int tot=toMove + content1;
              if(FysiksFun.rand.nextInt(tot+tot/4) < toMove) {
                this.setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1+toMove, "converting fluids", delayedBlockMarkSet);                 
              } else {
                Fluids.asFluid[id1].setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1+toMove, "converting fluids", delayedBlockMarkSet);
              }
              continue;
            } else if(content0 < maximumContent && content1 > content0 + maximumContent/4) {
              int toMove = (content1 - content0)/2;
              // See if we will become of the target cells type 
              int tot=toMove + content1;
              if(FysiksFun.rand.nextInt(tot+tot/4) < toMove) {
                Fluids.asFluid[id1].setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1-toMove, "converting fluids", delayedBlockMarkSet);                 
                Fluids.asFluid[id1].setBlockContent(world, chunk0, tempData0, x0, y0, z0, content0+toMove, "converting fluids", delayedBlockMarkSet);
              } else {
                Fluids.asFluid[id1].setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1-toMove, "converting fluids", delayedBlockMarkSet);                 
                this.setBlockContent(world, chunk0, tempData0, x0, y0, z0, content0+toMove, "converting fluids", delayedBlockMarkSet);
              }
              return;              
            }            
            */
          } 
          continue;
        }

        int prevContent0 = content0;
        if (dY < 0) {
          if (content1 < maximumContent) {
            // Move liquid downwards
            content0 = Math.min(maximumContent, content0);
            int toMove = Math.min(content0, maximumContent-content1);
            content0 -= toMove;
            content1 += toMove;
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
            // Note that we are intentionally moving slightly more than what is
            // needed to given an equilibrium. This makes liquids have a
            // slightly lower inclination when flowing large distances. */
            // int toMove = Math.min(((content0 - content1) * 3) / 4,
            // maximumContent - content1);
            int toMove = Math.min(((content0 - content1)) / 2, maximumContent - content1);
            // Lower flow in diagonal directions so that liquids spread out in
            // good approximations of circles
            if (isDiagonal) toMove -= toMove / 3;
            if (content1 + toMove >= viscosity && content0 - toMove >= viscosity) {
              content0 -= toMove;
              content1 += toMove;
              setBlockContent(world, chunk1, tempData1, x1, y1, z1, content1, "[Flowing sideways]", delayedBlockMarkSet);
            } else if (id1 == 0) {
              // Special case to make sure we can move the last drop of liquid over an edge
              int id1b = chunk1.getBlockID(x1 & 15, y1 - 1, z1 & 15);
              int content1b = 0;
              ChunkTempData tempData1b = tempData1;
              if (id1b == 0 || isSameLiquid(id1b)) {
                content1b = (id1b == 0 ? 0 : getBlockContent(chunk1, tempData1b, x1, y1 - 1, z1));
                if(maximumContent-content1b > content0) {
                  // There is space for these water drops here
                  content1b += content0;
                  content0 = 0;
                  // System.out.println("Flowing diagonally down... checking for erosion");
                  setBlockContent(world, chunk1, tempData1, x1, y1 - 1, z1, content1b, "[Flowing diagonally down]", delayedBlockMarkSet);
                  // Possibly trigger an erosion event                  
                  int erodeChance = FysiksFun.settings.erosionRate * erodeMultiplier;
                  if (r.nextInt(500) < erodeChance) {
                    int id0b = chunk0.getBlockID(x0 & 15, y0 - 1, z0 & 15);
                    if (canErodeBlock(id0b)) {
                      int cnt = 0;
                      for (int dx0 = -1; dx0 <= 1; dx0++)
                        for (int dz0 = -1; dz0 <= 1; dz0++) {
                          int sideId = world.getBlockId(x0 + dx0, y0 - 1, z0 + dz0);
                          if (sideId != 0 && !Fluids.isLiquid[sideId]) cnt++;
                        }
                      // If two or more blocks are missing at the layer below us, then cause erosion
                      if (cnt <= 7) {
                        // System.out.println("Erosion A: "+x0+" "+(y0-1)+" "+z0);
                        erodeBlock(world, x0, y0 - 1, z0);

                        // If this was a diagonal movement, also remove a straight neighbour                         
                        if (dX != 0 && dZ != 0) {
                          int idA = world.getBlockId(x0 + dX, y0 - 1, z0 + 0);
                          if (idA != 0 && !Fluids.isLiquid[idA]) erodeBlock(world, x0 + dX, y0 - 1, z0 + 0);
                          idA = world.getBlockId(x0 + 0, y0 - 1, z0 + dZ);
                          if (idA != 0 && !Fluids.isLiquid[idA]) erodeBlock(world, x0 + 0, y0 - 1, z0 + dZ);
                        }
                      }
                    }
                  }              
                }
              }
            }
          }
        }
        // We have moved sideways to some extent, see if we should remove any
        // surrounding dirt wall as erosion
        int erodeChance = FysiksFun.settings.erosionRate * erodeMultiplier;
        /* Higher chance to erode so we get rid of diagonal movements */
        // if (dY == 0 && dZ != 0 && dX != 0) erodeChance *= 2;

        /*
        if (content0 != prevContent0 && dY == 0 && r.nextInt(1000000) < erodeChance) {
          // System.out.println("Erode B");
          int idSideA = world.getBlockId(x0 + dZ, y0, z0 + dX);
          if (canErodeBlock(idSideA)) erodeBlock(world, x0 + dZ, y0, z0 + dX);
          int idSideB = world.getBlockId(x0 - dZ, y0, z0 - dX);
          if (canErodeBlock(idSideB)) erodeBlock(world, x0 - dZ, y0, z0 - dX);
          if (dX != 0 && dZ != 0) {
            int idSideC = world.getBlockId(x0 + dX, y0, z0 + 0);
            if (canErodeBlock(idSideC)) erodeBlock(world, x0 + dX, y0, z0 + 0);
            int idSideD = world.getBlockId(x0 + 0, y0, z0 + dZ);
            if (canErodeBlock(idSideD)) erodeBlock(world, x0 + 0, y0, z0 + dZ);
          }
        }
        */
      }

      /*
       * If we have made a pressurized move, make a random walk towards lower
       * pressures until we find a node we can steal liquid from
       */
      boolean pressurizedPull = oldContent0 >= maximumContent;
      if (content0 < maximumContent) {
        int steps;
        int xN = x0, yN = y0, zN = z0;
        int currValue;
        if (pressurizedPull) currValue = maximumContent;
        else currValue = content0;
        Chunk chunkN = chunk0;
        ChunkTempData tempDataN = tempData0;
        Chunk bestChunkM = null;
        ChunkTempData bestTempDataM = null;

        for (steps = 0; steps < 16; steps++) {
          int bestDir = -1;
          int bestValue = 0;
          for (int dir = 0; dir < 6; dir++) {
            int dX = Util.dirToDx(dir);
            int dY = Util.dirToDy(dir);
            int dZ = Util.dirToDz(dir);
            int xM = xN + dX;
            int yM = yN + dY;
            int zM = zN + dZ;
            if (yM < 0 || yM > 255) continue;
            if (!pressurizedPull && dY != 0) continue;

            Chunk chunkM = ChunkCache.getChunk(world, xM >> 4, zM >> 4, false);
            if (chunkM == null) continue;

            int idM = chunkM.getBlockID(xM & 15, yM, zM & 15);
            if (!isSameLiquid(idM)) continue;

            ChunkTempData tempDataM = ChunkCache.getTempData(world, xM >> 4, zM >> 4);
            int contentM = getBlockContent(chunkM, tempDataM, xM, yM, zM);
            int modifiedPressure = contentM + pressurePerY * dY - 2 * pressureLossPerStep;
            if (modifiedPressure > bestValue) {
              bestDir = dir;
              bestValue = modifiedPressure;
              bestChunkM = chunkM;
              bestTempDataM = tempDataM;
            }
          }
          if (bestDir == -1) break;
          if (bestValue < currValue) break;
          xN += Util.dirToDx(bestDir);
          yN += Util.dirToDy(bestDir);
          zN += Util.dirToDz(bestDir);
          chunkN = bestChunkM;
          tempDataN = bestTempDataM;
        }
        if (xN != x0 || yN != y0 || zN != z0) {
          /* Steal as much as possible from N */
          int contentN = getBlockContent(chunkN, tempDataN, xN, yN, zN);
          contentN = Math.min(maximumContent, contentN);
          int toMove;
          if (pressurizedPull) toMove = Math.min(contentN, maximumContent - content0);
          else toMove = Math.min((contentN - content0) / 2, maximumContent - content0);
          contentN -= toMove;
          content0 += toMove;
          setBlockContent(world, chunkN, tempDataN, xN, yN, zN, contentN, "[Propagated pressurized liquid]", delayedBlockMarkSet);
          if (content0 == maximumContent) content0 = oldContent0;
        }
      }

      /* Write our updated content to the world if it has changed */
      if (content0 != oldContent0) {
        if (logExcessively) FysiksFun.logger.log(Level.INFO, Util.logHeader() + "self update, oldContent0: " + oldContent0);
        setBlockContent(world, chunk0, tempData0, x0, y0, z0, content0, "[Final self update]", delayedBlockMarkSet);
      }
    } finally {
      preventSetBlockLiquidFlowover = false;
    }
  }

  private void erodeBlock(World world, int x0, int y0, int z0) {
    int erodedId;
    int x = x0, y = y0, z = z0;
    while (y < 254) {
      int idAbove = world.getBlockId(x, y + 1, z);
      if (!canErodeBlock(idAbove)) {
        erodedId = world.getBlockId(x, y, z);
        FysiksFun.setBlockWithMetadataAndPriority(world, x, y, z, 0, 0, 0);
        break;
      }
      y = y + 1;
    }
  }

  /**
   * Called less regularly to give the fluids a chance to do any expensive tick
   * updates (checking larger chunk areas etc)
   **/
  public void expensiveTick(World world, Chunk chunk0, ChunkTempData tempData0, int x0, int y0, int z0, Random r) {}

  /**
   * Perform a random walk that will eliminate puddles by moving them in the
   * general direction of free areas
   */
  public void updateRandomWalk(World world, Chunk chunk0, ChunkTempData tempData0, int x0, int y0, int z0, Random r) {
    int oldIndent = Util.loggingIndentation;
    int chunkX0 = x0 >> 4, chunkZ0 = z0 >> 4;
    IChunkProvider chunkProvider = world.getChunkProvider();
    int id0 = chunk0.getBlockID(x0 & 15, y0, z0 & 15);
    if (!isSameLiquid(id0)) return;
    int content0 = getBlockContent(chunk0, tempData0, x0, y0, z0);

    // This amount can flow by itself, no need for random walks
    if (content0 > 4 * minimumLiquidLevel) return;

    try {
      preventSetBlockLiquidFlowover = true;
      double bestDx = -1.d, bestDz = -1d;
      int bestDist = 24;
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
          if (y1 - 1 < 0) continue;
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
       * if(bestDist == 1) { // && r.nextInt(10) <
       * FysiksFun.settings.erosionRate) { System.out.println("Erosion B"); int
       * id0b = world.getBlockId(x0, y0-1, z0); if(Block.blocksList[id0b] ==
       * Block.dirt || Block.blocksList[id0b] == Block.sand) {
       * setBlockContent(world, x0, y0-1, z0, 0); } }
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
            setBlockContent(world, chunk0, tempData0, x0, y0, z0, 0, "", null);
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
    if (b instanceof IPlantable) return true;
    if (b instanceof BlockLeaves) return true;
    if (b == Block.dirt || b == Block.grass || b == Block.sand) return true;
    else return false;
  }

  static public boolean isOceanic(World w, int x, int y) {
    BiomeGenBase g = w.getBiomeGenForCoords(x, y);
    if (g == BiomeGenBase.ocean || g == BiomeGenBase.frozenOcean || g == BiomeGenBase.river) return true;
    else return false;
  }

  /* Full blocks of water with nowhere else to go, MAY leak through dirt */
  // Let liquids flow through open doors
  // Note: this may cause us to leak into an unloaded chunk, but since the
  // only effect is a slight efficiency cost
  // I consider this to be too rare to fix

  private boolean canFlowThrough(int blockIdNN, int blockMetaNN) {

    if ((blockIdNN == Block.doorWood.blockID || blockIdNN == Block.doorIron.blockID) && (blockMetaNN & 4) != 0) return true;
    else if (blockIdNN == Block.fence.blockID || blockIdNN == Block.fenceIron.blockID || blockIdNN == Block.fenceGate.blockID) return true;

    return false;
  }

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
     * If this block is adjacent to a tree (in any direction) then refuse to
     * erode it
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
     * Perform a random walk and see if we can walk into any water cell that has
     * free (liquid) below it
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
    // System.out.println("doing erode...");
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
   * Lets the block know when one of its neighbor changes. Doesn't know which
   * neighbor changed (coordinates passed are their own) Args: x, y, z, neighbor
   * blockID
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

    ChunkTempData tempData = ChunkCache.getTempData(w, x >> 4, z >> 4);

    int x2, y2, z2;
    // System.out.println("BlockFluid:breakBlock "+Util.xyzString(x, y,
    // z)+" preventFlowover: "+preventSetBlockLiquidFlowover);

    if (preventSetBlockLiquidFlowover) {
      // ChunkTempData.setTempData(w, x, y, z, 0);
      return;
    }

    try {
      preventSetBlockLiquidFlowover = true;

      Chunk chunk = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
      if (chunk == null) return;

      int newIdHere = chunk.getBlockID(x & 15, y, z & 15);
      // System.out.println("New ID here: "+newIdHere+" old meta: "+oldMetaData);
      if (newIdHere == 0 || isSameLiquid(newIdHere)) return;
      // Overwriting AIR into a block means that the block should be deleted...

      int thisContent = getBlockContent(w, x, y, z, oldMetaData);
      if (thisContent > maximumContent) thisContent = maximumContent;
      // System.out.println("thisContent: "+thisContent);
      for (int dy = 1; dy < 50 && y + dy < 255 && thisContent > 0; dy++) {
        int id = w.getBlockId(x, y + dy, z);
        // System.out.println("dy: "+dy+" id: "+id);
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
          // System.out.println("Setting content at: "+Util.xyzString(x, y+dy,
          // z)+" to "+newContent);
          setBlockContent(w, chunk, tempData, x, y + dy, z, newContent, "[From displaced block]", null);

          if (thisContent == 0) break;
        } else break;
      }
    } finally {
      preventSetBlockLiquidFlowover = false;
      tempData.setTempData(x, y, z, 0);
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
    if (entity instanceof EntityWaterMob) {
      velocity.xCoord = 0;
      velocity.yCoord = 0;
      velocity.zCoord = 0;
      return;
    }
    boolean isItem = entity instanceof EntityItem;
    if (isItem && FysiksFun.rand.nextInt(10) == 0) return;
    float speedMultiplier = 1.0f;
    if (isItem) speedMultiplier = 10.0f;

    Vec3 vec = this.getFFFlowVector(w, x, y, z);
    Vec3 vec2;

    // Let the direction of flow also be affected by streams of water one step
    // below us
    if (!isSameLiquid(w.getBlockId(x, y, z))) {
      FysiksFun.logger.log(Level.SEVERE, "BlockFluid::velocityToAdd called for a block that is not a fluid");
    }
    if (isSameLiquid(w.getBlockId(x, y - 1, z))) {
      vec2 = this.getFFFlowVector(w, x, y - 1, z);
      vec.xCoord += vec2.xCoord * speedMultiplier;
      vec.yCoord += vec2.yCoord * speedMultiplier;
    }
    // Let the direction of flow also be affected by streams of water above us
    /* Disabled for now, it is expensive with these checks due to synchronization issues
    for (int dy = 1; dy < 3; dy++) {
      if (!isSameLiquid(w.getBlockId(x, y + dy, z))) break;
      vec2 = this.getFFFlowVector(w, x, y + dy, z);
      vec.xCoord += vec2.xCoord * speedMultiplier;
      vec.yCoord += vec2.yCoord * speedMultiplier;
    }*/

    if (vec.lengthVector() > 0.0D && entity.isPushedByWater()) {
      double d1 = 0.0025d; // 0.014D;
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
    if(w != null) return myvec; // Disabled forces in water for now.... 
    /*
     * If the liquid can fall straight down, return a vector straight down at
     * "full" strength
     */
    int idBelow = w.getBlockId(x, y - 1, z);
    Chunk c0 = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    ChunkTempData tempData0 = ChunkCache.getTempData(w, x >> 4, z >> 4);
    // ChunkTempData tempData0 = ChunkTempData.getChunk(w, x, z); // sic! for
    // efficiency to avoid delays from server proc.
    int contentHere = getBlockContent(c0, tempData0, x, y, z);

    if (idBelow == 0 || idBelow == movingID || Gases.isGas[idBelow]) {
      int belowContent = (idBelow == movingID) ? getBlockContent(w, x, y - 1, z) : 0;
      if (contentHere < belowContent) myvec.yCoord = 0.0;
      else myvec.yCoord = -2.0 * (contentHere - belowContent) / (1.d * BlockFFFluid.maximumContent);

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
        Chunk c1 = ChunkCache.getChunk(w, (x + dx) >> 4, (z + dz) >> 4, false);
        ChunkTempData tempData1 = ChunkCache.getTempData(w, (x + dx) >> 4, (z + dz) >> 4);
        // ChunkTempData tempData1 = ChunkTempData.getChunk(w, x+dx, z+dz); //
        // sic! for efficiency to avoid delays from server proc.
        if (c1 == null) continue;
        int content2 = id2 == 0 ? 0 : getBlockContent(c1, tempData1, x + dx, y, z + dz);
        if (Math.abs(contentHere - content2) > 1) {
          int delta = contentHere - content2;
          if (Math.abs(delta) < BlockFFFluid.maximumContent / 4) delta = 0;
          myvec.xCoord += 0.10d * ((double) dx * delta) * contentHere / (1.d * BlockFFFluid.maximumContent * BlockFFFluid.maximumContent);
          myvec.zCoord += 0.10d * ((double) dz * delta) * contentHere / (1.d * BlockFFFluid.maximumContent * BlockFFFluid.maximumContent);
        }
      }
    }
    return myvec;
  }

  /* Removes a part of the liquid through evaporation and adds gas instead */
  public void evaporate(World w, Chunk c, int x, int y, int z, int amount, boolean produceSteam) {
    int content = getBlockContent(w, x, y, z);
    amount = Math.min(content, amount);
    content -= amount;
    setBlockContent(w, x, y, z, content);
    if (amount >= maximumContent / 16) {
      if (this == Fluids.stillWater || this == Fluids.flowingWater) {
        /* Attempt to create steam from this */
        if (content == 0) {
          if (produceSteam) Gases.steam.produceSteam(w, c, x, y, z, amount / (maximumContent / 16));
        } else {
          int steamAmount = amount / (maximumContent / 16);
          if (produceSteam) Gases.steam.produceSteam(w, c, x, y + 1, z, steamAmount);
        }
      }
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
        if (e instanceof EntityLivingBase) {
          EntityLivingBase living = (EntityLivingBase) e;
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
              // System.out.println("Redneck fishing afflicted " + living);
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

  public void isBurning(World world, Chunk chunk0, int x0, int y0, int z0) {
    int previousContent = getBlockContent(world, chunk0, x0, y0, z0);
    int newContent = Math.max(previousContent - burnConsumeRate, 0);
    setBlockContent(world, x0, y0, z0, newContent);

    /* Check if there is any neighbours that are burnable and that have an air supply.
     * Not doing this through diagonals is a feature that can be used for safely regulating where fires can spread. 
     */
    if (FysiksFun.rand.nextInt(10) == 0) for (int dir = 0; dir < 4; dir++) {
      int x1 = x0 + Util.dirToDx(dir);
      int z1 = z0 + Util.dirToDz(dir);
      int y1 = y0;
      Chunk c1 = ChunkCache.getChunk(world, x1 >> 4, z1 >> 4, false);
      int id1 = c1.getBlockID(x1 & 15, y1, z1 & 15);
      if (Fluids.isLiquid[id1] && Fluids.asFluid[id1].canBurn) {
        int id11 = c1.getBlockID(x1 & 15, y1 + 1, z1 & 15);
        if (id11 == 0) {
          /* Found a neighbour with an empty space above him, turn him on fire */
          FysiksFun.setBlockIDandMetadata(world, c1, x1, y1 + 1, z1, Block.fire.blockID, 0, -1, -1, null);
        }
      }
    }

    /* See if an explosion is triggered */
    int nfires = 0;
    if(newContent < BlockFFFluid.maximumContent / 4) return; // Small enough quantities will never explode
    if(explodeStrength <= 0) return;
    if (burnNeighboursForExplosion > 25) return;
    if (FysiksFun.rand.nextInt(100) == 0) {
      for (int dz = -2; dz <= 2; dz++)
        for (int dx = -2; dx <= 2; dx++)
          for (int dy = 0; dy <= 2; dy++) {
            Chunk c1 = ChunkCache.getChunk(world, (x0 + dx) >> 4, (z0 + dz) >> 4, false);
            int id1 = c1.getBlockID((x0 + dx) & 15, y0 + dy, (z0 + dz) & 15);
            if (id1 == Block.fire.blockID) nfires++;
          }
      if (nfires > burnNeighboursForExplosion) {
        synchronized (FysiksFun.vanillaMutex) {
          setBlockContent(world, x0, y0, z0, 0);
          float radius = (float) ((explodeStrength * newContent) / (float) BlockFFFluid.maximumContent);

          world.newExplosion(null, x0, y0, z0, radius, true, true);
          world.playSoundEffect(x0 + 0.5, y0 + 0.5, z0 + 0.5, "random.explode", radius / 4.f, 1.0f);
        }
      }
    }

  }
}
