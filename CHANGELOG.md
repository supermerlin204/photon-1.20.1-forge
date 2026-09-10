# Changelog
## Forge dependency correction
* Removed the Kotlin for Forge build dependency, repository and mandatory mod requirement.
* Uses the revised LDLib2 `2.2.39.a+forge.1.20.1` and KilaGraph `20.1.0.14` JARs without
  the erroneous Kotlin for Forge requirement. Their version numbers are unchanged; replace older JARs.

## v2.2.6.a — Forge 1.20.1 upstream sync
* Ported upstream `b288d1c..609a975` (v2.2.4 to v2.2.6.a), retaining Forge 1.20.1 / Java 17.
* Updated local dependencies to LDLib2 `2.2.39.a+forge.1.20.1` and KilaGraph `20.1.0.14`.
* Added glTF/GLB geometry loading, supplied/generated mesh tangents, and FX Pack model collection.
* Added tangent-aware shader variants and corrected object-space Transform / View Direction nodes.
* Added `FXSceneOptions` for embedded previews and fixed asset-browser creation menus.
* Retained the port's shader preview fixes, material provider editing, whole-FX rotation and test item.
* Adapted billboard tangent handedness to Forge 1.20.1's negative-Z particle normals.
* Included upstream glTF parser and mesh tangent unit tests. In-client UI scenarios remain excluded
  from production builds because the local dependency does not provide the optional test harness.

## Forge 1.20.1 backport maintenance
* Updated the bundled local LDLib2 dependency to the rebuilt 2.2.37 Forge backport.
* Reused LDLib2's native `TreeList` drag-reorder implementation. `ReorderableTreeList` remains as
  a compatibility subtype, while `FXHierarchyView` now consumes `TreeList.ReorderRequest` directly.
* The Forge editor path now shares LDLib2's HDRColor accessor/configurator, custom translucent
  particle queue classification, cached-scene rendering and resource-browser compatibility APIs.

## v2.2.4
* Cached the FX listing and dropped it on resource reload
* Fixed a failed shader compile being retried every frame
* Added project icon
* Fixed sun bloom
