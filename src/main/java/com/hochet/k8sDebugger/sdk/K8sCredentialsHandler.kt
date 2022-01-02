// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.intellij.remote.ext.RemoteCredentialsHandler
import com.intellij.util.containers.toArray
import com.intellij.util.text.nullize
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import org.jdom.Element

/**
 * This class provides a few utilities to help using the Kubernetes credentials.
 */
class K8sCredentialsHandler constructor(private val myCredentialsHolder: K8sCredentialsHolder) :
    RemoteCredentialsHandler {

    override fun getId(): String {
        val kubeconfigPath = myCredentialsHolder.kubeconfigPath
        val contextName = myCredentialsHolder.contextName
        val resourceName = myCredentialsHolder.resourceName
        val namespaceName = myCredentialsHolder.namespaceName
        return "kubernetes://$kubeconfigPath?context=$contextName&namespace=$namespaceName&resource=$resourceName/python"
    }

    override fun save(rootElement: Element) {
        rootElement.setAttribute("kubeconfigPath", myCredentialsHolder.kubeconfigPath ?: "")
        rootElement.setAttribute("contextName", myCredentialsHolder.contextName ?: "")
        rootElement.setAttribute("resourceName", myCredentialsHolder.resourceName ?: "")
        rootElement.setAttribute("namespaceName", myCredentialsHolder.namespaceName ?: "")
    }

    override fun getPresentableDetails(interpreterPath: String): String {
        return "Kubernetes ($interpreterPath)"
    }

    override fun load(rootElement: Element?) {
        myCredentialsHolder.kubeconfigPath = rootElement!!.getAttributeValue("kubeconfigPath", "").nullize()
        myCredentialsHolder.contextName = rootElement.getAttributeValue("contextName", "").nullize()
        myCredentialsHolder.resourceName = rootElement.getAttributeValue("resourceName", "").nullize()
        myCredentialsHolder.namespaceName = rootElement.getAttributeValue("namespaceName", "").nullize()
    }

    /**
     * Get the configuration for the Java Kubernetes client.
     */
    fun getK8sConfig(): Config {
        val kubeconfigPath = myCredentialsHolder.kubeconfigPath ?: (System.getProperty("user.home") + "/.kube/config")
        val contextName = myCredentialsHolder.contextName

        System.setProperty("kubeconfig", kubeconfigPath)
        System.setProperty("kubernetes.auth.tryKubeConfig", "true")
        System.setProperty("kubernetes.auth.tryServiceAccount", "false")
        System.setProperty("kubernetes.tryNamespacePath", "false")

        return Config.autoConfigure(contextName)
    }

    /**
     * Get parameters to set the credentials for the "kubectl" command.
     *
     * This set the kubeconfig, context, and namespace.
     */
    fun getKubectlParameters() : Array<String> {
        val parameters = mutableListOf<String>()

        val kubeconfigPath = myCredentialsHolder.kubeconfigPath
        val contextName = myCredentialsHolder.contextName
        val namespaceName = myCredentialsHolder.namespaceName

        if (kubeconfigPath != null) {
            parameters.add("--kubeconfig")
            parameters.add(kubeconfigPath)
        }

        if (contextName != null) {
            parameters.add("--context")
            parameters.add(contextName)
        }

        if (namespaceName != null) {
            parameters.add("-n")
            parameters.add(namespaceName)
        }

        return parameters.toArray(arrayOf())
    }

    /**
     * Check whether a resource (e.g. a deployment) exists in the Kubernetes cluster.
     */
    fun checkResource(client: DefaultKubernetesClient, resourceName: String?) : Boolean {

        if (resourceName == null)
            return false

        val parsedResource = resourceName.split("/", limit = 2)

        val namespaceName = myCredentialsHolder.namespaceName ?: "default"

        // The parsed resource might have multiple forms:
        // <pod name>
        // pod/<pod name>
        // <deploy/<deployment name>
        // ...
        if (parsedResource.size == 1) {
            if (client.pods().inNamespace(namespaceName).withName(parsedResource[0]).get() == null)
                return false
        } else {
            val resourceGroup = when (parsedResource[0]) {
                "deploy", "deployment" -> client.apps().deployments()
                "replicas", "replicaset" -> client.apps().replicaSets()
                "pod" -> client.pods()
                else -> null
            }

            val name = parsedResource[1]

            if (name == "" || resourceGroup?.inNamespace(namespaceName)?.withName(name)?.get() == null) {
                return false
            }
        }

        return true
    }

    /**
     * Get a list of all resources available on the cluster.
     *
     * Currently, we support deployments, replica sets, and pods.
     */
    fun getResources(client: DefaultKubernetesClient) : List<String> {
        val resourceNameList = mutableListOf<String>()

        val namespaceName = myCredentialsHolder.namespaceName ?: "default"

        // The parsed resource might have multiple forms:
        // <pod name>
        // pod/<pod name>
        // <deploy/<deployment name>
        // ...
        for (resourceType in arrayOf(
            Pair("deployment/", client.apps().deployments()),
            Pair("replicaset/", client.apps().replicaSets()),
            Pair("pod/", client.pods())
        )) {
            resourceNameList.addAll(
                resourceType.second.inNamespace(namespaceName).list().items.map { resource -> resourceType.first+resource.metadata.name })
        }

        return resourceNameList
    }

    /** For a given resource, find the latest pod owned by this resource.
     *
     * This function is required because some kubectl calls only work on pods.
     */
    fun getPodForResource(client: DefaultKubernetesClient, resourceName: String) : String? {

        val parsedResource = resourceName.split("/", limit = 2)

        val namespaceName = myCredentialsHolder.namespaceName ?: "default"

        if (parsedResource.size == 1) {
            return resourceName
        }
        
        fun getReplicaSetFromDeployment(deploymentName: String? ) : String? {
            if (deploymentName == null) return null

            var replicas = client.apps().replicaSets().inNamespace(namespaceName).list().items.stream().filter {
                    replicaset -> replicaset.metadata.ownerReferences.any { ownerReference -> ownerReference.name == deploymentName }
            }
            
            replicas = replicas.sorted(Comparator.comparing<ReplicaSet?, String?> { replicaSet -> replicaSet.metadata.creationTimestamp }.reversed())
            return (replicas.findFirst() ?: return null).get().metadata.name
        }

        fun getPodFromReplicaSet(replicaSetName: String?) : String? {
            if (replicaSetName == null) return null

            var pods = client.pods().inNamespace(namespaceName).list().items.stream().filter {
                    replicaset -> replicaset.metadata.ownerReferences.any { ownerReference -> ownerReference.name == replicaSetName }
            }

            pods = pods.sorted(Comparator.comparing<Pod?, String?> { pod -> pod.metadata.creationTimestamp }.reversed())
            return (pods.findFirst() ?: return null).get().metadata.name
        }

        val podName = when (parsedResource[0]) {
            "deploy", "deployment" -> {
                val replicaSet = getReplicaSetFromDeployment(parsedResource[1])
                getPodFromReplicaSet(replicaSet)
            }
            "replicas", "replicaset" -> getPodFromReplicaSet(parsedResource[1])
            "pod" -> parsedResource[0]
            else -> null
        }

        return podName
    }
}