/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.java

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.java.symbols.JavaClassSymbol
import org.jetbrains.kotlin.fir.resolve.AbstractFirSymbolProvider
import org.jetbrains.kotlin.fir.service
import org.jetbrains.kotlin.fir.symbols.ConeSymbol
import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.load.java.structure.classId
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade

class JavaSymbolProvider(
    val project: Project,
    private val searchScope: GlobalSearchScope,
    private val session: FirSession
) : AbstractFirSymbolProvider() {

    override fun getSymbolByFqName(classId: ClassId): ConeSymbol? {
        return classCache.lookupCacheOrCalculate(classId) {
            val facade = KotlinJavaPsiFacade.getInstance(project)
            val foundClass: JavaClass? = facade.findClass(classId, searchScope)
            if ((foundClass as? JavaClassImpl)?.psi is KtLightClass) {
                return@lookupCacheOrCalculate null
            }
            foundClass?.let { JavaClassSymbol(this, session.service(), it) }
        }
    }

    fun getSymbolByJavaClass(javaClass: JavaClass): ConeSymbol? {
        val classId = javaClass.classId ?: error("!")
        return classCache.lookupCacheOrCalculate(classId) {
            if ((javaClass as? JavaClassImpl)?.psi is KtLightClass) {
                return@lookupCacheOrCalculate null
            }
            javaClass.let { JavaClassSymbol(this, session.service(), it) }
        }
    }

    override fun getPackage(fqName: FqName): FqName? {
        return packageCache.lookupCacheOrCalculate(fqName) {
            val facade = KotlinJavaPsiFacade.getInstance(project)
            val javaPackage = facade.findPackage(fqName.asString(), searchScope) ?: return@lookupCacheOrCalculate null
            FqName(javaPackage.qualifiedName)
        }
    }
}

