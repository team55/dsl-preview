package org.jetbrains.kotlin.android.dslpreview

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import org.jetbrains.android.uipreview.AndroidEditorSettings
import kotlin.platform.platformStatic

State(
    name = "DslAndroidEditorSettings",
    storages = array(
        Storage(file = StoragePathMacros.APP_CONFIG + "/dslAndroidEditors.xml")
    )
)
public class DslAndroidEditorSettings : AndroidEditorSettings()