// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger

import com.hochet.k8sDebugger.sdk.K8sCredentialsHandler
import com.jetbrains.python.debugger.PyDebugSessionFactory
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.hochet.k8sDebugger.sdk.K8sCredentialsType
import com.hochet.k8sDebugger.sdk.K8sCredentialsHolder
import com.hochet.k8sDebugger.sdk.PyK8sRemoteProcessStarterManager
import com.intellij.openapi.application.ApplicationInfo
import java.io.FileOutputStream
import java.io.IOException
import java.lang.InterruptedException
import com.jetbrains.python.run.PythonCommandLineState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebugSession
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import java.net.ServerSocket
import com.intellij.execution.ExecutionResult
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugProcess
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.run.CommandLinePatcher
import com.jetbrains.python.debugger.PyDebugRunner
import java.io.FileWriter
import com.intellij.openapi.project.Project
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import java.io.File
import java.net.SocketTimeoutException
import java.nio.file.Files

/**
 * The debugger session for Kubernetes.
 *
 * This class is responsible for installing required dependencies in the pod, starting the tunnel,
 * and injecting the debugger in the running process in the pod.
 */
class PyK8sDebugSessionFactory : PyDebugSessionFactory() {
    override fun appliesTo(sdk: Sdk): Boolean {
        val additionalData = sdk.sdkAdditionalData as? PyRemoteSdkAdditionalDataBase ?: return false
        return additionalData.remoteConnectionType === K8sCredentialsType.instance
    }

    /**
     * Return a command which can be used to install GDB, pyrasite and pydevd in the pod.
     */
    private fun getDependencyInstallationCommand(
        credentials: K8sCredentialsHolder,
        podName: String,
        runConfiguration: K8sDebuggerInjectorRunConfiguration
    ): Array<String> {
        val currentBuild = ApplicationInfo.getInstance().build.asStringWithoutProductCode()
        val pydevdPackage = "pydevd-pycharm~=$currentBuild"
        return arrayOf(
            "kubectl", *K8sCredentialsHandler(credentials).getKubectlParameters(), "exec", podName, "-i", "--",
            "bash", "-c", runConfiguration.gdbInstallCommand + "; pip install pyrasite " + pydevdPackage
        )
    }

    /**
     * Get a command to start the pydevd tunnel.
     *
     * We use our own client/server implementation instead of a reverse port forwarding,
     * because PyCharm implements multiprocessing by opening separate ports. Therefore, we need to detect
     * when PyCharm creates new debugging servers, and listen for those as well on the remote end.
     */
    @Throws(ExecutionException::class)
    private fun getTunnelCommand(credentials: K8sCredentialsHolder, podName: String, serverPort: Int): Array<String> {
        try {
            // The pydev script is stored as a resesource in the JAR file.
            // Extract this script to a temporary file.
            val tunnelScriptStream =
                PyK8sDebugSessionFactory::class.java.getResourceAsStream("/pydev_tunnel/tunnel_single_script.py")!!
            val tunnelScriptLocalPath = Files.createTempFile("tunnel_single_script", ".py")!!
            val tunnelScriptOutStream = FileOutputStream(tunnelScriptLocalPath.toString())
            var readBytes: Int
            val buffer = ByteArray(4096)
            while (tunnelScriptStream.read(buffer).also { readBytes = it } > 0) {
                tunnelScriptOutStream.write(buffer, 0, readBytes)
            }
            tunnelScriptOutStream.close()

            // Copy the file to the remote pod.
            PyK8sRemoteProcessStarterManager().copyFileToRemote(tunnelScriptLocalPath.toString(), "/tunnel_single_script.py", credentials, podName)

            // Finally, define the command line for the script.
            // TODO: Review if PYTHONUNBUFFERED=1 is really needed. It's likely that we can remove it.
            return arrayOf(
                "PYTHONUNBUFFERED=1",
                "python", tunnelScriptLocalPath.toString(), serverPort.toString(),
                "kubectl", *K8sCredentialsHandler(credentials).getKubectlParameters(), "exec", podName, "-i", "--",
                "python", "-u", "/tunnel_single_script.py"
            )
        } catch (e: IOException) {
            throw ExecutionException(e)
        } catch (e: InterruptedException) {
            throw ExecutionException(e)
        }
    }

