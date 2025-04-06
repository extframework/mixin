package dev.extframework.mixin.test.engine.inject.impl.code

import dev.extframework.mixin.MixinEngine
import dev.extframework.mixin.RedefinitionFlags
import dev.extframework.mixin.api.InjectCode
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.api.MixinFlow
import dev.extframework.mixin.test.classNode
import dev.extframework.mixin.test.load
import kotlin.test.Test

class TestAbstractMixin {
    abstract class AbstractSuperDestination {
        fun injectHere(): Int {
            println("Inject here method has been called")

            return 5
        }
    }

    class NonAbstractDestination : AbstractSuperDestination()

    @Test
    fun `Test inject into abstract super type`() {
        @Mixin(AbstractSuperDestination::class)
        class MixinClass {
            @InjectCode
            fun injectHere(flow: MixinFlow): MixinFlow.Result<*> {
                return flow.yield(6)
            }
        }

        val engine = MixinEngine(RedefinitionFlags.ONLY_INSTRUCTIONS)
        engine.registerMixin(classNode(MixinClass::class))

        val cls = engine.load(NonAbstractDestination::class)
        val result = cls.getMethod("injectHere").invoke(cls.newInstance())
        println(result)

        check(result == 6)
    }
}