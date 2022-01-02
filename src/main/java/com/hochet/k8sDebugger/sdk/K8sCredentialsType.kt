// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.intellij.remote.CredentialsType
import com.intellij.remote.ui.CredentialsEditorProvider
import com.intellij.remote.ext.RemoteCredentialsHandler
import com.intellij.remote.ext.CredentialsLanguageContribution
import com.intellij.remote.ui.RemoteSdkEditorForm
import com.intellij.remote.ext.CredentialsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.remote.ui.BundleAccessor

/**
 * This class acts as a factory for various elements related to the Kubernetes SDK.
 *
 * It is the main extension defined in plugin.xml for the Kubernetes SDK.
 */
class K8sCredentialsType : CredentialsType<K8sCredentialsHolder>("Kubernetes", "kubernetes://"),
    CredentialsEditorProvider {
    override fun getCredentialsKey(): Key<K8sCredentialsHolder> {
        return KUBERNETES_CREDENTIALS
    }

    override fun getHandler(credentials: K8sCredentialsHolder): RemoteCredentialsHandler {
        return K8sCredentialsHandler(credentials)
    }

    override fun createCredentials(): K8sCredentialsHolder {
        return K8sCredentialsHolder()
    }

    override fun isAvailable(languageContribution: CredentialsLanguageContribution<*>?): Boolean {
        return languageContribution is K8sCredentialsPythonContribution
    }

    override fun createEditor(
        project: Project?,
        languageContribution: CredentialsLanguageContribution<*>?,
        parentForm: RemoteSdkEditorForm
    ): CredentialsEditor<*> {
        return K8sCredentialsEditor()
    }

    override fun getDefaultInterpreterPath(bundleAccessor: BundleAccessor): String {
        return "kubernetes:///home/user/.kube/config"
    }

    companion object {
        val KUBERNETES_CREDENTIALS = Key.create<K8sCredentialsHolder>("KUBERNETES_CREDENTIALS")
        @JvmStatic
        val instance: K8sCredentialsType
            get() = EP_NAME.findExtension(K8sCredentialsType::class.java)!!
    }
}