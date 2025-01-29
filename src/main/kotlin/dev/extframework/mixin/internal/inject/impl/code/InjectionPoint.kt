package dev.extframework.mixin.internal.inject.impl.code

import dev.extframework.mixin.internal.inject.impl.code.InjectionPoint.Group
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList

public interface InjectionPoint {
    public val placementId: Any

    public fun getPoints(
        source: InsnList,
//        injection: InsnList,
//        type: InjectionType,
    ): List<Group>

    public data class Group(
        val start: AbstractInsnNode,
        val end: AbstractInsnNode,
    )
}

public data class SingleInjectionPoint(
    public val selector: InstructionSelector,
) : InjectionPoint {
    override val placementId: Any = selector

    override fun getPoints(
        source: InsnList,
    ): List<Group> {
        return selector.select(source).map { Group(it, it) }
    }
}

public data class BlockInjectionPoint(
    public val start: InstructionSelector,
    public val end: InstructionSelector,
) : InjectionPoint {
    override val placementId: Any = start to end

    override fun getPoints(
        source: InsnList,
    ): List<Group> {
        val ending = end.select(source)

        return start.select(source).map {
            val e = ending.first { e ->
                source.indexOf(e) > source.indexOf(it)
            }

            Group(it, e)
        }
    }
}