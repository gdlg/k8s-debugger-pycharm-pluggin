// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.hochet.k8sDebugger.sdk.K8sCredentialsType.Companion.instance
import com.jetbrains.python.packaging.PyPackageManagerProvider
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase

/**
 * Factory for the Kubernetes package manager.
 */
class PyK8sPackageManagerProvider : PyPackageManagerProvider {
    override fun tryCreateForSdk(sdk: Sdk): PyPackageManager? {
        val additionalData = sdk.sdkAdditionalData as? PyRemoteSdkAdditionalDataBase ?: return null
        return if (additionalData.remoteConnectionType !== instance) {
            null
        } else PyK8sPackageManager(sdk)
    }
}