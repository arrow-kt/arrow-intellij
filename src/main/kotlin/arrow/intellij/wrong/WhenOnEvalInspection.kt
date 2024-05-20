package arrow.intellij.wrong

import arrow.intellij.fqNameString
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.whenExpressionVisitor
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class WhenOnEvalInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        whenExpressionVisitor visitor@{ expression ->
            val context = expression.analyze(BodyResolveMode.FULL)
            val subject = expression.subjectExpression ?: return@visitor
            val subjectType = subject.getType(context) ?: return@visitor
            if (subjectType.fqNameString == "arrow.eval.Eval" || subjectType.fqNameString == "arrow.core.Eval") {
                holder.registerProblem(
                    subject,
                    "Matching on Eval value is discouraged",
                    ProblemHighlightType.WARNING,
                )
            }
        }
}