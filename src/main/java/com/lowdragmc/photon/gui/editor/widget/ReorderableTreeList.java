package com.lowdragmc.photon.gui.editor.widget;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.util.ITreeNode;

/**
 * Compatibility name for Photon integrations written before LDLib2 exposed TreeList reordering.
 *
 * <p>All drag, drop, selection and overlay behavior now lives in LDLib2's TreeList. Keeping this
 * thin subtype preserves the old Photon class name while inheriting the current API and avoids
 * two independent drag implementations competing for the same UI events.</p>
 */
@Deprecated
public class ReorderableTreeList<NODE extends ITreeNode<?, ?>> extends TreeList<NODE> {
}
