// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import android.annotation.SuppressLint
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.remote.RemoteSdkAdditionalData
import com.intellij.remote.ext.CredentialsEditor
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.util.text.nullize
import com.intellij.util.ui.FormBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import java.awt.BorderLayout
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JTextField
import kotlin.collections.ArrayList

/**
 * Editor for Kubernetes credentials
 *
 * @param enablePythonInterpreterField Optional enable an extra Python interpreter field.
 *  When creating a new Kubernetes SDK, this field is enabled; however, it is not needed when editing an existing SDK
 *  because PyCharm SDK editor already provides this field.
 */
class K8sCredentialsEditor(private val enablePythonInterpreterField: Boolean = false) : CredentialsEditor<K8sCredentialsHolder> {

    class K8sCredentialsEditorPanel(enablePythonInterpreterField: Boolean) : javax.swing.JPanel() {
        val kubeconfigPathField = TextFieldWithBrowseButton().apply {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            addBrowseFolderListener(TextBrowseFolderListener(descriptor))
        }

        val contextList = ArrayList<String>()
        val contextField = ComboBox(CollectionComboBoxModel(contextList, null))

        val namespaceNameList = ArrayList<String>()
        val namespaceNameField = ComboBox(CollectionComboBoxModel(namespaceNameList, null))

        val resourceNameList = ArrayList<String>()
        val resourceNameField = ComboBox(CollectionComboBoxModel(resourceNameList, null))

        val pythonInterpreterField = JTextField()

        init {
            layout = BorderLayout()

            contextField.isEditable = true
            namespaceNameField.isEditable = true
            resourceNameField.isEditable = true

            val builder = FormBuilder.createFormBuilder().apply {
                addLabeledComponent("Kubernetes configuration", kubeconfigPathField)
                addLabeledComponent("Kubernetes context", contextField)
                addLabeledComponent("Namespace", namespaceNameField)
                addLabeledComponent("Resource (e.g. pod)", resourceNameField)
                if (enablePythonInterpreterField) {
                    addLabeledComponent("Python interpreter", pythonInterpreterField)
                }
            }

            add(builder.panel, BorderLayout.NORTH)

            // Trigger at least once the validation just to be able to auto-complete the form.
            // For editing existing credentials, continuous auto-validation is only enabled when validation failed at least once
            // while pressing the Ok button because postponeValidation == true in the DialogWrapper.
            // Ideally, we would enable the auto-validation right from the start.
            // For new credentials dialog, continuous auto-validation is enabled by default.
            validate()
        }
    }

    private val myPanel = K8sCredentialsEditorPanel(enablePythonInterpreterField)

    override fun getName(): String {
        return "Kubernetes"
    }

    override fun getMainPanel(): K8sCredentialsEditorPanel {
        return myPanel
    }

    override fun onSelected() {
    }

    override fun validate(): ValidationInfo? {
        val contextName = myPanel.contextField.model.selectedItem?.toString().nullize()
        val namespaceName = myPanel.namespaceNameField.model.selectedItem?.toString().nullize() ?: "default"
        val resourceName = myPanel.resourceNameField.model.selectedItem?.toString().nullize()

        val credentials = K8sCredentialsHolder()
        saveCredentials(credentials)

        val credentialsHandler = K8sCredentialsHandler(credentials)
        val config = credentialsHandler.getK8sConfig()

        // While validating, also update the list of contexts shown in the UI
        myPanel.contextList.clear()
        myPanel.contextList.addAll(config.contexts.map { context -> context.name })

        if (config.contexts.size == 0) return ValidationInfo("Failed to load the specified kubeconfig", myPanel.kubeconfigPathField)

        if (contextName != null && config.currentContext.name != contextName) return ValidationInfo("Cannot find the specified context in the configuration", myPanel.contextField)

        val client = DefaultKubernetesClient(config)

        try {
            // While validating, also update the list of namespaces shown in the UI
            myPanel.namespaceNameList.clear()
            myPanel.namespaceNameList.addAll(client.namespaces().list().items.map { namespace -> namespace.metadata.name })

            val namespace = client.namespaces().withName(namespaceName).get()

            @SuppressLint
            if (namespace == null) {
                return ValidationInfo(
                    "Cannot find the specified namespace in the configuration",
                    myPanel.namespaceNameField
                )
            }

            // While validating, also update the list of resources shown in the UI
            myPanel.resourceNameList.clear()
            myPanel.resourceNameList.addAll(credentialsHandler.getResources(client))

            if (resourceName == null)
                return ValidationInfo("Resource name cannot be empty", myPanel.resourceNameField)

            if (!credentialsHandler.checkResource(client, resourceName))
                return ValidationInfo("Resource not found", myPanel.resourceNameField)

        } catch (exc : Exception) {
            return ValidationInfo("Failed to connect to the cluster with: ${exc.cause}")
        }

        if (enablePythonInterpreterField && myPanel.pythonInterpreterField.text.nullize() == null) {
            return ValidationInfo("Please specify a Python interpreter")
        }

        return null
    }

    override fun validateFinal(supplier: Supplier<out RemoteSdkAdditionalData<*>>, helpersPathUpdateCallback: Consumer<String>): String? {
        return null
    }

    override fun saveCredentials(credentials: K8sCredentialsHolder?) {
        if (credentials == null) return

        credentials.kubeconfigPath = myPanel.kubeconfigPathField.text.nullize()
        credentials.contextName = myPanel.contextField.model.selectedItem?.toString().nullize()
        credentials.namespaceName = myPanel.namespaceNameField.model.selectedItem?.toString().nullize()
        credentials.resourceName = myPanel.resourceNameField.model.selectedItem?.toString().nullize()
    }

    override fun init(credentials: K8sCredentialsHolder?) {
        if (credentials == null) return

        myPanel.kubeconfigPathField.text = credentials.kubeconfigPath ?: ""
        myPanel.contextField.model.selectedItem = credentials.contextName
        myPanel.namespaceNameField.model.selectedItem = credentials.namespaceName
        myPanel.resourceNameField.model.selectedItem = credentials.resourceName
    }
}