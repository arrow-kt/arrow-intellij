package arrow.intellij

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.psi.KtExpression

class NonDuplicateProblemsHolder(
    private val holder: ProblemsHolder
) {
    private val alreadyReported = mutableListOf<KtExpression>()

    fun registerProblem(
        expression: KtExpression,
        descriptionTemplate: String,
        highlightType: ProblemHighlightType,
        vararg fixes: LocalQuickFix
    ) {
        if (expression in alreadyReported) return
        holder.registerProblem(
            expression,
            descriptionTemplate,
            highlightType,
            *fixes
        )
        alreadyReported.add(expression)
    }
}