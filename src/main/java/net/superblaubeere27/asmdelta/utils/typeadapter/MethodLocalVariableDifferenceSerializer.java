/*
 * Copyright (c) 2019 superblaubeere27
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.superblaubeere27.asmdelta.utils.typeadapter;

import com.google.gson.*;
import net.superblaubeere27.asmdelta.difference.AbstractDifference;
import net.superblaubeere27.asmdelta.difference.methods.MethodLocalVariableDifference;
import net.superblaubeere27.asmdelta.utils.Hex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Objects;

public class MethodLocalVariableDifferenceSerializer implements JsonSerializer<MethodLocalVariableDifference>, JsonDeserializer<MethodLocalVariableDifference> {

    private static final String CLASS_META_KEY = "CLASS_META_KEY";

    @Override
    public JsonElement serialize(MethodLocalVariableDifference methodLocalVariableDifference, java.lang.reflect.Type type, JsonSerializationContext jsonSerializationContext) {
        ClassNode classNode = new ClassNode();

        classNode.version = Opcodes.V17;
        classNode.access = Opcodes.ACC_PRIVATE;
        classNode.name = methodLocalVariableDifference.getClassName();
        classNode.signature = null;
        classNode.superName = "java/lang/Object";
        classNode.interfaces = Collections.emptyList();

        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC, methodLocalVariableDifference.methodName, methodLocalVariableDifference.methodDesc, null, null);
        methodNode.localVariables = methodLocalVariableDifference.localVariables;

        methodNode.accept(classNode);

        ClassWriter classWriter = new ClassWriter(0);


        classNode.accept(classWriter);

        JsonElement ele = new JsonObject();
        ele.getAsJsonObject().addProperty("content", Hex.encodeHexString(classWriter.toByteArray()));
        ele.getAsJsonObject().addProperty(CLASS_META_KEY,
                this.getClass().getCanonicalName());
        return ele;
    }

    @Override
    public MethodLocalVariableDifference deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        ClassNode classNode = new ClassNode();

        ClassReader cr;

        try {
            cr = new ClassReader(Hex.decodeHex(jsonElement.getAsJsonObject().get("content").getAsString()));
        } catch (IOException e) {
            System.err.println("Error while parsing method node");
            throw new JsonParseException(e);
        }

        cr.accept(classNode, 0);

        if (Objects.requireNonNull(classNode.methods).size() != 1) {
            System.err.println("Error while parsing method node");
            throw new JsonParseException("Class has more than a method");
        }
        return new MethodLocalVariableDifference(classNode.name, classNode.methods.get(0).name, classNode.methods.get(0).desc, classNode.methods.get(0).localVariables);
    }

}
