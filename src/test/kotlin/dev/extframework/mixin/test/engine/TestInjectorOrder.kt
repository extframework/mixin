package dev.extframework.mixin.test.engine

import dev.extframework.mixin.engine.buildTransformerTree
import dev.extframework.mixin.engine.getInjectionOrder
import dev.extframework.mixin.engine.operation.OperationData
import dev.extframework.mixin.engine.operation.OperationParent
import dev.extframework.mixin.engine.operation.OperationParent.Companion.parents
import dev.extframework.mixin.engine.operation.OperationParent.Order
import dev.extframework.mixin.engine.operation.OperationParent.Order.AFTER
import dev.extframework.mixin.engine.operation.OperationParent.Order.BEFORE
import dev.extframework.mixin.engine.operation.OperationRegistry
import dev.extframework.mixin.engine.testCircularity
import dev.extframework.mixin.engine.transform.*
import org.objectweb.asm.tree.ClassNode
import kotlin.reflect.KClass
import kotlin.test.Test

class TestInjectorOrder {
    class BasicTransformer(
        val name: String,
        override val parents: Set<OperationParent>
    ) : ClassTransformer<OperationData> {
        override fun toString(): String {
            return "INJECTOR-$name"
        }

        override val registry: OperationRegistry<OperationData>
            get() = TODO("Not yet implemented")

        override fun transform(node: ClassNode): ClassNode {
            return node
        }
    }

    fun mockTransformer(
        name: String,
        vararg parents: Pair<ClassTransformer<*>, Order>
    ): ClassTransformer<*> {
        return BasicTransformer(name, parents(*parents))
    }

    fun <T> List<T>.randomize(): List<T> {
        val set = toMutableSet()

        return map {
            val element = set.random()
            set.remove(element)
            element
        }
    }

    @Test
    fun `Test basic tree building`() {
        val injectorA = mockTransformer("a")
        val injectorB = mockTransformer(
            "b",
            injectorA to BEFORE
        )
        val injectorC = mockTransformer(
            "c",
            injectorB to BEFORE
        )
        val injectorD = mockTransformer(
            "d",
            injectorA to BEFORE,
            injectorC to BEFORE
        )

        val tree =
            buildTransformerTree(setOf(injectorA, injectorB, injectorC, injectorD))

        check(
            tree == mapOf(
                injectorA to setOf(),
                injectorB to setOf(injectorA),
                injectorC to setOf(injectorB),
                injectorD to setOf(injectorA, injectorC),
            )
        )
    }

    @Test
    fun `Test reverse tree building`() {
        val injectorA = mockTransformer("a")
        val injectorB = mockTransformer("b", injectorA to AFTER)
        val injectorC = mockTransformer("c", injectorB to BEFORE)
        val injectorD = mockTransformer("d", injectorC to BEFORE)

        val tree =
            buildTransformerTree(setOf(injectorA, injectorB, injectorC, injectorD))

        check(
            tree == mapOf(
                injectorA to setOf(injectorB),
                injectorB to setOf(),
                injectorC to setOf(injectorB),
                injectorD to setOf(injectorC),
            )
        )
    }

    @Test
    fun `Test circularity check`() {
        val injectorA = mockTransformer("a")
        val injectorB = mockTransformer("b", injectorA to BEFORE)
        val injectorC = mockTransformer("c", injectorB to BEFORE)
        val injectorD = mockTransformer("d", injectorA to AFTER, injectorC to BEFORE)


        val tree = buildTransformerTree(setOf(injectorA, injectorB, injectorC, injectorD))

        var circularity = testCircularity(tree)
        check(circularity != null)
        println("circularity: $circularity")
        check(circularity.containsAll(setOf(injectorA, injectorB, injectorC, injectorD)))
    }

    @Test
    fun `Test forward ordering`() {
        val injectorA = mockTransformer("a")
        val injectorB = mockTransformer("b", injectorA to BEFORE)
        val injectorC = mockTransformer("c", injectorB to BEFORE)
        val injectorD = mockTransformer("d", injectorA to BEFORE, injectorC to BEFORE)

        val tree =
            buildTransformerTree(setOf(injectorA, injectorB, injectorC, injectorD))

        val order = getInjectionOrder(tree)
        println(order)
        check(order == listOf(injectorA, injectorB, injectorC, injectorD))
    }

    @Test
    fun `Test ordering with reverse`() {
        val injectorA = mockTransformer("a")
        val injectorB = mockTransformer("b", injectorA to AFTER)
        val injectorC = mockTransformer("c", injectorB to BEFORE)
        val injectorD = mockTransformer("d", injectorA to BEFORE, injectorC to AFTER)

        val tree =
            buildTransformerTree(setOf(injectorA, injectorB, injectorC, injectorD))

        var order = getInjectionOrder(tree)
        println(order)
        check(order == listOf(injectorB, injectorA, injectorD, injectorC))
    }

//    @Test
//    fun `Test generation processor`() {
//        val injectorA = mockTransformer("a")
//        val injectorB = mockTransformer("b")
//        val injectorC = mockTransformer("c", injectorB to BEFORE)
//        val injectorD = mockTransformer("d", injectorC to BEFORE)
//
//        
//
//        val tree: Map<OperationResult<*>, Set<OperationResult<*>>> =
//            buildTransformerTree(listOf(injectorA, injectorD, injectorB, injectorC).randomize())
//
//        if (testCircularity(tree) != null) {
//            throw Exception()
//        }
//
//        var order = getInjectionOrder(tree)
//        println(order)
//        check(order == listOf(injectorA, injectorB, injectorC, injectorD))
//    }
}