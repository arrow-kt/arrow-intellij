package arrow.intellij.gutter

import arrow.intellij.RAISE_ID
import arrow.intellij.hasContextWithClassId
import com.intellij.codeInsight.daemon.DefaultGutterIconNavigationHandler
import com.intellij.codeInsight.daemon.GutterName
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class RaiseGutter: LineMarkerProviderDescriptor() {
    override fun getName(): @GutterName String? = "Gutter icons for Raise"

    val ICON = IconLoader.getIcon("/icons/predictedException.svg", this::class.java)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is KtNameReferenceExpression) return null
        analyze(element) {
            val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
            if (!hasContextWithClassId(RAISE_ID, call)) return null
            val identifier = element.getIdentifier() ?: return null
            val title = "Raise function call '${element.text}'"
            return LineMarkerInfo(
                identifier, identifier.textRange,
                ICON,
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