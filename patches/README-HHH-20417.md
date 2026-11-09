# HHH-20417 focused maintainer-follow-up validation

Exact base: b2b3cb99d1a72024a804d8d58de966f4a2c1cb7c

- custom-persister-red.patch SHA-256: b0726be9479361f32b25457a7d73e6051b1c8a910c7c9229c488e73d7625e2dd
- maintainer-followup.patch SHA-256: 9fbd15f5cee3b38abfdab900ca1186d3734da6bbd9e4f5cfe0435df6f68a75f5

These patches are staged for focused H2/JDK 25 checks using the existing publication verification workflow. The test-only patch must demonstrate the optimistic custom-persister regression before the combined candidate is tested. No Spanner baseline or emulator is part of these checks. This staging branch is separate from the official PR branch.
