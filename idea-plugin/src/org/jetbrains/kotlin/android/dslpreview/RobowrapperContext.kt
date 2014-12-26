package org.jetbrains.kotlin.android.dslpreview

import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import kotlin.properties.Delegates
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.roots.ModuleRootManager
import java.util.ArrayList
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ApplicationManager

class RobowrapperContext(description: PreviewClassDescription) {

    val androidFacet = description.androidFacet
    val activityClassName = description.qualifiedName

    private val mainSourceSet = androidFacet.getMainSourceProvider()
    private val applicationPackage = androidFacet.getManifest().getPackage().getXmlAttributeValue().getValue()

    private val apiLevel = androidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel()
    private val androidAll = DependencyUtils.getAndroidAllVersionPath(apiLevel)

    private val assetsDirectory = mainSourceSet.getAssetsDirectories().firstOrNull()
    private val resDirectory = mainSourceSet.getResDirectories().firstOrNull()

    private val activities by Delegates.lazy {
        androidFacet.getManifest().getApplication().getActivities().map { it.getActivityClass().toString() }
    }

    private val manifest by Delegates.lazy { generateManifest() }

    private fun runReadAction<T>(action: () -> T): T {
        return ApplicationManager.getApplication().runReadAction<T>(action)
    }

    private fun generateManifest() = runReadAction {
        val activityEntries = activities.map { "<activity android:name=\"$it\" />" }.joinToString("\n")
        val manifestFile = File.createTempFile("AndroidManifest", ".xml")
        manifestFile.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="%PACKAGE%">
              <application>
                    %ACTIVITIES%
              </application>
            </manifest>""".replace("%PACKAGE%", applicationPackage).replace("%ACTIVITIES%", activityEntries))
        manifestFile
    }

    // `manifest` is already initialized at this point
    fun removeManifest() {
        if (manifest.exists()) {
            manifest.delete()
        }
    }

    private fun ArrayList<String>.add(name: String, value: String) = add(name + escape(value))
    private fun ArrayList<String>.add(name: String, value: File) = add(name + escape(value.getAbsolutePath()))

    public fun makeArguments(): List<String> {
        val roots = ModuleRootManager.getInstance(androidFacet.getModule()).orderEntries().classes().getRoots()
        val androidSdkDirectory = androidFacet.getSdkData().getLocation().getPath()

        val pluginDirectory = File(PathManager.getJarPathForClass(javaClass)).getParent()
        val robowrapperDependencies = listOf(
                "robowrapper.jar",
                "gson-2.3.jar",
                "jeromq-0.3.4.jar",
                "junit-4.11.jar",
                "robolectric-with-dependencies.jar")
            .map { File(pluginDirectory, it).getAbsolutePath() }.joinToString(":", prefix = ":")

        val androidDependencies = resolveAndroidDependencies(roots, androidSdkDirectory)

        val dependencyDirectory = DependencyUtils.getDependencyDirectory()

        val a = arrayListOf("java", "-cp")
        with (a) {
            add(androidAll + robowrapperDependencies + androidDependencies)
            //add('"' + androidAll + robowrapperDependencies + androidDependencies + '"')
            add("-Djava.io.tmpdir=", File(dependencyDirectory, "tmp"))
            add("-Drobolectric.offline=true")
            add("-Drobolectric.dependency.dir=", dependencyDirectory)
            add("-Drobo.activityClass=", activityClassName)
            add("-Drobo.packageName=", applicationPackage)
            add("-Dandroid.manifest=", manifest)
            add("-Dandroid.resources=", resDirectory!!)
            add("-Dandroid.assets=", assetsDirectory!!)
            //TODO: check policy file loading
            //add("-Djava.security.manager=default")
            //add("-Djava.security.policy=", File(dependencyDirectory, "robowrapper.policy"))
            add("org.jetbrains.kotlin.android.robowrapper.Robowrapper")
            this
        }
        return a
    }

    private fun resolveAndroidDependencies(roots: Array<VirtualFile>, androidSdkDirectory: String?): String {
        val sb = StringBuilder()

        for (root in roots) {
            var item = root.getPath()
            if (androidSdkDirectory != null && item.startsWith(androidSdkDirectory)) {
                continue
            }
            if (item.endsWith("!/")) {
                item = item.substring(0, item.length() - 2)
            }
            sb.append(':').append(item.replace(":", "\":"))
        }

        return sb.toString()
    }

    private fun escape(v: String?): String {
        return (v ?: "").replace("\"", "\\\"").replace(" ", "\\ ")
    }

}