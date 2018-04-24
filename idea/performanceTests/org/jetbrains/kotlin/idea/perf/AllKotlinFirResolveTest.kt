/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.java.FirJavaModuleBasedSession
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.FirProvider
import org.jetbrains.kotlin.fir.resolve.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.resolve.transformers.FirTotalResolveTransformer
import org.jetbrains.kotlin.fir.service
import org.jetbrains.kotlin.idea.caches.project.productionSourceInfo
import org.jetbrains.kotlin.idea.fir.IdeFirDependenciesSymbolProvider
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.system.measureNanoTime

class AllKotlinFirResolveTest : AllKotlinTest() {

    override fun setUpModule() {
        super.setUpModule()

        ModuleRootModificationUtil.addContentRoot(module, VfsUtil.findFileByIoFile(rootProjectFile, true)!!)

        ModuleRootModificationUtil.updateModel(module) {
            rootProjectFile.walkTopDown().onEnter {
                it.name.toLowerCase() !in setOf("testdata", "resources")
            }.filter {
                it.isDirectory
            }.forEach { dir ->
                val vdir by lazy { VfsUtil.findFileByIoFile(dir, true)!! }
                if (dir.name in setOf("src", "test", "tests")) {
                    it.contentEntries.single().addSourceFolder(vdir, false)
                } else if (dir.name in setOf("build")) {
                    it.contentEntries.single().addExcludeFolder(vdir)
                }
            }
        }
    }

    override fun setUpProject() {
        super.setUpProject()
        setUpModule()
    }

    private val sessionProvider by lazy { FirProjectSessionProvider(project) }

    private fun getOrCreateSession(): FirSession {
        val moduleInfo = module.productionSourceInfo()!!

        return sessionProvider.getSession(moduleInfo) ?: FirJavaModuleBasedSession(
            moduleInfo, sessionProvider, moduleInfo.contentScope(),
            IdeFirDependenciesSymbolProvider(moduleInfo, project, sessionProvider)
        )
    }

    override fun doTest(file: File): PerFileTestResult =
        file.toPsiFile()?.let { doFirTest(it) }
                ?: PerFileTestResult(emptyMap(), 0L, listOf(AssertionError("PsiFile not found for $file")))

    private val transformers = FirTotalResolveTransformer().transformers

    private fun doFirTest(psiFile: PsiFile): PerFileTestResult {
        val results = mutableMapOf<String, Long>()
        var totalNs = 0L
        val errors = mutableListOf<Throwable>()

        val session = getOrCreateSession()
        val builder = RawFirBuilder(session)
        var firFile: FirFile? = null
        val rawResult = measureNanoTime {
            psiFile as? KtFile ?: return@measureNanoTime
            try {
                firFile = builder.buildFirFile(psiFile)
                (session.service<FirProvider>() as FirProviderImpl).recordFile(firFile!!)
            } catch (t: Throwable) {
                t.printStackTrace()
                errors += t
            }
        }
        results["FIR_RawBuild"] = rawResult
        totalNs += rawResult

        if (firFile != null) {
            for (transformer in transformers) {
                val resolveResult = measureNanoTime {
                    try {
                        transformer.transformFile(firFile!!, null)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        errors += t
                    }
                }
                results["FIR_Transformer_${transformer::class.java}"] = resolveResult
                totalNs += resolveResult
            }
        }

        return PerFileTestResult(results, totalNs, errors)

    }
}