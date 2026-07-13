import pathlib
import struct
import tempfile
import unittest

import update_ut1


class DomainListPublisherTest(unittest.TestCase):
    def test_normalize_domain_rejects_non_domain_input(self) -> None:
        self.assertEqual("example.com", update_ut1.normalize_domain("WWW.Example.com."))
        self.assertIsNone(update_ut1.normalize_domain("0.0.0.0 example.com"))
        self.assertIsNone(update_ut1.normalize_domain("localhost"))
        self.assertIsNone(update_ut1.normalize_domain("example.com/path"))

    def test_merge_and_subtract_keep_real_unique_contributions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            primary = self._write(root / "primary", "alpha.example\nshared.example\n")
            secondary = self._write(root / "secondary", "beta.example\nshared.example\n")

            combined = update_ut1.merge_sorted_files((primary, secondary), root / "combined")
            contribution = update_ut1.subtract_sorted_file(secondary, primary, root / "contribution")

            self.assertEqual(["alpha.example", "beta.example", "shared.example"], combined.read_text().splitlines())
            self.assertEqual(["beta.example"], contribution.read_text().splitlines())

    def test_mixed_category_drops_domains_already_in_adult_alias(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            adult = self._write(root / "adult", "adult.example\nshared.example\n")
            mixed = self._write(root / "mixed", "mixed.example\nshared.example\n")

            unique_mixed = update_ut1.subtract_sorted_file(mixed, adult, root / "unique-mixed")

            self.assertEqual(["mixed.example"], unique_mixed.read_text().splitlines())

    def test_current_bundle_has_extensible_category_table(self) -> None:
        source = {
            "categories": {
                "adult": {"bits": bytearray(128), "exact": bytes(8), "count": 1, "bit_count": 1024},
                "gambling": {"bits": bytearray(128), "exact": bytes(8), "count": 1, "bit_count": 1024},
            },
            "educational_exceptions": [],
            "canary_included": False,
        }

        encoded = update_ut1.encode_bundle(42, source)
        version, format_version, hashes, categories, exceptions, canaries = struct.unpack(
            ">qiiiii", encoded[8:36]
        )

        self.assertEqual((42, 3, 7, 2, 0, 0), (version, format_version, hashes, categories, exceptions, canaries))

    @staticmethod
    def _write(path: pathlib.Path, content: str) -> pathlib.Path:
        path.write_text(content, encoding="ascii")
        return path


if __name__ == "__main__":
    unittest.main()
