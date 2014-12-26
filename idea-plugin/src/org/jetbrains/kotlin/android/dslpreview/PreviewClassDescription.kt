package org.jetbrains.kotlin.android.dslpreview

import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet
import com.intellij.psi.PsiFile
import org.jetbrains.jet.lang.psi.JetFile
import org.jetbrains.jet.lang.psi.JetClass
import org.jetbrains.jet.lang.psi.JetClassOrObject
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import java.util.Collections
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass

public abstract class PreviewClassDescription(val androidFacet: AndroidFacet) {
    abstract val name: String
    abstract val qualifiedName: String
    abstract val packageName: String
}

public class PreviewPsiClassDescription(val psiClass: PsiClass, androidFacet: AndroidFacet): PreviewClassDescription(androidFacet) {
    override val qualifiedName: String = psiClass.getQualifiedName() ?: ""
    override val packageName: String
    override val name: String

    {
        val finalDotIndex = qualifiedName.lastIndexOf('.')
        packageName = if (finalDotIndex <= 0) "" else qualifiedName.substring(0, finalDotIndex)
        name = if (finalDotIndex <= 0) qualifiedName else qualifiedName.substring(finalDotIndex + 1)
    }

    override fun toString(): String {
        return if (packageName.isNotEmpty())
                "<html>$packageName.<b>$name</b></html>"
            else
                "<html><b>$name</b></html>"
    }
}

public class PreviewJetClassDescription(val jetClass: JetClass, androidFacet: AndroidFacet): PreviewClassDescription(androidFacet) {
    override val packageName: String = jetClass.getContainingJetFile().getPackageFqName().asString()
    override val name: String = jetClass.getName()
    override val qualifiedName: String = packageName + "." + name

    override fun toString(): String {
        return if (packageName.isNotEmpty())
            "<html>$packageName.<b>$name</b></html>"
        else
            "<html><b>$name</b></html>"
    }
}