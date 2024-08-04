package btw.community.fabric;

import net.devtech.grossfabrichacks.entrypoints.PrePreLaunch;
import net.devtech.grossfabrichacks.mixin.GrossFabricHacksPlugin;
import net.devtech.grossfabrichacks.transformer.TransformerApi;
import net.devtech.grossfabrichacks.transformer.asm.AsmClassTransformer;
import net.devtech.grossfabrichacks.unsafe.UnsafeUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.game.minecraft.Hooks;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.superblaubeere27.asmdelta.ASMDeltaPatch;
import net.superblaubeere27.asmdelta.ASMDeltaTransformer;
import net.superblaubeere27.asmdelta.difference.AbstractDifference;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import sun.misc.Unsafe;

import java.applet.Applet;
import java.applet.AppletStub;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

public class BTWFabricMod implements ModInitializer, PrePreLaunch {
    private static boolean DISABLE_BYTECODE_VERIFIER = false;
    public static String[] args = null;
    private static ASMDeltaTransformer transformer;
    private static AsmClassTransformer asmClassTransformer;

    @Override
    public void onInitialize() {
    }

    public static String getMappedName(String cls) {
        return FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", cls);
    }

    public static String getMappedNameField(String cls, String field, String desc) {
        return FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", cls, field, desc);
    }

    public static String getMappedNameMethod(String cls, String method, String desc) {
        return FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", cls, method, desc);
    }

    //@Override
    public void onPrePrePreLaunch() {

    }
    @Override
    public void onPrePreLaunch() {
        if (DISABLE_BYTECODE_VERIFIER) {
            // This following code will set the verifier to none.
            // Because the JVM does not like it when you totally modify the bytecode at runtime.
            try {
                HashMap<String, JVMStruct> structs = getStructs();
                //System.out.println("Structs: " + structs);
                HashMap<String, BTWFabricMod.JVMType> types = getTypes(structs);
                //System.out.println("Types: " + types);
                List<BTWFabricMod.JVMFlag> flags = getFlags(types);
                //System.out.println("Flags: " + flags);
                for (JVMFlag flag : flags) {
                    if (flag.name.equals("BytecodeVerificationLocal")
                            || flag.name.equals("BytecodeVerificationRemote")) {
                        System.out.println(UnsafeUtil.getByte(flag.address));
                    }
                }
                disableBytecodeVerifier();
                structs = getStructs();
                //System.out.println("Structs: " + structs);
                types = getTypes(structs);
                //System.out.println("Types: " + types);
                flags = getFlags(types);
                //System.out.println("Flags: " + flags);
                for (JVMFlag flag : flags) {
                    if (flag.name.equals("BytecodeVerificationLocal")
                            || flag.name.equals("BytecodeVerificationRemote")) {
                        System.out.println(UnsafeUtil.getByte(flag.address));
                    }
                }

            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        agentmain("", null);
    }

    private void disableBytecodeVerifier() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        List<JVMFlag> flags = getFlags(getTypes(getStructs()));

        for (JVMFlag flag : flags) {
            if (flag.name.equals("BytecodeVerificationLocal")
                    || flag.name.equals("BytecodeVerificationRemote")) {
                UnsafeUtil.putByte(flag.address, (byte) 0);
            }
        }
    }

    private void enableBytecodeVerifier() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Unsafe theUnsafe = (Unsafe) UnsafeUtil.theUnsafe;
        List<JVMFlag> flags = getFlags(getTypes(getStructs()));

        for (JVMFlag flag : flags) {
            if (flag.name.equals("BytecodeVerificationRemote")) {
                theUnsafe.putByte(flag.address, (byte) 1);
            }
        }
    }

