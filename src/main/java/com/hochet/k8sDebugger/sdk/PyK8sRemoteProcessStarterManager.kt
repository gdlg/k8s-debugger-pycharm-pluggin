// Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.hochet.k8sDebugger.sdk

import com.jetbrains.python.run.PyRemoteProcessStarterManager
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import java.lang.InterruptedException
import com.intellij.execution.configurations.GeneralCommandLine
import com.jetbrains.python.remote.PyRemotePathMapper
import java.io.FileOutputStream
import java.io.IOException
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.containers.toArray
import java.io.File
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.ArrayList

/**
 * Wrapper around kubectl to start processes inside a Kubernetes pod.
 *
 * This class is used to start the debugging process, but also contains a few helper utilities interacting with kubectl.
 */
class PyK8sRemoteProcessStarterManager : PyRemoteProcessStarterManager {
    override fun supports(pyRemoteSdkAdditionalDataBase: PyRemoteSdkAdditionalDataBase): Boolean {
        return pyRemoteSdkAdditionalDataBase.remoteConnectionType === K8sCredentialsType.instance
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    override fun startRemoteProcess(
        project: Project?,
        generalCommandLine: GeneralCommandLine,
        pyRemoteSdkAdditionalDataBase: PyRemoteSdkAdditionalDataBase,
        pyRemotePathMapper: PyRemotePathMapper
    ): ProcessHandler {
        val credentials = pyRemoteSdkAdditionalDataBase.connectionCredentials().credentials as K8sCredentialsHolder
        return startRemoteProcess(generalCommandLine, credentials, pyRemotePathMapper)
    }

    /**
     * Start a process on the remote pod.
     *
     * Additionally, for debugging, we also need to start a few local background processes (such as a tunnel).
     * The GeneralCommandLine does not provide any ways to explicitly describe such processes.
     * To circumvent the issue, we use a special parmameter group which we call “Sidecar”.
     * Any parameters in this group is run locally instead of remotely.
     * One reason for using this mechanism is that we would like to ensure that all the processes logs their output
     * in the same stdout/stderr which can be shown in the UI console.
     */
    @Throws(ExecutionException::class, InterruptedException::class)
    fun startRemoteProcess(
        generalCommandLine: GeneralCommandLine,
        credentials: K8sCredentialsHolder,
        pyRemotePathMapper: PyRemotePathMapper
    ): ProcessHandler {
        val parameters = generalCommandLine.parametersList
        val sidecarGroup = parameters.getParamsGroup("Sidecar")
        val builder: ProcessBuilder
        val fullCommand: MutableList<String?> = ArrayList()
        if (sidecarGroup == null) {
            // Case with no sidecar. Call the command line using kubectl.
            builder = generalCommandLine.toProcessBuilder()
            fullCommand.addAll(
                arrayOf("kubectl",
                                 *K8sCredentialsHandler(credentials).getKubectlParameters(),
            "exec",
            "-it",
            credentials.resourceName,
            "--",
            *builder.command().toArray(arrayOf())))
        } else {
            // Case with sidecar. Create a shell script on the host with all the commands.
            // Then execute the shell script.
            try {
                val scriptPath = Files.createTempFile("pycharm_k8s_process", ".sh")
                val script = FileOutputStream(scriptPath.toString())

                // Copy all the parameters from the shell script.
                for (param in sidecarGroup.parameters) {
                    // All the parameters are escaped with quotes; however, allow the PYTHONUNBUFFERED and ; to
                    // be unquoted.
                    script.write(
                        if (param == "PYTHONUNBUFFERED=1" || param == ";") {
                            "$param "
                        } else {
                            "'$param' "
                        }.toByteArray(StandardCharsets.UTF_8))
                }

                // Run the last sidecar command as background process.
                // TODO: Add a bit more flexibility and allow the input to specify what can run as background.
                script.write("&\n".toByteArray(StandardCharsets.UTF_8))

                // Finally, remove the sidecar parameters for the original command line
                for (i in 0 until parameters.paramsGroupsCount) {
                    if (parameters.getParamsGroupAt(i) == sidecarGroup) {
                        parameters.removeParamsGroup(i)
                        break
                    }
                }

                // Add a call to “kubectl” to the script to execute the rest of the command line.
                builder = generalCommandLine.toProcessBuilder()
                val kubectlParams = K8sCredentialsHandler(credentials).getKubectlParameters().joinToString(separator = " ")
                script.write(
                    ("kubectl " + kubectlParams + " exec -it " + credentials.resourceName + " -- ").toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                for (param in builder.command()) {
                    script.write("'$param' ".toByteArray(StandardCharsets.UTF_8))
                }
                script.close()

                // Execute the script.
                fullCommand.add("bash")
                fullCommand.add(scriptPath.toString())
            } catch (exc: IOException) {
                throw ExecutionException(exc)
            }
        }
        return PyK8sProcessHandler(GeneralCommandLine(fullCommand), pyRemotePathMapper)
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    override fun executeRemoteProcess(
        project: Project?,
        command: Array<String>,
        workingDirectory: String?,
        pyRemoteSdkAdditionalDataBase: PyRemoteSdkAdditionalDataBase,
        pyRemotePathMapper: PyRemotePathMapper
    ): ProcessOutput {
        val credentials = pyRemoteSdkAdditionalDataBase.connectionCredentials().credentials as K8sCredentialsHolder
        return executeRemoteProcess(command, workingDirectory, credentials, pyRemotePathMapper)
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    fun executeRemoteProcess(
        command: Array<String>,
        workingDirectory: String?,
        credentials: K8sCredentialsHolder,
        pyRemotePathMapper: PyRemotePathMapper
    ): ProcessOutput {
        val commandLine = GeneralCommandLine(*command)
        commandLine.setWorkDirectory(workingDirectory)
        val processHandler = startRemoteProcess(commandLine, credentials, pyRemotePathMapper)
        val processOutput = ProcessOutput()
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER.value(outputType)) {
                    val text = event.text
                    processOutput.appendStdout(text)
                }
                if (ScriptRunnerUtil.STDERR_OUTPUT_KEY_FILTER.value(outputType)) {
                    val text = event.text
                    processOutput.appendStderr(text)
                }
            }
        })
        val timeout = 300000 // TODO: Review this timeout
        processHandler.startNotify()
        if (!processHandler.waitFor(timeout.toLong())) {
            throw ExecutionException(ExecutionBundle.message("script.execution.timeout", (timeout / 1000).toString()))
        }
        processOutput.exitCode = processHandler.exitCode!!
        return processOutput
    }

