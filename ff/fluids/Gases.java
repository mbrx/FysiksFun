package mbrx.ff.fluids;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import mbrx.ff.FysiksFun;
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
  public static BlockSteam steam;
  public static BlockPyroclastic pyroclastic;
  
  public static int      nGases;
  public static int      gasIDs[] = new int[256];
  public static boolean  isGas[]  = new boolean[4096];
  public static BlockFFGas asGas[]  = new BlockFFGas[4096];

  public static void postInit() {}

  
  public static void load() {
    for(int i=0;i<4096;i++) {
      isGas[i]=false;
      asGas[i]=null;
    }
        
    
    steam = new BlockSteam(FysiksFun.settings.blockSteamDefaultID, Material.air);
    steam.setUnlocalizedName("steam");
    steam.setCreativeTab(CreativeTabs.tabBlock);
    steam.setHardness(0.0F);
    steam.setIconName("fysiksfun:steam");
    steam.setLighterThanAir(true);
    steam.setDamageToEntity(1, 10);
    
    GameRegistry.registerBlock(steam, "steam");
    LanguageRegistry.addName(steam, "steam");
    registerGasBlock(steam);
    
    //ItemStack steamStack = new ItemStack(steam);
    //GameRegistry.addShapelessRecipe(steamStack,  new ItemStack(Block.dirt));
    
    
    pyroclastic = new BlockPyroclastic(FysiksFun.settings.blockPyroclasticDefaultID, Material.air);
    pyroclastic.setUnlocalizedName("pyroclast");
    pyroclastic.setCreativeTab(CreativeTabs.tabBlock);
    pyroclastic.setHardness(0.0F);
    pyroclastic.setIconName("fysiksfun:pyroclast");
    pyroclastic.setLighterThanAir(true);
    pyroclastic.setDamageToEntity(1, 2);
    
    GameRegistry.registerBlock(pyroclastic, "pyroclastic");
    LanguageRegistry.addName(pyroclastic, "pyroclastic");
    registerGasBlock(pyroclastic);
    
   
    
  }
  public static void registerGasBlock(BlockFFGas block) {
    gasIDs[nGases++] = block.blockID;
    isGas[block.blockID] = true;     
    asGas[block.blockID]= block; 
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
          asGas[id].interactWithEntity(e);
        }
      }                     
    }    
  }
  
}
