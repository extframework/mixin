package dev.extframework.mixin.internal

import dev.extframework.mixin.*
import dev.extframework.mixin.api.ClassReference
import dev.extframework.mixin.api.ClassReference.Companion.ref
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.api.MixinApplicator
import dev.extframework.mixin.internal.annotation.AnnotationProcessor
import dev.extframework.mixin.internal.annotation.impl.AnnotationProcessorImpl
import dev.extframework.mixin.internal.inject.InjectionData
import dev.extframework.mixin.internal.inject.MixinInjector
import dev.extframework.mixin.internal.inject.MixinInjector.InjectionHelper
import dev.extframework.mixin.internal.util.find
import dev.extframework.mixin.internal.util.value
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

internal class InternalMixinEngine(
    private val typeProvider: (ClassReference) -> Class<*>?,
    private val injectors: Map<Type, MixinInjector<*>>,
    private val annotationProcessor: AnnotationProcessor = AnnotationProcessorImpl {
        typeProvider(ClassReference(it)) as? Class<out Annotation> ?: throw ClassNotFoundException(it)
    }
) : ClassTransformer {
    internal val applicators = mutableMapOf<ClassReference, MixinApplicator>()
    internal val injections = HashMap<
            MixinApplicator,
            MutableList<QualifiedInjection<*>>
            >()
    internal val allInjectionData = HashMap<MixinInjector<*>, MutableSet<InjectionData>>()

//    internal val residuals = HashMap<
//            MixinApplicator,
//            MutableList<QualifiedInjection<*>>
//            >()
//    internal val residuals = HashMap<
//            Pair<MixinInjector<*>, ClassReference>,
//            Map<MixinApplicator, List<QualifiedInjection<*>>>
//            >()

    fun registerMixin(
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

        val definedApplicator = if (target != null) {
            TargetedApplicator(ClassReference(target))
        } else {
            instantiateApplicator(
                ClassReference(customApplicator!!)
            ) {
                node.ref()
            }
        }

//        fun resolveInjections(
//            residual: MixinInjector.Residual<*>
//        ) {
//            val (data, applicator, injector) = residual
//            injector as MixinInjector<InjectionData>
//
//            injections
//                .getOrPut(applicator) { mutableListOf() }
//                .add(
//                    QualifiedInjection(
//                        data,
//                        injector
//                    )
//                )
//
//            injector.residualsFor(
//                data,
//                definedApplicator,
//            ).forEach(::resolveInjections)
//        }

        allData.forEach { data ->
            injections
                .getOrPut(definedApplicator) { mutableListOf() }
                .add(
                    data
                )
            allInjectionData
                .getOrPut(data.injector) { HashSet() }
                .add(
                    data.injection
                )
        }

//        allData.mapTo(ArrayList()) {
//            MixinInjector.Residual(
//                it.injection, definedApplicator, it.injector,
//            )
//        }.forEach(::resolveInjections)
    }

    override fun transform(
        name: String,
        node: ClassNode
    ): ClassNode {
        val reference = ClassReference(name)

        // Grouping all injections together
        val injections = injections
            .filter { (applicator) -> applicator.applies(reference) }
            .flatMap { (_, data) -> data }

//        injections.forEach { (_, i) ->
//            i.forEach { (_, injector) ->
//                residuals.remove(injector to node.ref())
//            }
//        }

//        val residualInjections = residuals
//            .values
//            .flatMap { it.entries }
//            .filter { (applicator) -> applicator.applies(reference, node) }
//            .flatMap { (_, data) -> data }

//        var injections: List<QualifiedInjection<*>> = regularInjections + residualInjections

        // We want to continue as long as there are more changes to apply to this
        // class node. This happens when an injection triggers more injections on
        // this class.
//        var changes = injections.isNotEmpty()

//        while (changes) {
        // Iterate each injection grouped by injector.
        injections
            .groupBy { it.injector }
            .mapValues { (_, data) -> data.map(QualifiedInjection<*>::injection) }
            .forEach { (injector, allData) ->
                (injector as MixinInjector<InjectionData>).inject(
                    node,
                    createHelper(allData, injector),
                )

//                    // Decide if another pass is required (ie do any of the produced residuals
//                    // apply to this class node).
//                    changes = results.any { result ->
//                        result.applicator.applies(reference, node)
//                    }
            }
//        }

        return node
    }

    private fun createHelper(
        applicable: List<InjectionData>,
        injector: MixinInjector<InjectionData>
    ): InjectionHelper<InjectionData> {
        return object : InjectionHelper<InjectionData> {
            override val allData: Set<InjectionData> = allInjectionData[injector].orEmpty() + applicable

            override fun applicable(): List<InjectionData> {
                return applicable
            }

            override fun <T2 : InjectionData> inject(
                node: ClassNode,
                injector: MixinInjector<T2>,
                data: List<T2>
            ) {
                injector.inject(
                    node,
                    createHelper(data, injector as MixinInjector<InjectionData>) as InjectionHelper<T2>
                )
            }
        }
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


    internal data class QualifiedInjection<T : InjectionData>(
        val injection: T,
        val injector: MixinInjector<T>
    )
}