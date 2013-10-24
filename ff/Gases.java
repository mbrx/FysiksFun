package mbrx.ff;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAITaskEntry;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class Gases {
  public static BlockGas steam;
  
  public static int      nGases;
  public static int      gasIDs[] = new int[256];
  public static boolean  isGas[]  = new boolean[4096];
  public static BlockGas gas[]  = new BlockGas[4096];

  public static void postInit() {}

  
  public static void load() {
    for(int i=0;i<4096;i++) {
      isGas[i]=false;
      gas[i]=null;
    }
        
    
    steam = new BlockGas(FysiksFun.settings.blockSteamDefaultID, Material.air);
    steam.setUnlocalizedName("steam");
    steam.setCreativeTab(CreativeTabs.tabBlock);
    steam.setHardness(0.0F);
    // Was FysiksFun:steam
    steam.setIconName("fysiksfun:steam");
    steam.setLighterThanAir(true);
    steam.setDamageToEntity(1, 20);
    
    GameRegistry.registerBlock(steam, "steam");
    LanguageRegistry.addName(steam, "steam");
    registerGasBlock(steam);
    
    ItemStack steamStack = new ItemStack(steam);
    GameRegistry.addShapelessRecipe(steamStack,  new ItemStack(Block.dirt));
    
  }
  public static void registerGasBlock(BlockGas block) {
    gasIDs[nGases++] = block.blockID;
    isGas[block.blockID] = true;     
    gas[block.blockID]= block; 
  }

  public static void doWorldTick(World w) {
    /* Iterate over all entities, if they are standing in a gas, give the gas a chance to interact with the entity */
    LinkedList allEntities = new LinkedList();
    allEntities.addAll(w.loadedEntityList);
    for(Object o : allEntities) {
      if(o instanceof Entity) {
        Entity e = (Entity) o;
        int x = (int) e.posX;
        int y = (int) e.posY;
        int z = (int) e.posZ;
        int id = w.getBlockId(x, y, z);
        if(id>0 && id<=4096 && isGas[id]) {
          gas[id].interactWithEntity(e);
        }
      }                     
    }    
  }
  
  /**
   * Performs random expensive ticks on every block in a randomly selected layer
   * of each chunk it is called for (chunk center XZ).
   */
  public static void doChunkTick(World w, int x, int z) {
    
    for (int i = 0; i < 10; i++) {
      int y = 1 + FysiksFun.rand.nextInt(255);
      Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);

      for (int dx = 0; dx < 16; dx++)
        for (int dz = 0; dz < 16; dz++) {
          int id = c.getBlockID(dx, y, dz);
          if(id > 0 && id < 4096 && isGas[id]) {
            BlockGas b = (BlockGas) Block.blocksList[id];
            b.updateTickSafe(w, x + dx, y, dz + z, FysiksFun.rand);
          }
        }
    }
  }
}
