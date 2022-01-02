// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls

/**
 * Factory for the Kubernetes run configuration.
 */
class K8sDebuggerConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return K8sDebuggerInjectorRunConfiguration(project, this)
    }

    override fun getId(): @NonNls String {
        return "KUBERNETES_DEBUGGER_INJECTOR_CONFIGURATION_FACTORY"
    }
}