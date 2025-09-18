package arrow.intellij.raise

import arrow.intellij.NonDuplicateProblemsHolder
import arrow.intellij.addImportIfMissing
import arrow.intellij.commonDiagnosticsFor
import arrow.intellij.isBindable
import arrow.intellij.isClassId
import arrow.intellij.RAISE_ID
import arrow.intellij.raiseContexts
import arrow.intellij.simpleName
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.expressionVisitor

class WithErrorInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        expressionVisitor visitor@{ expr ->
            analyze(expr) {
                // we should be in Raise context
                val raiseContexts = raiseContexts(expr)
                if (raiseContexts.isEmpty()) return@visitor
                // check depending on the diagnostic type
                val ndHolder = NonDuplicateProblemsHolder(holder)
                commonDiagnosticsFor(expr).forEach {
                    checkWrongReceiver(raiseContexts, expr, it, ndHolder)
                }
            }
        }

    private val problematicDiagnostics = listOf("UNRESOLVED_REFERENCE", "NO_CONTEXT_ARGUMENT")

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.checkWrongReceiver(
        raiseContexts: List<KaType>,
        expr: KtExpression,
        diagnostic: KaDiagnosticWithPsi<*>,
        holder: NonDuplicateProblemsHolder
    ) {
        if (problematicDiagnostics.none { diagnostic.factoryName.startsWith(it) }) return
        val call = expr.resolveToCall() ?: return
        for (candidate in call.calls) {
            if (candidate !is KaCallableMemberCall<*, *>) continue
            val pas = candidate.partiallyAppliedSymbol
            val symbol = candidate.symbol
            checkContext(raiseContexts, expr, pas.extensionReceiver?.type, holder)
            checkContext(raiseContexts, expr, pas.dispatchReceiver?.type, holder)
            checkContext(raiseContexts, expr, symbol.receiverType, holder)
            for (contextArgument in pas.contextArguments) {
                checkContext(raiseContexts, expr, contextArgument.type, holder, contextArgument = true)
            }
        }
    }

    private fun KaSession.checkContext(
        raiseContexts: List<KaType>,
        expression: KtExpression,
        receiverType: KaType?,
        holder: NonDuplicateProblemsHolder,
        contextArgument: Boolean = false,
    ) {
        if (receiverType == null) return
        if (!isClassId(RAISE_ID, receiverType) && !isBindable(receiverType)) return

        if (receiverType !is KaClassType) return
        val errorType = receiverType.typeArguments.first().type ?: return
        val hasRequiredContext = raiseContexts.any { raise ->
            val raiseType = (raise as? KaClassType)?.typeArguments?.first()?.type
            raiseType != null && errorType.isSubtypeOf(raiseType)
        }
        if (!hasRequiredContext) {
            holder.registerProblem(
                expression,
                "Missing Raise context with error type '${errorType.simpleName ?: "??"}'",
                ProblemHighlightType.GENERIC_ERROR,
                AddWithError(contextArgument)
            )
        }
    }

    class AddWithError(val contextArgument: Boolean): LocalQuickFix {
        override fun getName(): String = "Add call to 'withError'"
        override fun getFamilyName(): String = "Fixes related to Raise"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtExpression ?: return
            val factory = KtPsiFactory(project)
            val missingImport = when {
                contextArgument -> "arrow.core.raise.context.withContext"
                else -> "arrow.core.raise.withError"
            }
            element.containingKtFile.addImportIfMissing(missingImport)
            val elementToModify = when {
                element.parent.parent is KtDotQualifiedExpression -> element.parent.parent
                element is KtCallExpression -> element
                else -> element.parent
            } as KtExpression
            val originalOffset = elementToModify.textOffset
            val newElementText = "withError({  }) { ${elementToModify.text} }"
            elementToModify.replace(factory.createExpression(newElementText))
            element.findExistingEditor()?.moveCaret(originalOffset + "withError({ ".length)
        }
    }
}