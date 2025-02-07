package dev.extframework.mixin

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.InjectCode
import dev.extframework.mixin.api.InjectMethod
import dev.extframework.mixin.api.InstructionSelector
import dev.extframework.mixin.internal.InternalMixinEngine
import dev.extframework.mixin.internal.inject.MixinInjector
import dev.extframework.mixin.internal.inject.impl.code.InstructionInjector
import dev.extframework.mixin.internal.inject.impl.method.MethodInjector
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

public class MixinEngine(
    private val redefinitionFlags: RedefinitionFlags,
    private val typeProvider: (ClassReference) -> Class<*>? = {
        Class.forName(it.name)
    },
    injectors: Map<Type, MixinInjector<*>> = defaultInjectors(redefinitionFlags) {
        typeProvider(ClassReference(it)) as Class<out InstructionSelector>
    }
) : ClassTransformer {
    private val internal = InternalMixinEngine(typeProvider, injectors)

    public fun registerMixin(
        node: ClassNode
    ) {
        internal.registerMixin(node)
    }

    override fun transform(
        name: String,
        node: ClassNode
    ): ClassNode {
        return internal.transform(name, node)
    }

    public companion object {
        public fun defaultInjectors(
            redefinitionFlags: RedefinitionFlags,
            customPointProvider: (name: String) -> Class<out InstructionSelector>
        ): Map<Type, MixinInjector<*>> {
            val methodInjector = MethodInjector(redefinitionFlags)

            return mapOf(
                Type.getType(InjectMethod::class.java) to methodInjector,
                Type.getType(InjectCode::class.java) to InstructionInjector(methodInjector, customPointProvider)
            )
        }
    }
}