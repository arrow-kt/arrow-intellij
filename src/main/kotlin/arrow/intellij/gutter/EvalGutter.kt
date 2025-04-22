package arrow.intellij.gutter

import arrow.intellij.EVAL_CALLABLES
import arrow.intellij.EVAL_COMPANION_TYPES
import com.intellij.codeInsight.daemon.DefaultGutterIconNavigationHandler
import com.intellij.codeInsight.daemon.GutterName
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class EvalGutter: LineMarkerProviderDescriptor() {
    override fun getName(): @GutterName String? = "Gutter icons for Eval"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is KtNameReferenceExpression) return null
        analyze(element) {
            val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
            val callableId = call.symbol.callableId ?: return null
            if (callableId.classId !in EVAL_COMPANION_TYPES) return null
            val (title, icon) = EVAL_CALLABLES[callableId.callableName] ?: return null
            val identifier = element.getIdentifier() ?: return null
            return LineMarkerInfo(
                identifier, identifier.textRange,
                icon,
                { title },
                DefaultGutterIconNavigationHandler(
                    listOf(call.symbol.psi as? NavigatablePsiElement ?: element),
                    title
                ),
                GutterIconRenderer.Alignment.LEFT,
                { title }
            )
        }
    }
}