package mbrx.ff;

import java.util.Random;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class BlockGas extends Block {
  public String          iconName                   = "generic-gas";
  public int             updateRate;
  private static boolean preventSetBlockGasFlowover = false;
  private boolean        lighterThanAir;
  
  /**
   * The relative weight of this gas compared to air. +1 means lighter than air,
   * -1 heavier.
   */
  private int            weightToAir;
  private int damageToEntity, chanceToDamageEntity;

  public BlockGas(int id, Material material) {
    super(id, material);
    updateRate = 5;
    setLighterThanAir(true);
    setDamageToEntity(0, 0);
  }

  public void setIconName(String s) {
    iconName = s;
  }
  public void setDamageToEntity(int hearts, int chance) {
    damageToEntity=hearts;
    chanceToDamageEntity=chance;
  }

  public void registerIcons(IconRegister iconRegister) {
    blockIcon = iconRegister.registerIcon(iconName);
  }

  public void setUpdateRate(int rate) {
    updateRate = rate;
  }


  /** If set to true the gas will rise when there are empty blocks above it */
  public void setLighterThanAir(boolean b) {
    lighterThanAir = b;
    if (lighterThanAir) weightToAir = +1;
    else weightToAir = -1;
  }

  public int meta2content(int meta) {
    return 16 - meta;
  }

  public int getBlockContent(World w, int x, int y, int z) {
    return 16 - w.getBlockMetadata(x, y, z);
  }

  public int getBlockContent(Chunk c, int x, int y, int z) {
    return 16 - c.getBlockMetadata(x & 15, y, z & 15);
  }

  public void setBlockContent(World w, Chunk c, int x, int y, int z, int quantity) {
    if (quantity == 0) {
      c.setBlockIDWithMetadata(x & 15, y, z & 15, 0, 0);
    } else {
      c.setBlockIDWithMetadata(x & 15, y, z & 15, blockID, 16 - quantity);
    }
    ChunkMarkUpdater.scheduleBlockMark(w, x, y, z);
  }

  public void setBlockContent(World w, int x, int y, int z, int quantity) {
    Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
    setBlockContent(w, c, x, y, z, quantity);
  }

  @Override
  public void updateTick(World w, int x, int y, int z, Random r) {
    IChunkProvider chunkProvider = w.getChunkProvider();
    if (!chunkProvider.chunkExists(x >> 4, z >> 4)) return;
    updateTickSafe(w, x, y, z, r);
  }

  /** Called only when we KNOW that the original chunk is loaded */
  public void updateTickSafe(World w, int x, int y, int z, Random r) {
    int chunkX = x >> 4, chunkZ = z >> 4;
    IChunkProvider chunkProvider = w.getChunkProvider();
    Chunk origChunk = chunkProvider.provideChunk(chunkX, chunkZ);
    if (y <= 0) return;
    Counters.gasTicks++;

    try {
      preventSetBlockGasFlowover = true;
      BlockFluid.preventSetBlockLiquidFlowover = true;

      int oldID = origChunk.getBlockID(x & 15, y, z & 15);
      if (oldID != blockID) return;
      int oldContent = getBlockContent(w, x, y, z);
      int newContent = oldContent;

      if (oldContent == 0) {
        setBlockContent(w, x, y, z, 0);
        return;
      }

      if(y >= 120 && FysiksFun.rand.nextInt(100) == 0) {
        /* Block condensate into water */ 
        Fluids.stillWater.setBlockContent(w,  x, y, z, newContent * BlockFluid.maximumContent/16);
        //FysiksFun.scheduleBlockTick(w, Fluids.stillWater, x, y, z, 1);
        return;
      }
      
      /* First, move gas in direction of wind with a given probability */
      float windX = Wind.getWindX(w,x,y,z);
      float windZ = Wind.getWindZ(w,x,y,z);
      if(windX > 0.f && r.nextFloat() < windX) {
        int id1 = w.getBlockId(x+1, y, z);
        if(id1 == 0) {          
          setBlockContent(w,x+1,y+computeUpdraft(w,y,x,z,x+1,z),z,newContent);
          newContent=0;
        }
      } else if(windX <0.f && r.nextFloat() < -windX) {
        int id1 = w.getBlockId(x-1, y, z);
        if(id1 == 0) {
          setBlockContent(w,x-1,y+computeUpdraft(w,y,x,z,x-1,z),z,newContent);
          newContent=0;
        }        
      }
      if(windZ > 0.f && r.nextFloat() < windZ) {
        int id1 = w.getBlockId(x, y, z+1);
        if(id1 == 0) {
          setBlockContent(w,x,y+computeUpdraft(w,y,x,z,x,z+1),z+1,newContent);
          newContent=0;
        }
      } else if(windZ <0.f && r.nextFloat() < -windZ) {
        int id1 = w.getBlockId(x, y, z-1);
        if(id1 == 0) {
          setBlockContent(w,x,y+computeUpdraft(w,y,x,z,x,z-1),z-1,newContent);
          newContent=0;
        }        
      }
      
            
      int blockIdAbove = w.getBlockId(x, y + 1, z);
      /*if (lighterThanAir && blockIdAbove == 0) {
        // Let the gas move upwards if the block above is empty
        setBlockContent(w, x, y + 1, z, newContent);
        FysiksFun.scheduleLiquidTick(w, this, x, y + 1, z, updateRate);
        newContent = 0;
      }*/
      if(blockIdAbove > 0 && blockIdAbove < 4096 && Fluids.isLiquid[blockIdAbove]) {
        /* There's a fluid above this block - exchange their positions */
        BlockFluid fluid = Fluids.fluid[blockIdAbove];
        fluid.setBlockContent(w, x, y, z, fluid.getBlockContent(w, x, y+1, z));
        setBlockContent(w, x, y+1, z, newContent);
        FysiksFun.scheduleBlockTick(w, this, x, y+1, z, updateRate);
        FysiksFun.scheduleBlockTick(w, fluid, x, y, z, fluid.liquidUpdateRate);
        // No more checks of this function since we not longer are located at the given x,y,z coordinate
        return;
      }      

      /* Equalize pressure in each direction. First up, then sideways (randomized order) and finally down */
      int dirOffset = FysiksFun.rand.nextInt(4);
      for (int dir0 = -1; dir0 < 5; dir0++) {
        int x2 = x, z2 = z, y2 = y;
        if (dir0 == -1) y2 += weightToAir;
        else if (dir0 == 4) y2 -= weightToAir;
        else {
          switch ((dir0 + dirOffset)%4) {
          case 0:
            x2++;
            break;
          case 1:
            z2++;
            break;
          case 2:
            x2--;
            break;
          case 3:
            z2--;
            break;
          }
        }
        int dx = x2 - x;
        int dy = y2 - y;
        int dz = z2 - z;
        if(y2 > y && y2 >= 128) continue; // Top of the world...
        
        if(x2 == x && y2 == y && z2 == z) {
          System.out.println("[WARN] Unexpected X2 Y2 Z2 value in BlockGas");
        }
        Chunk chunk2 = origChunk;
        if (x2 >> 4 != chunkX || z2 >> 4 != chunkZ) {
          if (!chunkProvider.chunkExists(x2 >> 4, z2 >> 4)) continue;
          else chunk2 = chunkProvider.provideChunk(x2 >> 4, z2 >> 4);
        }

        int blockIdNN = chunk2.getBlockID(x2 & 15, y2, z2 & 15);
        int blockMetaNN = chunk2.getBlockMetadata(x2 & 15, y2, z2 & 15);
        int blockContentNN;
        if (blockIdNN == blockID) blockContentNN = meta2content(blockMetaNN);
        else blockContentNN = 0;
        if ((blockIdNN == blockID || blockIdNN == 0) && newContent > 0 && blockContentNN <= newContent) {
          /* Always move unless it would increase the pressure. This means that lone gases will make a random walk */
          int toMove = (newContent - blockContentNN) / 2;
          if (toMove == 0 && blockContentNN < 15 && FysiksFun.rand.nextInt(2) == 0) toMove = 1;
          newContent -= toMove;
          blockContentNN += toMove;
          if(blockContentNN >= 16) { newContent += blockContentNN-15; blockContentNN = 15; }
          setBlockContent(w, x2, y2, z2, blockContentNN);
          FysiksFun.scheduleBlockTick(w, this, x2, y2, z2, updateRate);
        }

      }

      if (newContent != oldContent) {
        setBlockContent(w, x, y, z, newContent);
        if (newContent > 0) FysiksFun.scheduleBlockTick(w, this, x, y, z, updateRate);         
        notifySameGasNeighboursWithMore(w, x, y, z, newContent - 1, updateRate);
      } else
        // Always schedule a tick... a bit expensive but much more responsive
        FysiksFun.scheduleBlockTick(w, this, x, y, z, updateRate);

    } finally {
      preventSetBlockGasFlowover = false;
      BlockFluid.preventSetBlockLiquidFlowover = false;
    }
  }

  /** Checks if there should be movement in Y (up/down draft) when moving from x0,z0 to x1,z1 */
  private int computeUpdraft(World w, int y, int x0, int z0, int x1, int z1) {
    int height0,height1;
    
    if(y >= 128) return 0;
    
    IChunkProvider chunkProvider = w.getChunkProvider();
    Chunk c = chunkProvider.provideChunk(x0>>4, z0>>4);
    for(height0=y;height0>1;height0--) 
      if(c.getBlockID(x0&15, height0, z0&15) != 0) break;
    c = chunkProvider.provideChunk(x1>>4, z1>>4);
    for(height1=y;height1>1;height1--) 
      if(c.getBlockID(x1&15, height1, z1&15) != 0) break;
    if(height1 > height0+2) return +2;
    else if(height1 > height0) return +1;
    else if(height1 < height0-1) return -1;
    else return 0;
  }

  /**
   * Schedules an update for all neighbours that are of the same gas type as we
   * are - but only if they have strictly MORE liquid than what is given
   */
  private void notifySameGasNeighboursWithMore(World w, int x, int y, int z, int limit, int delay) {
    int id;

    id = w.getBlockId(x + 1, y, z);
    if ((id == blockID) && getBlockContent(w, x + 1, y, z) > limit) {
      FysiksFun.scheduleBlockTick(w, this, x + 1, y, z, delay);
    }
    id = w.getBlockId(x - 1, y, z);
    if ((id == blockID) && getBlockContent(w, x - 1, y, z) > limit) {
      FysiksFun.scheduleBlockTick(w, this, x - 1, y, z, delay);
    }
    id = w.getBlockId(x, y + 1, z);
    if ((id == blockID) && getBlockContent(w, x, y + 1, z) > limit) {
      FysiksFun.scheduleBlockTick(w, this, x, y + 1, z, delay);
    }
    id = w.getBlockId(x, y - 1, z);
    if ((id == blockID) && getBlockContent(w, x, y - 1, z) > limit) {
      FysiksFun.scheduleBlockTick(w, this, x, y - 1, z, delay);
    }
    id = w.getBlockId(x, y, z + 1);
    if ((id == blockID) && getBlockContent(w, x, y, z + 1) > limit) {
      FysiksFun.scheduleBlockTick(w, this, x, y, z + 1, delay);
    }
    id = w.getBlockId(x, y, z - 1);
    if ((id == blockID) && getBlockContent(w, x, y, z - 1) > limit) {
      FysiksFun.scheduleBlockTick(w, this, x, y, z - 1, delay);
    }
  }

  public int idDropped(int par1, Random par2Random, int par3) {
    return 0;
  }

  public int quantityDropped(Random par1Random) {
    return 0;
  }

  @Override
  public void onBlockAdded(World w, int x, int y, int z) {
    FysiksFun.scheduleBlockTick(w, this, x, y, z, updateRate);
  }
  @Override
  public void onNeighborBlockChange(World w, int x, int y, int z, int ignore)  {
    FysiksFun.scheduleBlockTick(w, this, x, y, z, updateRate);      
  }
  @Override
  public int getRenderBlockPass() { return 1; }
  @Override
  public boolean getBlocksMovement(IBlockAccess w, int x, int y, int z) { return false; }

  @SideOnly(Side.CLIENT)
  public int getBlockColor() {
      return 0xFFFFFF;
  }
  public int colorMultiplier(IBlockAccess w, int x, int y, int z) {
    return 0xFFFFFF;
  }
  
  public boolean renderAsNormalBlock() {
    return false;
  }

  public boolean isOpaqueCube() {
    return false;
  }

  /* TODO - figure out what to do here... */
  public boolean isBlockSolid(IBlockAccess par1IBlockAccess, int par2, int par3, int par4, int par5) {
    return true;
  }
  
  @Override
  public boolean canCollideCheck(int par1, boolean par2) {
    return true;
  }
  public MovingObjectPosition collisionRayTrace(World w, int x, int y, int z, Vec3 start, Vec3 end) {
    return null;
  }
  public AxisAlignedBB getCollisionBoundingBoxFromPool(World par1World, int par2, int par3, int par4)
  {
      return null;
  }

  public void produceSteam(World w, Chunk c, int x, int y, int z, int amount) {
    int id;
    
    amount = produceSteamAt(w, c, x, y, z, amount);
    if(amount > 0) amount = produceSteamAt(w, c, x, y+1, z, amount);    
    if(amount > 0) amount = produceSteamAt(w, c, x-1, y, z, amount);
    if(amount > 0) amount = produceSteamAt(w, c, x+1, y, z, amount);
    if(amount > 0) amount = produceSteamAt(w, c, x, y, z-1, amount);
    if(amount > 0) amount = produceSteamAt(w, c, x, y, z+1, amount);
    if(amount > 0) amount = produceSteamAt(w, c, x, y-1, z, amount);
    /* What do we do if the steam can't be produced? */
    
  }

  private int produceSteamAt(World w, Chunk c, int x, int y, int z, int amount) {
    int id = w.getBlockId(x,y,z);
    if(id == 0) {
      setBlockContent(w, c, x, y, z, amount);
      FysiksFun.scheduleBlockTick(w, this, x, y, z, updateRate);
      return 0;
    }
    if(id == blockID) {
      int currentContent = getBlockContent(c, x, y, z);      
      int toMove = 15 - currentContent;
      if(toMove > amount) toMove=amount;
      setBlockContent(w, c, x, y, z, currentContent + toMove);
      FysiksFun.scheduleBlockTick(w, this, x, y, z, updateRate);
      return amount - toMove;
    }   
    return amount;
  }

  public void interactWithEntity(Entity e) {
    if(damageToEntity > 0 && e instanceof EntityLiving) {
      EntityLiving living = (EntityLiving) e;
      if(FysiksFun.rand.nextInt(chanceToDamageEntity) == 0)
        living.attackEntityFrom(DamageSource.inWall, damageToEntity);

    }
  }
}
