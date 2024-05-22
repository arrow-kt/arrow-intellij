package arrow.intellij.raise

import arrow.intellij.hasRaiseContext
import arrow.intellij.inRaiseContext
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.contracts.description.CallsEffectDeclaration
import org.jetbrains.kotlin.contracts.description.ContractProviderKey
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class EscapedRaiseInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        callExpressionVisitor visitor@{ expr ->
            val context = expr.analyze(BodyResolveMode.FULL)
            val resolver = expr.getResolutionFacade()
            // we should be in Raise context
            if (!expr.inRaiseContext(context, resolver)) return@visitor
            // get the resolved call
            val call = expr.getResolvedCall(context) ?: return@visitor
            // start filtering out good cases
            val descriptor = call.resultingDescriptor
            // - if the function is inline, it's OK --> should this be overtaken by callsInPlace?
            if ((descriptor as? FunctionDescriptor)?.isInline == true) return@visitor
            // - if we require the Raise context, it's OK
            if (descriptor.hasRaiseContext) return@visitor
            // - we must have a lambda which may capture
            val potentiallyCaptured = call.valueArguments.flatMap { (param, argument) ->
                argument.arguments.filterIsInstance<KtLambdaArgument>().map { param to it }
            }
            if (potentiallyCaptured.isEmpty()) return@visitor
            val contracts = descriptor.getUserData(ContractProviderKey)?.getContractDescription()?.effects.orEmpty()
            val callsInPlaceVariables = contracts.mapNotNull { (it as? CallsEffectDeclaration)?.variableReference?.descriptor }
            // ... and for which the parameter has no Raise, nor a callsInPlace
            val hasProblems = potentiallyCaptured.any { (param, _) ->
                when {
                    param.hasRaiseContext -> false
                    param in callsInPlaceVariables -> false
                    else -> true
                }
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