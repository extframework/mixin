package dev.extframework.mixin.internal.util

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.InjectionType
import org.objectweb.asm.Label
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import kotlin.collections.find
import kotlin.reflect.KClass
import kotlin.sequences.chunked
import kotlin.sequences.map

public fun MethodNode.method(): Method {
    return Method(name, desc)
}

public fun ClassNode.ref(): ClassReference {
    return ClassReference(name)
}

public fun methodFromDesc(str: String): Method {
    return Method(str.substringBefore("("), str.substring(str.indexOf("(")))
}

public fun List<AnnotationNode>.find(
    type: KClass<out Annotation>
): AnnotationNode? {
    return find { node ->
        node.desc == Type.getDescriptor(type.java)
    }
}

public inline fun <reified E : Enum<E>> AnnotationNode.enumValue(
    name: String,
): E? {
    val value: Array<String> = value(name) ?: return null

    val owner = Type.getType(value[0])
    val name = value[1]

    check(owner == Type.getType(E::class.java)) {"Expected enum of type: '${E::class}', but found '$owner'."}

    return E::class.java
        .getMethod("valueOf", String::class.java)
        .invoke(null, name) as? E
}

public inline fun <reified T> AnnotationNode.value(
    name: String
): T? {
    val err = {
        throw Exception("Internal exception parsing annotations.")
    }

    val any = (values ?: listOf())
        .asSequence()
        .chunked(2)
        .map { it[0] to it[1] }
        .toMap()[name] ?: return null

    if (!T::class.isInstance(any)) {
        err()
    }

    return any as T
}

public val KClass<*>.internalName: String
    get() = java.name.replace(".", "/")

public val KClass<*>.descriptor: String
    get() = "L$internalName;"

public fun InsnList.clone() : InsnList {
    val list = InsnList()

    val labels = filterIsInstance<LabelNode>()
        .associate { node -> node to LabelNode(node.label) }

    forEach {
        var insnNode = it.clone(labels)
        list.add(insnNode)
    }

    return list
}