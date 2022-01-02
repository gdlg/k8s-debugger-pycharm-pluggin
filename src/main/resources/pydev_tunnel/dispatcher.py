# Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import select
import socket
from typing import Any, Dict, Union, TextIO, TYPE_CHECKING, Optional, List


if TYPE_CHECKING:
    from processor import Processor
    from pydev_server_monitor import PydevServerMonitor


class Dispatcher:
    """
    The dispatcher class implements the main loop of the program,
    waiting for new I/O inputs (either from socket or pipe),
    then calling the relevant processor to handle the input.

    It also regularly calls monitors which are used to perform health checks
    on Pydev debug servers. If auto_stop is enabled, the loop exits when the last
    monitor terminates (i.e. no Pydev debug servers are running).
    """
    def __init__(self, auto_stop: bool):
        self._port_to_processors: "Dict[Any, Processor]" = {}
        self._socket_to_processors: Dict[Union[socket.socket, TextIO], Processor] = {}
        self._server_monitors: Dict[Any, PydevServerMonitor] = {}
        self._auto_stop = auto_stop

    def add_processor(self, processor: "Processor"):
        self._port_to_processors[processor.key] = processor
        self._socket_to_processors[processor.socket] = processor

    def remove_processor(self, processor: "Processor"):
        try:
            del self._port_to_processors[processor.key]
            del self._socket_to_processors[processor.socket]
        except KeyError:
            pass
        processor.close()

    def add_server_monitor(self, monitor: "PydevServerMonitor"):
        self._server_monitors[monitor.key] = monitor

    def remove_server_monitor(self, monitor: "PydevServerMonitor"):
        try:
            del self._server_monitors[monitor.key]
        except KeyError:
            pass

    def find_processor(self, key: Any) -> "Optional[Processor]":
        return self._port_to_processors.get(key, None)

    def get_all_processors(self) -> "List[Processor]":
        return list(self._port_to_processors.values())

    def dispatch_loop(self):
        while True:
            inputs = list(self._socket_to_processors.keys())
        
            inputs_ready, _, _ = select.select(inputs, [], [], 1)

            for input_socket in inputs_ready:
                processor = self._socket_to_processors[input_socket]
                processor.on_input_ready()

            for monitor in list(self._server_monitors.values()):
                monitor.monitor()

            if self._auto_stop and len(self._server_monitors) == 0:
                return
  
