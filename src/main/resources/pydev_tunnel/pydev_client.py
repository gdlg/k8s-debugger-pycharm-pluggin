# Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import logging
import socket
from typing import Any

from dispatcher import Dispatcher
from processor import Processor
from pipe_client_server import PipeClientServer

logger = logging.getLogger("pydev_client")


class PydevClient(Processor):
    """
    Client which reads Pydev commands (either on the local or remote) and send them through the pipe
    to the other end.

    The client also detects when a Pydev debug server starts a new server.
    When this happens, a monitor is created to handle this new server.
    (this is part of the support for multiproc in PyCharm)
    """
    def __init__(self, dispatcher: Dispatcher, local_port: str, remote_port: str, client_socket=None):
        logger.debug(f"start new client (local: {local_port}, remote: {remote_port})")
        self._read_buffer = ""
        self._dispatcher = dispatcher

        if client_socket is None:
            self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self._socket.connect(("127.0.0.1", int(local_port)))
        else:
            self._socket = client_socket

        self._socket.setblocking(False)
        self._local_port = local_port
        self._remote_port = remote_port

    @property
    def key(self) -> Any:
        return self._local_port, self._remote_port

    @property
    def socket(self) -> socket.socket:
        return self._socket

    def write(self, data: str):
        logger.debug("write: "+data)
        self._socket.sendall(data.encode())

    def on_input_ready(self):
        server = self._dispatcher.find_processor(None)
        assert isinstance(server, PipeClientServer)

        recv_data = self._socket.recv(1024).decode()
        if len(recv_data) == 0:
            # The socket has been closed
            logger.debug(f"stop this client, and ask remote to stop (local: {self._local_port}, "
                         f"remote: {self._remote_port})")
            server.write(self._local_port, self._remote_port, "stop_client\n")
            self._dispatcher.remove_processor(self)

        self._read_buffer += recv_data

        while self._read_buffer.find("\n") != -1:
            command, read_buffer = self._read_buffer.split("\n", 1)
            self._read_buffer = read_buffer

            # Detect when PyCharm tries to start a new server
            args = command.split("\t", 2)
            if len(args) == 3 and args[0] == "99" and args[1] == "-1":
                new_local_port = args[2]
                logger.debug(f"start monitoring for {new_local_port} (local: {self._local_port}, "
                             f"remote: {self._remote_port})")
                from pydev_server_monitor import PydevServerMonitor
                self._dispatcher.add_server_monitor(PydevServerMonitor(self._dispatcher, new_local_port))
            
            logger.debug("read : "+command)
            server.write(self._local_port, self._remote_port, command+"\n")

    def close(self):
        self._socket.close()
