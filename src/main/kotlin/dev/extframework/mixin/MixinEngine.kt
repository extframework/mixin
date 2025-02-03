package dev.extframework.mixin

import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.ClassReference.Companion.ref
import dev.extframework.mixin.api.InjectCode
import dev.extframework.mixin.api.InjectMethod
import dev.extframework.mixin.api.InstructionSelector
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.api.MixinApplicator
import dev.extframework.mixin.internal.annotation.AnnotationProcessor
import dev.extframework.mixin.internal.annotation.impl.AnnotationProcessorImpl
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.MixinInjector
import dev.extframework.mixin.internal.inject.impl.code.InstructionInjector
import dev.extframework.mixin.internal.inject.impl.method.MethodInjector
import dev.extframework.mixin.internal.util.find
import dev.extframework.mixin.internal.util.value
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

public class MixinEngine(
    private val redefinitionFlags: RedefinitionFlags,
    private val typeProvider: (ClassReference) -> Class<*>? = {
        Class.forName(it.name)
    },
    private val injectors: Map<Type, MixinInjector<*>> = defaultInjectors(redefinitionFlags) {
        typeProvider(ClassReference(it)) as Class<out InstructionSelector>
    },
    private val annotationProcessor: AnnotationProcessor = AnnotationProcessorImpl {
        typeProvider(ClassReference(it)) as? Class<out Annotation> ?: throw ClassNotFoundException(it)
    }
) : ClassTransformer {
    internal val applicators = mutableMapOf<ClassReference, MixinApplicator>()
    internal val injections = HashMap<
            MixinApplicator,
            MutableList<QualifiedInjection<*>>
            >()
    internal val residuals = HashMap<
            Pair<MixinInjector<*>, ClassReference>,
            Map<MixinApplicator, List<QualifiedInjection<*>>>
            >()

    public fun registerMixin(
        node: ClassNode
    ) {
        val mixinAnnotation = node.visibleAnnotations.find(Mixin::class)
            ?: throw InvalidMixinException(node.ref(), MixinExceptionCause.NoMixinAnnotation)

        fun invalidMixinAnno() {
            throw InvalidMixinException(
                node.ref(),
                MixinExceptionCause.NoTarget
            )
        }

        if (mixinAnnotation.values == null) invalidMixinAnno()

        val target = mixinAnnotation.value<Type>("value")
        val customApplicator = mixinAnnotation.value<Type>("applicator")

        if (target == null && customApplicator == null) invalidMixinAnno()

        val allAnnotations = annotationProcessor.processAll(
            node
        )

        val allData = allAnnotations.mapNotNull {
            val injector = injectors[Type.getType(it.annotation.desc)] as? MixinInjector<InjectionData>
                ?: return@mapNotNull null

            val data = injector.parse(
                it.target,
                it.annotation
            )

            QualifiedInjection(data, injector)
        }

        val applicator = if (target != null) {
            TargetedApplicator(ClassReference(target))
        } else {
            instantiateApplicator(
                ClassReference(customApplicator!!)
            ) {
                node.ref()
            }
        }

        injections
            .getOrPut(applicator) { mutableListOf() }
            .addAll(allData)
    }

    override fun transform(
        name: String,
        node: ClassNode
    ): ClassNode {
        val reference = ClassReference(name)

        // Grouping all injections together
        val regularInjections = injections
            .filter { (applicator) -> applicator.applies(reference, node) }
            .flatMap { (_, data) -> data }

        val residualInjections = residuals
            .values
            .flatMap { it.entries }
            .filter { (applicator) -> applicator.applies(reference, node) }
            .flatMap { (_, data) -> data }

        var injections: List<QualifiedInjection<*>> = regularInjections + residualInjections

        // We want to continue as long as there are more changes to apply to this
        // class node. This happens when an injection triggers more injections on
        // this class.
        var changes = injections.isNotEmpty()

        while (changes) {
            // Iterate each injection grouped by injector.
            injections
                .groupBy { it.injector }
                .mapValues { (_, data) -> data.map(QualifiedInjection<*>::injection) }
                .forEach { (injector, allData) ->
                    val results: List<MixinInjector.Residual<*>> = (injector as MixinInjector<InjectionData>).inject(
                        node,
                        allData
                    )

                    // Cache all residuals returned by this injector by the class node
                    // it processed and itself. We overwrite completely (not adding) for
                    // two reasons. 1) Residuals should not be accumulated, one stage of
                    // processing for a class should always return all residuals required.
                    // 2) On future redefinitions we don't want multiple of the same residuals
                    // to be applied leading to duplicate changes in classes/clashes.
                    residuals[
                        injector to reference
                    ] = results.groupBy {
                        it.applicator
                    }.mapValues { (_, results) ->
                        results.map {
                            QualifiedInjection(
                                it.data,
                                it.injector as MixinInjector<InjectionData>
                            )
                        }
                    }

                    // Save the residuals as the next injections (only does anything if there
                    // are changes to make)
                    injections = results.map { it ->
                        QualifiedInjection(
                            it.data,
                            it.injector as MixinInjector<InjectionData>
                        )
                    }

                    // Decide if another pass is required (ie do any of the produced residuals
                    // apply to this class node).
                    changes = results.any { result ->
                        result.applicator.applies(reference, node)
                    }
                }
        }

        return node
    }

    private fun instantiateApplicator(
        ref: ClassReference,
        mixin: () -> ClassReference
    ): MixinApplicator {
        if (applicators.containsKey(ref)) {
            return applicators[ref]!!
        }

        val applicatorCls = typeProvider(ref)

        if (applicatorCls == null) {
            throw ClassNotFoundException(ref.name)
        }

        try {
            val applicator = applicatorCls.getConstructor().newInstance() as MixinApplicator
            applicators[ref] = applicator

            return applicator
        } catch (e: Throwable) {
            throw InvalidMixinException(
                mixin(),
                MixinExceptionCause.ApplicatorInstantiation,
                listOf(ref.toString()),
                e
            )
        }
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

    internal data class QualifiedInjection<T : InjectionData>(
        val injection: T,
        val injector: MixinInjector<T>
    )
}