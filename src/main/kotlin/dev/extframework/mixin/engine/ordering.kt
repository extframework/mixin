package dev.extframework.mixin.engine

import dev.extframework.mixin.MixinEngine.Companion.checkRegistration
import dev.extframework.mixin.engine.operation.OperationParent
import dev.extframework.mixin.engine.transform.ClassTransformer

internal fun buildTransformerTree(
    transformers: Set<ClassTransformer<*>>,
    // Result is Child to parent where parents are ordered before children
): Map<ClassTransformer<*>, Set<ClassTransformer<*>>> {
    // Key injection is before the value
    val result = HashMap<ClassTransformer<*>, MutableSet<ClassTransformer<*>>>()

    val visited = HashSet<ClassTransformer<*>>()
    val edges = ArrayList<ClassTransformer<*>>()
    edges.addAll(transformers)

    while (edges.isNotEmpty()) {
        val current = edges.removeFirst()
        val currentList = result.getOrPut(current) { HashSet() }

        if (!visited.add(current)) {
            continue
        }

        for (parent in current.parents) {
            edges.add(transformers.checkRegistration(parent.transformer))

            if (parent.order == OperationParent.Order.BEFORE) {
                currentList.add(transformers.checkRegistration(parent.transformer))
            } else {
                result.getOrPut(transformers.checkRegistration(parent.transformer)) {
                    HashSet()
                }.add(current)
            }
        }
    }

    return result
}

internal fun testCircularity(
    tree: Map<ClassTransformer<*>, Set<ClassTransformer<*>>>
): List<ClassTransformer<*>>? {
    fun walk(
        current: ClassTransformer<*>,
        trace: MutableSet<ClassTransformer<*>>
    ): MutableSet<ClassTransformer<*>>? {
        if (!trace.add(current)) {
            return trace
        }

        return tree[current]!!.firstNotNullOfOrNull {
            walk(it, trace)
        }
    }

    return tree.firstNotNullOfOrNull { (key) ->
        walk(key, LinkedHashSet())
    }?.toList()
}

internal fun expandInjectionTree(
    tree: Map<ClassTransformer<*>, Set<ClassTransformer<*>>>
): Map<ClassTransformer<*>, Set<ClassTransformer<*>>> {
    val expanded = HashMap<ClassTransformer<*>, Set<ClassTransformer<*>>>()

    for ((key, value) in tree) {
        fun traverse(j: ClassTransformer<*>): Set<ClassTransformer<*>> {
            return setOf(j) + tree[j]!!.flatMapTo(HashSet(), ::traverse)
        }

        expanded[key] = value.flatMapTo(HashSet(), ::traverse)
    }

    return expanded
}

internal fun getInjectionOrder(
    tree: Map<ClassTransformer<*>, Set<ClassTransformer<*>>>
): List<ClassTransformer<*>> {
    val result = ArrayList<ClassTransformer<*>>()
    val expandedTree = expandInjectionTree(tree)

    for ((key, value) in expandedTree.entries) {
        val parentIndices = value.map {
            if (!result.contains(it)) {
                0
            } else {
                result.indexOf(it) + 1
            }
        }

        val index = parentIndices.maxOrNull() ?: 0

        result.add(index, key)
    }

    return result
}