/*
 * Copyright (c) 2019 superblaubeere27
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.superblaubeere27.asmdelta.difference.methods;

import net.superblaubeere27.asmdelta.difference.AbstractDifference;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AddMethodDifference extends AbstractDifference {
    private String className;
    private MethodNode methodNode;

    public AddMethodDifference(String className, MethodNode methodNode) {
        this.className = className;
        this.methodNode = methodNode;
    }

    @Override
    public void apply(HashMap<String, ClassNode> classes) {
        ClassNode classNode = classes.get(className);

        List<MethodNode> methods = classNode.methods;

        if (methods == null) { // According to the documentation, methods might be null
            classNode.methods = methods = new ArrayList<>();
        }

        // Check if the method already exists
        MethodNode existingMethod = null;
        for (MethodNode method : methods) {
            if (method.name.equals(methodNode.name) && method.desc.equals(methodNode.desc)) {
                existingMethod = method;
                break;
            }
        }
        // Yeet the old method, who needs it anyway
        methods.remove(existingMethod);
        methods.add(methodNode);

    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public boolean canBeAppliedAtRuntime() {
        return false;
    }
}
