package mbrx.ff.ecology;

import java.util.Iterator;
import java.util.LinkedList;

import mbrx.ff.BlockFFLeaves;
import mbrx.ff.FysiksFun;
import mbrx.ff.fluids.Fluids;
import mbrx.ff.util.ChunkCache;
import mbrx.ff.util.Counters;
import mbrx.ff.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockSapling;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.ForgeDirection;

public class Trees {
  public static LinkedList<Trees> fallingTrees = new LinkedList<Trees>();
  public static LinkedList<Trees> landedTrees  = new LinkedList<Trees>();

  enum TreeCategory {
    NOT_TREE, TRUNK_PART, LEAF_PART
  };

  public static TreeCategory treeCategory[] = new TreeCategory[4096];

  /** Creates the list of which blocks correspond to different parts of trees */
  public static void initTreePartClassification() {
    for (int i = 0; i < 4096; i++) {
      treeCategory[i] = TreeCategory.NOT_TREE;
      Block b = Block.blocksList[i];
      if (i == 0 || b == null) continue;
      if (b instanceof BlockLog) treeCategory[i] = TreeCategory.TRUNK_PART;
      if (b instanceof BlockLeaves || b instanceof BlockFFLeaves) treeCategory[i] = TreeCategory.LEAF_PART;
    }
    int rubWood = Util.findBlockIdFromName("blockRubWood");
    if (rubWood != 0) treeCategory[rubWood] = TreeCategory.TRUNK_PART;
  }

  /**
   * Represents a part of a tree that is stored in a structure while the tree is
   * falling
   */
  public static class TreeBlock {
    int dx, dy, dz, id, meta;

    public TreeBlock(int dx, int dy, int dz, int id, int meta) {
      this.dx = dx;
      this.dy = dy;
      this.dz = dz;
      this.id = id;
      this.meta = meta;
    }

    /*
     * Trees always fall around the Z-axis (for now) so that the order of the
     * loops in which they are placed into the world appears correct.
     */
    public int AngleToDx(double angle, int dir) {
      switch (dir) {
      case 0:
        return (int) Math.round(Math.cos(angle) * dx + Math.sin(angle) * dy);
      case 1:
        return (int) Math.round(Math.cos(-angle) * dx + Math.sin(-angle) * dy);
      case 2:
      case 3:
        return dx;
      }
      return 0;
    }

    public int AngleToDy(double angle, int dir) {
      switch (dir) {
      case 0:
      case 1:
        return (int) Math.round(Math.cos(angle) * dy - Math.sin(angle) * dx);
      case 2:
      case 3:
        return (int) Math.round(Math.cos(angle) * dy - Math.sin(angle) * dz);
      }
      return 0;
    }

