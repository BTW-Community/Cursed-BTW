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
import net.superblaubeere27.asmdelta.difference.VerificationException;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.List;

public class MethodLocalVariableDifference extends AbstractDifference {
    private String className;
    public String methodName;
    public String methodDesc;
    public List<LocalVariableNode> localVariables;

    public MethodLocalVariableDifference(String className, String methodName, String methodDesc, List<LocalVariableNode> localVariables) {
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.localVariables = localVariables;
    }

    @Override
    public void apply(HashMap<String, ClassNode> classes) throws VerificationException {
        MethodNode methodNode = verifyNotNull(verifyNotNull(classes.get(className), "Class wasn't found").methods, "Method wasn't found")
                .stream()
                .filter(m -> m.name.equals(methodName) && m.desc.equals(methodDesc))
                .findFirst()
                .orElseThrow(VerificationException::new);

        methodNode.localVariables = localVariables;
    }

    @Override
    public boolean canBeAppliedAtRuntime() {
        return true;
    }

    @Override
    public String getClassName() {
        return className;
    }
}
