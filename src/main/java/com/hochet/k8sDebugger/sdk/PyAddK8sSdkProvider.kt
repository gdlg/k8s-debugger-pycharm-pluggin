// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.jetbrains.python.sdk.add.PyAddSdkProvider
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.python.sdk.add.PyAddSdkView
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Factory for the panel to create a new Kubernetes SDK.
 */
class PyAddK8sSdkProvider : PyAddSdkProvider {
    override fun createView(
        project: Project?,
        module: Module?,
        newProjectPath: String?,
        existingSdks: List<Sdk>,
        context: UserDataHolder
    ): PyAddSdkView {
        return PyAddNewK8sEnvPanel(project, existingSdks, newProjectPath)
    }
}