"""Owner of fire-and-forget tasks: keeps references (no garbage collection mid-run) and cancels them on shutdown."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Coroutine

logger = logging.getLogger(__name__)


class BackgroundTasks:
    def __init__(self) -> None:
        self._tasks: set[asyncio.Task[None]] = set()

    def spawn(self, coroutine: Coroutine[object, object, None], *, name: str) -> asyncio.Task[None]:
        task = asyncio.create_task(coroutine, name=name)
        self._tasks.add(task)
        task.add_done_callback(self._forget)
        return task

    def _forget(self, task: asyncio.Task[None]) -> None:
        self._tasks.discard(task)
        if not task.cancelled() and task.exception() is not None:
            logger.error("Hintergrundaufgabe %s fehlgeschlagen", task.get_name(), exc_info=task.exception())

    async def shutdown(self, timeout_seconds: float) -> None:
        tasks = list(self._tasks)
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.wait(tasks, timeout=timeout_seconds)

    async def wait_idle(self) -> None:
        """Wait until all currently running tasks are done (used by tests and graceful flows)."""
        while self._tasks:
            await asyncio.wait(list(self._tasks))
