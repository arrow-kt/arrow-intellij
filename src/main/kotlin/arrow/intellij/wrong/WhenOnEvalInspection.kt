package arrow.intellij.wrong

import arrow.intellij.EVAL_TYPES
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.whenExpressionVisitor

class WhenOnEvalInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        whenExpressionVisitor visitor@{ expression ->
            analyze(expression) {
                val subject = expression.subjectExpression ?: return@visitor
                val subjectType = subject.expressionType ?: return@visitor
                if (EVAL_TYPES.any { subjectType.isClassType(it) }) {
                    holder.registerProblem(
                        subject,
                        "Matching on Eval value is discouraged",
                        ProblemHighlightType.WARNING,
                    )
                }
            }
        }
}