package mbrx.ff;

import java.util.Random;

import mbrx.ff.util.Counters;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFluid;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

public class FFBlockRewriter extends BlockFluid {
	private int targetBlockId;
	
	public FFBlockRewriter(int myBlockId, Material myMaterial,int newBlockId) {
		super(myBlockId, myMaterial);
		this.targetBlockId = newBlockId;
		System.out.println("[FF] Automatically rewriting blocks "+myBlockId+" -> "+targetBlockId);
	}
	
    public void updateTick(World w, int x, int y, int z, Random par5Random) {
    	Counters.rewriteBlockCounter++;
    	//w.setBlockAndMetadataWithNotify(x,y,z,targetBlockId,0, 6);
  		//FlowingFluids.scheduleLiquidTick(w, this, x, y, z, 1);
    }
    
    //@Override
    public int tickRate() { return 100; }
	
    /**
     * Lets the block know when one of its neighbor changes. Doesn't know which neighbor changed (coordinates passed are
     * their own) Args: x, y, z, neighbor blockID
     */
    public void onNeighborBlockChange(World w, int x, int y, int z, int par5) {
    	//FlowingFluids.rewriteBlockCounter++;
    	//w.scheduleBlockUpdate(x, y, z, this.blockID, 1);
    //	w.setBlockAndMetadataWithNotify(x,y,z,targetBlockId,0, 6);
  		//FlowingFluids.scheduleLiquidTick(w, this, x, y, z, 1);

    }
    
}
