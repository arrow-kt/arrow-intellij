package arrow.intellij.wrong

import arrow.intellij.isClassTypeFrom
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.expressionVisitor

class AtomicPrimitiveInspection: AbstractKotlinInspection() {
    private val ATOMIC_CLASSES = setOf(
        ClassId.fromString("arrow/atomic/Atomic"),
        ClassId.fromString("java/util/concurrent/atomic/AtomicReference")
    )

    fun KaSession.isPrimitiveWithAtomic(type: KaType): Boolean =
        type.isIntType || type.isBooleanType || type.isFloatType

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        expressionVisitor visitor@{ expression ->
            analyze(expression) {
                val expressionType = expression.expressionType ?: return@visitor
                if (!isClassTypeFrom(expressionType, ATOMIC_CLASSES)) return@visitor
                val innerType = (expressionType as? KaClassType)?.typeArguments?.firstOrNull()?.type ?: return@visitor
                val notFlexibleInnerType = when (innerType) {
                    is KaFlexibleType -> innerType.lowerBound
                    else -> innerType
                }
                if (!isPrimitiveWithAtomic(notFlexibleInnerType)) return@visitor

                val call = expression.resolveToCall()?.successfulCallOrNull<KaFunctionCall<*>>() ?: return@visitor
                val isConstructor = call.symbol is KaConstructorSymbol

                val smallType = notFlexibleInnerType.symbol?.name?.asString() ?: "??"
                holder.registerProblem(
                    expression,
                    "Atomic reference used with primitive type '$smallType'",
                    ProblemHighlightType.GENERIC_ERROR,
                    *(if (isConstructor) arrayOf(ReplaceConstructor(smallType)) else arrayOf())
                )
            }
        }

    class ReplaceConstructor(private val typeName: String): LocalQuickFix {
        override fun getName(): String = "Replace with 'Atomic$typeName'"
        override fun getFamilyName(): String = "Fixes related to Atomic"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtCallExpression ?: return
            val factory = KtPsiFactory(project)
            expression.containingKtFile.addImport(FqName("arrow.atomic.Atomic$typeName"))
            val argument = expression.valueArguments.singleOrNull() ?: return
            val newExpression = factory.createExpression("Atomic${typeName}(${argument.text})")
            expression.replace(newExpression)
        }
    }
}