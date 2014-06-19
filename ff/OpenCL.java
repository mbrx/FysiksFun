package mbrx.ff;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import mbrx.ff.util.Util;

import org.lwjgl.LWJGLException;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CLCommandQueue;
import org.lwjgl.opencl.CLContext;
import org.lwjgl.opencl.CLDevice;
import org.lwjgl.opencl.CLKernel;
import org.lwjgl.opencl.CLPlatform;
import org.lwjgl.opencl.CLProgram;

/**
 * Wrapper class for all OpenCL handling. Stores devices, kernels and memory
 * objects. Allows invokation of OpenCL kernels. Actual logic for OpenCL kernel
 * interaction is done by the using class.
 * 
 * @author Mathias Broxvall (mathias.broxvall@gmail.com)
 */
public class OpenCL {
  public static boolean        enabled  = false;
  public static CLPlatform     platform = null;
  public static CLDevice       device   = null;
  public static CLContext      context  = null;
  public static CLCommandQueue queue    = null;

  public static void initOpenCL() {
    try {
      CL.create();
      List<CLPlatform> platforms = CLPlatform.getPlatforms();
      int index = 0;

      for (CLPlatform platform : platforms) {
        String s = platform.getInfoString(CL10.CL_PLATFORM_NAME);
        System.out.println("*** OPENCL found platform " + index + ": " + s);
        List<CLDevice> devices = platform.getDevices(CL10.CL_DEVICE_TYPE_ALL);
        int index2 = 0;
        for (CLDevice device : devices) {
          System.out.println("   device " + index2 + ": " + device.getInfoString(CL10.CL_DEVICE_NAME));
          if (index == 0 && index2 == 0) {
            System.out.println("       (the above device will be used)");
            OpenCL.platform = platform;
            OpenCL.device = device;
          }
          index2++;
        }
        index++;
      }
      if (platform == null || device == null) {
        FysiksFun.logger.log(Level.WARNING, " No valid OpenCL device selected. This will affect which features are enabled and may even crash the mod");
        return;
      }
      List<CLDevice> usedDevices = new LinkedList<CLDevice>();
      usedDevices.add(device);
      context = CLContext.create(platform, usedDevices, null);
      enabled = true;
    } catch (LWJGLException error) {
      FysiksFun.logger.log(Level.SEVERE, " There was an error while initializing OpenCL");
      Util.printStackTrace();
      enabled = false;
    }
  }

  public static String loadResourceAsText(String resourceName) {   
    try {      
      InputStream stream = OpenCL.class.getResourceAsStream("/assets/fysiksfun/"+resourceName);
      if(stream == null) {
        FysiksFun.logger.log(Level.SEVERE,"  Could not open resource: /assets/fysiksfun/"+resourceName);
        return "";
      }
      byte[] byteTempData = new byte[65536];
      String resultString="";
      while(true) {
        int len = stream.read(byteTempData);
        String s = new String(byteTempData);
        resultString = resultString+s;
        if(len<65536) break;
      }
      return resultString;
    }catch (IOException exception) {
      FysiksFun.logger.log(Level.SEVERE,"  Could not open resource: "+resourceName); 
      exception.printStackTrace();
    }
    return null; 
  }

  /** Loads the given source file from the assets directory and compiles it. Does not yet handle any include directives or other pre-processing. */
  public static CLProgram compileOpenCLProgram(String programName) {
    String sourceCode = loadResourceAsText("kernels/"+programName);
    CLProgram program = CL10.clCreateProgramWithSource(context, sourceCode, null);
    int err = CL10.clBuildProgram(program, device, "", null);
    if(err != CL10.CL_SUCCESS) {
      FysiksFun.logger.log(Level.SEVERE," Failed to compile opencl program, error code was: "+err+" source code below:\n--------------"+sourceCode+"\n-----------");      
      return null;
    }
    return null;
    
  }

}
