/*
 * MIT License
 *
 * Copyright (c) 2019 superblaubeere27
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.superblaubeere27.asmdelta;

import btw.community.fabric.BTWFabricMod;
import net.devtech.grossfabrichacks.transformer.asm.RawClassTransformer;
import net.devtech.grossfabrichacks.unsafe.UnsafeUtil;
import net.superblaubeere27.asmdelta.difference.AbstractDifference;
import net.superblaubeere27.asmdelta.difference.clazz.AddClassDifference;
import net.superblaubeere27.asmdelta.difference.clazz.ClassMakePublicDifference;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.transformers.MixinClassWriter;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.regex.PatternSyntaxException;

public class ASMDeltaTransformer implements ClassFileTransformer, RawClassTransformer {
    private static boolean EXPORT_CLASSES = false;
    private static boolean DEBUG = false;

    // Temporary hackaround
    private HashMap<String, byte[]> appliedPatches = new HashMap<>();

    private HashMap<String, HashSet<AbstractDifference>> patches;

    public ASMDeltaTransformer(HashMap<String, HashSet<AbstractDifference>> patches) {
        this.patches = patches;
        // manually do a few patches
        patches.put("net.minecraft.src.SpawnerAnimals$1", new HashSet<AbstractDifference>() {{
            add(new ClassMakePublicDifference("net/minecraft/src/SpawnerAnimals$1"));
        }});
        patches.put("net.minecraft.src.SpawnerAnimals$2", new HashSet<AbstractDifference>() {{
            add(new ClassMakePublicDifference("net/minecraft/src/SpawnerAnimals$2"));
        }});

        patches.put("(?=(emi\\.|btw\\.|net\\.minecraft\\.))([\\w\\.]+\\$)([\\w\\.]+\\$?)*", new HashSet<AbstractDifference>() {{
            add(new ClassMakePublicDifference("", true));
        }});
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        //println(className);
        // Check dot notation and slash notation. Use correct one. I dunno which one is correct, always mix them up
        if (className.contains(".")) {
            className = className.replace(".", "/");
        }
        if (noMatchingPatches(className)) {
            className = className.replace("/", ".");
            if (noMatchingPatches(className)) {
                //println("Skipping class: " + className);
                return classfileBuffer;
            }
        }

        ClassNode classNode = null;
        if (classfileBuffer != null) {
            ClassReader classReader = new ClassReader(classfileBuffer);
            classNode = new ClassNode();

            // The debug information is removed since it isn't needed and causes bugs (??)
            //classReader.accept(classNode, ClassReader.SKIP_DEBUG);
            classReader.accept(classNode, 0);
        }

        return doTransform(className, classNode);
    }

    private boolean noMatchingPatches(String className) {
        return !patches.containsKey(className) && patches.entrySet().parallelStream().noneMatch(x -> {
            try {
                return x.getValue().stream().anyMatch(y -> y.isRegex) && className.matches(x.getKey());
            } catch (PatternSyntaxException e) {
                return false;
            }
        });
    }

    public byte[] doTransform(String className, ClassNode classNode) {
        if (this.appliedPatches.containsKey(className)) {
            println("Error: Class " + className + " was already transformed!");
            conjureNeededClasses(className);
            return this.appliedPatches.get(className);
        }
        classNode = applyPatches(className, classNode, classNode == null);

        // Recalculate frames and maximums. All classes are available at runtime
        // so it makes the agent a lot safer from producing illegal classes
        ClassWriter classWriter = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        // set class version to Java 17
        classNode.version = 61;

        // This seems to work?
        for (MethodNode m : classNode.methods) {
            List<LocalVariableNode> toRemove = new ArrayList<>();
            HashMap<String, LocalVariableNode> seen = new HashMap<>();
            if (m == null || m.localVariables == null) continue;
            for (LocalVariableNode l : m.localVariables) {
                if (seen.containsKey(l.name)) {
                    println("Duplicate local variable: " + l.name + " in " + className + "." + m.name + m.desc);
                    println("    " + seen.get(l.name).desc + " vs " + l.desc);

                    // Attempt to fix it
                    if (seen.get(l.name).desc.equals(l.desc)) {
                        println("    Fixing...");
                        // Remove the duplicate
                        toRemove.add(l);
                    } else {
                        println("    Failed to fix");
                    }
                } else {
                    seen.put(l.name, l);
                }
            }
            m.localVariables.removeAll(toRemove);
        }
        /*List<FieldNode> toRemove = new ArrayList<>();
        HashMap<String, FieldNode> seen = new HashMap<>();
        for (FieldNode f : classNode.fields) {
            if (f == null) continue;
            if (seen.containsKey(f.name)) {
                println("Duplicate field: " + f.name + " in " + className + "." + f.name + f.desc);
                println("    " + seen.get(f.name).desc + " vs " + f.desc);

                // Attempt to fix it
                if (seen.get(f.name).desc.equals(f.desc)) {
                    println("    Fixing...");
                    // Remove the duplicate
                    toRemove.add(f);
                } else {
                    println("    Failed to fix");
                }
            } else {
                seen.put(f.name, f);
            }
        }
        classNode.fields.removeAll(toRemove);*/
        // Now check for inconsistent constant value types
        // Also seems to work?
        for (FieldNode f : classNode.fields) {
            if (f != null && f.value != null) {
                // Use the type of the Field as ground truth
                if (f.desc.equals("I")) {
                    if (!(f.value instanceof Integer)) {
                        println("Inconsistent constant value type: " + f.name + " in " + className);
                        println("    " + f.value.getClass().getName() + " vs " + f.desc);
                        println("    Fixing...");
                        f.value = ((Number) f.value).intValue();
                    }
                } else if (f.desc.equals("J")) {
                    if (!(f.value instanceof Long)) {
                        println("Inconsistent constant value type: " + f.name + " in " + className);
                        println("    " + f.value.getClass().getName() + " vs " + f.desc);
                        println("    Fixing...");
                        f.value = ((Number) f.value).longValue();
                    }
                } else if (f.desc.equals("F")) {
                    if (!(f.value instanceof Float)) {
                        println("Inconsistent constant value type: " + f.name + " in " + className);
                        println("    " + f.value.getClass().getName() + " vs " + f.desc);
                        println("    Fixing...");
                        f.value = ((Number) f.value).floatValue();
                    }
                } else if (f.desc.equals("D")) {
                    if (!(f.value instanceof Double)) {
                        println("Inconsistent constant value type: " + f.name + " in " + className);
                        println("    " + f.value.getClass().getName() + " vs " + f.desc);
                        println("    Fixing...");
                        f.value = ((Number) f.value).doubleValue();
                    }
                } else if (f.desc.equals("Z")) {
                    if (!(f.value instanceof Boolean)) {
                        println("Inconsistent constant value type: " + f.name + " in " + className);
                        println("    " + f.value.getClass().getName() + " vs " + f.desc);
                        println("    Fixing...");
                        f.value = ((Number) f.value).intValue() != 0;
                    }
                } else if (f.desc.equals("S")) {
                    if (!(f.value instanceof Short)) {
                        println("Inconsistent constant value type: " + f.name + " in " + className);
                        println("    " + f.value.getClass().getName() + " vs " + f.desc);
                        println("    Fixing...");
                        f.value = ((Number) f.value).shortValue();
                    }
                } else if (f.desc.equals("B")) {
                    if (!(f.value instanceof Byte)) {
                        println("Inconsistent constant value type: " + f.name + " in " + className);
                        println("    " + f.value.getClass().getName() + " vs " + f.desc);
                        println("    Fixing...");
                        f.value = ((Number) f.value).byteValue();
                    }
                } else if (f.desc.equals("C")) {
                    if (!(f.value instanceof Character)) {
                        println("Inconsistent constant value type: " + f.name + " in " + className);
                        println("    " + f.value.getClass().getName() + " vs " + f.desc);
                        println("    Fixing...");
                        f.value = (char) ((Number) f.value).intValue();
                    }
                }
            }
        }

        classNode.accept(classWriter);
        byte[] b = classWriter.toByteArray();
        // Export classes for debugging
        if (EXPORT_CLASSES) {
            try {
                Path exportPath = new File("./exported_classes/").toPath().resolve(className + ".class");
                Files.createDirectories(exportPath.getParent());
                Files.write(exportPath, b);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        conjureNeededClasses(className);

        this.appliedPatches.put(className, b);
        return b;
    }

    private void conjureNeededClasses(String className) {
        // Let me get my wand and conjure some classes
        // And yes, the dot notation is correct here
        if (className.equals(BTWFabricMod.getMappedName("net.minecraft.server.MinecraftServer"))) {
            conjureClass("net.minecraft.class_738");
        } else if (className.equals(BTWFabricMod.getMappedName("net.minecraft.class_101"))) {
            conjureClass("net.minecraft.class_1444");
        }
    }

    private List<String> conjuredClasses = new ArrayList<>();

    // This *can* fuck up the loading order
    private void conjureClass(String className) {
        if (conjuredClasses.contains(className)) return;
        ClassWriter classWriter;
        String name = BTWFabricMod.getMappedName(className);
        println("Conjuring class " + name);
        ClassNode classNode = new ClassNode();
        classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode = this.doTransform2(name, classNode, true);
        if (classNode == null) {
            return;
        }
        classNode.accept(classWriter);
        // Apply Mixins to conjured classes like this:
        // BTWFabricMod.class.getClassLoader() is KnotClassLoader in this case
        // kcl.delegate.getMixinTransformer().transformClassBytes(String name, String transformedName, byte[] basicClass)
        // BUT everything is private... so, Reflection time!
        try {
            Object kcl = BTWFabricMod.class.getClassLoader(); // KnotClassLoader
            Field f = kcl.getClass().getDeclaredField("delegate");
            f.setAccessible(true);
            Object delegate = f.get(kcl); // KnotClassDelegate
            f = delegate.getClass().getDeclaredField("mixinTransformer");
            f.setAccessible(true);
            Object mixinTransformer = f.get(delegate); // FabricMixinTransformerProxy
            Method m = mixinTransformer.getClass().getDeclaredMethod("transformClassBytes", String.class, String.class, byte[].class);
            m.setAccessible(true);
            byte[] mixinOutput = (byte[]) m.invoke(mixinTransformer, name, name, classWriter.toByteArray());
            UnsafeUtil.defineClass(name, mixinOutput);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            println(e.getMessage());
        }
        conjuredClasses.add(className);
    }

    public ClassNode doTransform2(String className, ClassNode classNode, boolean createNew) {
        println("Doing Transform2 " + className);
        if (this.appliedPatches.containsKey(className)) {
            println("Class " + className + " was already transformed!");
            conjureNeededClasses(className);
            return classNode;
        }
        classNode = applyPatches(className, classNode, createNew);
        if (classNode == null) {
            return null;
        }
        // set class version to Java 17
        classNode.version = 61;

        ClassWriter classWriter = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(classWriter);

        byte[] b = classWriter.toByteArray();
        this.appliedPatches.put(className, b);
        if (EXPORT_CLASSES) {
            try {
                Path exportPath = new File("./exported_classes/").toPath().resolve(className + ".class");
                Files.createDirectories(exportPath.getParent());
                Files.write(exportPath, b);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        conjureNeededClasses(className);
        return classNode;
    }

    private ClassNode applyPatches(String className, ClassNode classNode, boolean createNew) {
        println("Applying patches to " + className);

        HashMap<String, ClassNode> map = new HashMap<>();
        // The patch has to be applied to this one class only since they have already been grouped in AgentMain
        map.put(classNode != null ? classNode.name : className, classNode);

        // First apply regex patches
        for (Map.Entry<String, HashSet<AbstractDifference>> abstractDifferenceEntry : patches.entrySet().parallelStream().filter(x -> {
            try {
                return className.matches(x.getKey());
            } catch (PatternSyntaxException e) {
                return false;
            }
        }).toList()) {
            for (AbstractDifference abstractDifference : abstractDifferenceEntry.getValue()) {
                if (!abstractDifference.isRegex) {
                    continue;
                }
                try {
                    println("Applying regex patch: " + abstractDifference.getClassName());
                    //println(abstractDifference.getClassName());
                    if (createNew && abstractDifference instanceof AddClassDifference) {
                        //println("Creating new class");
                        abstractDifference.apply(map);
                    } else if (!createNew && !(abstractDifference instanceof AddClassDifference)) {
                        //println("Applying patch");
                        abstractDifference.apply(map);
                    }
                } catch (Exception e) {
                    //System.err.println("Failed to apply patch: " + e.getLocalizedMessage());
                    if (!(abstractDifference instanceof AddClassDifference)) {
                        throw new IllegalStateException("Failed to apply patch", e);
                    } else if (createNew && abstractDifference instanceof AddClassDifference) {
                        throw new IllegalStateException("Failed to add class", e);
                    }
                }
            }
        }

        for (AbstractDifference abstractDifference : patches.getOrDefault(className, new HashSet<>())) {
            if (abstractDifference.isRegex) {
                continue;
            }
            try {
                //println("Applying non-regex patch: " + abstractDifference.getClassName());
                if (createNew && abstractDifference instanceof AddClassDifference) {
                    //println("Creating new class");
                    abstractDifference.apply(map);
                } else if (!createNew && !(abstractDifference instanceof AddClassDifference)) {
                    //println("Applying patch");
                    abstractDifference.apply(map);
                }
            } catch (Exception e) {
                //System.err.println("Failed to apply patch: " + e.getLocalizedMessage());
                if (!(abstractDifference instanceof AddClassDifference)) {
                    throw new IllegalStateException("Failed to apply patch", e);
                } else if (createNew && abstractDifference instanceof AddClassDifference) {
                    throw new IllegalStateException("Failed to add class", e);
                }
            }
        }

        if (createNew) {
            return map.get(className.replace(".", "/"));
        } else {
            return classNode;
        }
    }

    private List<String> inProgress = new ArrayList<>();
    @Override
    public byte[] transform(String name, byte[] data) {
        if (inProgress.contains(name)) {
            return data;
        }
        inProgress.add(name);
        data = this.transform(null, name, null, null, data);
        inProgress.remove(name);
        return data;
    }

    private static void println(String s) {
        if (DEBUG) {
            System.out.println(s);
        }
    }
}