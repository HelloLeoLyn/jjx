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
        self.assertTrue(step["cellImageBase64"])

    def test_empty_cells_are_kept_for_manual_review(self):
        image = np.full((120, 240, 3), 255, dtype=np.uint8)
        cv2.line(image, (0, 20), (239, 20), (0, 0, 0), 2)
        cv2.line(image, (0, 100), (239, 100), (0, 0, 0), 2)

        result = app.workflow_rows(image, [], "PANEL", 0, 1, 0.1, 0.9, 1)

        self.assertEqual(1, len(result["steps"]))
        self.assertEqual("EMPTY", result["steps"][0]["contentType"])
        self.assertEqual("EMPTY", result["steps"][0]["processStructure"])


if __name__ == "__main__":
    unittest.main()
