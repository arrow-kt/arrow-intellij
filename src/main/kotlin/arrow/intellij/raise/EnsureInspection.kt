package arrow.intellij.raise

import arrow.intellij.addImportIfMissing
import arrow.intellij.inRaiseContext
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpression
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.ifExpressionVisitor

class EnsureInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        ifExpressionVisitor visitor@{ expression ->
            // do the cheap checks before
            if (expression.condition == null) return@visitor
            analyze(expression) {
                val singleThenExpression = expression.then?.singleBlockExpression()
                val singleElseExpression = expression.`else`?.singleBlockExpression()
                // we should be in Raise context
                if (!inRaiseContext(expression)) return@visitor

                // check that we have a 'raise' in either then or else
                val originalCondition = expression.condition ?: return@visitor
                val originalConditionVariables = usedVariables(originalCondition)
                val (condition, place) = when {
                    findArgumentToRaise(singleElseExpression, originalConditionVariables) != null -> originalCondition.negate() to WithErrorPlace.ELSE
                    findArgumentToRaise(singleThenExpression, originalConditionVariables) != null -> originalCondition to WithErrorPlace.THEN
                    else -> return@visitor
                }
                // we found it!
                if (condition.findEqualsNull() == null) {
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
        }

    val RAISE_CALLABLE_ID = CallableId(ClassId.fromString("arrow/core/raise/Raise"), Name.identifier("raise"))

    fun KaSession.findArgumentToRaise(
        expression: KtExpression?,
        conditionVariables: Set<KaVariableSymbol>
    ): KtExpression? {
        val call = expression?.resolveToCall()?.successfulCallOrNull<KaFunctionCall<*>>() ?: return null
        if (call.symbol.callableId != RAISE_CALLABLE_ID) return null
        val argument = call.argumentMapping.keys.singleOrNull() ?: return null
        val smartCastedVariables = smartCastedVariables(argument)
        if (smartCastedVariables.any { it in conditionVariables }) return null
        return argument
    }

    enum class WithErrorPlace { THEN, ELSE }

    abstract class AbstractReplaceWithEnsure(private val place: WithErrorPlace): LocalQuickFix {
        override fun getFamilyName(): String = "Fixes related to Raise"

        fun getCondition(expression: KtIfExpression): Triple<KtExpression, KtExpression, List<KtExpression>>? {
            val condition = when (place) {
                WithErrorPlace.THEN -> expression.condition?.negate()
                WithErrorPlace.ELSE -> expression.condition
            }
            val insideRaise = when (place) {
                WithErrorPlace.THEN -> expression.then?.singleBlockExpression()?.raiseArgument
                WithErrorPlace.ELSE -> expression.`else`?.singleBlockExpression()?.raiseArgument
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

        val KtExpression.raiseArgument: KtExpression?
            get() = (this as? KtCallExpression)?.valueArguments?.singleOrNull()?.getArgumentExpression()

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
                CodeStyleManager.getInstance(project).reformat(parent, true)
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

fun KtExpression.findEqualsNull(): KtExpression? {
    if (this !is KtBinaryExpression) return null
    if (this.operationToken != KtTokens.EQEQ && this.operationToken != KtTokens.EQEQEQ) return null
    if (right.isNullExpression()) return left
    if (left.isNullExpression()) return right
    return null
}

fun KtExpression.singleBlockExpression(): KtExpression? = when (this) {
    is KtBlockExpression -> statements.singleOrNull()
    else -> this
}

fun KaSession.usedVariables(expression: PsiElement): Set<KaVariableSymbol> {
    if (expression is KtElement) {
        val variable = expression.resolveToCall()?.successfulCallOrNull<KaVariableAccessCall>()
        if (variable != null) return setOf(variable.symbol)
    }
    return expression.childrenOfType<KtExpression>().flatMapTo(mutableSetOf()) { usedVariables(it) }
}

fun KaSession.smartCastedVariables(expression: PsiElement): Set<KaVariableSymbol> {
    if (expression is KtElement) {
        val variable = expression.resolveToCall()?.successfulCallOrNull<KaVariableAccessCall>()
        if (variable != null) return setOf(variable.symbol)
    }
    return expression.children.flatMapTo(mutableSetOf()) { smartCastedVariables(it) }
}