    private long findNative(String name) {
        try {
            Method findNative = ClassLoader.class.getDeclaredMethod("findNative", ClassLoader.class, String.class);
            findNative.setAccessible(true);
            return (long) findNative.invoke(null, null, name);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    HashMap<String, JVMStruct> getStructs() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // get findNative reference from ClassLoader.class
        Method findNative = ClassLoader.class.getDeclaredMethod("findNative", ClassLoader.class, String.class);
        Method loadLibrary = ClassLoader.class.getDeclaredMethod("loadLibrary", Class.class, String.class, boolean.class);
        Method initializePath = ClassLoader.class.getDeclaredMethod("initializePath", String.class);
        findNative.setAccessible(true);
        initializePath.setAccessible(true);
        loadLibrary.setAccessible(true);
        loadLibrary.invoke(null, null, "server/jvm", false);
        //System.out.println("findNative: " + findNative.invoke(null, null, "gHotSpotVMStructs"));
        // get the field offset
        long fieldOffset = UnsafeUtil.getLong((long) findNative.invoke(null, null, "gHotSpotVMStructs"));
        Function<String, Long> symbol = (String name) -> {
            try {
                return UnsafeUtil.getLong((Long) findNative.invoke(null, null, name));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        };
        Function<String, Long> offsetSymbol = name -> symbol.apply("gHotSpotVMStructEntry" + name + "Offset");
        Function<Long, String> derefReadString = addr -> {
            Long l = UnsafeUtil.getLong(addr);
            StringBuilder sb = new StringBuilder();
            if (l != 0) {
                while (true) {
                    char c = (char) UnsafeUtil.getByte(l);
                    if (c == 0)
                        break;
                    sb.append(c);
                    l += 1;
                }
            }
            return sb.toString();
        };

        Long currentEntry = symbol.apply("gHotSpotVMStructs");
        Long arrayStride = symbol.apply("gHotSpotVMStructEntryArrayStride");

        HashMap<String, JVMStruct> structs = new HashMap<String, JVMStruct>();
        while (true) {
            String typeName = derefReadString.apply(currentEntry + offsetSymbol.apply("TypeName"));
            String fieldName = derefReadString.apply(currentEntry + offsetSymbol.apply("FieldName"));
            if (typeName == null || fieldName == null || typeName.isEmpty() || fieldName.isEmpty())
                break;
            //System.out.println(typeName + " " + fieldName);
            String typeString = derefReadString.apply(currentEntry + offsetSymbol.apply("TypeString"));
            boolean isStatic = UnsafeUtil.getInt(currentEntry + offsetSymbol.apply("IsStatic")) != 0;

            Long offsetOffset = isStatic ? offsetSymbol.apply("Address") : offsetSymbol.apply("Offset");
            Long offset = UnsafeUtil.getLong(currentEntry + offsetOffset);

            structs.putIfAbsent(typeName, new JVMStruct(typeName));
            JVMStruct struct = structs.get(typeName);
            struct.set(fieldName, new BTWFabricMod.Field(fieldName, typeString, offset, isStatic));

            currentEntry += arrayStride;
        }
        return structs;
    }

    HashMap<String, JVMType> getTypes(HashMap<String, JVMStruct> structs)  {
        Function<String, Long> symbol = (String name) -> {
            return UnsafeUtil.getLong((Long) findNative(name));
        };
        Function<String, Long> offsetSymbol = name -> symbol.apply("gHotSpotVMTypeEntry" + name + "Offset");
        Function<Long, String> derefReadString = addr -> {
            Long l = UnsafeUtil.getLong(addr);
            StringBuilder sb = new StringBuilder();
            if (l != 0) {
                while (true) {
                    char c = (char) UnsafeUtil.getByte(l);
                    if (c == 0)
                        break;
                    sb.append(c);
                    l += 1;
                }
            }
            return sb.toString();
        };

        Long entry = symbol.apply("gHotSpotVMTypes");
        Long arrayStride = symbol.apply("gHotSpotVMTypeEntryArrayStride");

        HashMap<String, JVMType> types = new HashMap<String, JVMType>();

        while (true) {
            String typeName = derefReadString.apply(entry + offsetSymbol.apply("TypeName"));
            if (typeName == null || typeName.isEmpty()) break;

            String superClassName = derefReadString.apply(entry + offsetSymbol.apply("SuperclassName"));

            //System.out.println(typeName + " " + superClassName);

            int size = UnsafeUtil.getInt(entry + offsetSymbol.apply("Size"));
            boolean oop = UnsafeUtil.getInt(entry + offsetSymbol.apply("IsOopType")) != 0;
            boolean int_ = UnsafeUtil.getInt(entry + offsetSymbol.apply("IsIntegerType")) != 0;
            boolean unsigned = UnsafeUtil.getInt(entry + offsetSymbol.apply("IsUnsigned")) != 0;

            if (structs.get(typeName) != null) {
                HashMap<String, Field> structFields = structs.get(typeName).fields;
                JVMType t = new JVMType(
                        typeName, superClassName, size,
                        oop, int_, unsigned
                );
                types.put(typeName, t);

                if (structFields != null)
                    t.fields.putAll(structFields);
            }

            entry += arrayStride;
        }

        return types;
    }

    List<JVMFlag> getFlags(HashMap<String, JVMType> types)  {
        ArrayList<JVMFlag> jvmFlags = new ArrayList<JVMFlag>();

        JVMType flagType = types.get("Flag") != null ? types.get("Flag") : types.get("JVMFlag");
        if (flagType == null) {
            throw new RuntimeException("Could not resolve type 'Flag'");
        }

        BTWFabricMod.Field flagsField = flagType.fields.get("flags");
        if (flagsField == null) {
            throw new RuntimeException("Could not resolve field 'Flag.flags'");
        }
        long flags = UnsafeUtil.getAddress(flagsField.offset);

        BTWFabricMod.Field numFlagsField = flagType.fields.get("numFlags");
        int numFlags = UnsafeUtil.getInt(numFlagsField.offset);

        BTWFabricMod.Field nameField = flagType.fields.get("_name");

        BTWFabricMod.Field addrField = flagType.fields.get("_addr");

        for (long i = 0; i < numFlags; i++) {
            long flagAddress = flags + (i * flagType.size);
            long flagValueAddress = UnsafeUtil.getAddress(flagAddress + addrField.offset);
            long flagNameAddress = UnsafeUtil.getAddress(flagAddress + nameField.offset);

            String flagName;
            StringBuilder sb = new StringBuilder();
            if (flagNameAddress != 0) {
                while (true) {
                    char c = (char) UnsafeUtil.getByte(flagNameAddress);
                    if (c == 0)
                        break;
                    sb.append(c);
                    flagNameAddress += 1;
                }
            }
            flagName = sb.toString();
            if (flagName != null && !flagName.isEmpty()) {
                JVMFlag flag = new JVMFlag(flagName, flagValueAddress);
                jvmFlags.add(flag);
            }
        }

        return jvmFlags;
    }

    class JVMFlag /*(val name: String, val address: Long)*/ {
        String name;
        Long address;

        public JVMFlag(String name, Long address) {
            this.name = name;
            this.address = address;
        }
    }

    class JVMType {

        String type;
        String superClass;
        int size;
        boolean oop;
        boolean int_;
        boolean unsigned;

        HashMap<String, BTWFabricMod.Field> fields = new HashMap<String, BTWFabricMod.Field>();

        public JVMType(String type, String superClass, int size, boolean oop, boolean int_, boolean unsigned) {
            this.type = type;
            this.superClass = superClass;
            this.size = size;
            this.oop = oop;
            this.int_ = int_;
            this.unsigned = unsigned;
        }
    }

    class JVMStruct {
        private final String name;

        public JVMStruct(String name) {
            this.name = name;
        }

        HashMap<String, BTWFabricMod.Field> fields = new HashMap<String, BTWFabricMod.Field>();

        public BTWFabricMod.Field get(String f) {
            return fields.get(f);
        }
        public void set(String f, BTWFabricMod.Field value) {
            fields.put(f, value);
        }


    }
    private class Field {
        public final String name;
        public final String type;
        public final Long offset;
        public final Boolean isStatic;

        public Field(String name, String type, Long offset, Boolean isStatic) {
            this.name = name;
            this.type = type;
            this.offset = offset;
            this.isStatic = isStatic;
        }
    }

    //@Override
    public void onPreLaunch() {

    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        ASMDeltaPatch patch;

        try {
            if (FabricLoader.getInstance().isDevelopmentEnvironment())
                try (InputStream inputStream = BTWFabricMod.class.getResourceAsStream("/output-client-development.patch")) {
                    patch = ASMDeltaPatch.read(inputStream);
                }
            else try (InputStream inputStream = Knot.getLauncher().getEnvironmentType() == EnvType.CLIENT ? BTWFabricMod.class.getResourceAsStream("/output-client-release.patch") : BTWFabricMod.class.getResourceAsStream("/output-server-release.patch")) {
                patch = ASMDeltaPatch.read(inputStream);
            }
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
            transformer = new ASMDeltaTransformer(patchMap);
            asmClassTransformer = new AsmClassTransformer() {
                @Override
                public void transform(String name, ClassNode node) {
                    String classNameMC = getMappedName("net.minecraft.client.main.Main");
                    String classNameMinecraft = getMappedName("net.minecraft.class_1600");
                    String classNameDedicatedServer = getMappedName("net.minecraft.class_770");
                    //String classNameMinecraftServer = getMappedName("net.minecraft.server.MinecraftServer");

                    String startGameName = getMappedNameMethod("net.minecraft.class_1600", "method_2921", "()V");
                    String gameDirName = getMappedNameField("net.minecraft.class_1600", "field_3762", "Ljava/io/File;");

                    if (name.equals(classNameMC) || name.equals(classNameMC.replace('.', '/'))) {
                        System.out.println("Found Main class");
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
                    if (name.equals(classNameMinecraft) || name.equals(classNameMinecraft.replace('.', '/'))) {
                        System.out.println("Found Minecraft class");
                        for (MethodNode method : node.methods) {
                            if (startGameName.equals(method.name)) {
                                // Find first return statement
                                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                                    if (insn.getOpcode() == INVOKESTATIC && ((MethodInsnNode) insn).name.equals("setResizable") && ((MethodInsnNode) insn).desc.equals("(Z)V") && ((MethodInsnNode) insn).owner.equals("org/lwjgl/opengl/Display")) {
                                        InsnList list = new InsnList();
                                        // get net/minecraft/class_1600.field_3762 Ljava/io/File; (not static)
                                        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                        list.add(new FieldInsnNode(Opcodes.GETFIELD, classNameMinecraft.replace('.', '/'), gameDirName, "Ljava/io/File;"));
                                        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, "startClient", "(Ljava/io/File;Ljava/lang/Object;)V", false));
                                        method.instructions.insertBefore(insn.getNext(), list);
                                        System.out.println("Injected startClient call");
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (name.equals(classNameDedicatedServer) || name.equals(classNameDedicatedServer.replace('.', '/'))) {
                        System.out.println("Found Dedicated Minecraft Server class");
                        for (MethodNode method : node.methods) {
                            // Inject main into constructor
                            if ("<init>".equals(method.name) && method.desc.equals("(Ljava/io/File;)V")) {
                                // Find first return statement
                                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                                    if (insn.getOpcode() == RETURN) {
                                        InsnList list = new InsnList();
                                        // get net/minecraft/class_1600.field_3762 Ljava/io/File; (not static)
                                        list.add(new VarInsnNode(ALOAD, 1));
                                        list.add(new VarInsnNode(ALOAD, 0));
                                        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, "startServer", "(Ljava/io/File;Ljava/lang/Object;)V", false));
                                        method.instructions.insertBefore(insn, list);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            };

            //TransformerApi.registerPreMixinAsmClassTransformer(asmClassTransformer);
            TransformerApi.registerPreMixinRawClassTransformer(transformer);
            TransformerApi.registerNullClassTransformer(transformer);
        } catch (Throwable e) {
            System.err.println("Failed to initialize agent");
            e.printStackTrace();
        }
        if (GrossFabricHacksPlugin.preApplyList == null) {
            GrossFabricHacksPlugin.preApplyList = new ArrayList<>();
        }
        try {
            GrossFabricHacksPlugin.preApplyList.add(BTWFabricMod.class.getMethod("performPreApplyOperation", String.class, ClassNode.class, String.class, IMixinInfo.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static void performPreApplyOperation(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        transformer.doTransform2(targetClassName, targetClass, false);

        // Use reflection to get MixinInfo.targetClasses
        ArrayList<ClassInfo> targetClasses;
        Set<ClassInfo.Method> methods;
        Set<ClassInfo.Field> fields;
        try {
            java.lang.reflect.Field f = mixinInfo.getClass().getDeclaredField("targetClasses");
            f.setAccessible(true);
            targetClasses = (ArrayList<ClassInfo>) f.get(mixinInfo);
            f = ClassInfo.class.getDeclaredField("methods");
            f.setAccessible(true);

            java.lang.reflect.Field f2 = ClassInfo.class.getDeclaredField("fields");
            f2.setAccessible(true);
            for (ClassInfo ci : targetClasses) {
                if (ci.getClassName().equals(targetClassName)) {
                    methods = (Set<ClassInfo.Method>) f.get(ci);
                    methods.clear();
                    methods.addAll(targetClass.methods.stream().map(m -> ci.new Method(m)).collect(Collectors.toSet()));

                    fields = (Set<ClassInfo.Field>) f2.get(ci);
                    fields.clear();
                    fields.addAll(targetClass.fields.stream().map(m -> ci.new Field(m)).collect(Collectors.toSet()));
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        asmClassTransformer.transform(targetClassName, targetClass);
    }
}