    public int AngleToDz(double angle, int dir) {
      switch (dir) {
      case 0:
      case 1:
        return dz;
      case 2:
        return (int) Math.round(Math.cos(angle) * dz + Math.sin(angle) * dy);
      case 3:
        return (int) Math.round(Math.cos(-angle) * dz + Math.sin(-angle) * dy);
      }
      return 0;
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj instanceof TreeBlock) {
        final TreeBlock other = (TreeBlock) obj;
        return dx == other.dx && dy == other.dy && dz == other.dz;
      } else return false;
    }
  };

  public LinkedList<TreeBlock> blocks;
  int                          tickCounter;
  int                          centerX, centerY, centerZ;
  int                          minDx, maxDx, minDz, maxDz, maxDy;
  // A counter that gives an angle for a falling tree. Is updated one per tick
  // until tree reaches stable position.
  int                          fallingAngle;
  World                        w;
  private double               lastAngle;
  private int                  fallingDirection;

  /**
   * Creates and extracts a tree with it's footprint (XZ) center at the given
   * coordinates and footprint bottom at given Y
   */
  public Trees(World w2, int x, int y, int z) {
    this.w = w2;
    lastAngle = 0.0;

    centerX = x;
    centerY = y;
    centerZ = z;

    blocks = new LinkedList<TreeBlock>();
    LinkedList<TreeBlock> blocksToAdd = new LinkedList<TreeBlock>();
    tickCounter = 0;

    IChunkProvider chunkProvider = w.getChunkProvider();
    if (!chunkProvider.chunkExists(x >> 4, z >> 4)) return;
    Chunk c = ChunkCache.getChunk(w2, x>>4, z>>4, false);
    if(c == null) return;

    blocks.add(new TreeBlock(0, 0, 0, c.getBlockID(x & 15, y, z & 15), c.getBlockMetadata(x & 15, y, z & 15)));
    int treeType = w.getBlockMetadata(x, y, z);

    boolean allowFoilage = false;
    int firstLeaf = -1;
    for (int iteration = 0; iteration < 50; iteration++) {
      boolean hasChanged = false;
      for (TreeBlock t : blocks) {
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++)
            for (int dy = 0; dy <= 1; dy++) {
              int dx2 = dx + t.dx;
              int dy2 = dy + t.dy;
              int dz2 = dz + t.dz;

              boolean alreadyContains = false;
              for (TreeBlock t2 : blocks) {
                if (t2.dx == dx2 && t2.dy == dy2 && t2.dz == dz2) {
                  alreadyContains = true;
                  break;
                }
              }
              for (TreeBlock t2 : blocksToAdd) {
                if (t2.dx == dx2 && t2.dy == dy2 && t2.dz == dz2) {
                  alreadyContains = true;
                  break;
                }
              }
              if (alreadyContains) continue;
              int x2 = x + dx2, y2 = y + dy2, z2 = z + dz2;
              c = ChunkCache.getChunk(w,x2>>4,z2>>4,false);
              if(c == null) continue;

              int id2 = c.getBlockID(x2 & 15, y2, z2 & 15);
              int meta2 = c.getBlockMetadata(x2 & 15, y2, z2 & 15);

              if(treeCategory[id2] == TreeCategory.LEAF_PART) {
                if (firstLeaf == -1) firstLeaf = meta2;
                // else if(meta2 != firstLeaf) continue;
              } else if (meta2 != treeType) continue;
              // if ((!allowFoilage && id2 == Block.wood.blockID) ||
              // (allowFoilage && id2 == Block.leaves.blockID))
              if ((!allowFoilage && treeCategory[id2] == TreeCategory.TRUNK_PART) || (allowFoilage && treeCategory[id2] == TreeCategory.LEAF_PART))
                blocksToAdd.add(new TreeBlock(dx2, dy2, dz2, id2, meta2));
            }
      }
      if (blocksToAdd.size() > 0) {
        blocks.addAll(blocksToAdd);
        blocksToAdd.clear();
      } else if (allowFoilage == false) {
        allowFoilage = true;
        iteration = 45;
      } else break;
    }

    minDx = 256;
    maxDx = -256;
    minDz = 256;
    maxDz = -256;
    maxDy = 0;
    for (TreeBlock t : blocks) {
      if (t.dx > maxDx) maxDx = t.dx;
      if (t.dy > maxDy) maxDy = t.dy;
      if (t.dz > maxDz) maxDz = t.dz;
      if (t.dx < minDx) minDx = t.dx;
      if (t.dz < minDz) minDz = t.dz;
    }
  }

  public static void doTick(World w) {
    /* Go through all falling trees and tick them */
    for (Trees t : fallingTrees) {
      t.tickFallingTree();
    }
    fallingTrees.removeAll(landedTrees);
    landedTrees.clear();
  }

  public static void doTrees(World w, int cx, int cz) {
    
    for (int tries = 0; tries < 10; tries++) {
      int dx = (FysiksFun.rand.nextInt(16) + tries) % 16;
      int dz = (FysiksFun.rand.nextInt(16) + Counters.tick) % 16;
      int x = cx + dx, z = cz + dz;
      checkAndTickTree(w, x, z);
    }
  }

  /**
   * Tests if a tree can be found at the given XZ coordinates, and ticks it if
   * so.
   */
  public static void checkAndTickTree(World w, int x, int z) {
    int logCount = 0;
    int nLeaves = 0;
    // woodDown represents how many wood blocks straight down that we have
    // walked.
    // If bigger than a threshold we are on the main trunk, and should now not
    // cross over leaves anymore
    int woodDown = 0;

    boolean foundTree = false;
    int treeYStart;
    Chunk c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    for (treeYStart = 128; treeYStart > 20; treeYStart--) {
      if (c == null) continue;
      int id = c.getBlockID(x & 15, treeYStart, z & 15);
      //if (id == Block.wood.blockID) logCount++;
      if((treeCategory[id] == TreeCategory.TRUNK_PART)) logCount++;
      else logCount = 0;
      if (logCount >= 2) {
        foundTree = true;
        break;
      }
      if(id == 0) continue;
      else if(treeCategory[id] != TreeCategory.NOT_TREE) continue;
      else if(Block.blocksList[id] != null && !Block.blocksList[id].isOpaqueCube()) continue;
      //if (id == 0 || id == Block.leaves.blockID || id == Block.wood.blockID || (Block.blocksList[id] != null && !Block.blocksList[id].isOpaqueCube())) continue;
      else break;
    }
    if (!foundTree) return;
    int y, steps;
    // tries += 4; // Reduce the CPU cost by doing fewer tries whenever we found
    // a potential tree...

    /*
     * Make a random walk that always takes us "down" as much as possible". This
     * serves two purposes: (1) Takes us from branches into the main trunk and
     * (2) moves us down to the bottom of the tree (even if there are parts of a
     * large trunk that are cut out).
     */
    int woodX = x, woodY = treeYStart, woodZ = z;
    woodDown = 0;
    c = ChunkCache.getChunk(w, x >> 4, z >> 4, false);
    for (steps = 0, y = treeYStart; y > 0 && steps < 250; steps++) {
      if (c == null) continue;
      int id = c.getBlockID(x & 15, y - 1, z & 15);
      if(treeCategory[id] == TreeCategory.TRUNK_PART) {
        y--;
        woodX = x;
        woodY = y;
        woodZ = z;
        woodDown++;
        continue;
      } else if(treeCategory[id] == TreeCategory.LEAF_PART && woodDown < 3) { 
        y--;
        nLeaves++;
      }
      int dx2 = FysiksFun.rand.nextInt(3) - 1;
      int dz2 = FysiksFun.rand.nextInt(3) - 1;
      if (dx2 * dx2 + dz2 * dz2 == 1) {

        int x2 = x + dx2, z2 = z + dz2;
        Chunk c2 = ChunkCache.getChunk(w, x2 >> 4, z2 >> 4, false);
        if (c2 == null) continue;
        id = c2.getBlockID(x2 & 15, y, z2 & 15);
        if(treeCategory[id] == TreeCategory.LEAF_PART) nLeaves++;
        //if (id == Block.leaves.blockID) nLeaves++;        
        //if (id == Block.wood.blockID || id == Block.leaves.blockID) {
        if(treeCategory[id] != TreeCategory.NOT_TREE) {
          x += dx2;
          z += dz2;
          c=c2;
        }
      }
    }
    // x=woodX; y=woodY; z=woodZ;
    int finalId = w.getBlockId(x, y, z);
    if(treeCategory[finalId] == TreeCategory.TRUNK_PART && (woodDown >= 2 || nLeaves > 0)) {
    //if (w.getBlockId(x, y, z) == Block.wood.blockID && (woodDown >= 2 || nLeaves > 0)) {
      // We have found a local minima (Y wise) that is wood. It "must" be the
      // bottom of a tree.
      tickTree(w, x, y, z);
      Counters.treeCounter++;
    }
  }

  public static void tickTree(World w, int x, int y, int z) {
    boolean killTree = false;
    int blockBelowId = w.getBlockId(x, y - 1, z);
    IChunkProvider chunkProvider = w.getChunkProvider();

    // Trees hanging in the air should fall in a semi-realistic manner.
    if (blockBelowId == 0 || (Block.blocksList[blockBelowId] != null && !Block.blocksList[blockBelowId].isOpaqueCube())) {
      /**
       * TODO add a sound effect when trees are falling (is this dobe
       * client-side?)
       */
      if (FysiksFun.settings.treesFall) fellTree(w, x, y, z);
      return;
    }

    if (blockBelowId != Block.dirt.blockID && blockBelowId != Block.grass.blockID && FysiksFun.rand.nextInt(10) == 0) killTree = true;

    // Update the state of the tree iff we are doing dynamic plants
    if (!FysiksFun.settings.doTreeConsumptions) return;

    /* See if there is nearby water for the tree to drink */
    int dx, dy, dz;
    boolean treeThirsty = FysiksFun.rand.nextInt(200 / FysiksFun.settings.treeThirst) <= 2;
    int surroundingWater = 0;
    int submergedWater = 0;
    for (dx = -4; dx <= 4; dx++)
      for (dz = -4; dz <= 4; dz++)
        for (dy = 2; dy >= -4; dy--) {
          Chunk c = chunkProvider.provideChunk((x + dx) >> 4, (z + dz) >> 4);
          int id = c.getBlockID((x + dx) & 15, y + dy, (z + dz) & 15);
          if (id == Fluids.stillWater.blockID || id == Fluids.flowingWater.blockID) {
            surroundingWater++;
            if (dy >= 1) submergedWater++;
            if (treeThirsty) {
              Fluids.stillWater.consume(w, c, x + dx, y + dy, z + dz, 1);
              treeThirsty = false;
              Counters.treeDrink++;
            }
          }
        }
    // There is just too much water around this tree... let's slowly kill it
    if (submergedWater > 9 * 9 * 1 - 10 && FysiksFun.rand.nextInt(10) == 0) killTree = true;

    if (killTree) {
      boolean killFoilage = true;
      for (dy = 0; dy < 20 && killFoilage; dy++)
        for (dx = -4; dx <= 4 && killFoilage; dx++)
          for (dz = -4; dz <= 4 && killFoilage; dz++) {
            int id = w.getBlockId(x + dx, y + dy, z + dz);
            if(treeCategory[id] == TreeCategory.LEAF_PART || (treeCategory[id] == TreeCategory.TRUNK_PART && dx != 0 && dz != 0)) {
              //if (id == Block.leaves.blockID || (id == Block.wood.blockID && dx != 0 && dz != 0)) {
              w.setBlock(x + dx, y + dy, z + dz, 0, 0, 0x02);
              killFoilage = false;
              Counters.treeKill++;
              break;
            }
          }
      if (killFoilage && FysiksFun.rand.nextInt(5) == 0) {
        // All foilage must have been killed already, let's start removing the
        // remaining trunk of the tree
        for (dy = 20; dy >= 0; dy--) {
          int id = w.getBlockId(x, y + dy, z);
          if(treeCategory[id] == TreeCategory.TRUNK_PART) {
          //if (id == Block.wood.blockID) {
            w.setBlock(x, y + dy, z, 0, 0, 0x02);
            killFoilage = false;
            break;
          }
        }
      }
      return;
    } else {
      /* This tree seems to be in good health. See if it can spread */
      if (FysiksFun.rand.nextInt(500) == 0) {
        findTreeToPlant:
        for (int tries = 0; tries < 1; tries++) {
          dx = FysiksFun.rand.nextInt(24) - 12;
          dz = FysiksFun.rand.nextInt(24) - 12;
          Chunk c = ChunkCache.getChunk(w, (x + dx) >> 4, (z + dz) >> 4, false);
          if (c == null) continue;
          for (int y2 = y + 8; y2 > y - 4 && y2 > 0; y2--) {
            // int id = c.getBlockID((x+dx)&15, y2, (z+dz)>>4);
            int id = w.getBlockId(x + dx, y2, z + dz);
            if (id == 0) continue;
            else if (y2 == y + 5) break; // Can't grow here, ground starts above
                                         // our starting level.
            else if (Block.blocksList[id].canSustainPlant(w, x + dx, y2, z + dz, ForgeDirection.UP, (BlockSapling) Block.sapling)) {
              int myMeta = w.getBlockMetadata(x, y, z);
              FysiksFun.setBlockWithMetadataAndPriority(w, x + dx, y2 + 1, z + dz, Block.sapling.blockID, myMeta & 3, 0);
              // System.out.println("Planted sapling on top of :" + id);
              tries = 10;
              break findTreeToPlant;
            } else break;
          }
        }
      }
    }

  }

  /** Makes a tree fall to the ground in a somewhat semi-realistic manner */
  private synchronized static void fellTree(final World w, final int x, final int y, final int z) {

    float volume = 1.0F;
    float pitch = 1.0F;       
    w.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "fysiksfun:timber", volume, pitch);
    
    
    int dx, dz;
    for (dx = -1; dx <= 1; dx++)
      for (dz = -1; dz <= 1; dz++) {
        if (w.getBlockId(x + dx, y, z + dz) == Block.wood.blockID && w.getBlockId(x + dx, y - 1, z + dz) != 0) return;
      }

    /* First check that this tree isn't already falling, if so ignore it */
    for (Trees t : fallingTrees) {
      dx = t.centerX - x;
      dz = t.centerZ - z;
      if (dx * dx + dz * dz < 6 * 6) return;
      // if(t.centerX == x && t.centerY == y && t.centerZ == z) return;
    }

    /* It doesn't, register it as a new tree that is falling */
    Trees t = new Trees(w, x, y, z);
    t.fallingAngle = 0;

    // Determine best falling direction
    // TODO
    t.fallingDirection = FysiksFun.rand.nextInt(4);
    fallingTrees.add(t);
  }

  private void tickFallingTree() {
    if (++tickCounter % 2 != 0) return;
    // System.out.println("A tree is falling, does it make any sound?");

    /* First, remove all of the blocks from the world, using the old angle */
    final double stepsToFall = 200.0;

    double oldAngle = fallingAngle * (Math.PI / stepsToFall);

    int dx, dy, dz;
    for (TreeBlock b : blocks) {
      dx = b.AngleToDx(oldAngle, fallingDirection);
      dy = b.AngleToDy(oldAngle, fallingDirection);
      dz = b.AngleToDz(oldAngle, fallingDirection);
      int id = w.getBlockId(centerX + dx, centerY + dy, centerZ + dz);
      if (id == b.id) w.setBlock(centerX + dx, centerY + dy, centerZ + dz, 0, 0, 0x01 + 0x02);
    }

    double newAngle = (fallingAngle + 1) * (Math.PI / stepsToFall);
    /*
     * Check if the new falling angle would cause a WOOD block to be placed
     * within some other block
     */
    boolean hasCollided = false;
    for (TreeBlock b : blocks) {
      if(treeCategory[b.id] != TreeCategory.TRUNK_PART) continue;
      //if (b.id != Block.wood.blockID) continue;
      dx = b.AngleToDx(newAngle, fallingDirection);
      dy = b.AngleToDy(newAngle, fallingDirection);
      dz = b.AngleToDz(newAngle, fallingDirection);
      int id = w.getBlockId(centerX + dx, centerY + dy, centerZ + dz);
      if (id != 0 && treeCategory[id] != TreeCategory.LEAF_PART && Block.blocksList[id] != null && Block.blocksList[id].isOpaqueCube()) {
        //if (id != 0 && id != Block.leaves.blockID && Block.blocksList[id] != null && Block.blocksList[id].isOpaqueCube()) {
        hasCollided = true;
        break;
      }
    }

    /* Check if the whole tree (counting only wood blocks) could fall one Y-step */
    if (!hasCollided) {
      boolean canFall = true;
      for (TreeBlock b : blocks) {
        if(treeCategory[b.id] != TreeCategory.TRUNK_PART) continue;
        //if (b.id != Block.wood.blockID) continue;
        dx = b.AngleToDx(newAngle, fallingDirection);
        dy = b.AngleToDy(newAngle, fallingDirection);
        dz = b.AngleToDz(newAngle, fallingDirection);
        int id = w.getBlockId(centerX + dx, centerY + dy - 1, centerZ + dz);
        if (id != 0 && treeCategory[id] != TreeCategory.LEAF_PART && Block.blocksList[id] != null && Block.blocksList[id].isOpaqueCube()) {
          //if (id != 0 && id != Block.leaves.blockID && Block.blocksList[id] != null && Block.blocksList[id].isOpaqueCube()) {
          canFall = false;
          break;
        }
      }
      if (canFall) centerY--;
    }
    // If this is reached we have a problem, maybe a singleton block?
    if (fallingAngle >= 160) hasCollided = true;

    /*
     * Now, if placing the tree with the new angle ended up with a collision.
     * place it back with the old angle and remove us from the list of falling
     * trees
     */
    if (hasCollided) {
      placeTreeInWorld(oldAngle);
      landedTrees.add(this);
    } else {
      placeTreeInWorld(newAngle);
      fallingAngle++;
    }
  }

  /*
   * int dy, dx, dz; for (dy = 0; dy < maxDy; dy++) { for (dx = minDx; dx <=
   * maxDx; dx++) for (dz = minDz; dz <= maxDz; dz++) { TreeBlock t = null; for
   * (TreeBlock t2 : blocksToFall) { if (t2.dx == dx && t2.dy == dy && t2.dz ==
   * dz) { t = t2; break; } } if (t == null) continue;
   * 
   * int metaHere = w.getBlockMetadata(x + dx, y + dy, z + dz); w.setBlock(x +
   * dx, y + dy, z + dz, 0, 0, 0x02); for (int fall = 0; fall < 256; fall++) {
   * int id2 = w.getBlockId(x - dy, y + dx - fall - 1, z + dz); if (id2 != 0 &&
   * id2 != Block.leaves.blockID) { // Foliage that lands under other wood
   * pieces will be crushed by // the above wood pieces w.setBlock(x - dy, y +
   * dx - fall, z + dz, t.id, t.meta, 3); break; } } } }
   */

  private void placeTreeInWorld(double angle) {
    lastAngle = angle;
    int dx, dy, dz;
    /* First place the leaves on air */
    for (TreeBlock b : blocks) {
      if(treeCategory[b.id] != TreeCategory.LEAF_PART) continue;
      //if (b.id != Block.leaves.blockID) continue;
      dx = b.AngleToDx(angle, fallingDirection);
      dy = b.AngleToDy(angle, fallingDirection);
      dz = b.AngleToDz(angle, fallingDirection);
      int id = w.getBlockId(centerX + dx, centerY + dy, centerZ + dz);
      if (id == 0 || (Block.blocksList[id] != null && !Block.blocksList[id].isOpaqueCube()))
        w.setBlock(centerX + dx, centerY + dy, centerZ + dz, b.id, b.meta, 0x01 + 0x02);
    }
    /* Second, place the wood blocks */
    for (TreeBlock b : blocks) {
      if(treeCategory[b.id] != TreeCategory.TRUNK_PART) continue;
      //if (b.id != Block.wood.blockID) continue;
      dx = b.AngleToDx(angle, fallingDirection);
      dy = b.AngleToDy(angle, fallingDirection);
      dz = b.AngleToDz(angle, fallingDirection);
      int id = w.getBlockId(centerX + dx, centerY + dy, centerZ + dz);
      if (id == 0 || treeCategory[b.id] == TreeCategory.LEAF_PART || (Block.blocksList[id] != null && !Block.blocksList[id].isOpaqueCube())) {
        int newMeta = b.meta;
        if (fallingAngle > 30) {
          if (fallingDirection == 0 || fallingDirection == 1) newMeta ^= 0x04;
          else if (fallingDirection == 2 || fallingDirection == 3) newMeta ^= 0x08;
        }
        w.setBlock(centerX + dx, centerY + dy, centerZ + dz, b.id, newMeta, 0x01 + 0x02);
      }
    }

  }
}
