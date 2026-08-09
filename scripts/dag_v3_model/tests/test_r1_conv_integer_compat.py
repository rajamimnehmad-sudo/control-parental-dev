import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from r1_conv_integer_compat import rewrite_conv_integer


class R1ConvQdqCompatTest(unittest.TestCase):
    def test_rewrite_preserves_scale_tail_and_uses_integer_valued_float_conv(self):
        import onnx
        from onnx import TensorProto, helper

        quantizer = helper.make_node("DynamicQuantizeLinear", ["x"], ["xq", "xs", "xz"], name="quantizer")
        scale = helper.make_node("Mul", ["xs", "ws"], ["combined"], name="scale")
        integer = helper.make_node("ConvInteger", ["xq", "wq", "xz", "wz"], ["integer"], name="conv", kernel_shape=[1, 1])
        cast = helper.make_node("Cast", ["integer"], ["cast"], name="cast", to=TensorProto.FLOAT)
        output = helper.make_node("Mul", ["cast", "combined"], ["y"], name="output")
        graph = helper.make_graph([quantizer, scale, integer, cast, output], "test", [], [])
        model = helper.make_model(graph)

        rewritten, count = rewrite_conv_integer(model)

        operations = [node.op_type for node in rewritten.graph.node]
        self.assertEqual(1, count)
        self.assertNotIn("ConvInteger", operations)
        self.assertIn("Conv", operations)
        self.assertEqual(5, operations.count("Cast"))
        self.assertEqual(2, operations.count("Sub"))
        self.assertEqual(2, operations.count("Mul"))


if __name__ == "__main__":
    unittest.main()
