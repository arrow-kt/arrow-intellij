package arrow.intellij

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticProvider
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

@OptIn(KaExperimentalApi::class)
fun KaDiagnosticProvider.commonDiagnosticsFor(
    element: KtElement
): Collection<KaDiagnosticWithPsi<*>> = element.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)

fun KtFile.addImportIfMissing(fqName: String) {
    if (importDirectives.any { it.importedFqName?.asString() == fqName }) return
    addImport(FqName(fqName))
}

val KaType.simpleName: String?
    get() = (this as? KaClassType)?.classId?.shortClassName?.asString()

fun KaSession.isClassTypeFrom(
    type: KaType,
    classes: Collection<ClassId>
): Boolean = classes.any { type.isClassType(it) }

fun KaSession.getReceivers(
    expression: KtExpression
): List<KaType> = expression.containingKtFile.scopeContext(expression).implicitReceivers.map { it.type }

fun KaSession.inRaiseContext(
    expression: KtExpression
): Boolean = raiseContexts(expression).any()

fun KaSession.raiseContexts(
    expression: KtExpression
): List<KaType> = getReceivers(expression).filter { isRaise(it) }

val RAISE_ID = ClassId.fromString("arrow/core/raise/Raise")

fun KaSession.isRaise(type: KaType?): Boolean =
    type != null && (type.isClassType(RAISE_ID) || type.allSupertypes.any { isRaise(it) })

fun KaSession.hasRaiseContext(
    symbol: KaVariableSymbol
): Boolean = symbol.returnType is KaFunctionType && isRaise((symbol.returnType as KaFunctionType).receiverType)

fun KaSession.hasRaiseContext(
    call: KaCall
): Boolean = when (call) {
    is KaCallableMemberCall<*, *> -> {
        val pas = call.partiallyAppliedSymbol
        isRaise(pas.dispatchReceiver?.type) == true || isRaise(pas.extensionReceiver?.type) == true
    }
    else -> false
}

val BINDABLE_TYPES = setOf(
    ClassId.fromString("arrow/core/Either"),
    ClassId.fromString("arrow/core/Validated"),
    ClassId.fromString("arrow/core/Ior"),
    ClassId.fromString("arrow/core/Option"),
    ClassId.fromString("arrow/core/Effect"),
    ClassId.fromString("arrow/core/EagerEffect"),
    ClassId.fromString("kotlin/Result"),
    ClassId.fromString("app/cash/quiver/Outcome"),
)

fun KaSession.isBindable(type: KaType?) =
    type != null && isClassTypeFrom(type, BINDABLE_TYPES)

fun KaSession.isNotBindable(type: KaType?) =
    type != null && !isClassTypeFrom(type, BINDABLE_TYPES)

val ITERABLE_ID = ClassId.fromString("kotlin/collections/Iterable")

fun KaSession.iterableElement(type: KaType): KaType? =
    (type.allSupertypes.firstOrNull { it.isClassType(ITERABLE_ID) } as? KaClassType)
        ?.typeArguments?.firstOrNull()?.type
