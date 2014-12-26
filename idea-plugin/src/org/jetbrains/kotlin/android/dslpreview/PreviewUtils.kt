package org.jetbrains.kotlin.android.dslpreview

import org.jetbrains.jet.lang.psi.JetClass
import org.jetbrains.jet.lang.psi.JetClassOrObject
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import org.jetbrains.jet.lang.psi.JetFile
import java.util.Collections
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.jet.lang.descriptors.ClassifierDescriptor
import org.jetbrains.jet.lang.resolve.java.lazy.descriptors.LazyJavaClassDescriptor
import org.jetbrains.jet.lang.psi.JetClassBody
import org.jetbrains.jet.plugin.caches.resolve.KotlinCacheService.*
import org.jetbrains.jet.plugin.caches.resolve.KotlinCacheService

private val DEBUG = false

public class AndroidFacetNotFoundException : RuntimeException()

public class CantCreateDependencyDirectoryException : RuntimeException()

public class UnsupportedClassException : RuntimeException()

public fun getQualifiedName(clazz: JetClass): String? {
    val parts = arrayListOf<String>()

    var current: Any? = clazz
    while (current != null) {
        parts.add((current as JetClassOrObject).getName())
        current = PsiTreeUtil.getParentOfType<JetClassOrObject>(current as PsiElement, javaClass<JetClassOrObject>())
    }

    val file = clazz.getContainingFile()
    return if (file is JetFile) {
        val fileQualifiedName = file.getPackageFqName().asString()
        if (!fileQualifiedName.isEmpty()) {
            parts.add(fileQualifiedName)
        }

        Collections.reverse(parts)
        StringUtil.join(parts, ".")
    } else null
}

fun resolveJetClass(prob: PsiElement?, cacheService: KotlinCacheService): JetClass? {

    fun isClassSupported(descriptor: ClassifierDescriptor): Boolean {
        if (descriptor !is LazyJavaClassDescriptor)
            return false

        val ljcd = descriptor: LazyJavaClassDescriptor
        val name = ljcd.fqName.asString()
        if ("android.app.Activity" == name)
            return true
        if ("android.app.Fragment" == name)
            return true
        if ("android.support.v4.app.Fragment" == name)
            return true

        for (jt in ljcd.getTypeConstructor().getSupertypes()) {
            val superTypeDescriptor = jt.getConstructor().getDeclarationDescriptor()
            if (isClassSupported(superTypeDescriptor))
                return true
        }
        return false
    }

    if (prob == null) {
        return null
    }

    if (prob is JetClass && (prob.getParent() !is JetClassBody) &&
            !prob.isEnum() && !prob.isTrait() && !prob.isAnnotation() && !prob.isInner()) {
        try {
            val session = cacheService.getLazyResolveSession(prob)
            val memberDescriptor = session.getClassDescriptor(prob)

            val constructor = memberDescriptor.getTypeConstructor()
            for (type in constructor.getSupertypes()) {
                val descriptor = type.getConstructor().getDeclarationDescriptor()
                if (descriptor != null) {
                    if (isClassSupported(descriptor)) {
                        return prob
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
    }

    return resolveJetClass(prob.getParent(), cacheService)
}