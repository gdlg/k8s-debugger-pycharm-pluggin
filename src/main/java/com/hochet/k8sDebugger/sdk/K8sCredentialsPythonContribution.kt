// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.intellij.remote.ext.CredentialsLanguageContribution
import com.jetbrains.python.remote.PyCredentialsContribution

class K8sCredentialsPythonContribution :
    CredentialsLanguageContribution<PyCredentialsContribution<K8sCredentialsHolder>>(),
    PyCredentialsContribution<K8sCredentialsHolder> {
    override fun getType(): K8sCredentialsType {
        return K8sCredentialsType.instance
    }

    override fun getLanguageContributionClass(): Class<PyCredentialsContribution<K8sCredentialsHolder>> {
        @Suppress("UNCHECKED_CAST")
        return PyCredentialsContribution::class.java as Class<PyCredentialsContribution<K8sCredentialsHolder>>
    }

    override fun getLanguageContribution(): PyCredentialsContribution<K8sCredentialsHolder> {
        return this
    }

    override fun isValid(k8sCredentialsHolder: K8sCredentialsHolder?): Boolean {
        // The only requirement to be valid is that the resource name is not null.
        // We do not check whether the Kubernetes cluster is available for performance reason.
        return k8sCredentialsHolder?.resourceName != null
    }

    override fun isPackageManagementEnabled(): Boolean {
        return false
    }

    override fun isSpecificCoverageAttach(): Boolean {
        return false
    }

    override fun isSpecificCoveragePatch(): Boolean {
        return false
    }

    override fun isRemoteProcessStartSupported(): Boolean {
        return false
    }
}