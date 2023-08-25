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
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Textifier;

import java.util.Arrays;
import java.util.HashMap;

public class MethodInstructionDifference extends AbstractDifference {
    private String className;
    private String methodName;
    private String methodDesc;
    private MethodNode content;

    public MethodInstructionDifference(String className, String methodName, String methodDesc, MethodNode methodNode) {
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        //this.content = new MethodNode();

        this.content = methodNode;
    }

    @Override
    public void apply(HashMap<String, ClassNode> classes) throws VerificationException {
        try {
            /*if (classes.keySet().isEmpty()) {
                System.out.println("Classes is empty");
            } else {
                System.out.println(Arrays.toString(classes.keySet().toArray()));
            }*/
            MethodNode methodNode = verifyNotNull(verifyNotNull(classes.get(className), className + " class wasn't found").methods, "Field wasn't found")
                    .stream()
                    .filter(m -> m.name.equals(methodName) && m.desc.equals(methodDesc))
                    .findFirst()
                    .orElseThrow(VerificationException::new);
            methodNode.instructions.clear();
            methodNode.instructions = new InsnList();
            AbstractInsnNode[] abstractInsnNodes = this.content.instructions.toArray();
            for (AbstractInsnNode abstractInsnNode : abstractInsnNodes) {
                methodNode.instructions.add(abstractInsnNode);
            }
            //this.content.instructions.clear();
            methodNode.tryCatchBlocks = this.content.tryCatchBlocks;
        } catch (VerificationException e) {
            System.out.println("Method " + methodName + " not found");
            throw e;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            //throw e;
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

    public String getMethodName() {
        return methodName;
    }
}
