// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger

import com.intellij.execution.configurations.ConfigurationType
import resources.K8sIcons
import com.intellij.execution.configurations.ConfigurationFactory
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * Factory for the Kubernetes run configuration.
 */
class K8sDebuggerRunConfigurationType : ConfigurationType {
    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
        return "Kubernetes"
    }

    override fun getConfigurationTypeDescription(): @Nls(capitalization = Nls.Capitalization.Sentence) String {
        return "Kubernetes debugger"
    }

    override fun getIcon(): Icon {
        return K8sIcons.Kubernetes
    }

    override fun getId(): @NonNls String {
        return "KUBERNETES_DEBUGGER_CONFIGURATION"
    }

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(K8sDebuggerConfigurationFactory(this))
    }
}