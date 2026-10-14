package com.hfr.asm.earth;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Exact Forge 1.7.10 startup method, including MCP development and runtime SRG names/descriptors. */
public final class XFEarthLaunchTransformer implements IClassTransformer {
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (!"net.minecraft.client.Minecraft".equals(transformedName) || bytes == null) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node,0);
        FMLDeobfuscatingRemapper remapper = FMLDeobfuscatingRemapper.INSTANCE;
        FieldNode directory = null; MethodNode launch = null;
        for (FieldNode field : node.fields) {
            String mapped = remapper.mapFieldName(node.name,field.name,field.desc);
            if (("mcDataDir".equals(field.name) || "field_71412_D".equals(mapped)) && "Ljava/io/File;".equals(field.desc)) {
                if (directory != null) throw new IllegalStateException("Ambiguous Minecraft save directory field");
                directory = field;
            }
        }
        for (MethodNode method : node.methods) {
            String mapped = remapper.mapMethodName(node.name,method.name,method.desc);
            if (("launchIntegratedServer".equals(method.name) || "func_71371_a".equals(mapped))
                    && "(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V".equals(remapper.mapMethodDesc(method.desc))) {
                if (launch != null) throw new IllegalStateException("Ambiguous Minecraft integrated server launch method");
                launch = method;
            }
        }
        if (directory == null || launch == null) throw new IllegalStateException("Cannot install the Forge 1.7.10 Earth pre-write safety gate");
        for (AbstractInsnNode insn : launch.instructions.toArray())
            if (insn instanceof MethodInsnNode && "com/hfr/world/earth/storage/XFEarthStorageIdentity".equals(((MethodInsnNode)insn).owner)
                    && "preflightClient".equals(((MethodInsnNode)insn).name)) return bytes;
        InsnList gate = new InsnList();
        gate.add(new VarInsnNode(Opcodes.ALOAD,0));
        gate.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,directory.name,directory.desc));
        gate.add(new VarInsnNode(Opcodes.ALOAD,1));
        gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"com/hfr/world/earth/storage/XFEarthStorageIdentity",
                "preflightClient","(Ljava/io/File;Ljava/lang/String;)V",false));
        launch.instructions.insert(gate);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
