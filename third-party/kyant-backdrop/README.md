# SleepDown Kyant Backdrop source dependency

Upstream: https://github.com/Kyant0/AndroidLiquidGlass

Baseline: tag `2.0.0`, commit `bebb11a91bd97bf1dabde479f3b332ad9898731f`.
License: Apache-2.0; see LICENSE. Original `com.kyant.backdrop` packages are retained.
The 34 commonMain/androidMain source files were copied from that tag. This module builds only
the Android target; its Compose dependencies resolve against the application's existing 1.11.2
version. No new platform target or upstream catalog implementation is bundled.

## SleepDown changes (2026-09-08)

- `internal/ShapeProvider`: element equality follows shape callback and render options; equivalent
  element updates keep the node-owned outline cache. Fixed-host geometry uses translated outlines.
- `DrawBackdropModifier`: separate draw invalidation from effect invalidation; optional sample-free
  decoration and paused rendering retain the original isolation/clip/blend order.
- `BackdropRenderOptions`: per-consumer enabled predicate, logical bounds and bounded allocation
  padding. Defaults preserve upstream rendering. Fixed geometry cannot be used with export yet.
- `BackdropEffectScope`, `effects/Lens`: logical effect size and origin are independent of host
  allocation. Each consumer still owns its own mutable RuntimeShader and effect chain.
- `highlight/HighlightModifier`, `shadow/*Modifier`: pause before material work; inset highlighting
  uses the original local coordinate system. Native material-layer lifecycle/recording counters.
- `backdrops/LayerBackdropModifier`: producer recording and size counters.
- `layerBackdrop(recordKey)`: opt-in completed-recording fingerprint. Null keeps upstream live
  recording. The node invalidates on source, size, density, font-scale, direction and lifecycle
  changes; callers own content/animation invalidation. Home enables this only for settled wallpaper.
- Course cards opt into decoration recording reuse. Size, density, font scale, layout direction,
  outline and immutable material values form the key; translation alone retains the existing
  highlight/shadow recording. Custom highlight shaders keep recording every draw. Sampling,
  refraction and blur remain live, with the original layer ownership and blend order.
- `BackdropDiagnostics`: optional sink. `RecordedPixelArea` counts recorded pixels; it is not GPU
  allocation, resident-memory, frame-submission or first-visible-frame evidence.

`patches/kyant-backdrop-2.0.0-sleepdown.patch` records changes to upstream sources. The Android-only
Gradle adapter and new extension files live here. Compare/rebase against the exact tag above;
do not substitute a newer binary without repeating equivalence and lifecycle validation.

## Candidate limitations

Fixed Morph and retained-node occlusion are diagnostics-only until phone/tablet visual and GPU
validation. Release does not enable either candidate through Gradle experiment properties.
Fixed geometry callers supply a finite host envelope and enough padding for the complete effect
curve, preserve the local shape, and release the envelope at Open. Mutable shader instances must
never be shared across consumers. Unsupported custom export is rejected, not silently shifted.

## Host regression tests

`SharedBlurBackdrop` is a SleepDown implementation of the shared wallpaper prefix approach
observed in NexioSchedule commit 77b78f24741d513447715f40b9892228b1cedef8 (master, not release
v1.4.8-0903). The current prefix uses 0.5 source scale and proportionally scaled blur, retaining
the original vibrancy-before-blur order;
cards retain their own lens, highlight and shadows. The recorder node owns one layer and releases
it on detach. No Nexio source text is vendored. Nonmatching consumers keep the original source.

The shared layer keeps its RenderEffect attached: recording drawLayer is a display-list reference,
not a pixel bake. Sampling applies inverse consumer transform, full-resolution source offset, then
texture upscaling; placing texture upscaling before offset doubles the displacement at 0.5 scale.

Course cards now use node-internal sampling buffers, following Nexio's full-size layout approach.
`BackdropRenderOptions.sampleScale` scales only the sampling buffer and effect density/geometry;
source coordinates, clipping and decorations stay at full resolution. Fixed Morph allocations and
exported backdrops retain scale 1. No scroll-driven material culling is introduced. The host geometry
test checks proportional dp conversion, stable updates and restoration to full resolution.

`testAndroidHostTest` checks completed recording reuse, unkeyed dynamic frames, content/geometry
changes and lifecycle reset. These tests do not measure GPU execution or prove pixel equivalence.
