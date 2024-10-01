package arrow.intellij.fix

import arrow.intellij.addImportIfMissing
import arrow.intellij.isClassTypeFrom
import arrow.intellij.simpleName
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.parameterVisitor

class MissingSerializer: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        parameterVisitor visitor@{ parameter ->
            analyze(parameter) {
                val typeReference = parameter.typeReference ?: return@visitor
                val allDiagnostics = parameter.containingKtFile.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                val hasMissingSerializer = allDiagnostics.any {
                    it.factoryName == "SERIALIZER_NOT_FOUND" && typeReference.textRange in it.textRanges
                }
                val type = typeReference.type
                if (!hasMissingSerializer || !isClassTypeFrom(type, SERIALIZABLE_TYPES)) return@visitor

                val simpleName = type.simpleName ?: "??"
                holder.registerProblem(
                    typeReference.typeElement!!,
                    "Missing Arrow serializer for '$simpleName'",
                    ProblemHighlightType.GENERIC_ERROR,
                    AddSerializer(simpleName)
                )
            }
        }

    val SERIALIZABLE_TYPES = setOf(
        ClassId.fromString("arrow/core/Either"),
        ClassId.fromString("arrow/core/Validated"),
        ClassId.fromString("arrow/core/Ior"),
        ClassId.fromString("arrow/core/Option"),
        ClassId.fromString("arrow/core/NonEmptyList"),
        ClassId.fromString("arrow/core/NonEmptySet"),
    )

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