// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger

import com.hochet.k8sDebugger.sdk.K8sCredentialsHandler
import com.hochet.k8sDebugger.sdk.K8sCredentialsHolder
import com.hochet.k8sDebugger.sdk.K8sCredentialsType
import com.hochet.k8sDebugger.sdk.PyK8sRemoteProcessStarterManager
import com.intellij.execution.util.PathMappingsComponent
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.text.nullize
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.run.PyCommonOptionsFormFactory
import com.jetbrains.python.sdk.PySdkListCellRenderer
import com.jetbrains.python.sdk.PythonSdkUtil
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * Editor for the Kubernetes run configuration.
 */
class K8sDebuggerInjectorSettingsEditor(runConfiguration: K8sDebuggerInjectorRunConfiguration) :
    SettingsEditor<K8sDebuggerInjectorRunConfiguration>() {


    init {
        PyCommonOptionsFormFactory.getInstance().createForm(runConfiguration.commonOptionsFormData)

    }

    class SettingsEditorPanel : javax.swing.JPanel() {


        private val myPythonSdks = PythonSdkUtil.getAllSdks().filter { sdk: Sdk? ->
            val sdkAdditionalData = sdk?.sdkAdditionalData
            (sdkAdditionalData is PyRemoteSdkAdditionalDataBase && sdkAdditionalData.remoteConnectionType == K8sCredentialsType.instance)
        }

        val myInterpreterField = ComboBox(CollectionComboBoxModel(myPythonSdks, null))

        val myPathMappingComponent = PathMappingsComponent()

        private val myResourceNameList = ArrayList<String>()
        val myResourceNameField = ComboBox(CollectionComboBoxModel(myResourceNameList, null))

        val myPidList = ArrayList<Pair<String,String>>()
        val myPidField = ComboBox(CollectionComboBoxModel(myPidList, null))

        val myGdbInstallCommandField = JTextField()

        init {
            layout = BorderLayout()
            myPathMappingComponent.labelLocation = "West"

            myInterpreterField.renderer = PySdkListCellRenderer()

            myPidField.renderer = SimpleListCellRenderer.create("")  { value -> "${value.first}: ${value.second}" }

            val builder = FormBuilder.createFormBuilder().apply {
                addLabeledComponent("Python interpreter:", myInterpreterField)
                addLabeledComponent("Resource name:", myResourceNameField)
                addLabeledComponent("PID:", myPidField)
                addLabeledComponent("GDB installation command:", myGdbInstallCommandField)
                addComponent(myPathMappingComponent)
            }

            add(builder.panel, BorderLayout.NORTH)

            myInterpreterField.addActionListener { updateFields() }
            myResourceNameField.addActionListener { updateFields() }
        }

        fun updateFields() {
            val sdk = myInterpreterField.selectedItem
            if (sdk !is Sdk) return

            val additionalData = sdk.sdkAdditionalData

            if (additionalData !is PyRemoteSdkAdditionalDataBase) return


            val credentials = additionalData.connectionCredentials().credentials

            if (credentials !is K8sCredentialsHolder) return

            val credentialsHandler = K8sCredentialsHandler(credentials)

            val config = credentialsHandler.getK8sConfig()

            val client = DefaultKubernetesClient(config)

            myResourceNameList.clear()
            myResourceNameList.addAll(credentialsHandler.getResources(client))

            var resourceName = myResourceNameField.model?.selectedItem?.toString().nullize()

            if (resourceName == null)
                resourceName = credentials.resourceName ?: return

            val podName = credentialsHandler.getPodForResource(client, resourceName) ?: return

            myPidList.addAll(PyK8sRemoteProcessStarterManager().getPidList(credentials, podName))
        }
    }

    private val myPanel = SettingsEditorPanel()

    override fun resetEditorFrom(runConfiguration: K8sDebuggerInjectorRunConfiguration) {
        myPanel.myInterpreterField.model.selectedItem = runConfiguration.sdk
        myPanel.myPathMappingComponent.setMappingSettings(runConfiguration.mappingSettings)
        myPanel.myResourceNameField.model.selectedItem = runConfiguration.resourceName
        myPanel.myGdbInstallCommandField.text = runConfiguration.gdbInstallCommand

        myPanel.updateFields()

        myPanel.myPidField.model.selectedItem = Pair(runConfiguration.pid, "")
        for (pidItem in myPanel.myPidList) {
            if (pidItem.first == runConfiguration.pid) {
                myPanel.myPidField.model.selectedItem = pidItem
                break
            }
        }
    }

    override fun applyEditorTo(runConfiguration: K8sDebuggerInjectorRunConfiguration) {
        val selectedItem = myPanel.myInterpreterField.selectedItem
        if (selectedItem !is Sdk) return

        val resourceName = myPanel.myResourceNameField.model.selectedItem
        if (resourceName !is String) return

        val pidItem = myPanel.myPidField.model.selectedItem
        if (pidItem !is Pair<*, *>) return

        val pid = pidItem.first as String

        runConfiguration.sdkHome = selectedItem.homePath
        runConfiguration.mappingSettings = myPanel.myPathMappingComponent.mappingSettings
        runConfiguration.resourceName = resourceName
        runConfiguration.pid = pid
        runConfiguration.gdbInstallCommand = myPanel.myGdbInstallCommandField.text
    }

    override fun createEditor(): JComponent {
        return myPanel
    }
}