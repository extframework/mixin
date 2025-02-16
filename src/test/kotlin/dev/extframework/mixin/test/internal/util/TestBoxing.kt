package dev.extframework.mixin.test.internal.util

import dev.extframework.mixin.api.TypeSort
import dev.extframework.mixin.internal.analysis.ObjectValueRef
import dev.extframework.mixin.internal.analysis.SortValueRef
import dev.extframework.mixin.internal.util.boxableTo
import org.objectweb.asm.Type
import kotlin.test.Test

class TestBoxing {
    @Test
    fun `Test type boxing check`() {
        val typeInt = SortValueRef(TypeSort.INT)
        val typeDouble = SortValueRef(TypeSort.DOUBLE)
        val object1 = ObjectValueRef(Type.getObjectType("java/lang/Object"))
        val object2 = ObjectValueRef(Type.getType(Integer::class.java))
        val object3 = ObjectValueRef(Type.getObjectType("java/lang/Object"))

        check(typeInt.boxableTo(object1) == false)
        check(typeInt.boxableTo(typeDouble) == false)
        check(object1.boxableTo(object2) == false)
        check(typeInt.boxableTo(typeDouble) == false)

        check(object1.boxableTo(object3) == true)
        check(object2.boxableTo(typeInt) == true)
    }
}