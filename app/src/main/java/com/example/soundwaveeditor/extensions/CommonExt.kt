package com.example.soundwaveeditor.extensions

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.Html
import android.text.Spanned
import androidx.fragment.app.Fragment
import java.util.*


fun <T1 : Any, T2 : Any, R : Any> safeLet(p1: T1?, p2: T2?, block: (T1, T2) -> R?): R? {
    return if (p1 != null && p2 != null) block(p1, p2) else null
}

fun <T1 : Any, T2 : Any, T3 : Any, R : Any> safeLet(p1: T1?, p2: T2?, p3: T3?, block: (T1, T2, T3) -> R?): R? {
    return if (p1 != null && p2 != null && p3 != null) block(p1, p2, p3) else null
}

fun <T1 : Any, T2 : Any, T3 : Any, T4 : Any, R : Any> safeLet(p1: T1?, p2: T2?, p3: T3?, p4: T4?, block: (T1, T2, T3, T4) -> R?): R? {
    return if (p1 != null && p2 != null && p3 != null && p4 != null) block(p1, p2, p3, p4) else null
}

fun <T1 : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any, R : Any> safeLet(p1: T1?, p2: T2?, p3: T3?, p4: T4?, p5: T5?, block: (T1, T2, T3, T4, T5) -> R?): R? {
    return if (p1 != null && p2 != null && p3 != null && p4 != null && p5 != null) block(p1, p2, p3, p4, p5) else null
}

fun <T1 : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any, T6 : Any, R : Any> safeLet(p1: T1?, p2: T2?, p3: T3?, p4: T4?, p5: T5?, p6: T6?, block: (T1, T2, T3, T4, T5, T6) -> R?): R? {
    return if (p1 != null && p2 != null && p3 != null && p4 != null && p5 != null && p6 != null) block(p1, p2, p3, p4, p5, p6) else null
}

inline fun <reified I> bindInterface(obj: Any?, block: I.() -> Unit) {
    if (obj is I) obj.block()
}

@Suppress("DEPRECATION")
fun CharSequence.fromHtml(): Spanned =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
        Html.fromHtml(toString(), Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(toString())

fun Context.checkIntentForHandling(intent: Intent)
        = intent.resolveActivity(packageManager) != null

fun Fragment.checkIntentForHandling(intent: Intent) = activity?.checkIntentForHandling(intent)

inline fun <reified K : Enum<K>, V> enumMapOf(): MutableMap<K, V> = EnumMap<K, V>(K::class.java)

inline fun <reified K : Enum<K>, V> enumMapOf(vararg pairs: Pair<K, V>): MutableMap<K, V>
        = pairs.toMap(EnumMap<K, V>(K::class.java))

inline fun <T, R> withNotNull(receiver: T?, block: T.() -> R): R? = receiver?.block()

inline fun <T, V, R> letIfAllNotNull(subject1: T?, subject2: V?, action: (T, V) -> R): R?
        = subject1?.let { first -> subject2?.let { second -> action(first, second) } }

inline fun <T, V, P, R> letIfAllNotNull(subject1: T?, subject2: V?, subject3: P?, action: (T, V, P) -> R): R?
        = subject1?.let { first -> subject2?.let { second -> subject3?.let { third -> action(first, second, third) } } }

infix fun Any?.bothNotNull(another: Any?): Boolean = this != null && another != null


val Int.isPositive
    get() = this >= 0

fun Bitmap?.getIfNotRecycled() = this?.let { if (it.isRecycled) null else it }

fun Bitmap?.checkAndRecycle() = withNotNull(this) { if (!isRecycled) recycle() }
