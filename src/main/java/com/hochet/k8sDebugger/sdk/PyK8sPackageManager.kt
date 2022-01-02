// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.intellij.execution.ExecutionException
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyPackage
import com.intellij.openapi.vfs.VirtualFile
import java.util.ArrayList
import java.util.HashSet

/**
 * Package manager for Kubernetes.
 *
 * This is a dummy class because due to the ephemeral nature of Kubernetes pods,
 * it does not make sense to install packages into the pods.
 */
class PyK8sPackageManager internal constructor(sdk: Sdk) : PyPackageManager() {
    @Throws(ExecutionException::class)
    override fun installManagement() {
    }

    @Throws(ExecutionException::class)
    override fun hasManagement(): Boolean {
        return true
    }

    @Throws(ExecutionException::class)
    override fun install(s: String) {
    }

    @Throws(ExecutionException::class)
    override fun install(list: List<PyRequirement>?, list1: List<String>) {
    }

    @Throws(ExecutionException::class)
    override fun uninstall(list: List<PyPackage>) {
    }

    override fun refresh() {}
    @Throws(ExecutionException::class)
    override fun createVirtualEnv(s: String, b: Boolean): String {
        return ""
    }

    override fun getPackages(): List<PyPackage> {
        return ArrayList()
    }

    @Throws(ExecutionException::class)
    override fun refreshAndGetPackages(b: Boolean): List<PyPackage> {
        return ArrayList()
    }

    override fun getRequirements(module: Module): List<PyRequirement> {
        return ArrayList()
    }

    override fun parseRequirement(s: String): PyRequirement? {
        return null
    }

    override fun parseRequirements(s: String): List<PyRequirement> {
        return ArrayList()
    }

    override fun parseRequirements(virtualFile: VirtualFile): List<PyRequirement> {
        return ArrayList()
    }

    @Throws(ExecutionException::class)
    override fun getDependents(pyPackage: PyPackage): Set<PyPackage> {
        return HashSet()
    }
}