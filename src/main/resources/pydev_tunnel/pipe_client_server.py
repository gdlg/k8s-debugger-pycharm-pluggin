# Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import fcntl
import logging
import os
import io
from typing import Any, BinaryIO

from dispatcher import Dispatcher
from processor import Processor

logger = logging.getLogger("pipe_client_server")


class PipeClientServer(Processor):
    """
    This class handles the communication between the local and remote hosts using a pipe.
    """
    def __init__(self, dispatcher: Dispatcher, stdin: BinaryIO, stdout: BinaryIO):
        logger.debug("create new pipe client/server")
        self._dispatcher = dispatcher
        self._read_buffer = ""
        self._stdin = stdin
        self._stdout = stdout
        orig_fl = fcntl.fcntl(self._stdin, fcntl.F_GETFL)
        fcntl.fcntl(self._stdin, fcntl.F_SETFL, orig_fl | os.O_NONBLOCK)

    @property
    def key(self) -> Any:
        return None

    @property
    def socket(self) -> BinaryIO:
        return self._stdin

    def on_input_ready(self):
        data = self._stdin.read(1024)
        if len(data) == 0:
            logger.debug("the end of the pipe has been closed. Exiting.")
            import sys
            sys.exit(0)

        self._read_buffer += (data if isinstance(data, str) else data.decode())

        while self._read_buffer.find("\n") != -1:
            command, read_buffer = self._read_buffer.split("\n", 1)
            self._read_buffer = read_buffer

            args = command.split("\t", 2)

            local_port = args[0]
            remote_port = args[1]
            command = args[2]

            if command == "start_client":
                self.start_client(local_port, remote_port)
            elif command == "stop_client":
                self.close_client(local_port, remote_port)
            elif command == "start_server":
                self.start_server(local_port)
            elif command == "stop_server":
                self.stop_server(local_port)
            else:
                self.dispatch_command_to_client(local_port, remote_port, command+"\n")

    def write(self, local_port: str, remote_port: str, command: str):
        data = local_port+"\t"+remote_port+"\t"+command
        if isinstance(self._stdout, (io.BufferedIOBase, io.RawIOBase)):
            data = data.encode()
        self._stdout.write(data)
        self._stdout.flush()

    def start_server(self, local_port: str):
        logger.debug(f"start the server on {local_port}")
        from pydev_server import PydevServer
        server = PydevServer(self._dispatcher, local_port)
        self._dispatcher.add_processor(server)

    def stop_server(self, local_port: str):
        logger.debug(f"stop the server on {local_port}")
        server = self._dispatcher.find_processor(local_port)
        self._dispatcher.remove_processor(server)

    def start_client(self, local_port: str, remote_port: str):
        from pydev_client import PydevClient
        logger.debug(f"create new client (local: {local_port}, remote: {remote_port}")
        client = PydevClient(self._dispatcher, local_port, remote_port)
        self._dispatcher.add_processor(client)

    def dispatch_command_to_client(self, local_port: str, remote_port: str, command: str):
        key = (local_port, remote_port)
        client = self._dispatcher.find_processor(key)
        client.write(command)

    def close_client(self, local_port: str, remote_port: str):
        logger.debug(f"close the client (local: {local_port}, remote: {remote_port})")
        key = (local_port, remote_port)

        client = self._dispatcher.find_processor(key)

        if client is not None:
            self._dispatcher.remove_processor(client)

    def close(self) -> None:
        pass
