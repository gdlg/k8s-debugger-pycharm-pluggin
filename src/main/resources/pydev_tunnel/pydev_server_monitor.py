# Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import logging
import socket
from typing import Any

from dispatcher import Dispatcher
from pipe_client_server import PipeClientServer

logger = logging.getLogger("pydev_server_monitor")


class PydevServerMonitor:
    """
    Monitor a local Pydev debug server.

    When initialised, this class sends a message to the remote to create a corresponding listening server.
    When the Pydev server stops, this class detects that the server is no longer running
    and also close the remote server.
    """
    def __init__(self, dispatcher: Dispatcher, local_port: str):
        logger.debug(f"start monitoring the port {local_port}")
        self._dispatcher = dispatcher
        self._local_port = local_port
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        #self._socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        self._is_terminated = False

        if self.is_socket_alive():
            server = self._dispatcher.find_processor(None)
            assert isinstance(server, PipeClientServer)

            logger.debug(f"ask remote to start new server on port {local_port}")
            server.write(local_port, "", "start_server\n")
        else:
            logger.debug(f"server is not running")
            self._is_terminated = True

    @property
    def key(self) -> Any:
        return self._local_port
    
    def is_socket_alive(self) -> bool:
        if self._is_terminated:
            return False

        try:
            self._socket.bind(('', int(self._local_port)))
        except Exception:
            return True

        try:
            self._socket.shutdown(2)
        except:
            pass

        return False

    def monitor(self):
        if not self.is_socket_alive() and not self._is_terminated:
            server = self._dispatcher.find_processor(None)
            assert isinstance(server, PipeClientServer)

            logger.debug(f"ask remote to stop server on port {self._local_port}")
            server.write(self._local_port, "", "stop_server\n")
            self._dispatcher.remove_server_monitor(self)
            self._is_terminated = True
