# Copyright 2021 Grégoire Payen de La Garanderie. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import abc
import socket
from typing import Any, Union, TextIO


class Processor(abc.ABC):
    @property
    @abc.abstractmethod
    def key(self) -> Any: raise NotImplementedError

    @property
    @abc.abstractmethod
    def socket(self) -> Union[socket.socket, TextIO]: raise NotImplementedError

    @abc.abstractmethod
    def on_input_ready(self) -> None: raise NotImplementedError

    @abc.abstractmethod
    def close(self) -> None: raise NotImplementedError
