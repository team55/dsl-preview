package org.jetbrains.kotlin.android.dslpreview

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileStatusNotification
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.Alarm
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager
import org.jetbrains.jet.lang.psi.JetClass
import org.jetbrains.jet.lang.psi.JetClassOrObject
import org.jetbrains.jet.lang.psi.JetFile
import org.jetbrains.jet.plugin.internal.Location
import org.jetbrains.jet.plugin.util.InfinitePeriodicalTask
import org.jetbrains.jet.plugin.util.LongRunningReadTask
import org.jetbrains.jet.plugin.util.ProjectRootsUtil

import com.android.sdklib.IAndroidTarget
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationListener
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.android.sdk.AndroidTargetData

import javax.swing.*
import javax.swing.border.Border
import java.util.ArrayList
import java.util.Collections

import com.intellij.util.Alarm.ThreadToUse.SWING_THREAD
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.search.PsiSearchHelper
import java.awt.BorderLayout
import java.util.Comparator
import com.intellij.openapi.roots.ProjectRootManager
import java.awt.Component
import com.intellij.ui.components.JBLabel
import com.intellij.openapi.project.IndexNotReadyException
import org.jetbrains.jet.plugin.caches.resolve.KotlinCacheService
import com.intellij.openapi.editor.ex.EditorEx

public class DslPreviewToolWindowManager(private val myProject: Project, fileEditorManager: FileEditorManager) : AndroidLayoutPreviewToolWindowManager(myProject, fileEditorManager), DslWorker.Listener, com.intellij.openapi.Disposable {

    private var myDslWorker: DslWorker? = null
    private var myActivityListModel: DefaultComboBoxModel? = null

    private var myLastFile: PsiFile? = null
    private var myLastAndroidFacet: AndroidFacet? = null

    {
        ApplicationManager.getApplication().invokeLater(object : Runnable {
            override fun run() {
                val task = object : Computable<LongRunningReadTask<Pair<JetClass, String>, String>> {
                    override fun compute(): LongRunningReadTask<Pair<JetClass, String>, String> {
                        return UpdateActivityNameTask()
                    }
                }
                InfinitePeriodicalTask(1000, SWING_THREAD, this@DslPreviewToolWindowManager, task).start()
            }
        })
    }

    override fun initToolWindow() {
        super<AndroidLayoutPreviewToolWindowManager>.initToolWindow()
        myDslWorker = DslWorker(myProject, getToolWindow(), this)

        val panel = getToolWindowForm().getContentPanel().getComponent(1)

        if (panel is JPanel) {
            val firstToolbar = panel.getComponent(0)
            val secondToolbar = panel.getComponent(1)

            panel.remove(firstToolbar)
            panel.remove(secondToolbar)
            val manager = GridLayoutManager(3, 1)
            panel.setLayout(manager)

            myActivityListModel = DefaultComboBoxModel()
            val comboBox = ComboBox(myActivityListModel)

            fun constraints(row: Int, column: Int, init: GridConstraints.() -> Unit): GridConstraints {
                return with(GridConstraints()) {
                    setRow(row)
                    setColumn(column)
                    init()
                    this
                }
            }

            panel.add(firstToolbar, constraints(0, 0) {
                setFill(GridConstraints.FILL_BOTH)
            })

            panel.add(secondToolbar, constraints(1, 0) {
                setFill(GridConstraints.FILL_VERTICAL)
                setAnchor(GridConstraints.ANCHOR_EAST)
            })

            panel.add(comboBox, constraints(2, 0) {
                setFill(GridConstraints.FILL_BOTH)
            })
        }

        resolveAvailableClasses()
    }

    override fun getCustomRefreshRenderAction(): AnAction? {
        return RefreshDslAction()
    }

    override fun getComponentName(): String {
        return "LayoutPreviewToolWindowManager"
    }

    override fun disposeComponent() {
        super<AndroidLayoutPreviewToolWindowManager>.disposeComponent()
        myDslWorker?.finishWorkingProcess()
    }

    override fun isUseInteractiveSelector(): Boolean {
        return false
    }

    override fun getToolWindowId(): String {
        return "DSL Preview"
    }

    override fun isRenderAutomatically(): Boolean {
        return false
    }

    override fun isForceHideOnStart(): Boolean {
        return true
    }

    override fun onXmlReceived(cmd: RobowrapperContext, xml: String) {
        val filename = cmd.activityClassName + "_converted__.xml"

        val psiFile = PsiFileFactory.getInstance(myProject).createFileFromText(filename, XmlFileType.INSTANCE, xml)
        val wrappedPsiFile = LayoutPsiFile(psiFile as XmlFile)
        render(wrappedPsiFile, cmd.androidFacet, false)
    }

