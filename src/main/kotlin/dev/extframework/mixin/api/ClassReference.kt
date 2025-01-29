package dev.extframework.mixin.api

import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import kotlin.reflect.KClass

public data class ClassReference (
    val type: Type
) {
    public constructor(name: String) : this(
        Type.getType(
        "L" + name
            .replace('.', '/')
            .removePrefix("L")
            .removeSuffix(";") + ";"
    ))
    public constructor(klass: KClass<*>) : this(klass.java.name)

    public val name: String by type::className
    public val internalName: String by type::internalName

    override fun toString(): String {
        return name
    }

    public companion object {
        public fun ClassNode.ref() : ClassReference {
            return ClassReference(this.name)
        }
    }
}