import os
import tempfile
import unittest
from pathlib import Path

from operator_credentials import load_operator_key


class OperatorCredentialsTest(unittest.TestCase):
    def test_explicit_value_has_priority(self):
        self.assertEqual("a" * 32, load_operator_key("a" * 32, Path("/missing")))

    def test_private_file_loads(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "operator.key")
            path.write_text("b" * 64 + "\n", encoding="ascii")
            path.chmod(0o600)
            self.assertEqual("b" * 64, load_operator_key("", path))

    def test_group_readable_file_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "operator.key")
            path.write_text("c" * 64, encoding="ascii")
            path.chmod(0o640)
            with self.assertRaisesRegex(RuntimeError, "0600"):
                load_operator_key("", path)


if __name__ == "__main__":
    unittest.main()
