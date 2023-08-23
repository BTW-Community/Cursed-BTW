package btw.community.fabric;

import net.devtech.grossfabrichacks.entrypoints.PrePreLaunch;
import net.devtech.grossfabrichacks.instrumentation.InstrumentationApi;
import net.devtech.grossfabrichacks.transformer.TransformerApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftApplet;
import net.superblaubeere27.asmdelta.ASMDeltaPatch;
import net.superblaubeere27.asmdelta.ASMDeltaTransformer;
import net.superblaubeere27.asmdelta.difference.AbstractDifference;
import org.objectweb.asm.tree.*;

import java.applet.Applet;
import java.applet.AppletStub;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;

import static org.objectweb.asm.Opcodes.*;

public class BTWFabricMod implements ModInitializer, PrePreLaunch {
    public static String[] args = null;
    @Override
    public void onInitialize() {
    }

    public static String getIntermediary(String cls) {
        return FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", cls);
    }

    //@Override
    public void onPrePrePreLaunch() {

    }
    @Override
    public void onPrePreLaunch() {
        agentmain("", null);
    }

    //@Override
    public void onPreLaunch() {

    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        ASMDeltaPatch patch;

        try (InputStream inputStream = BTWFabricMod.class.getResourceAsStream(FabricLoader.getInstance().isDevelopmentEnvironment() ? "/output.patch" : "/output-rel.patch")) {
            patch = ASMDeltaPatch.read(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read patch");
        }

        HashMap<String, HashSet<AbstractDifference>> patchMap = new HashMap<>();

        // Grouping patches by class names
        for (AbstractDifference abstractDifference : patch.getDifferenceList()) {
            patchMap.computeIfAbsent(abstractDifference.getClassName().replace('/', '.'), e -> new HashSet<>()).add(abstractDifference);
        }

        System.out.println("Loaded " + patch.getDifferenceList().size() + " patches for " + patchMap.size() + " classes");
        System.out.println("Applying patch " + patch.getPatchName());

        try {
            ASMDeltaTransformer transformer = new ASMDeltaTransformer(patchMap);
            TransformerApi.registerPreMixinAsmClassTransformer((name, node) -> {
                String className = getIntermediary("net.minecraft.class_343");
                String classNameMC = getIntermediary("net.minecraft.client.main.Main");
                if (name.equals(className) || name.equals(className.replace('.', '/'))) {
                    System.out.println("Found Minecraft Canvas class");
                    for (MethodNode method : node.methods) {
                        // Inject main into constructor
                        if ("<init>".equals(method.name)) {
                            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                                if (insn.getOpcode() == RETURN) {
                                    InsnList list = new InsnList();
                                    // print hello world
                                    list.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                                    list.add(new LdcInsnNode("Hello BTW World!"));
                                    list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));

                                    // call BTWFabricMod.doIt, use the supplied applet
                                    list.add(new VarInsnNode(ALOAD, 1));
                                    list.add(new MethodInsnNode(INVOKESTATIC, "btw/community/fabric/BTWFabricMod", "initArgs", "(Lnet/minecraft/client/MinecraftApplet;)V", false));


                                    // get BTWFabricMod.args, call static main string args
                                    list.add(new FieldInsnNode(GETSTATIC, "btw/community/fabric/BTWFabricMod", "args", "[Ljava/lang/String;"));
                                    list.add(new MethodInsnNode(INVOKESTATIC, getIntermediary("net.minecraft.class_1600").replace('.', '/'), "main", "([Ljava/lang/String;)V", false));

                                    // Throw exception
                                    list.add(new TypeInsnNode(NEW, "java/lang/RuntimeException"));
                                    list.add(new InsnNode(DUP));
                                    list.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/InterruptedException", "<init>", "()V", false));
                                    list.add(new InsnNode(ATHROW));

                                    method.instructions.insertBefore(insn, list);
                                    break;
                                }
                            }
                        }
                    }
                }
                if (name.equals(classNameMC) || name.equals(classNameMC.replace('.', '/'))) {
                    System.out.println("Found Minecraft Main class");
                    for (MethodNode method : node.methods) {
                        // Inject main into constructor
                        if ("start".equals(method.name)) {
                            // Find first return statement
                            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                                if (insn.getOpcode() == RETURN) {
                                    InsnList list = new InsnList();
                                    // wait for this.mcThread to finish
                                    list.add(new VarInsnNode(ALOAD, 0));
                                    list.add(new FieldInsnNode(GETFIELD, classNameMC.replace('.', '/'), "mcThread", "Ljava/lang/Thread;"));
                                    list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Thread", "join", "()V", false));

                                    method.instructions.insertBefore(insn, list);
                                    break;
                                }
                            }
                        }
                    }
                }
            });
            TransformerApi.registerPreMixinRawClassTransformer(transformer);
        } catch (Throwable e) {
            System.err.println("Failed to initialize agent");
            e.printStackTrace();
        }
    }

    public static void initArgs(MinecraftApplet applet) {
        Field s;
        try {
            s = Applet.class.getDeclaredField("stub");
            s.setAccessible(true);
            AppletStub stub = (AppletStub) s.get(applet);
            args = new String[] {"--username", stub.getParameter("username"), "--session", stub.getParameter("sessionid"), "--gameDir", stub.getParameter("gameDir") == null ? "." : stub.getParameter("gameDir")};
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
