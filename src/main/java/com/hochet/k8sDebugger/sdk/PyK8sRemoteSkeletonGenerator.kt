// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.intellij.execution.ExecutionException
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.skeletons.PySkeletonGenerator
import com.intellij.openapi.progress.ProgressIndicator
import com.jetbrains.python.sdk.InvalidSdkException
import java.util.ArrayList

/**
 * TODO: Not sure if skeletons are needed. For the moment, provide a dummy class.
 */
class PyK8sRemoteSkeletonGenerator(skeletonPath: String?, pySdk: Sdk) :
    PySkeletonGenerator(skeletonPath, pySdk, null) {
    @Throws(InvalidSdkException::class, ExecutionException::class)
    override fun runGeneration(builder: Builder, indicator: ProgressIndicator?): List<GenerationResult> {
        return ArrayList()
    }

    override fun finishSkeletonsGeneration() {}
}