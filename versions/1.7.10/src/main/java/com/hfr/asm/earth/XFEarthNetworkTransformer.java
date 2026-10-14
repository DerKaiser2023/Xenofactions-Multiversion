package com.hfr.asm.earth;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Optional, exact pinned native methods. Changes subscription timing and transport, not cube codecs. */
public final class XFEarthNetworkTransformer implements IClassTransformer {
    private static final String NATIVE="com.cardinalstar.cubicchunks.", BRIDGE="com/hfr/world/earth/network/XFEarthHeightSync";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null) return null;
        boolean manager=(NATIVE+"server.CubicPlayerManager").equals(transformedName);
        boolean channel=(NATIVE+"network.NetworkChannel").equals(transformedName);
        boolean sync=(NATIVE+"server.WorldSyncStateMachine").equals(transformedName);
        if(!manager&&!channel&&!sync) return bytes;
        ClassNode node=new ClassNode(); new ClassReader(bytes).accept(node,0);
        FMLDeobfuscatingRemapper remapper=FMLDeobfuscatingRemapper.INSTANCE;
        // Class transforms can be requested more than once by development launchers.
        for(MethodNode method:node.methods) for(AbstractInsnNode insn:method.instructions.toArray())
            if(insn instanceof MethodInsnNode&&BRIDGE.equals(((MethodInsnNode)insn).owner)) return bytes;
        int add=0,remove=0,send=0,descriptions=0;
        for(MethodNode method:node.methods) {
            String mapped=remapper.mapMethodName(node.name,method.name,method.desc), desc=remapper.mapMethodDesc(method.desc);
            if(manager&&"(Lnet/minecraft/entity/player/EntityPlayerMP;)V".equals(desc)) {
                if("addPlayer".equals(method.name)||"func_72683_a".equals(mapped)) {
                    add++; conditionalReturn(method,"deferSubscription","(Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayerMP;)Z",0,1);
                } else if("removePlayer".equals(method.name)||"func_72695_c".equals(mapped)) {
                    remove++;
                    for(AbstractInsnNode insn:method.instructions.toArray()) if(insn.getOpcode()==Opcodes.RETURN) {
                        InsnList hook=new InsnList(); hook.add(new VarInsnNode(Opcodes.ALOAD,0)); hook.add(new VarInsnNode(Opcodes.ALOAD,1));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"removed","(Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayerMP;)V",false));
                        method.instructions.insertBefore(insn,hook);
                    }
                }
            }
            if(channel&&"sendToPlayer".equals(method.name)
                    &&"(Lcom/cardinalstar/cubicchunks/network/CCPacket;Lnet/minecraft/entity/player/EntityPlayerMP;)V".equals(desc)) {
                send++; conditionalReturn(method,"sendNative","(Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayerMP;)Z",1,2);
            }
            if(sync) for(AbstractInsnNode insn:method.instructions.toArray()) if(insn instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)insn;
                if("net/minecraft/network/NetHandlerPlayServer".equals(remapper.mapType(call.owner))
                        &&"(Lnet/minecraft/network/Packet;)V".equals(remapper.mapMethodDesc(call.desc))
                        &&("sendPacket".equals(call.name)||"func_147359_a".equals(remapper.mapMethodName(call.owner,call.name,call.desc)))) {
                    descriptions++;
                    method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"sendDescription",
                            "(Lnet/minecraft/network/NetHandlerPlayServer;Lnet/minecraft/network/Packet;)V",false));
                }
            }
        }
        if((manager&&(add!=1||remove!=1))||(channel&&send!=1)||(sync&&descriptions!=2))
            throw new IllegalStateException("Cannot install pinned Earth height networking hooks in "+transformedName
                    +" (add="+add+", remove="+remove+", send="+send+", descriptions="+descriptions+")");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static void conditionalReturn(MethodNode method,String target,String descriptor,int first,int second) {
        InsnList hook=new InsnList(); LabelNode resume=new LabelNode();
        hook.add(new VarInsnNode(Opcodes.ALOAD,first)); hook.add(new VarInsnNode(Opcodes.ALOAD,second));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,target,descriptor,false));
        hook.add(new JumpInsnNode(Opcodes.IFEQ,resume)); hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(resume); hook.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
        method.instructions.insert(hook);
    }
}
