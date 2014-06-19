package mbrx.ff.solidBlockPhysics;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CLMem;
import org.lwjgl.opencl.CLProgram;

import mbrx.ff.OpenCL;
import mbrx.ff.util.ChunkCache;

public class OpenCLBlockPhysics {
  private static final int chunkRadius = 4;
  
  private static CLProgram physicsProgram;
  private static IntBuffer errcodeRet = IntBuffer.allocate(2);
  private static CLMem blockData=null;
  private static CLMem physicsData=null;
  
  public static void init() {
    physicsProgram = OpenCL.compileOpenCLProgram("physics.cl");
    blockData = CL10.clCreateBuffer(OpenCL.context, CL10.CL_READ_WRITE_CACHE, 2*16*16*256*chunkRadius*chunkRadius, errcodeRet);
    physicsData = CL10.clCreateBuffer(OpenCL.context, CL10.CL_READ_WRITE_CACHE, 2*16*16*256*chunkRadius*chunkRadius, errcodeRet);       
  }
  
  public static void doChunksAroundCoord(World w, int chunkBaseX,int chunkBaseZ) {
    // Assumptions: single player -> no risk to do physics multiple times on overlapping chunks
    // For each voxel we give TWO arrays: blockData and physicsData
    // 'blockData' is for the "traditional" storage of 2 bytes (12 bit ID + 4 bit meta)
    // 'physicsData' is for the physics calculations - 4 bytes 
    
    // 1. Upload chunkRadius*chunkRadius chunks around the given chunk coord
    int cX = 0;
    int cZ = 0;
    int cY = 0;
    Chunk c = ChunkCache.getChunk(w, chunkBaseX+cX, chunkBaseZ+cZ, true);
    ExtendedBlockStorage ebs = c.getBlockStorageArray()[cY];
    ByteBuffer blockData = ByteBuffer.allocate(16*16*16*2);         
    
    //CL10.clEnqueueWriteBuffer(OpenCL.device, blockData, CL10.CL_TRUE, ((cX+cZ*chunkRadius)*256*16*16)+16*16*cY)*2, blockData, event_wait_list, event)
    
  }

}
