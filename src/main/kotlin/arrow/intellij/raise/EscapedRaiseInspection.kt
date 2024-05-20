package arrow.intellij.raise

import arrow.intellij.fqNameString
import arrow.intellij.inRaiseContext
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.CallableDescriptor
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
            if (!call.resultingDescriptor.potentiallyWrongCapture) return@visitor
            val lastArgument = call.valueArgumentsByIndex?.lastOrNull() ?: return@visitor
            if (lastArgument.arguments.any { it is KtLambdaArgument }) {
                holder.registerProblem(
                    expr.calleeExpression as KtExpression,
                    "Raise context may escape through this lambda",
                    ProblemHighlightType.WARNING
                )
            }
        }

    private val CallableDescriptor.potentiallyWrongCapture: Boolean
        get() = fqNameString in listOf("kotlin.lazy", "kotlin.sequences.sequence", "kotlinx.coroutines.flow.flow")
}