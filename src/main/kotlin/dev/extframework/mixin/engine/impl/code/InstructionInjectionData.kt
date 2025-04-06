package dev.extframework.mixin.engine.impl.code

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.InjectionType
import dev.extframework.mixin.engine.operation.OperationData
import dev.extframework.mixin.engine.impl.method.MethodInjectionData
import dev.extframework.mixin.engine.tag.ClassTag
import org.objectweb.asm.commons.Method

public data class InstructionInjectionData(
    val mixinClass: ClassTag,

    val mixinMethod: MethodInjectionData,
    val inferredTarget: Method,

    val injectionType: InjectionType,
    val point: InjectionPoint,

    val capturedLocals: List<Int>,

    val targets: Set<ClassReference>
) : OperationData