package arrow.intellij.raise

import arrow.intellij.addImportIfMissing
import arrow.intellij.findArgumentToRaise
import arrow.intellij.findEqualsNull
import arrow.intellij.inRaiseContext
import arrow.intellij.singleBlockExpression
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.ifExpressionVisitor
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class EnsureInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        ifExpressionVisitor visitor@{ expression ->
            // do the cheap checks before
            if (expression.condition == null) return@visitor
            val singleThenExpression = expression.then?.singleBlockExpression()
            val singleElseExpression = expression.`else`?.singleBlockExpression()
            // we should be in Raise context
            val context = expression.analyze(BodyResolveMode.FULL)
            val resolver = expression.getResolutionFacade()
            if (!expression.inRaiseContext(context, resolver)) return@visitor
            // check that we have a 'raise' in either then or else
            val (condition, place) = when {
                singleThenExpression?.findArgumentToRaise(context) != null -> expression.condition to WithErrorPlace.THEN
                singleElseExpression?.findArgumentToRaise(context) != null -> expression.condition!!.negate() to WithErrorPlace.ELSE
                else -> return@visitor
            }
            // we found it!
            if (condition?.findEqualsNull() == null) {
                holder.registerProblem(
                    expression,
                    "Conditional expression may be replaced with 'ensure'",
                    ProblemHighlightType.WEAK_WARNING,
                    ReplaceWithEnsure(place)
                )
            } else {
                holder.registerProblem(
                    expression,
                    "Conditional expression may be replaced with 'ensureNotNull'",
                    ProblemHighlightType.WEAK_WARNING,
                    ReplaceWithEnsureNotNull(place)
                )
            }
        }

    enum class WithErrorPlace { THEN, ELSE }

    abstract class AbstractReplaceWithEnsure(private val place: WithErrorPlace): LocalQuickFix {
        override fun getFamilyName(): String = "Fixes related to Raise"

        fun getCondition(expression: KtIfExpression): Triple<KtExpression, KtExpression, List<KtExpression>>? {
            val context = expression.analyze(BodyResolveMode.PARTIAL)
            val condition = when (place) {
                WithErrorPlace.THEN -> expression.condition?.negate()
                WithErrorPlace.ELSE -> expression.condition
            }
            val insideRaise = when (place) {
                WithErrorPlace.THEN -> expression.then?.singleBlockExpression()?.findArgumentToRaise(context)
                WithErrorPlace.ELSE -> expression.`else`?.singleBlockExpression()?.findArgumentToRaise(context)
            }
            val other = when (place) {
                WithErrorPlace.THEN -> expression.`else`
                WithErrorPlace.ELSE -> expression.then
            }
            val otherStatements = when (other) {
                null -> emptyList<KtExpression>()
                is KtBlockExpression -> other.statements
                else -> listOf(other)
            }
            if (condition == null || insideRaise == null) return null
            return Triple(condition, insideRaise, otherStatements)
        }

        fun execute(project: Project, originalExpression: KtIfExpression, import: String, others: List<KtExpression>, newExpressionText: String) {
            val factory = KtPsiFactory(project)
            val parent = originalExpression.parent
            originalExpression.containingKtFile.addImportIfMissing(import)
            for (other in others.reversed()) {
                parent.addAfter(other, originalExpression)
                val space = factory.createWhiteSpace("\n")
                parent.addAfter(space, originalExpression)
            }
            val newExpression = factory.createExpression(newExpressionText)
            originalExpression.replace(newExpression)
            if (others.isNotEmpty()) {
                parent.reformat(true)
            }
        }
    }

    class ReplaceWithEnsure(place: WithErrorPlace): AbstractReplaceWithEnsure(place) {
        override fun getName(): String = "Replace with call to 'ensure'"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtIfExpression ?: return
            val (condition, insideRaise, others) = getCondition(expression) ?: return
            execute(
                project,
                expression,
                "arrow.core.raise.ensure",
                others,
                "ensure(${condition.text}) { ${insideRaise.text} }"
            )
        }
    }

    class ReplaceWithEnsureNotNull(place: WithErrorPlace): AbstractReplaceWithEnsure(place) {
        override fun getName(): String = "Replace with call to 'ensureNotNull'"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtIfExpression ?: return
            val (condition, insideRaise, others) = getCondition(expression) ?: return
            val equalsNull = condition.negate().findEqualsNull()!!
            execute(
                project,
                expression,
                "arrow.core.raise.ensureNotNull",
                others,
                "ensureNotNull(${equalsNull.text}) { ${insideRaise.text} }"
            )
        }
    }
}