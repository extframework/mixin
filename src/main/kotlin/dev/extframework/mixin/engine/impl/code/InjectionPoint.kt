package dev.extframework.mixin.engine.impl.code

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.MixinExceptionCause
import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.InstructionSelector
import dev.extframework.mixin.engine.impl.code.InjectionPoint.Group
import dev.extframework.mixin.engine.tag.BasicTag
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

public interface InjectionPoint {
    public val placementId: Any

    public fun getPoints(
        method: MethodNode,
        cls: ClassNode,
    ): List<Group>

    public data class Group(
        val start: AbstractInsnNode,
        val end: AbstractInsnNode,
    )
}

public data class SingleInjectionPoint(
    public val selector: InstructionSelector,
    public val ordinal: Int,
    public val count: Int,
) : InjectionPoint {
    override val placementId: Any = selector

    override fun getPoints(
        method: MethodNode,
        cls: ClassNode,
    ): List<Group> {
        var select = selector.select(method, cls)
            .map { Group(it, it) }

        val take = select
            .subList(ordinal, select.size)
            .take(count)

        if (take.size != count) {
            // TODO better error message
            throw InvalidMixinException(
                BasicTag(ClassReference("<unknown>")),
                MixinExceptionCause.CodeFailedToMatchPoints,
                count,
                selector,
                "<unknown>",
            )
        }

        return take
    }
}

public data class BlockInjectionPoint(
    public val start: InstructionSelector,
    public val end: InstructionSelector,
    public val ordinal: Int,
    public val count: Int,
) : InjectionPoint {
    override val placementId: Any = start to end

    override fun getPoints(
        method: MethodNode,
        cls: ClassNode,
    ): List<Group> {
        val ending = end.select(method, cls)

        var select = start.select(method, cls).map {
            val e = ending.first { e ->
                method.instructions.indexOf(e) > method.instructions.indexOf(it)
            }

            Group(it, e)
        }

        return select
            .subList(ordinal, select.size)
            .take(count)
    }
}