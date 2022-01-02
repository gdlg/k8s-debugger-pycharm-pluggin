# Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import logging
import socket
from typing import Any

from dispatcher import Dispatcher
from processor import Processor

logger = logging.getLogger("pydev_server")


class PydevServer(Processor):
    """
    Listen on the remote pod for new debugger connection and create a new client for each connection.
    """
    def __init__(self, dispatcher: Dispatcher, local_port: str):
        logger.debug(f"start new server on port {local_port}")
        self._dispatcher = dispatcher
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._socket.bind(('', int(local_port)))
        self._socket.listen(100)
        self._socket.setblocking(False)
        self._local_port = str(local_port)

    @property
    def key(self) -> Any:
        return self._local_port

    @property
    def socket(self) -> socket.socket:
        return self._socket
    
    def on_input_ready(self):
        client_socket, address = self._socket.accept()
        remote_port = address[1]

        from pydev_client import PydevClient
        from pipe_client_server import PipeClientServer

        self._dispatcher.add_processor(
                PydevClient(self._dispatcher, self._local_port, str(remote_port), client_socket))
        
        server = self._dispatcher.find_processor(None)
        assert isinstance(server, PipeClientServer)

        server.write(self._local_port, str(remote_port), "start_client\n")

    def close(self):
        self._socket.close()
