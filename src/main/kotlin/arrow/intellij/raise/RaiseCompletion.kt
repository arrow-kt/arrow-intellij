package arrow.intellij.raise

import arrow.intellij.inRaiseAccumulateContext
import arrow.intellij.inRaiseContext
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.IconLoader
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtExpression

class RaiseCompletion : CompletionContributor() {
    init {
        extend(null, PlatformPatterns.psiElement(), RaiseCompletionProvider)
    }
}

object RaiseCompletionProvider : CompletionProvider<CompletionParameters>() {
    val ICON = IconLoader.getIcon("/icons/predictedException.svg", this::class.java)

    sealed interface ElementString
    data class Same(val info: String): ElementString
    data class Two(val simple: String, val accumulate: String): ElementString

    val knownRaiseFunctions = mapOf(
        "raise" to Two("End the computation with an error condition", "Accumulate an error"),
        "ensure" to Same("Check whether the condition is true, raise otherwise"),
        "ensureNotNull" to Same("Check whether the value is not null, raise otherwise"),
        "withError" to Same("Transform the errors from the inner computation"),
        "accumulate" to Two("Accumulate errors instead of fail-first", "Keep accumulating errors"),
        "zipOrAccumulate" to Same("Accumulate errors from more than one computation"),
        "mapOrAccumulate" to Same("Accumulate errors from iteration"),
    )

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        results: CompletionResultSet,
    ) {
        val parent = parameters.position.parentOfType<KtExpression>() ?: return
        analyze(parent) {
            if (!inRaiseContext(parent)) return
            val isRaiseAccumulateContext = inRaiseAccumulateContext(parent)
            for ((function, info) in knownRaiseFunctions) {
                val tailText = when (info) {
                    is Same -> info.info
                    is Two -> if (isRaiseAccumulateContext) info.accumulate else info.simple
                }
                val element = LookupElementBuilder.create(function).withIcon(ICON).withTypeText(tailText)
                results.addElement(element)
            }
        }
    }

}