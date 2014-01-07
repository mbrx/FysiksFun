package mbrx.ffcore;

import java.lang.reflect.Method;
import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.world.World;

public class FFCoreClassTransformer implements IClassTransformer {

  @Override
  public byte[] transform(String targetClassName, String arg1, byte[] bytecode) {

    if (targetClassName.equals("nn")) return patchClassASM(targetClassName, bytecode, true);
    else if (targetClassName.equals("net.minecraft.entity.Entity")) return patchClassASM(targetClassName, bytecode, false);
    return bytecode;
  }

  private byte[] patchClassASM(String targetClassName, byte[] bytecode, boolean isObfuscated) {
    System.out.println("FF: is patching " + targetClassName+"  obf: "+isObfuscated);

    String targetMethodName = "";

    if (isObfuscated == true) targetMethodName = "d";
    else targetMethodName = "moveEntity";

    //set up ASM class manipulation stuff. Consult the ASM docs for details
    ClassNode classNode = new ClassNode();
    ClassReader classReader = new ClassReader(bytecode);
    classReader.accept(classNode, 0);

    //Now we loop over all of the methods declared inside the Explosion class until we get to the targetMethodName "doExplosionB"

    Iterator<MethodNode> methods = classNode.methods.iterator();
    while (methods.hasNext()) {
      MethodNode m = methods.next();

      //System.out.println("Method: "+m.name+" sign: "+m.desc);
      //Check if this is doExplosionB and it's method signature is (Z)V which means that it accepts a boolean (Z) and returns a void (V)
      if ((m.name.equals(targetMethodName) && m.desc.equals("(DDD)V"))) {        
        System.out.println("Found the target method");

        Iterator<AbstractInsnNode> iter = m.instructions.iterator();
        // currentNode is first instruction in the function
        AbstractInsnNode currentNode = iter.next();
        int index=0;
        while(currentNode.getOpcode() != Opcodes.ALOAD && iter.hasNext()) {
          //System.out.println("Skipping node: "+currentNode);
          index++;
          currentNode=iter.next();
        }
        //System.out.println("Next node is: "+currentNode);
        //System.out.println("patch index: "+index);
        
        InsnList toInject = new InsnList();        
        toInject.add(new VarInsnNode(Opcodes.DLOAD, 1));
        toInject.add(new VarInsnNode(Opcodes.DLOAD, 1));
        toInject.add(new InsnNode(Opcodes.DMUL));
        toInject.add(new VarInsnNode(Opcodes.DLOAD, 3));
        toInject.add(new VarInsnNode(Opcodes.DLOAD, 3));
        toInject.add(new InsnNode(Opcodes.DMUL));
        toInject.add(new InsnNode(Opcodes.DADD));
        toInject.add(new VarInsnNode(Opcodes.DLOAD, 5));
        toInject.add(new VarInsnNode(Opcodes.DLOAD, 5));
        toInject.add(new InsnNode(Opcodes.DMUL));
        toInject.add(new InsnNode(Opcodes.DADD));
        toInject.add(new LdcInsnNode(new Double("10000.0")));
        toInject.add(new InsnNode(Opcodes.DCMPL));
        LabelNode l5 = new LabelNode();
        toInject.add(new JumpInsnNode(Opcodes.IFLE, l5));
        
        //toInject.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        //toInject.add(new LdcInsnNode("patched moveEntity triggered"));
        //toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V"));
        
        toInject.add(new InsnNode(Opcodes.DCONST_0));
        toInject.add(new VarInsnNode(Opcodes.DSTORE, 1));
        
        toInject.add(new InsnNode(Opcodes.DCONST_0));
        toInject.add(new VarInsnNode(Opcodes.DSTORE, 3));

        toInject.add(new InsnNode(Opcodes.DCONST_0));
        toInject.add(new VarInsnNode(Opcodes.DSTORE, 5));
        
        /*
        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));        
        toInject.add(new TypeInsnNode(Opcodes.INSTANCEOF, isObfuscated ? "uf" : "net/minecraft/entity/player/EntityPlayer"));
        toInject.add(new JumpInsnNode(Opcodes.IFNE, l5));

        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                      
        String entity = isObfuscated ? "nn" : "net/minecraft/entity/Entity";
        String world = isObfuscated ? "abw" : "net/minecraft/world/World";        
        
        //toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "worldObj", "Lnet/minecraft/world/World;"));
        // not p, maybe h?
        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, entity, isObfuscated ? "h" : "worldObj", "L"+world+";"));

        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));        
        //toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", isObfuscated ? "e" : "removeEntity", "(Lnet/minecraft/entity/Entity;)V"));
        toInject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, world, isObfuscated ? "e" : "removeEntity", "(L"+entity+";)V"));
        */
        
        toInject.add(l5);
        
        m.instructions.insertBefore(currentNode, toInject);
        System.out.println("FF finished patching net.minecraft.entity.Entity/moveEntity");
        
/*
        iter = m.instructions.iterator();
        // currentNode is first instruction in the function
        currentNode = iter.next();
        index=0;
        while(iter.hasNext()) {
          System.out.println(""+currentNode);
          index++;
          currentNode=iter.next();
          if(index > 20) break;
        }
        */        
        break;
      }
      //ASM specific for cleaning up and returning the final bytes for JVM processing.
      //ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    }
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    classNode.accept(writer);
    return writer.toByteArray();        

    //System.out.println("Warning, couldn't find the target method to patch...");
    //return bytecode;
  }
}
