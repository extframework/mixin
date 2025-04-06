package dev.extframework.mixin.engine.util

import dev.extframework.mixin.engine.OperationRegistrationException
import dev.extframework.mixin.engine.buildTransformerTree
import dev.extframework.mixin.engine.getInjectionOrder
import dev.extframework.mixin.engine.testCircularity
import dev.extframework.mixin.engine.transform.ClassTransformer

public class SelfOrderingTransformerList(
    private val delegate: MutableList<ClassTransformer<*>> = ArrayList()
) : MutableList<ClassTransformer<*>> by delegate {
    init {
        reorder()
    }

    public fun reorder() {
        val tree = buildTransformerTree(toSet())
        var circularity = testCircularity(tree)
        if (circularity != null) {
            throw OperationRegistrationException(
                "The following operations are circular: $circularity"
            )
        }

        val order = getInjectionOrder(tree)

        clear()
        delegate.addAll(order)
    }

    override fun add(element: ClassTransformer<*>): Boolean {
        return delegate.add(element).also {
            reorder()
        }
    }

    override fun add(index: Int, element: ClassTransformer<*>) {
        delegate.add(index, element)
        reorder()
    }

    override fun addAll(
        index: Int,
        elements: Collection<ClassTransformer<*>>
    ): Boolean {
        return delegate.addAll(index, elements).also {
            reorder()
        }
    }

    override fun addAll(elements: Collection<ClassTransformer<*>>): Boolean {
        return delegate.addAll(elements).also {
            reorder()
        }
    }
}