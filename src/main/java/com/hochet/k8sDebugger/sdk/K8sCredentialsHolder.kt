// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

/**
 * This class stores the credentials to connect to a Kubernetes cluster.
 */
class K8sCredentialsHolder {
    var kubeconfigPath: String? = null
    var contextName: String? = null
    var resourceName: String? = null
    var namespaceName: String? = null

    internal constructor()

    constructor(kubeconfigPath: String?, contextName: String?, resourceName: String?, namespaceName: String?) {
        this.kubeconfigPath = kubeconfigPath
        this.contextName = contextName
        this.resourceName = resourceName
        this.namespaceName = namespaceName
    }
}