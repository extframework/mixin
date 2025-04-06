package dev.extframework.mixin.engine.operation

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.engine.tag.ClassTag

public class TargetedMixinRegistry<T : OperationData>(
    private val dependencies: List<OperationRegistry<*>>,
    private val registrar: (T) -> List<OperationData>,
    private val targets: (T) -> Set<ClassReference>,
) : OperationRegistry<T> {
    private val registry = HashSet<RegistryItem>()

    // O(n), not an issue here as data sets will be small and operations are fast.
    override fun register(
        data: T,
        tag: ClassTag
    ) {
        registry.add(
            RegistryItem(
                data,
                targets(data),
                tag
            )
        )

        dependencies
            .zip(registrar(data))
            .forEach { (registry: OperationRegistry<*>, data) ->
                (registry as OperationRegistry<OperationData>).register(
                    data, tag
                )
            }
    }

    override fun unregister(tag: ClassTag): List<T> {
        dependencies.forEach { registry ->
            registry.unregister(tag)
        }

        val toRemove = registry.filter { item ->
            item.tag == tag
        }
        registry.removeAll(toRemove)

        return toRemove.map {
            it.data
        }
    }

    public fun applicable(target: ClassReference): List<T> {
        return registry
            .filter {
                it.targets.contains(target)
            }.map {
                it.data
            }
    }

    public fun all() : List<T> {
        return registry.map { it.data }
    }

    private inner class RegistryItem(
        val data: T,
        val targets: Set<ClassReference>,
        val tag: ClassTag,
    )
}