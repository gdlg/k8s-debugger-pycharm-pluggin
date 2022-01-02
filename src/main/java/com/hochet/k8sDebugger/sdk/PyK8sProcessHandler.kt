// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.intellij.execution.process.OSProcessHandler
import com.jetbrains.python.debugger.PositionConverterProvider
import com.jetbrains.python.remote.PyRemotePathMapper
import com.intellij.execution.configurations.GeneralCommandLine
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyPositionConverter
import com.jetbrains.python.debugger.remote.vfs.PyRemotePositionConverter

/**
 * Process handler for the PyK8sRemoteProcessStarterManager.
 *
 * This class is just a wrapper around the OSProcessHandler which also implements the PositionConverterProvider.
 *
 * Not sure if OSProcessHandler is the best class. We just need a basic ProcessHandler
 * which can run commands on the local (not remote) machine.
 *
 * The PositionConverterProvider interface is used to implement remote path mappings.
 */
class PyK8sProcessHandler(commandLine: GeneralCommandLine, remotePathMapper: PyRemotePathMapper) : OSProcessHandler(
    commandLine
), PositionConverterProvider {
    private var myRemotePathMapper: PyRemotePathMapper = remotePathMapper

    override fun createPositionConverter(pyDebugProcess: PyDebugProcess): PyPositionConverter {
        return PyRemotePositionConverter(pyDebugProcess, myRemotePathMapper)
    }
}