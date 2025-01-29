//package dev.extframework.mixin.test.internal.inject.impl.code
//
//import dev.extframework.archives.transform.ByteCodeUtils
//import dev.extframework.mixin.RedefinitionFlags
//import dev.extframework.mixin.api.*
//import dev.extframework.mixin.internal.inject.impl.code.InjectionPoint
//import dev.extframework.mixin.internal.inject.impl.code.InstructionInjectionData
//import dev.extframework.mixin.internal.inject.impl.code.InstructionInjector
//import dev.extframework.mixin.internal.inject.impl.method.MethodInjector
//import dev.extframework.mixin.internal.util.descriptor
//import dev.extframework.mixin.test.classNode
//import dev.extframework.mixin.test.internal.analysis.Sample
//import dev.extframework.mixin.test.load
//import org.objectweb.asm.Label
//import org.objectweb.asm.Type
//import org.objectweb.asm.tree.InsnList
//import org.objectweb.asm.tree.LabelNode
//import org.objectweb.asm.tree.MethodNode
//import org.objectweb.asm.tree.VarInsnNode
//import kotlin.test.Test
//
//// TODO its just really hard to test this honestly, really the best test is making sure no errors are thrown
//class InstructionInjectionTests {
//    @Test
//    fun `Test stack mixin builds correctly`() {
//        val injector = InstructionInjector(MethodInjector(RedefinitionFlags.FULL))
//
//        val node = classNode(Sample::class)
//        val method = node.methods.first {
//            it.name == Sample::sampleMethod.name
//        }
//
//        val end = method.instructions.get(4)
//
//        val locals = computeLocalsState(method.instructions, end, false, listOf(Type.INT_TYPE))
//
//        val insn = InsnList()
//
//        val stack = computeStackState(
//            method.instructions, end
//        )
//
//        val stackInsn = injector.captureStack(
//            stack,
//            locals.size,
//            insn,
//        )
//
//        println(stackInsn)
//    }
//
//    @Test
//    fun `Test full mixin flow insn production`() {
//        val injector = InstructionInjector(MethodInjector(RedefinitionFlags.FULL))
//
//        val node = classNode(Sample::class)
//        val method = node.methods.first {
//            it.name == Sample::sampleMethod.name
//        }
//
//        val locals = computeLocalsState(
//            method.instructions,
//            method.instructions[10],
//            false,
//            listOf(Type.INT_TYPE),
//        )
//
//        val insn = injector.buildMixinFlow(
//            listOf(),
//            method.instructions,
//            method.instructions[10],
//            locals,
//            false,
//            null,
//            ClassReference(Sample::class)
//        )
//
//        println(insn)
//    }
//
//    @Test
//    fun `Test full mixin flow insn production with fake injection data`() {
//        val injector = InstructionInjector(MethodInjector(RedefinitionFlags.FULL))
//
//        val node = classNode(Sample::class)
//        val method = node.methods.first {
//            it.name == Sample::sampleMethod.name
//        }
//
//        val locals = computeLocalsState(
//            method.instructions,
//            method.instructions[10],
//            false,
//            listOf(Type.INT_TYPE),
//        )
//
//        val insn = injector.buildMixinFlow(
//            listOf(
//                InstructionInjectionData(
//                    ClassReference("org.example.Class"),
//                    MethodNode().apply {
//                        name = "test"
//                        desc =
//                            "(${Stack::class.descriptor}${Captured::class.descriptor})${MixinFlow.Result::class.descriptor}"
//                    },
//                    Sample::sampleMethod.name,
//                    InjectionType.AFTER,
//                    object : InjectionPoint {
//                        override val placementId: Any
//                            get() = TODO("Not yet implemented")
//
//                        override fun getPoints(source: InsnList): List<InjectionPoint.Group> {
//                            TODO("Not yet implemented")
//                        }
//                    },
//                    capturedLocals = listOf(1)
//                )
//            ),
//            method.instructions,
//            method.instructions[10],
//            locals,
//            false,
//            null,
//            ClassReference(Sample::class)
//        )
//
//        println(insn)
//    }
//
//    @Test
//    fun `Test stack build and release`() {
//        val injector = InstructionInjector(MethodInjector(RedefinitionFlags.FULL))
//
//        val instructions = InsnList()
//        instructions.add(LabelNode(Label()))
//
//        val insn = InsnList()
//
//        val stack = computeStackState(
//            instructions,
//            instructions[0]
//        )
//
//        injector.captureStack(
//            stack,
//            0,
//            insn
//        )
//        insn.add(VarInsnNode(Opcodes.ASTORE, 2))
//
//        injector.releaseStack(2, stack, insn)
//
//        println(insn)
//    }
//
//    @Test
//    fun `Test full injection`() {
//        val injector = InstructionInjector(MethodInjector(RedefinitionFlags.FULL))
//
//        val node = classNode(Sample::class)
//        val method = node.methods.first {
//            it.name == Sample::sampleMethod.name
//        }
//
//        val pointInsn = method.instructions[4]
//
//
//        val locals = computeLocalsState(
//            method.instructions,
//            pointInsn,
//            false,
//            listOf(Type.INT_TYPE),
//        )
//
//        val insn = injector.buildMixinFlow(
//            listOf(
//                InstructionInjectionData(
//                    ClassReference("org.example.Class"),
//                    MethodNode().apply {
//                        name = "test"
//                        desc =
//                            "(${Stack::class.descriptor}${Captured::class.descriptor}${MixinFlow::class.descriptor})${MixinFlow.Result::class.descriptor}"
//                    },
//                    Sample::sampleMethod.name,
//                    InjectionType.AFTER,
//                    object : InjectionPoint {
//                        override val placementId: Any
//                            get() = TODO("Not yet implemented")
//
//                        override fun getPoints(source: InsnList): List<InjectionPoint.Group> {
//                            TODO("Not yet implemented")
//                        }
//                    },
//                    capturedLocals = listOf(1)
//                )
//            ),
//            method.instructions,
//            pointInsn,
//            locals,
//            false,
//            TypeSort.INT,
//            ClassReference(Sample::class)
//        )
//
//        ByteCodeUtils.textifyInsn(insn).withIndex().forEach { (i, t) -> println("${i + 11}  $t") }
//
//        method.instructions.insertBefore(pointInsn, insn)
//
//        val cls = load(node)
//        val obj = cls.getConstructor().newInstance()
//        println(cls.getMethod(Sample::sampleMethod.name, Int::class.java).invoke(obj, 8))
//    }
//}