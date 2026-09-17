import unittest

import cv2
import numpy as np

import app


class SegmentationTest(unittest.TestCase):
    def test_cell_borders_are_not_treated_as_an_icon(self):
        image = np.full((90, 130, 3), 255, dtype=np.uint8)
        cv2.rectangle(image, (0, 0), (129, 89), (0, 0, 0), 3)

        normalized = app.normalized_icon(image)

        self.assertEqual(0, cv2.countNonZero(normalized))

    def test_detects_workflow_time_column_border(self):
        image = np.full((240, 400, 3), 255, dtype=np.uint8)
        for x in (40, 280, 360):
            cv2.line(image, (x, 30), (x, 210), (0, 0, 0), 2)

        x2, used_fallback = app.detect_workflow_right_border(image, 0.1, 0.9, 0.1, 0.9)

        self.assertAlmostEqual(0.7, x2, places=2)
        self.assertFalse(used_fallback)

    def test_workflow_column_border_falls_back_to_outer_bound(self):
        image = np.full((200, 300, 3), 255, dtype=np.uint8)

        x2, used_fallback = app.detect_workflow_right_border(image, 0.1, 0.9, 0.1, 0.9)

        self.assertEqual(0.9, x2)
        self.assertTrue(used_fallback)

    def test_detects_dynamic_row_count_from_black_borders(self):
        image = np.full((240, 300, 3), 255, dtype=np.uint8)
        for y in (40, 80, 120, 160, 200):
            cv2.line(image, (30, y), (270, y), (0, 0, 0), 2)

        bounds, detection = app.detect_row_bounds(image, 0.1, 0.9, 0.1, 0.9, 14)

        self.assertEqual(4, len(bounds))
        self.assertEqual(5, detection["detectedBorderCount"])
        self.assertFalse(detection["usedFallback"])

    def test_falls_back_only_when_borders_cannot_be_detected(self):
        image = np.full((200, 200, 3), 255, dtype=np.uint8)

        bounds, detection = app.detect_row_bounds(image, 0.1, 0.9, 0.1, 0.9, 3)

        self.assertEqual(3, len(bounds))
        self.assertTrue(detection["usedFallback"])

    def test_plus_text_creates_ordered_subprocesses_and_keeps_cell(self):
        image = np.full((120, 240, 3), 255, dtype=np.uint8)
        cv2.line(image, (0, 20), (239, 20), (0, 0, 0), 2)
        cv2.line(image, (0, 100), (239, 100), (0, 0, 0), 2)
        lines = [(0.2, 0.5, "冲切+贴合+")]

        result = app.workflow_rows(image, lines, "PANEL", 0, 1, 0.1, 0.9, 1)
        step = result["steps"][0]

        self.assertTrue(step["isComposite"])
        self.assertEqual("COMPOSITE", step["processStructure"])
        self.assertFalse(step["classificationConfirmed"])
        self.assertEqual(["冲切", "贴合", None], [item["text"] for item in step["components"]])
        self.assertEqual([1, 2, 3], [item["order"] for item in step["components"]])
        self.assertTrue(all(item["perceptualHash"] for item in step["components"]))
        self.assertTrue(step["cellImageBase64"])

    def test_empty_cells_are_kept_for_manual_review(self):
        image = np.full((120, 240, 3), 255, dtype=np.uint8)
        cv2.line(image, (0, 20), (239, 20), (0, 0, 0), 2)
        cv2.line(image, (0, 100), (239, 100), (0, 0, 0), 2)

        result = app.workflow_rows(image, [], "PANEL", 0, 1, 0.1, 0.9, 1)

        self.assertEqual(1, len(result["steps"]))
        self.assertEqual("EMPTY", result["steps"][0]["contentType"])
        self.assertEqual("EMPTY", result["steps"][0]["processStructure"])

    def test_icon_process_and_right_side_remark_are_separated_by_text_order(self):
        image = np.full((120, 240, 3), 255, dtype=np.uint8)
        cv2.line(image, (0, 20), (239, 20), (0, 0, 0), 2)
        cv2.line(image, (0, 100), (239, 100), (0, 0, 0), 2)
        lines = [(0.2, 0.5, "□"), (0.6, 0.5, "一车一模")]

        result = app.workflow_rows(image, lines, "PANEL", 0, 1, 0.1, 0.9, 1)
        step = result["steps"][0]

        self.assertEqual("□", step["rawText"])
        self.assertEqual("一车一模", step["operationRemark"])

    def test_thin_icon_is_not_downgraded_to_text_only(self):
        image = np.full((120, 240, 3), 255, dtype=np.uint8)
        cv2.line(image, (0, 20), (239, 20), (0, 0, 0), 2)
        cv2.line(image, (0, 100), (239, 100), (0, 0, 0), 2)
        # 模拟去边框后只剩少量有效像素的细线图标。
        cv2.line(image, (30, 55), (30, 65), (0, 0, 0), 1)
        lines = [(0.2, 0.5, "田")]
        result = app.workflow_rows(image, lines, "PANEL", 0, 1, 0.1, 0.9, 1)
        self.assertIn(result["steps"][0]["contentType"], ("ICON_ONLY", "MIXED"))

    def test_composite_components_keep_full_segment_and_shape_direction(self):
        image = np.full((120, 240, 3), 255, dtype=np.uint8)
        cv2.line(image, (0, 20), (239, 20), (0, 0, 0), 2)
        cv2.line(image, (0, 100), (239, 100), (0, 0, 0), 2)
        cv2.drawContours(image, [np.array([[[35, 75]], [[55, 75]], [[45, 45]]])], -1, (0, 0, 0), 2)
        cv2.drawContours(image, [np.array([[[155, 45]], [[175, 45]], [[165, 75]]])], -1, (0, 0, 0), 2)
        components = app.split_composite_components(image[20:100], ["面板", "下线跳"])

        self.assertEqual(2, len(components))
        self.assertEqual("UP", components[0]["shapeDirection"])
        self.assertEqual("DOWN", components[1]["shapeDirection"])
        self.assertTrue(all(component["imageBase64"] for component in components))

    def test_step_keeps_raw_ocr_text_separate(self):
        image = np.full((120, 240, 3), 255, dtype=np.uint8)
        cv2.line(image, (0, 20), (239, 20), (0, 0, 0), 2)
        cv2.line(image, (0, 100), (239, 100), (0, 0, 0), 2)
        result = app.workflow_rows(image, [(0.2, 0.5, "中")], "PANEL", 0, 1, 0.1, 0.9, 1)
        step = result["steps"][0]

        self.assertEqual("中", step["ocrRawText"])
        self.assertEqual("中", step["recognizedText"])


if __name__ == "__main__":
    unittest.main()
