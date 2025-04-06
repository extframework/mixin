package dev.extframework.mixin.engine.impl.method

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.engine.operation.OperationData
import org.objectweb.asm.tree.MethodNode

public class MethodInjectionData private constructor(
    // Note that this method should NEVER be mutated if you want reloading to be safe.
    public val method: MethodNode,
    public val targets: Set<ClassReference>,
    public val uniqueId: Int,
) : OperationData {
    public companion object {
        private var idCounter: Int = 0

        public fun buildData(
            method: MethodNode,
            targets: Set<ClassReference>,
        ): MethodInjectionData {
            synchronized(this) {
                return MethodInjectionData(
                    method,
                    targets,
                    idCounter++,
                )
            }
        }
    }
}