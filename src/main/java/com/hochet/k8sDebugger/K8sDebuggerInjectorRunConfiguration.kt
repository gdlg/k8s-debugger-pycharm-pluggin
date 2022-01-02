// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger

import com.hochet.k8sDebugger.sdk.K8sCredentialsType
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.intellij.openapi.util.WriteExternalException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import org.jdom.Element

/**
 * Class representing the state of a run configuration.
 *
 * This run configuration extends the Python run configuration. This is needed to interact with many PyCharm features.
 */
class K8sDebuggerInjectorRunConfiguration constructor(project: Project, factory: ConfigurationFactory) :
    AbstractPythonRunConfiguration<K8sDebuggerInjectorRunConfiguration>(project, factory) {

    // In the run configuration, we allow the user to overwrite the resource name
    // which is specified in the SDK.
    var resourceName: String? = null

    // The user also need to specify the PID of the program to debug
    // and a command which can be used to install GDB in the pod.
    var pid = "1"
    var gdbInstallCommand = "yum -y install gdb"

    /**
     * Create an editor panel to edit this run configuration.
     */
    override fun createConfigurationEditor(): SettingsEditor<K8sDebuggerInjectorRunConfiguration> {
        return K8sDebuggerInjectorSettingsEditor(this)
    }

    @Throws(InvalidDataException::class)
    override fun readExternal(element: Element) {
        super.readExternal(element)
        resourceName = element.getAttributeValue("k8s_resource_name", "")
        if (resourceName == "") {
            resourceName = null
        }
        pid = element.getAttributeValue("k8s_pid", "1")
        gdbInstallCommand = element.getAttributeValue("gdb_install_command", "yum -y install gdb")
    }

    @Throws(WriteExternalException::class)
    override fun writeExternal(element: Element) {
        if (resourceName != null) {
            element.setAttribute("k8s_resource_name", resourceName)
        }
        element.setAttribute("k8s_pid", pid)
        element.setAttribute("gdb_install_command", gdbInstallCommand)
        super.writeExternal(element)
    }

    /**
     * Create the runtime state at the beginning of debugging.
     */
    @Throws(ExecutionException::class)
    override fun getState(executor: Executor, executionEnvironment: ExecutionEnvironment): RunProfileState {
        return K8sDebuggerInjectorRunProfileState(this, executionEnvironment)
    }

    /**
     * Check the configuration.
     *
     * We are limited to simple checks to avoid UI slowdowns.
     * Therefore, we do not check if the Kubernetes pod is accessible and contains the right PID.
     */
    override fun checkConfiguration() {
        super.checkConfiguration()

        val mySdk = sdk ?: throw RuntimeConfigurationError("Unknown Python interpreter")

        val sdkAdditionalData = mySdk.sdkAdditionalData
        if (sdkAdditionalData !is PyRemoteSdkAdditionalDataBase || sdkAdditionalData.remoteConnectionType != K8sCredentialsType.instance)
            throw RuntimeConfigurationError("The Kubernetes configuration only supports the Kubernetes Python interpreter")

        val number = pid.toIntOrNull()
        if (number == null || number <= 0) throw RuntimeConfigurationError("The PID should be a positive number")
    }
}