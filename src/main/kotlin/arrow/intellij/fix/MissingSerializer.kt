package arrow.intellij.fix

import arrow.intellij.addImportIfMissing
import arrow.intellij.fqNameString
import arrow.intellij.isArrowSerializable
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.declarationVisitor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class MissingSerializer: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        declarationVisitor visitor@{ declaration ->
            val callable = declaration as? KtCallableDeclaration ?: return@visitor
            val context = callable.analyze(BodyResolveMode.FULL)
            val typeRef = callable.typeReference ?: return@visitor
            val typeElement = typeRef.typeElement  ?: return@visitor
            val type = context[BindingContext.TYPE, typeRef] ?: return@visitor
            val hasMissingSerializer = context.diagnostics.forElement(typeElement).any {
                it.factoryName == "SERIALIZER_NOT_FOUND"
            }
            if (!hasMissingSerializer || !type.isArrowSerializable) return@visitor

            val simpleName = type.fqNameString!!.split('.').last()
            holder.registerProblem(
                typeElement,
                "Missing Arrow serializer for '$simpleName'",
                ProblemHighlightType.GENERIC_ERROR,
                AddSerializer(simpleName)
            )
        }

    class AddSerializer(val simpleName: String): LocalQuickFix {
        override fun getName(): String = "Import serializer for '$simpleName'"
        override fun getFamilyName(): String = "Fixes related to serialization"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val typeElement = descriptor.psiElement as? KtTypeElement ?: return
            val file = typeElement.containingKtFile
            val factory = KtPsiFactory(project)
            file.addImportIfMissing("kotlinx.serialization.UseSerializers")
            file.addImportIfMissing("arrow.core.serialization.${simpleName}Serializer")
            val annotation = factory.createFileAnnotation("@file:UseSerializers(${simpleName}Serializer::class)")
            val space = factory.createWhiteSpace("\n")
            if (file.annotationEntries.isEmpty()) {
                file.addBefore(annotation, file.packageDirective)
                file.addBefore(space, file.packageDirective)
            } else {
                file.addAfter(space, file.annotationEntries.last())
                file.addAfter(annotation, space)
            }
        }
    }
}