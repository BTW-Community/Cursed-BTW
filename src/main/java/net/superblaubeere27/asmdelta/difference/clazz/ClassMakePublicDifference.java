/*
 * Copyright (c) 2019 superblaubeere27
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.superblaubeere27.asmdelta.difference.clazz;

import net.superblaubeere27.asmdelta.difference.AbstractDifference;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;


public class ClassMakePublicDifference extends AbstractDifference {
    private String className;
    public ClassMakePublicDifference(String className) {
        this.className = className;
    }

    public static ClassMakePublicDifference createNew(String className) {
        return new ClassMakePublicDifference(className);
    }


    @Override
    public void apply(HashMap<String, ClassNode> classes) {
        System.out.println("Making " + className + " public");
        ClassNode node = classes.get(className);
        node.access |= ACC_PUBLIC | ACC_SUPER;
        for (MethodNode method : node.methods) {
            method.access = method.access | ACC_PUBLIC;
        }
        for (FieldNode field : node.fields) {
            field.access = field.access | ACC_PUBLIC;
        }
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public boolean canBeAppliedAtRuntime() {
        return true;
    }
}
