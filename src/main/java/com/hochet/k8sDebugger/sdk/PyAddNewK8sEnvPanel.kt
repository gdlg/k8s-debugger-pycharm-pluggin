// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.remote.RemoteSdkException
import com.intellij.ui.DocumentAdapter
import com.intellij.util.text.nullize
import com.jetbrains.python.remote.PyRemoteInterpreterUtil
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.remote.PythonRemoteInterpreterManager
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.PyAddNewEnvPanel
import org.jdom.Element
import resources.K8sIcons
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.event.DocumentEvent

/**
 * Panel to create a new Kubernetes SDK.
 */
class PyAddNewK8sEnvPanel(
    private val project: Project?,
    private val existingSdks: List<Sdk>,
    override var newProjectPath: String?
) : PyAddNewEnvPanel() {

    override val envName: String
        get() = "Kubernetes"
    override val panelName: String
        get() = "Kubernetes Environment"

    override val icon: Icon
        get() = K8sIcons.Kubernetes

    // Instead of reimplementing the whole panel from scratch,
    // we can reuse the panel used to edit existing Kubernetes credentials.
    private val panel = K8sCredentialsEditor(true)

    init {
        layout = BorderLayout()

        // Default value for the Python interpreter path
        panel.mainPanel.pythonInterpreterField.text = "python"

        add(panel.mainPanel)
    }

    /**
     * This function is called when the user press the Ok button to create the new SDK.
     */
    override fun getOrCreateSdk(): Sdk? {
        val credentials = K8sCredentialsHolder()
        credentials.kubeconfigPath = panel.mainPanel.kubeconfigPathField.text.nullize()
        credentials.contextName = panel.mainPanel.contextField.model.selectedItem?.toString().nullize()
        credentials.namespaceName = panel.mainPanel.namespaceNameField.model.selectedItem?.toString().nullize()

        val resourceNameItem = panel.mainPanel.resourceNameField.model.selectedItem ?: return null
        credentials.resourceName = resourceNameItem.toString().nullize()

        val remoteInterpreterManager = PythonRemoteInterpreterManager.getInstance() ?: return null

        val interpreterPath = panel.mainPanel.pythonInterpreterField.text

        val sdkType = PythonSdkType.getInstance()
        val sdk = SdkConfigurationUtil.createSdk(
            existingSdks,
            K8sCredentialsHandler(credentials).id,
            sdkType,
            null, // populated later
            "Kubernetes (${credentials.kubeconfigPath} ${credentials.namespaceName ?: "default"})")

        // We need to create a PyRemoteSdkAdditionalData; however, it seems that there is no other public method
        // than loadRemoteSdkData() to create one. To use this function, we create a dummy <additional/> XML element.
        sdk.sdkAdditionalData = remoteInterpreterManager.loadRemoteSdkData(sdk, Element("additional"))

        val additionalData = sdk.sdkAdditionalData

        if (additionalData !is PyRemoteSdkAdditionalDataBase) {
            return null
        }

        additionalData.setCredentials(K8sCredentialsType.instance.credentialsKey, credentials)
        additionalData.interpreterPath = interpreterPath
        additionalData.isValid = true

        sdkType.setupSdkPaths(sdk)

        // Call getInterpreterVersion to ensure that the specified Kubernetes resource is reachable
        // and Python is available. The valid Python interpreter is required in the pod because
        // various PyCharm modules will also do the check and mark the SDK as invalid if the
        // Python interpreter cannot be found.
        try {
            PyRemoteInterpreterUtil.getInterpreterVersion(project, additionalData, false)
        } catch (exc : RemoteSdkException) {
            ApplicationManager.getApplication().invokeAndWait {
                Messages.showErrorDialog(
                    panel.mainPanel,
                    "Failed to create the Kubernetes SDK: Python interpreter not found")
            }
            return null
        }

        return sdk
    }

    override fun addChangeListener(listener: Runnable) {
        panel.mainPanel.kubeconfigPathField.textField.document.addDocumentListener(object: DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                listener.run()
            }
        })

        super.addChangeListener(listener)
    }

    override fun validateAll(): List<ValidationInfo> {
        val validationInfo = panel.validate()
        return if (validationInfo == null) {
            emptyList()
        } else {
            listOf(validationInfo)
        }
    }
}