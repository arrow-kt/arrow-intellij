package arrow.intellij.wrong

import arrow.intellij.fqNameString
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.expressionVisitor
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class AtomicPrimitiveInspection: AbstractKotlinInspection() {
    val ATOMIC_TYPES = listOf("java.util.concurrent.atomic.AtomicReference", "arrow.atomic.Atomic")
    val PRIMITIVE_WITH_ATOMIC = listOf("kotlin.Boolean", "kotlin.Int", "kotlin.Float")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        expressionVisitor visitor@{ expression ->
            val context = expression.analyze(BodyResolveMode.FULL)
            val expressionType = expression.getType(context)
            if (expressionType?.fqNameString !in ATOMIC_TYPES) return@visitor
            val innerType = expressionType?.arguments?.firstOrNull()?.type ?: return@visitor
            if (innerType.fqNameString in PRIMITIVE_WITH_ATOMIC) {
                val call = expression.getResolvedCall(context)
                val isConstructor = call?.resultingDescriptor is ConstructorDescriptor
                val smallType = innerType.nameIfStandardType!!.asString()
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
            val expression = descriptor.psiElement as? KtExpression ?: return
            val factory = KtPsiFactory(project)
            val context = expression.analyze(BodyResolveMode.FULL)
            expression.containingKtFile.addImport(FqName("arrow.atomic.Atomic$typeName"))
            val call = expression.getResolvedCall(context)
            val argument = call?.valueArgumentsByIndex?.firstOrNull()?.arguments?.firstOrNull()?.getArgumentExpression() ?: return
            val newExpression = factory.createExpression("Atomic${typeName}(${argument.text})")
            expression.replace(newExpression)
        }
    }
}