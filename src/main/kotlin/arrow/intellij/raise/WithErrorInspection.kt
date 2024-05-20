package arrow.intellij.raise

import arrow.intellij.NonDuplicateProblemsHolder
import arrow.intellij.isBindable
import arrow.intellij.isRaise
import arrow.intellij.raiseContexts
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.resolveCandidates
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.expressionVisitor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class WithErrorInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        expressionVisitor visitor@{ expr ->
            val context = expr.analyze(BodyResolveMode.PARTIAL)
            val resolver = expr.getResolutionFacade()
            // we should be in Raise context
            val raiseContexts = expr.raiseContexts(context, resolver).toList()
            if (raiseContexts.isEmpty()) return@visitor
            // check depending on the diagnostic type
            val ndHolder = NonDuplicateProblemsHolder(holder)
            context.diagnostics.forElement(expr).forEach {
                checkWrongReceiver(context, resolver, raiseContexts, expr, it, ndHolder)
            }
        }

    private fun checkWrongReceiver(
        context: BindingContext,
        resolver: ResolutionFacade,
        raiseContexts: List<KotlinType>,
        expr: KtExpression,
        diagnostic: Diagnostic,
        holder: NonDuplicateProblemsHolder
    ) {
        if (!diagnostic.factoryName.startsWith("UNRESOLVED_REFERENCE")) return
        val call = context[BindingContext.CALL, expr] ?: return
        call.resolveCandidates(context, resolver, filterOutWrongReceiver = false).forEach { candidate ->
            checkContext(raiseContexts, expr, candidate.extensionReceiver?.type, holder)
            checkContext(raiseContexts, expr, candidate.dispatchReceiver?.type, holder)
            checkContext(raiseContexts, expr, candidate.candidateDescriptor.extensionReceiverParameter?.type, holder)
            checkContext(raiseContexts, expr, candidate.candidateDescriptor.dispatchReceiverParameter?.type, holder)
        }
    }

    private fun checkContext(
        raiseContexts: List<KotlinType>,
        expression: KtExpression,
        receiverType: KotlinType?,
        holder: NonDuplicateProblemsHolder
    ) {
        if (receiverType == null || (!receiverType.isRaise && !receiverType.isBindable)) return
        val errorType = receiverType.arguments.first().type
        val hasRequiredContext = raiseContexts.any { raise ->
            val raiseType = raise.arguments.first().type
            errorType.isSubtypeOf(raiseType)
        }
        if (!hasRequiredContext) {
            holder.registerProblem(
                expression,
                "Missing Raise context with error type '$errorType'",
                ProblemHighlightType.POSSIBLE_PROBLEM,
                AddWithError()
            )
        }
    }

    class AddWithError: LocalQuickFix {
        override fun getName(): String = "Add call to 'withError'"
        override fun getFamilyName(): String = "Fixes related to Raise"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtExpression ?: return
            val factory = KtPsiFactory(project)
            element.containingKtFile.addImport(FqName("arrow.core.raise.withError"))
            val elementToModify = when (val p = element.parent.parent) {
                is KtDotQualifiedExpression -> p
                else -> element.parent as KtExpression
            }
            val originalOffset = elementToModify.textOffset
            val newElementText = "withError({  }) { ${elementToModify.text} }"
            elementToModify.replace(factory.createExpression(newElementText))
            element.findExistingEditor()?.moveCaret(originalOffset + "withError({ ".length)
        }
    }
}