    override fun onXmlError(kind: DslWorker.ErrorKind, description: String, alive: Boolean) {
        when (kind) {
            DslWorker.ErrorKind.INVALID_ROBOWRAPPER_DIRECTORY ->
                showNotification("Invalid Robowrapper directory.", MessageType.ERROR)
            DslWorker.ErrorKind.UNKNOWN_ANDROID_VERSION ->
                showNotification("Unknown Android version.", MessageType.ERROR)
            else ->
                showNotification("Dsl processing error (" + kind.name() + ").", MessageType.ERROR)
        }
    }

    override fun render(): Boolean {
        val file = myLastFile
        val facet = myLastAndroidFacet

        if (file == null || facet == null) {
            return false
        }
        return render(file, facet, false)
    }

    private fun getOnCursorPreviewClassDescription(): PreviewClassDescription {
        val editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor()
        val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument())

        if (psiFile !is JetFile || editor !is EditorEx) {
            throw UnsupportedClassException()
        }

        val virtualFile = (editor: EditorEx).getVirtualFile()

        val selectionStart = editor.getCaretModel().getPrimaryCaret().getSelectionStart()
        val cacheService = KotlinCacheService.getInstance(myProject)
        val jetClass = resolveJetClass(psiFile.findElementAt(selectionStart), cacheService)

        val module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(virtualFile)
        val androidFacet = module.resolveAndroidFacet()

        if (jetClass == null || androidFacet == null) {
            throw UnsupportedClassException()
        }

