from unittest import TestCase
from typing import Optional

from .CircularBuffer import CircularBuffer

class TestCircularBuffer(TestCase):
    def test_circular_buffer_no_dedup(self) -> None:
        circular_buffer = CircularBuffer(10)

        circular_buffer.write(["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])

        self.assertEqual(
            ["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"], circular_buffer.read_latest()
        )
        self.assertIsNone(
            circular_buffer.read_latest()
        )

        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])

        self.assertEqual(
            ["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"], circular_buffer.read_latest()
        )
        self.assertIsNone(
            circular_buffer.read_latest()
        )

    def test_circular_buffer_with_dedup(self) -> None:
        def dedupe_event_func(event: Optional[list[str]]) -> Optional[str]:
            if event is None or len(event) < 3:
                return None
            event_parts = event[0].strip(":")
            if len(event_parts) < 2:
                return None

            return f"{event_parts[1]}_{event[1]}"

        circular_buffer = CircularBuffer(10, dedupe_buffer_key=dedupe_event_func)

        circular_buffer.write(["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])

        self.assertEqual(
            ["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"], circular_buffer.read_latest()
        )
        self.assertEqual(
            ["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"], circular_buffer.read_latest()
        )

        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])

        self.assertEqual(
            ["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"], circular_buffer.read_latest()
        )
        self.assertEqual(
            ["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"], circular_buffer.read_latest()
        )
        self.assertIsNone(
            circular_buffer.read_latest()
        )

        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "FRONT_LEFT", "DCBA"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "FRONT_LEFT", "DBCA"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"])
        circular_buffer.write(["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"])

        self.assertEqual(
            ["101:UPDATE_EVENT", "REAR_LEFT", "ABDC"], circular_buffer.read_latest()
        )
        self.assertEqual(
            ["101:UPDATE_EVENT", "REAR_RIGHT", "ABCD"], circular_buffer.read_latest()
        )
        self.assertEqual(
            ["101:UPDATE_EVENT", "FRONT_LEFT", "DBCA"], circular_buffer.read_latest()
        )
        self.assertIsNone(
            circular_buffer.read_latest()
        )