    /**
     * This is the main entry point for this class. It is responsible for creating the debugging session.
     */
    @Throws(ExecutionException::class)
    override fun createSession(
        pythonCommandLineState: PythonCommandLineState,
        executionEnvironment: ExecutionEnvironment
    ): XDebugSession {
        try {
            val state = pythonCommandLineState as K8sDebuggerInjectorRunProfileState
            val runConfiguration = state.runConfiguration
            val sdk = pythonCommandLineState.getSdk()
            val additionalDataBase = sdk!!.sdkAdditionalData as PyRemoteSdkAdditionalDataBase?
            val credentials = additionalDataBase!!.connectionCredentials().credentials as K8sCredentialsHolder

            val resourceName = runConfiguration.resourceName ?: credentials.resourceName!!
            val credentialsHandler = K8sCredentialsHandler(credentials)
            val config = credentialsHandler.getK8sConfig()
            val client = DefaultKubernetesClient(config)

            val podName = try {
                K8sCredentialsHandler(credentials).getPodForResource(client, resourceName)!!
            } catch (_: NoSuchElementException) {
                throw ExecutionException("Cannot find a running pod for the resource.")
            }

            val environment = state.environment
            val project = environment.project
            val profile = environment.runProfile

            val serverSocket = PythonCommandLineState.createServerSocket()
            val serverLocalPort = serverSocket.localPort

            // Define the sidecar commands which are executed before injecting the debugger in the remote process.
            // The first command installs the required dependencies in the pod.
            // The second command starts the pydevd tunnel. This command will be running as a background process.
            val sidecarCommands = arrayOf(
                *getDependencyInstallationCommand(credentials, podName, runConfiguration),
                ";",
                *getTunnelCommand(credentials, podName, serverLocalPort))

            // Call the command line patchers and execute the sidecars and debugger process.
            val result = state.execute(
                environment.executor,
                *createCommandLinePatchers(
                    state,
                    credentials,
                    podName,
                    profile,
                    serverLocalPort,
                    sidecarCommands
                )
            )

            // Create the session
            val debugSession =
                XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession): XDebugProcess {
                        return createDebugProcess(project, session, result, serverSocket, state)
                    }
                })
            return debugSession
        } catch (exc: SocketTimeoutException) {
            throw ExecutionException("Timeout connecting to the Kubernetes cluster.")
        }
    }

    private fun createCommandLinePatchers(
        state: K8sDebuggerInjectorRunProfileState,
        credentials: K8sCredentialsHolder,
        podName: String,
        profile: RunProfile,
        serverLocalPort: Int,
        sidecarCommands: Array<String>
    ): Array<CommandLinePatcher?> {
        return arrayOf(
            createDebugServerPatcher(state, credentials, podName, serverLocalPort, sidecarCommands),
            PyDebugRunner.createRunConfigPatcher(state, profile)
        )
    }

    private fun createDebugServerPatcher(
        state: K8sDebuggerInjectorRunProfileState,
        credentials: K8sCredentialsHolder,
        podName: String,
        serverLocalPort: Int,
        sidecarCommands: Array<String>
    ): CommandLinePatcher {
        try {
            // Create a script which is injected in the process to debug
            // in order to attach the debugger.

            // The script is created in a temporary file, then copied to the remote host.
            val toInject = File.createTempFile("to_inject_", ".py")
            val writer = FileWriter(toInject)
            writer.write("import pydevd_pycharm\n")
            writer.write("import pydevd\n")
            writer.write("import traceback\n")
            writer.write("pydevd.stoptrace()\n")
            writer.write("port=$serverLocalPort\n")
            if (state.isMultiprocessDebug) {
                // The multiprocess case is a bit more complicated.
                // Instead of starting the debugger, we need to start the PyCharm pydved Dispatcher.
                // Unfortunately, this cannot be done by calling settrace(), so we have to call it manually.
                // Normally, when debugging a local process, the dispatcher is started in the main function of pydevd,
                // but in our case, we cannot call the main function since we would like to attach to an existing process.
                writer.write(
                    """import os
dispatcher = pydevd.Dispatcher()
try:
    dispatcher.connect('127.0.0.1', port)
    if dispatcher.port is not None:
        port = dispatcher.port
        pydevd.pydev_log.debug("Received port %d\n" % port)
        pydevd.pydev_log.debug("pydev debugger: process %d is connecting\n" % os.getpid())
        try:
            pydevd.pydev_monkey.patch_new_process_functions()
        except:
            pydevd.pydev_log.error("Error patching process functions\n")
            traceback.print_exc()
    else:
        pydevd.pydev_log.error("pydev debugger: couldn't get port for new debug process\n")
finally:
   dispatcher.close()
"""
                )
            }

            // Call to settrace() to start the debugging session in the remote process
            writer.write("pydevd_pycharm.settrace('127.0.0.1', port=port, stdoutToServer=True, stderrToServer=True)\n")

            writer.close()

            // Copy the Python script to the remote host.
            PyK8sRemoteProcessStarterManager().copyFileToRemote(toInject.absolutePath, "/to_inject.py", credentials, podName)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        // Finally, we just need to define the command line (execute in the remote pod) to inject our script.
        // We use Pyrasite which allow us to inject the script defined above in the Python process to debug.
        return CommandLinePatcher { generalCommandLine ->
            generalCommandLine.exePath = "bash"
            val parameters = generalCommandLine.parametersList
            parameters.clearAll()
            val sidecar = parameters.addParamsGroup("Sidecar")
            for (command in sidecarCommands) {
                sidecar.addParameter(command)
            }
            val main = parameters.addParamsGroup("Main")
            main.addParameter("-c")
            main.addParameter("pyrasite " + state.runConfiguration.pid + " /to_inject.py; sleep infinity")
        }
    }

    fun createDebugProcess(
        project: Project?,
        session: XDebugSession?,
        result: ExecutionResult,
        serverSocket: ServerSocket?,
        state: K8sDebuggerInjectorRunProfileState
    ): PyDebugProcess {
        val process = PyDebugProcess(
            session!!,
            serverSocket!!,
            result.executionConsole,
            result.processHandler,
            state.isMultiprocessDebug
        )
        PyDebugRunner.createConsoleCommunicationAndSetupActions(project!!, result, process, session)
        return process
    }
}