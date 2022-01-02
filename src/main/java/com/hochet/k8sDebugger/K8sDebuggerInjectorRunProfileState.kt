// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.python.run.PythonCommandLineState
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor

/**
 * The run profile is based on the PythonCommandLineState.
 *
 * However, we extend this profile state to allow public access to the "runConfiguration".
 * In PythonCommandLineState, the configuration is private. This is used by the PyK8sDebugSessionFactory.
 */
class K8sDebuggerInjectorRunProfileState(
    val runConfiguration: K8sDebuggerInjectorRunConfiguration,
    env: ExecutionEnvironment?
) : PythonCommandLineState(
    runConfiguration, env
) {
    @Throws(ExecutionException::class)
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        return super.execute(executor, runner)
    }
}