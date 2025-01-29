package dev.extframework.mixin.internal.inject

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.InjectionType
import org.objectweb.asm.commons.Method

public class ConflictingMixinInjections(
    conflicting: List<Triple<ClassReference, Method, InjectionType>>
) : Exception(
    "Conflicting injections found! The following conflict: ${
        conflicting.joinToString { (ref, method, type) ->
            "${ref.name}#${method.name}:${type.name}"
        }
    }")