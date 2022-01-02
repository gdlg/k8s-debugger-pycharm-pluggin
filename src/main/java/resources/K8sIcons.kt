package resources

import com.intellij.openapi.util.IconLoader

interface K8sIcons {
    companion object {
        val Kubernetes = IconLoader.getIcon("/icons/kubernetes.svg", K8sIcons::class.java)
    }
}