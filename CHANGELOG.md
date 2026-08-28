# Changelog
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