    /**
     * Utility function to copy a file to the remote pod.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun copyFileToRemote(source: String, destination: String, credentials: K8sCredentialsHolder, podName: String) {
        val cpCommand = mutableListOf(
            "kubectl",
        )

        cpCommand.addAll(K8sCredentialsHandler(credentials).getKubectlParameters())

        cpCommand.add("cp")
        cpCommand.add(source)
        cpCommand.add("$podName:$destination")

        val cpProcessBuilder = ProcessBuilder(*cpCommand.toArray(arrayOf()))
        val cpProcess = cpProcessBuilder.start()
        val exitCode = cpProcess.waitFor()
        if (exitCode != 0) throw IOException("Failure")
    }

    /**
     * Utility function to list all the processes running on the remote.
     *
     * @return List of pair containing the PID and command line used to create the process
     */
    fun getPidList(credentials: K8sCredentialsHolder, podName: String) : List<Pair<String, String>> {
        try {
            // To implement this feature, we create a small Python script listing all the PIDs.
            // Then, we copy and execute this script.
            val toInject = File.createTempFile("command", ".py")
            val writer = FileWriter(toInject)
            writer.write(
                """
import os

for pid in os.listdir("/proc"):
    try:
        pid = int(pid)
        print(pid, open(os.path.join("/proc", str(pid), "cmdline"), "r").read())
    except:
        pass
"""
            )

            writer.close()
            copyFileToRemote(toInject.absolutePath, "/pycharm_command.py", credentials, podName)

            val output = executeRemoteProcess(arrayOf("python", "/pycharm_command.py"), null, credentials, PyRemotePathMapper())

            if (output.exitCode != 0) return listOf()

            // Parse the output of the script, and return it as a list of pairs (PID, command line).
            return output.stdoutLines.map { line ->
                val data = line.split(" ", limit = 2)
                if (data.size > 1) Pair(data[0], data[1]) else Pair(data[0], "") }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return listOf()
    }
}