# Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

from dispatcher import Dispatcher
from pipe_client_server import PipeClientServer
from pydev_server_monitor import PydevServerMonitor
import sys
import subprocess
import os


import logging

is_local = len(sys.argv) > 1

handler = logging.StreamHandler(sys.stderr)
handler.setLevel(logging.DEBUG)

format_header = "local" if is_local else "remote"
formatter = logging.Formatter('%(asctime)s - '+format_header+' %(name)s - %(levelname)s - %(message)s')
handler.setFormatter(formatter)

logger = logging.getLogger()
logger.addHandler(handler)
logger.setLevel(logging.DEBUG)


if is_local:
    #Local connection worker.
    #
    #Start the child connection (the remote), establish the pipe between the parent and child process,
    #then add a monitor for the local Pydev server.
    local_port = sys.argv[1]
    worker_command = sys.argv[2:]

    child = subprocess.Popen(worker_command, stdin=subprocess.PIPE, stdout=subprocess.PIPE)

    dispatcher = Dispatcher(auto_stop=True)
    dispatcher.add_processor(PipeClientServer(dispatcher, child.stdout, child.stdin))

    server_monitor = PydevServerMonitor(dispatcher, local_port)
    if server_monitor.is_socket_alive():
        dispatcher.add_server_monitor(server_monitor)
else:
    # Remote connection worker.
    #
    # Establish the pipe between the parent and child process.
    dispatcher = Dispatcher(auto_stop=False)
    dispatcher.add_processor(PipeClientServer(dispatcher, sys.stdin, sys.stdout))
    child = None

# Finally, start the main loop
dispatcher.dispatch_loop()

if child is not None:
    child.terminate()
    child.wait()
