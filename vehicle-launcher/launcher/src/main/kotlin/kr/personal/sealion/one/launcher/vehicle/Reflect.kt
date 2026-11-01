package kr.personal.sealion.one.launcher.vehicle

import android.content.Context
import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Thin reflective handle on one BYD DiLink "device" singleton such as
 * `android.hardware.bydauto.speed.BYDAutoSpeedDevice`. These classes only exist
 * on the head unit, are undocumented and differ between firmware versions, so
 * every call tries a list of candidate method names and fails soft.
 */
class ReflectiveDevice(context: Context, val className: String) {
    private val clazz: Class<*>? = runCatching { Class.forName(className) }.getOrNull()
    private val instance: Any? = clazz?.let { c ->
        runCatching { c.getMethod("getInstance", Context::class.java).invoke(null, context) }
            .recoverCatching { c.getMethod("getInstance").invoke(null) }
            .onFailure { Log.w(TAG, "getInstance failed for $className", it) }
            .getOrNull()
    }
    val present: Boolean get() = instance != null

    private fun find(name: String, argc: Int): Method? =
        clazz?.methods?.firstOrNull { it.name == name && it.parameterTypes.size == argc }

    /** Calls the first candidate that exists; returns its result or null. */
    fun get(vararg candidates: String, args: Array<Any> = emptyArray()): Any? {
        val target = instance ?: return null
        for (name in candidates) {
            val method = find(name, args.size) ?: continue
            return runCatching { method.invoke(target, *coerce(method, args)) }
                .onFailure { Log.w(TAG, "$className.$name failed", it) }
                .getOrNull()
        }
        return null
    }

    fun number(vararg candidates: String, args: Array<Any> = emptyArray()): Double? =
        (get(*candidates, args = args) as? Number)?.toDouble()

    /** For setters: true if some candidate existed and returned without throwing / error code. */
    fun invoke(vararg candidates: String, args: Array<Any> = emptyArray()): Boolean? {
        val target = instance ?: return null
        for (name in candidates) {
            val method = find(name, args.size) ?: continue
            return runCatching {
                val result = method.invoke(target, *coerce(method, args))
                // BYD setters commonly return int status where negative means failure.
                !(result is Number && result.toInt() < 0)
            }.onFailure { Log.w(TAG, "$className.$name failed", it) }.getOrDefault(false)
        }
        return null
    }

    /** Static int constant lookup, e.g. GEARBOX_AUTO_MODE_P. */
    fun constant(name: String): Int? = runCatching { clazz?.getField(name)?.getInt(null) }.getOrNull()

    /** Name of the static int constant equal to [value] whose name contains [hint]. */
    fun constantName(value: Int, hint: String): String? = clazz?.fields
        ?.filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && it.name.contains(hint) }
        ?.firstOrNull { runCatching { it.getInt(null) == value }.getOrDefault(false) }
        ?.name

    /** Every public method + constant, for the diagnostics screen. */
    fun describe(): List<String> {
        val c = clazz ?: return listOf("(클래스 없음)")
        val methods = c.declaredMethods.filter { Modifier.isPublic(it.modifiers) }
            .map { m -> "${m.name}(${m.parameterTypes.joinToString { it.simpleName }}): ${m.returnType.simpleName}" }
            .sorted()
        val constants = c.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .map { f -> "${f.name} = ${runCatching { f.getInt(null) }.getOrNull()}" }
            .sorted()
        return listOf(if (present) "인스턴스: 사용 가능" else "인스턴스: 획득 실패 (권한/서비스 확인)") + methods + constants
    }

    private fun coerce(method: Method, args: Array<Any>): Array<Any> =
        Array(args.size) { i ->
            val wanted = method.parameterTypes[i]
            val arg = args[i]
            when {
                wanted == Int::class.javaPrimitiveType && arg is Number -> arg.toInt()
                wanted == Double::class.javaPrimitiveType && arg is Number -> arg.toDouble()
                wanted == Boolean::class.javaPrimitiveType && arg is Number -> arg.toInt() != 0
                else -> arg
            }
        }

    companion object { private const val TAG = "SealionReflect" }
}
