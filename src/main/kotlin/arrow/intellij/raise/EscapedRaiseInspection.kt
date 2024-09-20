package arrow.intellij.raise

import arrow.intellij.hasRaiseContext
import arrow.intellij.inRaiseContext
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.contracts.description.KaContractCallsInPlaceContractEffectDeclaration
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.callExpressionVisitor

class EscapedRaiseInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        callExpressionVisitor visitor@{ expr ->
            analyze(expr) {
                // we should be in Raise context
                if (!inRaiseContext(expr)) return@visitor
                // get the resolved call
                val call = expr.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return@visitor
                // start filtering out good cases
                val symbol = call.symbol
                // - if the function is inline, it's OK --> should this be overtaken by callsInPlace?
                if ((symbol as? KaNamedFunctionSymbol)?.isInline == true) return@visitor
                // - if we require the Raise context, it's OK
                if (hasRaiseContext(call)) return@visitor
                // - we must have a lambda we may capture
                val potentiallyCaptured =
                    (call as? KaFunctionCall<*>)?.argumentMapping.orEmpty().filterKeys { it is KtLambdaExpression }
                if (potentiallyCaptured.isEmpty()) return@visitor
                @OptIn(KaExperimentalApi::class)
                val callsInPlaceVariables =
                    (symbol as? KaNamedFunctionSymbol)?.contractEffects.orEmpty().mapNotNull {
                        (it as? KaContractCallsInPlaceContractEffectDeclaration)?.valueParameterReference?.parameterSymbol
                    }
                // ... and for which the parameter has no Raise, nor a callsInPlace
                val hasProblems = potentiallyCaptured.any { (_, param) ->
                    !hasRaiseContext(param.symbol) && param.symbol !in callsInPlaceVariables
                }
                if (hasProblems) {
                    holder.registerProblem(
                        expr.calleeExpression as KtExpression,
                        "Raise context may escape through this lambda",
                        ProblemHighlightType.WARNING
                    )
                }
            }
        }
}