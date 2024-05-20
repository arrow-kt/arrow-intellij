package arrow.intellij

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpression
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlinx.serialization.compiler.resolve.toClassDescriptor

fun KtExpression.inRaiseContext(
    context: BindingContext,
    resolver: ResolutionFacade
): Boolean = raiseContexts(context, resolver).any()

fun KtExpression.raiseContexts(
    context: BindingContext,
    resolver: ResolutionFacade
): Sequence<KotlinType> = getReceivers(context, resolver).filter(KotlinType::isRaise)

fun KtExpression.getReceivers(
    context: BindingContext,
    resolver: ResolutionFacade
): Sequence<KotlinType> = this.getResolutionScope(context, resolver).parentsWithSelf.mapNotNull {
    (it as? LexicalScope)?.implicitReceiver?.type
}

fun KtExpression.singleBlockExpression(): KtExpression? = when (this) {
    is KtBlockExpression -> statements.singleOrNull()
    else -> this
}

fun KtExpression.findEqualsNull(): KtExpression? {
    if (this !is KtBinaryExpression) return null
    if (this.operationToken != KtTokens.EQEQ && this.operationToken != KtTokens.EQEQEQ) return null
    if (right.isNullExpression()) return left
    if (left.isNullExpression()) return right
    return null
}

fun KtExpression.findArgumentToRaise(
    context: BindingContext
): KtExpression? =
    getResolvedCall(context)
        ?.valueArgumentsByIndex?.firstOrNull()
        ?.arguments?.firstOrNull()
        ?.getArgumentExpression()

val CallableDescriptor.fqNameString: String?
    get() = fqNameOrNull()?.asString()

val KotlinType.fqNameString: String?
    get() = toClassDescriptor?.fqNameOrNull()?.asString()

val KotlinType.isRaise: Boolean
    get() = fqNameString == "arrow.core.raise.Raise"

val BINDABLE_TYPES = setOf("arrow.core.Either", "arrow.core.Option", "kotlin.Result")

val KotlinType?.isBindable: Boolean
    get() = this != null && fqNameString in BINDABLE_TYPES

val KotlinType?.isNotBindable: Boolean
    get() = this != null && fqNameString !in BINDABLE_TYPES

val KotlinType.iterableElement: KotlinType?
    get() = supertypes()
        .firstOrNull { it.fqNameString == "kotlin.collections.Iterable" }
        ?.arguments?.first()?.type
