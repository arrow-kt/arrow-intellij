package arrow.intellij.raise

import arrow.intellij.NonDuplicateProblemsHolder
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
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters2
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.resolveCandidates
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.expressionVisitor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType

class BindInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        expressionVisitor visitor@{ expr ->
            val context = expr.analyze(BodyResolveMode.FULL)
            val resolver = expr.getResolutionFacade()
            // we should be in Raise context
            if (!expr.inRaiseContext(context, resolver)) return@visitor
            // check depending on the diagnostic type
            val ndHolder = NonDuplicateProblemsHolder(holder)
            context.diagnostics.forElement(expr).forEach {
                checkTypeMismatch(context, expr, it, ndHolder)
                checkNoneApplicable(context, resolver, expr, it, ndHolder)
            }
        }

    private fun checkTypeMismatch(
        context: BindingContext,
        expression: KtExpression,
        diagnostic: Diagnostic,
        holder: NonDuplicateProblemsHolder
    ) {
        if (diagnostic.factoryName != "TYPE_MISMATCH" || diagnostic !is DiagnosticWithParameters2<*, *, *>) return
        // check twice, as different options give better results
        // since we use a NonDuplicateProblemsHolder, we get no duplicates
        checkActualExpected(
            expression,
            expression.getType(context),
            context[BindingContext.EXPECTED_EXPRESSION_TYPE, expression],
            holder
        )
        checkActualExpected(
            expression,
            diagnostic.b as? KotlinType,
            diagnostic.a as? KotlinType,
            holder
        )
    }

    private fun checkNoneApplicable(
        context: BindingContext,
        resolver: ResolutionFacade,
        expr: KtExpression,
        diagnostic: Diagnostic,
        holder: NonDuplicateProblemsHolder
    ) {
        if (!diagnostic.factoryName.startsWith("NONE_APPLICABLE")) return
        val call = context[BindingContext.CALL, expr] ?: return
        call.resolveCandidates(context, resolver, filterOutWrongReceiver = false).forEach { candidate ->
            for (param in candidate.candidateDescriptor.valueParameters) {
                val expectedType = param.type
                for (argument in candidate.valueArguments[param]?.arguments.orEmpty()) {
                    val argumentExpression = argument.getArgumentExpression() ?: continue
                    val argumentType = argumentExpression.getType(context)
                    checkActualExpected(argumentExpression, argumentType, expectedType, holder)
                }
            }

            fun checkReceiver(
                parameter: ReceiverParameterDescriptor?,
                argument: ReceiverValue?
            ) {
                if (parameter == null) return
                if (argument !is ExpressionReceiver) return
                val expectedType = parameter.type
                checkActualExpected(argument.expression, argument.type, expectedType, holder)
            }

            checkReceiver(candidate.candidateDescriptor.extensionReceiverParameter, candidate.extensionReceiver)
            checkReceiver(candidate.candidateDescriptor.dispatchReceiverParameter, candidate.dispatchReceiver)
        }
    }

    private fun checkActualExpected(
        expression: KtExpression,
        expressionType: KotlinType?,
        expectedType: KotlinType?,
        holder: NonDuplicateProblemsHolder
    ) {
        if (expressionType.isBindable && expectedType.isNotBindable) {
            holder.registerProblem(
                expression,
                "Potentially missing 'bind'",
                ProblemHighlightType.GENERIC_ERROR,
                AddBind("bind")
            )
        }

       if (expressionType?.iterableElement.isBindable && expectedType?.iterableElement.isNotBindable) {
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