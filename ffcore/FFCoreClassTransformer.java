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
import org.objectweb.asm.tree.IntInsnNode;
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
    //System.out.println("Transforming: "+targetClassName);
    if (targetClassName.equals("nn")) return patchEntity(targetClassName, bytecode, true);
    else if (targetClassName.equals("net.minecraft.entity.Entity")) return patchEntity(targetClassName, bytecode, false);
    else if (targetClassName.equals("cn")) return patchMemoryConnection(targetClassName, bytecode, true);
    else if (targetClassName.equals("net.minecraft.network.MemoryConnection")) return patchMemoryConnection(targetClassName, bytecode, false);
    else if (targetClassName.equals("net.minecraft.world.gen.feature.WorldGenBigTree")) return patchWorldGenBigTree(targetClassName, bytecode, false);
    return bytecode;
  }

  private byte[] patchMemoryConnection(String targetClassName, byte[] bytecode, boolean isObfuscated) {
    System.out.println("[FFCore] is patching " + targetClassName+"  obf: "+isObfuscated);
    
    String targetMethodName = "";

    if (isObfuscated == true) targetMethodName = "b";
    else targetMethodName = "processReadPackets";

    ClassNode classNode = new ClassNode();
    ClassReader classReader = new ClassReader(bytecode);
    classReader.accept(classNode, 0);

    Iterator<MethodNode> methods = classNode.methods.iterator();
    while (methods.hasNext()) {
      MethodNode m = methods.next();
      if (m.name.equals(targetMethodName) && m.desc.equals("()V")) {        
        //System.out.println("Found the target method: "+m.desc);

        Iterator<AbstractInsnNode> iter = m.instructions.iterator();
        // currentNode is first instruction in the function
        AbstractInsnNode currentNode = iter.next();
        int index=0;
        while(currentNode.getOpcode() != Opcodes.SIPUSH && iter.hasNext()) {
          index++;
          currentNode=iter.next();
        }
        if(!iter.hasNext()) {
          System.out.println("[FF] Warning, could not find the correct instruction (SIPUSH) to patch");
          return bytecode;
        }

        InsnList replacedInstructions = new InsnList();
        replacedInstructions.add(new IntInsnNode(Opcodes.SIPUSH, 20000));
        m.instructions.insertBefore(currentNode, replacedInstructions);
        m.instructions.remove(currentNode);
        //System.out.println("FF finished patching net.minecraft.network.MemoryConnection/processReadPackets to allow more packages per tick");      
        break;        
      }
    }
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    classNode.accept(writer);
    return writer.toByteArray();        
  }

  private byte[] patchEntity(String targetClassName, byte[] bytecode, boolean isObfuscated) {
    System.out.println("[FFCore] is patching " + targetClassName+"  obf: "+isObfuscated);

    String targetMethodName = "";

    if (isObfuscated == true) targetMethodName = "d";
    else targetMethodName = "moveEntity";

    ClassNode classNode = new ClassNode();
    ClassReader classReader = new ClassReader(bytecode);
    classReader.accept(classNode, 0);

    Iterator<MethodNode> methods = classNode.methods.iterator();
    while (methods.hasNext()) {
      MethodNode m = methods.next();

      //System.out.println("Method: "+m.name+" sign: "+m.desc);
      if ((m.name.equals(targetMethodName) && m.desc.equals("(DDD)V"))) {        
        //System.out.println("Found the target method");

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
                
        toInject.add(new InsnNode(Opcodes.DCONST_0));
        toInject.add(new VarInsnNode(Opcodes.DSTORE, 1));
        
        toInject.add(new InsnNode(Opcodes.DCONST_0));
        toInject.add(new VarInsnNode(Opcodes.DSTORE, 3));

        toInject.add(new InsnNode(Opcodes.DCONST_0));
        toInject.add(new VarInsnNode(Opcodes.DSTORE, 5));
                
        toInject.add(l5);
        
        m.instructions.insertBefore(currentNode, toInject);
        //System.out.println("FF finished patching net.minecraft.entity.Entity/moveEntity");            
        break;
      }
    }
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    classNode.accept(writer);
    return writer.toByteArray();        
  }

  private byte[] patchWorldGenBigTree(String targetClassName, byte[] bytecode, boolean isObfuscated) {
    System.out.println("[FFCore] is patching " + targetClassName+"  obf: "+isObfuscated);

    String targetMethodName = "";

    if (isObfuscated == true) targetMethodName = "d";
    else targetMethodName = "placeBlockLine";
    String targetDesc = "([I[II)V";
    
    ClassNode classNode = new ClassNode();
    ClassReader classReader = new ClassReader(bytecode);
    classReader.accept(classNode, 0);

    Iterator<MethodNode> methods = classNode.methods.iterator();
    while (methods.hasNext()) {
      MethodNode m = methods.next();

      //System.out.println("Method: "+m.name+" sign: "+m.desc);
      if ((m.name.equals(targetMethodName) && m.desc.equals(targetDesc))) {        
        //System.out.println("Found the target method");

        Iterator<AbstractInsnNode> iter = m.instructions.iterator();
        // currentNode is first instruction in the function
        AbstractInsnNode currentNode = iter.next();
        currentNode=iter.next();
        currentNode=iter.next();
        int index=2;
                
        InsnList toInject = new InsnList();     

        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
        toInject.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/gen/feature/WorldGenBigTree", "worldObj", "Lnet/minecraft/world/World;"));
        toInject.add(new VarInsnNode(Opcodes.ALOAD, 1));        
        toInject.add(new VarInsnNode(Opcodes.ALOAD, 2));
        toInject.add(new VarInsnNode(Opcodes.ILOAD, 3));
        toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mbrx/ff/ecology/WorldGenTreeHelper", "placeBlockLine", "(Lnet/minecraft/world/World;[I[II)V"));
        toInject.add(new InsnNode(Opcodes.RETURN));       
        
        m.instructions.insertBefore(currentNode, toInject);
        //System.out.println("FF finished patching net.minecraft.entity.Entity/moveEntity");
        

        iter = m.instructions.iterator();
        // currentNode is first instruction in the function
        currentNode = iter.next();
        index=0;
        while(iter.hasNext()) {          
          //System.out.println(""+currentNode);
          /*if(currentNode instanceof FieldInsnNode) {
            FieldInsnNode node = (FieldInsnNode) currentNode;
            System.out.println("FieldInsnNode name: "+node.name+" desc: "+node.desc);
          }*/
          index++;
          currentNode=iter.next();
          if(index > 20) break;
        }
                
        break;
      }
    }
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    classNode.accept(writer);
    return writer.toByteArray();        
  }

  
  
}
