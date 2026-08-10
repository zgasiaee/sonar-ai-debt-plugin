#!/usr/bin/env python3
"""Deterministic unit tests for threshold direction and joint-rule fitting."""

from __future__ import annotations

import unittest

from calibrate_thresholds import fit, predict


class ThresholdCalibrationTest(unittest.TestCase):
    def test_csd_uses_below_threshold_direction(self) -> None:
        rows = [
            ({"similarity": 0.10, "naming_similarity": 0.10, "pattern_similarity": 0.10,
              "structure_similarity": 0.10, "group_id": "a"}, 1),
            ({"similarity": 0.20, "naming_similarity": 0.20, "pattern_similarity": 0.20,
              "structure_similarity": 0.20, "group_id": "b"}, 1),
            ({"similarity": 0.80, "naming_similarity": 0.80, "pattern_similarity": 0.80,
              "structure_similarity": 0.80, "group_id": "c"}, 0),
            ({"similarity": 0.90, "naming_similarity": 0.90, "pattern_similarity": 0.90,
              "structure_similarity": 0.90, "group_id": "d"}, 0),
        ]
        result = fit("CSD", rows, 0.80)
        threshold = result["thresholds"]
        self.assertTrue(predict("CSD", rows[0][0], threshold))
        self.assertFalse(predict("CSD", rows[-1][0], threshold))
        self.assertTrue(result["precision_constraint_met"])

    def test_rlr_joint_or_rule(self) -> None:
        rows = [
            ({"syntax_similarity": 0.95, "behavior_similarity": 0.10, "behavior_context_similarity": 0.10,
              "call_similarity": 0.10, "output_similarity": 0.10, "behavior_available": True, "group_id": "a"}, 1),
            ({"syntax_similarity": 0.10, "behavior_similarity": 0.95, "behavior_context_similarity": 0.95,
              "call_similarity": 0.95, "output_similarity": 0.95, "behavior_available": True, "group_id": "b"}, 1),
            ({"syntax_similarity": 0.20, "behavior_similarity": 0.20, "behavior_context_similarity": 0.20,
              "call_similarity": 0.20, "output_similarity": 0.20, "behavior_available": True, "group_id": "c"}, 0),
            ({"syntax_similarity": 0.30, "behavior_similarity": 0.30, "behavior_context_similarity": 0.30,
              "call_similarity": 0.30, "output_similarity": 0.30, "behavior_available": True, "group_id": "d"}, 0),
        ]
        result = fit("RLR", rows, 0.80)
        predictions = [predict("RLR", row, result["thresholds"]) for row, _ in rows]
        self.assertEqual([True, True, False, False], predictions)

    def test_sii_excludes_redundant_callable_pairs(self) -> None:
        row = {
            "concept_similarity": 1.0, "context_similarity": 1.0, "lexical_similarity": 0.0,
            "excluded_by_rlr": True,
        }
        self.assertFalse(predict("SII", row, {"concept": 0.7, "context": 0.7, "lexical": 0.4}))

    def test_egr_uses_any_selection_trigger(self) -> None:
        thresholds = {"cc": 5, "nesting": 3, "mixed_cc": 4, "flow_kinds": 2}
        nested = {"cyclomatic_complexity": 3, "maximum_nesting": 4, "flow_family_count": 1}
        simple = {"cyclomatic_complexity": 2, "maximum_nesting": 1, "flow_family_count": 1}
        self.assertTrue(predict("EGR", nested, thresholds))
        self.assertFalse(predict("EGR", simple, thresholds))


if __name__ == "__main__":
    unittest.main()
