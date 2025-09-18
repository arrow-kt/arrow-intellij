package arrow.intellij.raise

import arrow.intellij.COROUTINE_SCOPE_ID
import arrow.intellij.RAISE_ID
import arrow.intellij.hasContextWithClassId
import arrow.intellij.inRaiseContext
import arrow.intellij.isClassId
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.contracts.description.KaContractCallsInPlaceContractEffectDeclaration
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.callExpressionRecursiveVisitor
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
                val namedSymbol = symbol as? KaNamedFunctionSymbol
                // - if the function is inline, it's OK --> should this be overtaken by callsInPlace?
                if (namedSymbol?.isInline == true) return@visitor
                // - if it is part of the contract system, it's OK
                if (namedSymbol?.callableId?.packageName?.asString()?.startsWith("kotlin.contracts") == true) return@visitor
                // - if we require the Raise context, it's OK
                if (hasContextWithClassId(RAISE_ID, call)) return@visitor
                // - we must have an argument which captures
                //   and for which the parameter has no Raise, nor a callsInPlace
                val potentiallyCaptured = (call as? KaFunctionCall<*>)?.argumentMapping ?: return@visitor
                @OptIn(KaExperimentalApi::class)
                val callsInPlaceVariables =
                    (symbol as? KaNamedFunctionSymbol)?.contractEffects.orEmpty().mapNotNull {
                        (it as? KaContractCallsInPlaceContractEffectDeclaration)?.valueParameterReference?.symbol
                    }
                val isSuspend = namedSymbol?.isSuspend == true
                val hasProblems = potentiallyCaptured.any { (argument, param) ->
                    param.symbol !in callsInPlaceVariables
                            && !hasContextWithClassId(RAISE_ID, param.symbol)
                            // suspend blah(f: CoroutineScope.() -> ...) is OK (launch, async, parMap)
                            && !(isSuspend && hasContextWithClassId(COROUTINE_SCOPE_ID, param.symbol))
                            && containsPotentialCallToRaise(argument)
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

    // check that a call in this lambda may potentially end up in Raise
    fun KaSession.containsPotentialCallToRaise(
        lambda: KtExpression
    ): Boolean = when (lambda) {
        is KtLambdaExpression -> {
            // look inside the lambda, since the inferred type tells us nothing
            var somethingFound = false
            val visitor = callExpressionRecursiveVisitor loop@{ expr ->
                val call = expr.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return@loop
                val containing = isClassId(RAISE_ID, call.symbol.containingDeclaration as? KaClassSymbol)
                val param = isClassId(RAISE_ID, call.symbol.receiverParameter?.returnType)
                if (containing || param) { somethingFound = true }
            }
            visitor.visitLambdaExpression(lambda)
            somethingFound
        }
        else -> {
            // check the type
            val expressionType = lambda.expressionType
            val expectedType = lambda.expectedType
            when {
                // try each of the types we know in order
                expressionType != null -> hasContextWithClassId(RAISE_ID, expressionType)
                expectedType != null -> hasContextWithClassId(RAISE_ID, expectedType)
                else -> true
            }
        }
    }
}