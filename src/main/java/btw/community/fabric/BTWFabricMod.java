package btw.community.fabric;

import net.devtech.grossfabrichacks.entrypoints.PrePreLaunch;
import net.devtech.grossfabrichacks.transformer.TransformerApi;
import net.devtech.grossfabrichacks.transformer.asm.AsmClassTransformer;
import net.devtech.grossfabrichacks.unsafe.UnsafeUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftApplet;
import net.superblaubeere27.asmdelta.ASMDeltaPatch;
import net.superblaubeere27.asmdelta.ASMDeltaTransformer;
import net.superblaubeere27.asmdelta.difference.AbstractDifference;
import org.objectweb.asm.tree.*;
import sun.misc.Unsafe;

import java.applet.Applet;
import java.applet.AppletStub;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

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
        // This following code will set the verifier to none.
        // Because the JVM does not like it when you totally modify the bytecode at runtime.
        try {
            HashMap<String, JVMStruct> structs = getStructs();
            //System.out.println("Structs: " + structs);
            HashMap<String, BTWFabricMod.JVMType> types = getTypes(structs);
            //System.out.println("Types: " + types);
            List<BTWFabricMod.JVMFlag> flags = getFlags(types);
            //System.out.println("Flags: " + flags);
            Unsafe theUnsafe = (Unsafe) UnsafeUtil.theUnsafe;
            for (JVMFlag flag : flags) {
                if (flag.name.equals("BytecodeVerificationLocal")
                        || flag.name.equals("BytecodeVerificationRemote")) {
                    System.out.println(theUnsafe.getByte(flag.address));
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
                    System.out.println(theUnsafe.getByte(flag.address));
                }
            }

        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }


        agentmain("", null);
    }

    void disableBytecodeVerifier() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Unsafe theUnsafe = (Unsafe) UnsafeUtil.theUnsafe;
        List<JVMFlag> flags = getFlags(getTypes(getStructs()));

        for (JVMFlag flag : flags) {
            if (flag.name.equals("BytecodeVerificationLocal")
                    || flag.name.equals("BytecodeVerificationRemote")) {
                theUnsafe.putByte(flag.address, (byte) 0);
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
        Unsafe theUnsafe = (Unsafe) UnsafeUtil.theUnsafe;
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
        long fieldOffset = theUnsafe.getLong((long) findNative.invoke(null, null, "gHotSpotVMStructs"));
        Function<String, Long> symbol = (String name) -> {
            try {
                return theUnsafe.getLong((Long) findNative.invoke(null, null, name));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        };
        Function<String, Long> offsetSymbol = name -> symbol.apply("gHotSpotVMStructEntry" + name + "Offset");
        Function<Long, String> derefReadString = addr -> {
            Long l = theUnsafe.getLong(addr);
            StringBuilder sb = new StringBuilder();
            if (l != 0) {
                while (true) {
                    char c = (char) theUnsafe.getByte(l);
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
            boolean isStatic = theUnsafe.getInt(currentEntry + offsetSymbol.apply("IsStatic")) != 0;

            Long offsetOffset = isStatic ? offsetSymbol.apply("Address") : offsetSymbol.apply("Offset");
            Long offset = theUnsafe.getLong(currentEntry + offsetOffset);

            structs.putIfAbsent(typeName, new JVMStruct(typeName));
            JVMStruct struct = structs.get(typeName);
            struct.set(fieldName, new BTWFabricMod.Field(fieldName, typeString, offset, isStatic));

            currentEntry += arrayStride;
        }
        return structs;
    }

    HashMap<String, JVMType> getTypes(HashMap<String, JVMStruct> structs)  {
        Unsafe theUnsafe = (Unsafe) UnsafeUtil.theUnsafe;
        Function<String, Long> symbol = (String name) -> {
            return theUnsafe.getLong((Long) findNative(name));
        };
        Function<String, Long> offsetSymbol = name -> symbol.apply("gHotSpotVMTypeEntry" + name + "Offset");
        Function<Long, String> derefReadString = addr -> {
            Long l = theUnsafe.getLong(addr);
            StringBuilder sb = new StringBuilder();
            if (l != 0) {
                while (true) {
                    char c = (char) theUnsafe.getByte(l);
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

            int size = theUnsafe.getInt(entry + offsetSymbol.apply("Size"));
            boolean oop = theUnsafe.getInt(entry + offsetSymbol.apply("IsOopType")) != 0;
            boolean int_ = theUnsafe.getInt(entry + offsetSymbol.apply("IsIntegerType")) != 0;
            boolean unsigned = theUnsafe.getInt(entry + offsetSymbol.apply("IsUnsigned")) != 0;

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
        Unsafe theUnsafe = (Unsafe) UnsafeUtil.theUnsafe;
        ArrayList<JVMFlag> jvmFlags = new ArrayList<JVMFlag>();

        JVMType flagType = types.get("Flag") != null ? types.get("Flag") : types.get("JVMFlag");
        if (flagType == null) {
            throw new RuntimeException("Could not resolve type 'Flag'");
        }

        BTWFabricMod.Field flagsField = flagType.fields.get("flags");
        if (flagsField == null) {
            throw new RuntimeException("Could not resolve field 'Flag.flags'");
        }
        long flags = theUnsafe.getAddress(flagsField.offset);

        BTWFabricMod.Field numFlagsField = flagType.fields.get("numFlags");
        int numFlags = theUnsafe.getInt(numFlagsField.offset);

        BTWFabricMod.Field nameField = flagType.fields.get("_name");

        BTWFabricMod.Field addrField = flagType.fields.get("_addr");

        for (long i = 0; i < numFlags; i++) {
            long flagAddress = flags + (i * flagType.size);
            long flagValueAddress = theUnsafe.getAddress(flagAddress + addrField.offset);
            long flagNameAddress = theUnsafe.getAddress(flagAddress + nameField.offset);

            String flagName;
            StringBuilder sb = new StringBuilder();
            if (flagNameAddress != 0) {
                while (true) {
                    char c = (char) theUnsafe.getByte(flagNameAddress);
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
            AsmClassTransformer asmClassTransformer = new AsmClassTransformer() {
                @Override
                public void transform(String name, ClassNode node) {
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

                                        // call BTWFabricMod.initArgs, use the supplied applet
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
                }
            };

            TransformerApi.registerPreMixinAsmClassTransformer(asmClassTransformer);
            TransformerApi.registerPreMixinRawClassTransformer(transformer);
        } catch (Throwable e) {
            System.err.println("Failed to initialize agent");
            e.printStackTrace();
        }
    }

    public static void initArgs(MinecraftApplet applet) {
        java.lang.reflect.Field s;
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
