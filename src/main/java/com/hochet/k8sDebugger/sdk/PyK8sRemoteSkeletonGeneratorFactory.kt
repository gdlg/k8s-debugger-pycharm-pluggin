// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.jetbrains.python.remote.PyRemoteSkeletonGeneratorFactory
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.skeletons.PySkeletonGenerator
import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import java.awt.Component

/**
 * Factory for the skeleton generator.
 */
class PyK8sRemoteSkeletonGeneratorFactory : PyRemoteSkeletonGeneratorFactory() {
    override fun supports(pyRemoteSdkAdditionalDataBase: PyRemoteSdkAdditionalDataBase): Boolean {
        return pyRemoteSdkAdditionalDataBase.remoteConnectionType === K8sCredentialsType.instance
    }

    @Throws(ExecutionException::class)
    override fun createRemoteSkeletonGenerator(
        project: Project?,
        component: Component?,
        sdk: Sdk,
        skeletonPath: String
    ): PySkeletonGenerator {
        return PyK8sRemoteSkeletonGenerator(skeletonPath, sdk)
    }
}