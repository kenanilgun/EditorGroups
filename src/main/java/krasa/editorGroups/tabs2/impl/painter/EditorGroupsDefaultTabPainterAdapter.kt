package krasa.editorGroups.tabs2.impl.painter

import krasa.editorGroups.tabs2.EditorGroupsTabsPosition
import krasa.editorGroups.tabs2.impl.KrTabsImpl
import krasa.editorGroups.tabs2.label.EditorGroupTabLabel
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

/** Holds a tabPainter to paint the tab background. */
class EditorGroupsDefaultTabPainterAdapter : EditorGroupsTabPainterAdapter {
  override val tabPainter: EditorGroupsTabPainter
    get() = EditorGroupsTabPainter.DEFAULT

  private val magicOffset = 1

  override fun paintBackground(label: EditorGroupTabLabel, g: Graphics, tabs: KrTabsImpl) {
    val info = label.info
    val isSelected = info == tabs.selectedInfo
    val isHovered = tabs.isHoveredTab(label)

    val rect = Rectangle(0, 0, label.width, label.height)
    val g2d = g as Graphics2D

    when {
      isSelected -> tabPainter.paintSelectedTab(
        position = tabs.position,
        g = g2d,
        rect = rect,
        borderThickness = tabs.borderThickness,
        tabColor = info.tabColor,
        active = tabs.isActiveTabs(info),
        hovered = isHovered
      )

      else       -> {
        if (isHovered && tabs.tabsPosition == EditorGroupsTabsPosition.TOP) rect.height -= magicOffset
        if (isHovered && tabs.tabsPosition == EditorGroupsTabsPosition.LEFT) rect.width -= magicOffset

        tabPainter.paintTab(
          position = tabs.position,
          g = g2d,
          rect = rect,
          borderThickness = tabs.borderThickness,
          tabColor = info.tabColor,
          active = tabs.isActiveTabs(info),
          hovered = isHovered
        )
      }
    }
  }
}
