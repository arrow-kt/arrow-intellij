package arrow.intellij.raise

import arrow.intellij.NonDuplicateProblemsHolder
import arrow.intellij.commonDiagnosticsFor
import arrow.intellij.inRaiseContext
import arrow.intellij.isBindable
import arrow.intellij.isNotBindable
import arrow.intellij.iterableElement
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.expressionVisitor

class BindInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        expressionVisitor visitor@{ expr ->
            analyze(expr) {
                // we should be in Raise context
                if (!inRaiseContext(expr)) return@visitor
                // check depending on the diagnostic type
                val ndHolder = NonDuplicateProblemsHolder(holder)
                commonDiagnosticsFor(expr).forEach {
                    checkTypeMismatch(expr, it, ndHolder)
                    checkNoneApplicable(expr, it, ndHolder)
                }
            }

        }

    private fun KaSession.checkTypeMismatch(
        expression: KtExpression,
        diagnostic: KaDiagnosticWithPsi<*>,
        holder: NonDuplicateProblemsHolder
    ) {
        if (!diagnostic.factoryName.endsWith("TYPE_MISMATCH")) return

        // check twice, as different options give better results
        // since we use a NonDuplicateProblemsHolder, we get no duplicates
        checkActualExpected(expression, expression.expressionType, expression.expectedType, holder)
        if (diagnostic is KaFirDiagnostic.TypeMismatch) // only works on FIR
            checkActualExpected(expression, diagnostic.actualType, diagnostic.expectedType, holder)
    }

    private fun KaSession.checkNoneApplicable(
        expr: KtExpression,
        diagnostic: KaDiagnosticWithPsi<*>,
        holder: NonDuplicateProblemsHolder
    ) {
        if (!diagnostic.factoryName.startsWith("NONE_APPLICABLE")) return

        for (candidate in expr.resolveToCall()?.calls.orEmpty()) {
            if (candidate is KaFunctionCall<*>) {
                for ((expression, parameter) in candidate.argumentMapping) {
                    checkArgument(expression, parameter.returnType, holder)
                }
                val pas = candidate.partiallyAppliedSymbol
                pas.extensionReceiver?.let { checkArgument(it, holder) }
                pas.dispatchReceiver?.let { checkArgument(it, holder) }
            }
        }
    }

    private fun KaSession.checkArgument(
        expression: KtExpression,
        type: KaType,
        holder: NonDuplicateProblemsHolder
    ) {
        checkActualExpected(expression, expression.expressionType, expression.expectedType, holder)
        checkActualExpected(expression, expression.expressionType, type, holder)
    }

    private fun KaSession.checkArgument(
        argument: KaReceiverValue,
        holder: NonDuplicateProblemsHolder
    ) {
        if (argument !is KaExplicitReceiverValue) return
        checkArgument(argument.expression, argument.type, holder)
    }

    private fun KaSession.checkActualExpected(
        expression: KtExpression,
        expressionType: KaType?,
        expectedType: KaType?,
        holder: NonDuplicateProblemsHolder
    ) {
        if (isBindable(expressionType) && isNotBindable(expectedType)) {
            holder.registerProblem(
                expression,
                "Potentially missing 'bind'",
                ProblemHighlightType.GENERIC_ERROR,
                AddBind("bind")
            )
        }

        val iterableExpression = expressionType?.let { iterableElement(it) }
        val iterableExpected = expectedType?.let { iterableElement(it) }
        if (isBindable(iterableExpression) && isNotBindable(iterableExpected)) {
           holder.registerProblem(
               expression,
               "Potentially missing 'bindAll'",
               ProblemHighlightType.GENERIC_ERROR,
               AddBind("bindAll")
           )
       }
    }

    class AddBind(private val functionName: String): LocalQuickFix {
        override fun getName(): String = "Add call to '$functionName'"
        override fun getFamilyName(): String = "Fixes related to Raise"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtExpression ?: return
            val factory = KtPsiFactory(project)
            val text = if (element.text.contains(' ')) "(${element.text})" else element.text
            val newElement = factory.createExpression("$text.$functionName()")
            element.replace(newElement)
        }
    }

}