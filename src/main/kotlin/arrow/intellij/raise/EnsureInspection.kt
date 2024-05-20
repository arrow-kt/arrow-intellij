package arrow.intellij.raise

import arrow.intellij.findArgumentToRaise
import arrow.intellij.findEqualsNull
import arrow.intellij.fqNameString
import arrow.intellij.inRaiseContext
import arrow.intellij.singleBlockExpression
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.ifExpressionVisitor
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class EnsureInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        ifExpressionVisitor visitor@{ expression ->
            // do the cheap checks before
            // if (expr) singleThing()
            if (expression.`else` != null) return@visitor
            if (expression.condition == null) return@visitor
            val singleThenExpression = expression.then?.singleBlockExpression() ?: return@visitor
            // we should be in Raise context
            val context = expression.analyze(BodyResolveMode.PARTIAL)
            val resolver = expression.getResolutionFacade()
            if (!expression.inRaiseContext(context, resolver)) return@visitor
            // check that we have a 'raise'
            val call = singleThenExpression.getResolvedCall(context)
            if (call?.resultingDescriptor?.fqNameString != "arrow.core.raise.Raise.raise") return@visitor
            // we found it!
            if (expression.condition?.findEqualsNull() == null) {
                holder.registerProblem(
                    expression,
                    "Conditional expression may be replaced with 'ensure'",
                    ProblemHighlightType.WEAK_WARNING,
                    ReplaceWithEnsure()
                )
            } else {
                holder.registerProblem(
                    expression,
                    "Conditional expression may be replaced with 'ensureNotNull'",
                    ProblemHighlightType.WEAK_WARNING,
                    ReplaceWithEnsureNotNull()
                )
            }
        }

    class ReplaceWithEnsure: LocalQuickFix {
        override fun getName(): String = "Replace with call to 'ensure'"
        override fun getFamilyName(): String = "Fixes related to Raise"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtIfExpression ?: return
            val context = expression.analyze(BodyResolveMode.PARTIAL)
            val insideRaise = expression.then?.singleBlockExpression()?.findArgumentToRaise(context) ?: return
            val factory = KtPsiFactory(project)
            expression.containingKtFile.addImport(FqName("arrow.core.raise.ensure"))
            val newExpression = factory.createExpression("ensure(${expression.condition?.negate()?.text}) { ${insideRaise.text} }")
            expression.replace(newExpression)
        }
    }

    class ReplaceWithEnsureNotNull: LocalQuickFix {
        override fun getName(): String = "Replace with call to 'ensureNotNull'"
        override fun getFamilyName(): String = "Fixes related to Raise"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtIfExpression ?: return
            val context = expression.analyze(BodyResolveMode.PARTIAL)
            val insideRaise = expression.then?.singleBlockExpression()?.findArgumentToRaise(context) ?: return
            val factory = KtPsiFactory(project)
            expression.containingKtFile.addImport(FqName("arrow.core.raise.ensureNotNull"))
            val equalsNull = expression.condition!!.findEqualsNull()!!
            val newExpression = factory.createExpression("ensureNotNull(${equalsNull.text}) { ${insideRaise.text} }")
            expression.replace(newExpression)
        }
    }
}