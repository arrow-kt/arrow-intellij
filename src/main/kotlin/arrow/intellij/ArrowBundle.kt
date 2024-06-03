package arrow.intellij

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object ArrowBundle {
    @NonNls
    const val BUNDLE: String = "messages.Arrow"

    @Nls @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?) =
        DynamicBundle(ArrowBundle::class.java, BUNDLE).getMessage(key, *params)
}