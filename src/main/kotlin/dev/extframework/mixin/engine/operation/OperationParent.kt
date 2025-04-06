package dev.extframework.mixin.engine.operation

import dev.extframework.mixin.engine.transform.ClassTransformer
import kotlin.reflect.KClass

/**
 * It may seem like poor design that an independent type representing the parent of
 * a class operation refers to a very not independent type a class transformer. However,
 * in terms of CRUD (Creating, reading, updating deleting) only 2 of those operations are
 * actually needed to be performed by this library: creating and updating. For this reason
 * and operation in reality only represents these two types and therefore the ClassTransformer
 * and ClassGenerator types are the only two valid operations possible. Additionally, while it
 * is totally valid to generate a new class, it's not valid for multiple generators to generate
 * the same new class (this seems obvious doesn't it). For this reason, the only real parent type
 * of any operation is transforming it. Either the class will already be built and we can obviously
 * only transform it, or the operation instantiating/returning this parent will be a generator
 * and thus we cannot generate anymore.
 */
public data class OperationParent(
    val transformer: ClassTransformer<*>,
    val order: Order
) {
    public enum class Order {
        BEFORE,
        AFTER
    }

    public companion object {
        public fun parents(
            vararg parents: Pair<ClassTransformer<*>, Order>
        ) : Set<OperationParent> {
            return parents.mapTo(HashSet<OperationParent>()) {
                OperationParent(it.first, it.second)
            }
        }
    }
}