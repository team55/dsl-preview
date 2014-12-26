package org.jetbrains.kotlin.android.dslpreview

import com.android.builder.model.SourceProvider
import com.google.common.io.Files
import com.google.common.io.Resources
import com.google.gson.Gson
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.jet.lang.psi.JetClass
import org.jetbrains.jet.lang.psi.JetFile
import org.zeromq.ZMQ
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import kotlin.List
import kotlin.Collection
import com.intellij.psi.PsiClass
import org.jetbrains.jet.plugin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.android.dslpreview
import org.jetbrains.kotlin.android.dslpreview
import com.intellij.openapi.application.PathManager
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.io.FileOutputStream

public class DslWorker(
        private val myProject: Project,
        private val myToolWindow: ToolWindow,
        private val myListener: DslWorker.Listener
) {
    private val GSON = Gson()
    private val START_MARKER = "" + (5.toChar()) + (7.toChar()) + (5.toChar())
    private val END_MARKER = START_MARKER + START_MARKER

    private volatile var myAlive = false
    private volatile var myPort = 0
    private volatile var myLastProcess: Process? = null

    private val ROBOWRAPPER_LOCK = Object()
    private val DOWNLOAD_LOCK = Object()

    private fun downloadAndroidAll(version: Int, cmd: RobowrapperContext) {
        val url = DependencyUtils.getAndroidAllVersionUrl(version)
        val outputFile = DependencyUtils.getAndroidAllVersionPath(version)

        val downloadTask = FileDownloadTask(cmd, url, outputFile)
        ProgressManager.getInstance().run(downloadTask)
    }

    private fun downloadRobowrapperDependencies(cmd: RobowrapperContext, deps: List<Pair<String, String>>) {
        val tempDirectory = File(DependencyUtils.getDependencyDirectory(), "tmp/")
        if (!tempDirectory.exists()) {
            tempDirectory.mkdirs()
        }
        val downloadTask = FileDownloadTask(cmd, deps)
        ProgressManager.getInstance().run(downloadTask)
    }

    private fun checkRobowrapperDependencies(cmd: RobowrapperContext): Boolean {
        val robowrapperDeps = DependencyUtils.getRobowrapperDependencies()
        var downloaded = true
        for (p in robowrapperDeps) {
            if (!File(p.second).exists()) {
                downloaded = false
                break
            }
        }
        if (!downloaded) {
            downloadRobowrapperDependencies(cmd, robowrapperDeps)
        }
        return downloaded
    }

    public fun finishWorkingProcess() {
        if (myLastProcess != null) {
            myLastProcess!!.destroy()
        }
    }

    public fun exec(cmd: RobowrapperContext) {
        val sdkVersion = cmd.androidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel()
        val androidAllFile = DependencyUtils.getAndroidAllVersionPath(sdkVersion)
        if (!checkRobowrapperDependencies(cmd)) {
            return
        }
        if (!File(androidAllFile).exists()) {
            downloadAndroidAll(sdkVersion, cmd)
            return
        }

        if (myAlive && myPort > 0) {
            execAlive(cmd)
            return
        }

        val robowrapperDirectory = DependencyUtils.getDependencyDirectory().getAbsolutePath()
        if (robowrapperDirectory.isEmpty()) {
            myListener.onXmlError(ErrorKind.INVALID_ROBOWRAPPER_DIRECTORY, "", false)
            return
        }

        ProgressManager.getInstance().run(RobowrapperExecTask(cmd, robowrapperDirectory))
    }

    private fun execAlive(cmd: RobowrapperContext) {
        if (myPort <= 0) {
            myAlive = false
            exec(cmd)
            return
        }

        ProgressManager.getInstance().run(RobowrapperExecAliveTask(cmd))
    }

    private fun processData(cmd: RobowrapperContext, outputText: String) {
        val processor = DataProcessor(cmd, outputText)
        ApplicationManager.getApplication().invokeLater(processor)
    }

    public trait Listener {
        public fun onXmlError(kind: ErrorKind, description: String, alive: Boolean)
        public fun onXmlReceived(cmd: RobowrapperContext, xml: String)
    }

    public enum class ErrorKind {
        INVALID_ROBOWRAPPER_DIRECTORY
        UNKNOWN
        UNKNOWN_ANDROID_VERSION
    }

    private inner class DataProcessor(val cmd: RobowrapperContext, private val outputText: String) : Runnable {

        override fun run() {
            val startMarker = outputText.indexOf(START_MARKER)
            val endMarker = outputText.indexOf(END_MARKER, startMarker + START_MARKER.length())

            if (startMarker < 0 || endMarker < 0 || endMarker <= startMarker) {
                myAlive = false
                myListener.onXmlError(ErrorKind.UNKNOWN, "", false)
                return
            }

            val rawData = outputText.substring(startMarker + START_MARKER.length(), endMarker)
            val pack = GSON.fromJson<dslpreview.Pack>(rawData, javaClass<dslpreview.Pack>())

            myPort = pack.port
            myAlive = pack.alive

            if (pack.error_code != 0 || pack.xml.isEmpty()) {
                myListener.onXmlError(ErrorKind.UNKNOWN, "${pack.error_code}: ${pack.error}", myAlive)
            } else {
                if (DEBUG) println(pack.xml)
                myListener.onXmlReceived(cmd, pack.xml)
            }
        }
    }

    private inner class RobowrapperExecTask(val ctx: RobowrapperContext, val robowrapperDirectory: String) :
            Task.Backgroundable(ctx.androidFacet.getModule().getProject(), "Executing DSL", false) {

        override fun run(progressIndicator: ProgressIndicator) {
            progressIndicator.setIndeterminate(true)

            val pluginDirectory = File(PathManager.getJarPathForClass(javaClass)).getParent()
            val policyFile = File(DependencyUtils.getDependencyDirectory(), "custom.policy")

            synchronized (ROBOWRAPPER_LOCK) {
                try {
                    if (!policyFile.exists()) {
                        var robowrapperJar: JarFile? = null
                        try {
                            robowrapperJar = JarFile(File(pluginDirectory, "robowrapper.jar"))
                            robowrapperJar!!
                                    .getInputStream(ZipEntry("custom.policy"))
                                    .copyTo(FileOutputStream(policyFile))
                        } finally {
                            robowrapperJar?.close()
                        }
                    }

                    val builder = ProcessBuilder(ctx.makeArguments())
                    if (DEBUG) println("Robowrapper command-line: " + builder.command().joinToString(" "))

                    val process = builder.start()
                    myLastProcess = process
                    val text = process.getInputStream().reader("UTF-8").useLines {
                        it.takeWhile { !it.contains(END_MARKER) }.joinToString("\n", postfix = END_MARKER)
                    }

                    processData(ctx, text)
                } catch (e: Exception) {
                    ctx.removeManifest()
                    e.printStackTrace()
                }
            }
        }
    }

    private inner class RobowrapperExecAliveTask(private val ctx: RobowrapperContext) :
            Task.Backgroundable(ctx.androidFacet.getModule().getProject(), "Executing DSL", false) {
        override fun run(progressIndicator: ProgressIndicator) {
            progressIndicator.setIndeterminate(true)
            synchronized (ROBOWRAPPER_LOCK) {
                try {
                    val context = ZMQ.context(1)
                    val requester = context.socket(ZMQ.REQ)
                    requester.setSendTimeOut(1000)
                    requester.setReceiveTimeOut(20000)
                    requester.connect("tcp://localhost:" + myPort)

                    requester.send(ctx.activityClassName.toByteArray("UTF-8"), 0)
                    val reply = requester.recv(0)
                    if (DEBUG) println(String(reply, "UTF-8"))
                    processData(ctx, String(reply, "UTF-8"))

                    requester.close()
                    context.term()
                } catch (e: Exception) {
                    e.printStackTrace()
                    ctx.removeManifest()
                    myLastProcess?.destroy()

                    // If alive task failed, launch non-alive version
                    ApplicationManager.getApplication().invokeLater(object : Runnable {
                        override fun run() {
                            myAlive = false
                            exec(ctx)
                        }
                    })
                }

            }
        }
    }


    private fun FileDownloadTask(ctx: RobowrapperContext, url: String, outputFile: String): FileDownloadTask {
        return FileDownloadTask(ctx, Arrays.asList<Pair<String, String>>(url to outputFile))
    }

    private inner class FileDownloadTask(
            private val ctx: RobowrapperContext,
            private val filesToDownload: List<Pair<String, String>>
    ) : Task.Backgroundable(ctx.androidFacet.getModule().getProject(),
            "Downloading dependencies", false) {

        override fun run(progressIndicator: ProgressIndicator) {
            progressIndicator.setIndeterminate(true)

            fun reportDownloadError(filename: String) {
                ApplicationManager.getApplication().invokeLater(object : Runnable {
                    override fun run() {
                        myListener.onXmlError(ErrorKind.UNKNOWN, "Failed to download " + filename, false)
                    }
                })
            }

            val result = synchronized(DOWNLOAD_LOCK) {
                filesToDownload.all { fileToDownload ->
                    val file = File(fileToDownload.second)
                    try {
                        if (!file.exists()) {
                            val url = URL(fileToDownload.first)
                            Resources.asByteSource(url).copyTo(Files.asByteSink(file))
                        }
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (file.exists()) {
                            file.delete()
                        }
                        reportDownloadError(fileToDownload.first)
                        false
                    }
                }
            }

            if (result) {
                ApplicationManager.getApplication().invokeLater(object : Runnable {
                    override fun run() {
                        exec(ctx)
                    }
                })
            }
        }
    }

}
