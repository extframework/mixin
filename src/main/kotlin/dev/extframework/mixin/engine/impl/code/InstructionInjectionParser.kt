package dev.extframework.mixin.engine.impl.code

import dev.extframework.mixin.InvalidMixinException
import dev.extframework.mixin.MixinExceptionCause
import dev.extframework.mixin.annotation.AnnotationTarget
import dev.extframework.mixin.api.*
import dev.extframework.mixin.engine.InjectionParser
import dev.extframework.mixin.engine.transform.ClassTransformer
import dev.extframework.mixin.engine.impl.method.MethodInjectionData
import dev.extframework.mixin.engine.tag.ClassTag
import dev.extframework.mixin.engine.util.*
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode

public class InstructionInjectionParser(
    override val transformer: ClassTransformer<InstructionInjectionData>,
    private val customPointProvider: (name: String) -> Class<out InstructionSelector>,
) : InjectionParser<InstructionInjectionData> {
    private val customInjectionPoints = HashMap<Pair<String, List<String>>, InstructionSelector>()

    override fun parse(
        tag: ClassTag,
        element: AnnotationTarget,
        annotation: AnnotationNode,
        targets: Set<ClassReference>
    ): InjectionParser.Output<InstructionInjectionData> {
        val annotation = createAnnotation(annotation)

        val singlePoint = annotation.point != null
        val blockPoint = annotation.block != null

        val inferredTarget = inferTargetMethod(element.methodNode, annotation.method)

        val isStatic = element.methodNode.access.and(ACC_STATIC) == ACC_STATIC

        val point = if (singlePoint && blockPoint) {
            throw InvalidMixinException(
                tag,
                MixinExceptionCause.DoubleCodeInjectionPoints,
                listOf(element.methodNode.method().toString())
            )
        } else if (singlePoint) {
            SingleInjectionPoint(
                newSelector(annotation.point, isStatic) { tag },
                annotation.ordinal,
                annotation.count,
            )
        } else if (blockPoint) {
            if (annotation.block.size != 2) {
                throw InvalidMixinException(
                    tag,
                    MixinExceptionCause.InvalidCodeInjectionPointBlockSize
                )
            }

            BlockInjectionPoint(
                (newSelector(annotation.block[0], isStatic) { tag }),
                (newSelector(annotation.block[1], isStatic) { tag }),
                annotation.ordinal,
                annotation.count,
            )
        } else {
            SingleInjectionPoint(
                newSelector(SelectData(InjectionBoundary.HEAD), isStatic) { tag },
                annotation.ordinal,
                annotation.count,
            )
        }

        // TODO:  not mutating the mixin node (we already do this, possible do a deep copy?)
        return InjectionParser.Output(
            InstructionInjectionData(
                tag,
                MethodInjectionData.buildData(
                    element.methodNode,
                    targets
                ),
                inferredTarget,
                annotation.type,
                point,
                annotation.locals,
                targets
            ),
            targets
        )
    }

    private fun createAnnotation(
        inject: AnnotationNode,
    ): InjectCodeData {
        fun parseSelection(
            select: AnnotationNode
        ) = SelectData(
            select.enumValue<InjectionBoundary>("value") ?: InjectionBoundary.IGNORE,
            select.value<AnnotationNode>("invoke")?.let { invoke ->
                InvokeData(
                    invoke.value("value") ?: (Type.getObjectType(invoke.value<String>("clsName")!!.replace(".", "/"))),
                    invoke.value("method")!!,
                    invoke.value("opcode"),
                )
            },
            select.value<AnnotationNode>("field")?.let { field ->
                FieldData(
                    field.value("value")!!,
                    field.value("name")!!,
                    field.enumValue<FieldAccessType>("type") ?: FieldAccessType.EITHER,
                )
            },
            select.value<AnnotationNode>("opcode")?.let { opcode ->
                OpcodeData(
                    opcode.value<Int>("value")?.takeUnless { it == -1 },
                    opcode.value<AnnotationNode>("ldc")?.value<String>("value")
                )
            },
            select.value<AnnotationNode>("custom")?.let { custom ->
                CustomData(
                    custom.value<Type>("value")!!,
                    custom.value<Array<String>>("options")?.toList() ?: emptyList(),
                )
            },
        )

        return InjectCodeData(
            inject.value("value") ?: "",
            inject.enumValue<InjectionType>(
                "type"
            ) ?: InjectionType.BEFORE,
            inject.value<AnnotationNode>("point")?.let(::parseSelection),
            inject.value<Array<AnnotationNode>>("block")?.map(::parseSelection),
            inject.value<List<Int>>("locals") ?: emptyList(),
            inject.value("ordinal") ?: 0,
            inject.value("count") ?: 1
        )
    }

    private fun newSelector(
        select: SelectData,
        // Is the mixin method itself static.
        isStatic: Boolean,
        target: () -> ClassTag
    ): InstructionSelector {
        val boundary = select.boundary != InjectionBoundary.IGNORE
        val invoke = select.invoke != null
        val field = select.field != null
        val opcode = select.opcode != null
        val custom = select.custom != null

        var notExclusive = false // Starts exclusive
        for (bool in listOf(boundary, invoke, field, opcode, custom)) {
            if (notExclusive && bool) {
                throw InvalidMixinException(
                    target(),
                    MixinExceptionCause.CodeSelectorExclusivity
                )
            }

            notExclusive = notExclusive || bool
        }

        return if (boundary) {
            BoundarySelector(select.boundary, isStatic)
        } else if (invoke) {
            InvocationSelector(
                select.invoke.cls,
                methodFromDesc(
                    // Normalizing return type
                    select.invoke.method.substringBeforeLast(")") + ")V"
                ),
                select.invoke.opcode.takeUnless { it == -1 },
            )
        } else if (field) {
            FieldAccessSelector(
                select.field.cls,
                select.field.name,
                select.field.type
            )
        } else if (opcode) {
            OpcodeSelector(
                select.opcode.opcode ?: Opcodes.LDC,
                select.opcode.ldc
            )
        } else if (custom) {
            val key = select.custom.cls.internalName to select.custom.options

            customInjectionPoints[key] ?: run {
                val cls = customPointProvider(select.custom.cls.className)
                val instance: InstructionSelector =
                    cls.getConstructor(List::class.java).newInstance(select.custom.options)!!

                customInjectionPoints[key] = instance

                instance
            }
        } else {
            throw InvalidMixinException(
                target(),
                MixinExceptionCause.NoCodeSelectorsDefined,
            )
        }
    }

    private fun inferTargetMethod(
        mixinMethod: MethodNode,
        // Could be empty, an internal type ('name(<internal type params>)') or a fancy type ('name (<classnames>)')
        given: String,
    ): Method {
        val name = given
            .substringBefore("(")
            .substringBefore(" ")
            .takeUnless { it.isBlank() }
            ?: mixinMethod.name

        if (given.contains(" ")) {
            val curr = if (given.count { ch -> ch == ' ' } == 2) {
                given.substringAfter(" ")
            } else given

            return Method.getMethod("void $name ${curr.substringAfter(" ")}")
        }

        val descriptor = if (given.contains("(")) {
            "(" + given.substringAfter("(")
        } else
        // FIXME: This will also incldue locals, not just parameters.
            inferLocalsFromSignature(mixinMethod.signature).joinToString(
                prefix = "(",
                separator = "",
                postfix = ")V"
            )

        return Method(name, descriptor.substringBefore(")") + ")V")
    }

    private data class InjectCodeData(
        val method: String,
        val type: InjectionType,
        val point: SelectData?,
        val block: List<SelectData>?,
        val locals: List<Int>,

        val ordinal: Int,
        val count: Int,
    )

    private data class SelectData(
        val boundary: InjectionBoundary = InjectionBoundary.IGNORE,
        val invoke: InvokeData? = null,
        val field: FieldData? = null,
        val opcode: OpcodeData? = null,
        val custom: CustomData? = null,
    )

    private data class CustomData(
        val cls: Type,
        val options: List<String>
    )

    private data class OpcodeData(
        val opcode: Int?,
        val ldc: String?,
    )

    private data class FieldData(
        val cls: Type,
        val name: String,
        val type: FieldAccessType
    )

    private data class InvokeData(
        val cls: Type,
        val method: String,
        val opcode: Int?
    )
}