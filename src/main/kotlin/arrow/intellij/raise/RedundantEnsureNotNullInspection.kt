package arrow.intellij.raise

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.expressionOrReturnType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

class RedundantEnsureNotNullInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, RedundantEnsureNotNullInspection.Context>() {
    data class Context(val isUsedAsExpression: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val functionName = element.calleeExpression?.text ?: return false
        return functionName == "ensureNotNull"
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        if (!validateFunctionCall(element)) return null
        if (!firstArgumentNonNullable(element)) return null
        return Context(element.isUsedAsExpression)
    }

    override fun getProblemDescription(element: KtCallExpression, context: Context) =
        "Redundant 'ensureNotNull' call"

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtCallExpression> =
        RemoveRedundantEnsureNotNullCallFix(context)

    private class RemoveRedundantEnsureNotNullCallFix(
        private val elementContext: Context,
    ) : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): String = "Fixes related to Raise"
        override fun getName(): String = "Remove redundant 'ensureNotNull' call"

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val target = element.getQualifiedExpressionForSelector() ?: element
            if (elementContext.isUsedAsExpression) {
                val argument = element.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
                target.replace(argument)
            } else {
                target.delete()
            }
        }
    }
}

private fun KaSession.firstArgumentNonNullable(call: KtCallExpression): Boolean {
    val firstArg = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
    val expressionType = firstArg.expressionType ?: return false
    return !expressionType.isNullable
}

private fun KaSession.validateFunctionCall(element: KtCallExpression): Boolean {
    val call = element.resolveToCall() ?: return false
    return call.successfulFunctionCallOrNull()?.symbol?.callableId in ENSURE_NOT_NULL_CALLABLE_IDS
}

private val ENSURE_NOT_NULL_EXTENSION_CALLABLE_ID =
    CallableId(FqName("arrow.core.raise"), Name.identifier("ensureNotNull"))
private val ENSURE_NOT_NULL_CONTEXT_CALLABLE_ID =
    CallableId(FqName("arrow.core.raise.context"), Name.identifier("ensureNotNull"))
private val ENSURE_NOT_NULL_CALLABLE_IDS =
    setOf(ENSURE_NOT_NULL_EXTENSION_CALLABLE_ID, ENSURE_NOT_NULL_CONTEXT_CALLABLE_ID)
