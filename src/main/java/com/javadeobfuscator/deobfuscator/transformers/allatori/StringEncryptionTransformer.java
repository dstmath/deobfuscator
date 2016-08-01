/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.allatori;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.Context;

import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.commons.Method;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LdcInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class StringEncryptionTransformer extends Transformer {

    public StringEncryptionTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));

        AtomicInteger x = new AtomicInteger();

        classNodes().forEach(wrappedClassNode -> {
            wrappedClassNode.classNode.methods.forEach(methodNode -> {
                AnalyzerResult result = MethodAnalyzer.analyze(wrappedClassNode.classNode, methodNode);
                for (int index = 0; index < methodNode.instructions.size(); index++) {
                    AbstractInsnNode current = methodNode.instructions.get(index);
                    if (current instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) current;
                        MethodFrame frame = (MethodFrame) result.getFrames().get(m).get(0);
                        String strCl = m.owner;
                        if (m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                            if (frame.getArgs().get(0) instanceof LdcFrame) {
                                LdcFrame ldcFrame = (LdcFrame) frame.getArgs().get(0);
                                LdcInsnNode insn = (LdcInsnNode) result.getMapping().get(ldcFrame);
                                Context context = new Context(provider);
                                context.push(wrappedClassNode.classNode.name, methodNode.name, wrappedClassNode.constantPoolSize);
                                if (classes.containsKey(strCl)) {
                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
                                    try {
                                        Object o = MethodExecutor.execute(wrappedClassNode, decrypterNode, Collections.singletonList(JavaValue.valueOf(insn.cst)), null, context);
                                        insn.cst = o;
                                        methodNode.instructions.remove(current);
                                    } catch (Throwable t) {
                                        System.out.println("Error while decrypting Allatori string.");
                                        System.out.println("Are you sure you're deobfuscating something obfuscated by Allatori?");
                                        System.out.println(wrappedClassNode.classNode.name + " " + methodNode.name + methodNode.desc + " " + m.owner + " " + m.name + m.desc);
                                        t.printStackTrace(System.out);
                                    }
                                }
                            }
                        }
                    }
                }
            });
        });
    }
}