        return PreviewJetClassDescription(jetClass, androidFacet)
    }

    override fun render(psiFile: PsiFile?, facet: AndroidFacet?, forceFullRender: Boolean): Boolean {
        if (!forceFullRender) {
            val result = super<AndroidLayoutPreviewToolWindowManager>.render(psiFile, facet, false)
            if (result) {
                myLastFile = psiFile
                myLastAndroidFacet = facet
            } else {
                myLastFile = null
                myLastAndroidFacet = null
            }
            return result
        }

        var ctx: RobowrapperContext?
        try {
            ctx = RobowrapperContext(
                    myActivityListModel?.getSelectedItem() as? PreviewClassDescription
                    ?: getOnCursorPreviewClassDescription())
        } catch (e: AndroidFacetNotFoundException) {
            showNotification("Can't resolve Android facet.", MessageType.ERROR)
            return false
        } catch (e: CantCreateDependencyDirectoryException) {
            showNotification("Can't create Robolectric dependency folder.", MessageType.ERROR)
            return false
        } catch (e: UnsupportedClassException) {
            showNotification("This class is not supported.", MessageType.ERROR)
            return false
        }

        val module = ctx!!.androidFacet.getModule()
        CompilerManager.getInstance(module.getProject()).make(module, object : CompileStatusNotification {

            override fun finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
                if (!aborted && errors == 0) {
                    myDslWorker?.exec(ctx!!)
                } else if (errors > 0) {
                    showNotification("Build completed with errors.", MessageType.ERROR)
                }
            }
        })

        return true
    }

    override fun initListeners(project: Project) {

    }

    override fun update(event: PsiTreeChangeEvent) {

    }

    override fun dispose() {

    }

    private fun resolveAvailableClasses() {
        val packageNames = ModuleManager.getInstance(myProject).getModules()
                .map { it.resolveAndroidFacet() }.filterNotNull()
                .map { it.getManifest().getPackage().getXmlAttributeValue().getValue() }.toHashSet()

        val activityClasses = getAncestors(packageNames, "android.app.Activity")
        val fragmentClasses = getAncestors(packageNames, "android.app.Fragment")
        val supportFragmentClasses = getAncestors(packageNames, "android.support.v4.app.Fragment")

        if (myActivityListModel != null) {
            with(myActivityListModel!!) {
                setSelectedItem(null)
                removeAllElements()
                (activityClasses + fragmentClasses + supportFragmentClasses).forEach { addElement(it) }
            }
        }
    }

    private fun getAncestors(facetPackageNames: Set<String>, baseClassName: String): Collection<PreviewClassDescription> {
        val baseClasses = JavaPsiFacade.getInstance(myProject).findClasses(baseClassName, GlobalSearchScope.allScope(myProject))

        if (baseClasses.size() == 0) {
            return listOf()
        }

        // We actually omit inner classes so it's correct
        fun getPackageNameNaive(className: String): String {
            val index = className.lastIndexOf('.')
            return if (index<0) className else className.substring(0, index)
        }

        fun isValidAncestor(clazz: PsiClass) =
                !clazz.isEnum() &&
                !clazz.isInterface() &&
                clazz.getContainingClass() == null &&
                facetPackageNames.contains(getPackageNameNaive(clazz.getQualifiedName()))

        try {
            return ClassInheritorsSearch.search(baseClasses[0])
                    .findAll()
                    .filter(::isValidAncestor)
                    .map { it to it.getModule().resolveAndroidFacet() }
                    .filter { it.second != null }
                    .map { PreviewPsiClassDescription(it.first, it.second!!) }
        } catch (e: IndexNotReadyException) {
            return listOf()
        }
    }

    override fun isApplicableEditor(textEditor: TextEditor) = true

    private fun PsiClass.getModule(): Module {
        return ProjectRootManager.getInstance(myProject).getFileIndex()
                .getModuleForFile(getContainingFile().getVirtualFile())
    }

    private fun Module.resolveAndroidFacet(): AndroidFacet? {
        val facetManager = FacetManager.getInstance(this)
        for (facet in facetManager.getAllFacets()) {
            if (facet is AndroidFacet) {
                return facet
            }
        }
        return null
    }

    private fun showNotification(text: String, messageType: MessageType) {
        val statusBar = WindowManager.getInstance().getStatusBar(myProject)

        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(text, messageType, null)
                .setFadeoutTime(3000)
                .createBalloon()
                .show(RelativePoint.getCenterOf(statusBar.getComponent()), Balloon.Position.atRight)
    }

    private inner class RefreshDslAction : AnAction("Refresh", null, AllIcons.Actions.Refresh) {

        override fun actionPerformed(e: AnActionEvent) {
            val configuration = getToolWindowForm().getConfiguration()

            if (configuration != null) {
                // Clear layoutlib bitmap cache (in case files have been modified externally)
                val target = configuration.getTarget()
                val module = configuration.getModule()
                if (target != null && module != null) {
                    AndroidTargetData.getTargetData(target, module)?.clearLayoutBitmapCache(module)
                }

                AndroidFacet.getInstance(configuration.getModule())?.refreshResources()
                configuration.updated(ConfigurationListener.MASK_RENDERING)
            }

            render(null, null, true)
        }
    }

    public inner class UpdateActivityNameTask : LongRunningReadTask<Pair<JetClass, String>, String>() {
        override fun prepareRequestInfo(): Pair<JetClass, String>? {
            val toolWindow = getToolWindow()
            if (toolWindow == null || !toolWindow.isVisible()) {
                return null
            }

            val editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor()
            val location = Location.fromEditor(editor, myProject)
            if (location.getEditor() == null) {
                return null
            }

            val file = location.getJetFile()
            if (file == null || !ProjectRootsUtil.isInProjectSource(file)) {
                return null
            }

            val cacheService = KotlinCacheService.getInstance(myProject)
            val resolvedClass = resolveJetClass(file.findElementAt(location.getStartOffset()), cacheService)
            if (resolvedClass == null || resolvedClass !is JetClass) {
                return null
            }

            return Pair(resolvedClass, getQualifiedName(resolvedClass) ?: "")
        }

        override fun cloneRequestInfo(requestInfo: Pair<JetClass, String>): Pair<JetClass, String> {
            val newRequestInfo = super.cloneRequestInfo(requestInfo)
            assert(requestInfo == newRequestInfo, "cloneRequestInfo should generate same location object")
            return newRequestInfo
        }

        override fun hideResultOnInvalidLocation() {

        }

        override fun processRequest(requestInfo: Pair<JetClass, String>): String? {
            return getQualifiedName(requestInfo.first)
        }

        override fun onResultReady(requestInfo: Pair<JetClass, String>, resultText: String?) {
            if (resultText == null) {
                return
            }

            fun setSelection(): Boolean {
                var found = false
                if (myActivityListModel != null) with (myActivityListModel!!) {
                    for (i in 0 .. (getSize() - 1)) {
                        val item = getElementAt(i)
                        if (item != null && resultText == (item as PreviewClassDescription).qualifiedName) {
                            setSelectedItem(item)
                            found = true
                            break
                        }
                    }
                }
                return found
            }

            // If class with such name was not found (prob. after refactoring)
            if (!setSelection()) {
                resolveAvailableClasses()
                setSelection()
            }
        }
    }
}
