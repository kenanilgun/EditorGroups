package krasa.editorGroups.tools

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.actions.SwitchGroupAction
import krasa.editorGroups.settings.EditorGroupsSettings
import javax.swing.SwingConstants

class EditorGroupsToolWindowFactory : ToolWindowFactory {
  
  companion object {
    private val toolWindowContents = mutableMapOf<Project, EditorGroupsToolWindowContent>()
    
    fun getToolWindowContent(project: Project): EditorGroupsToolWindowContent? {
      return toolWindowContents[project]
    }
  }
  
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val editorGroupManager = EditorGroupManager.getInstance(project)
    val toolWindowContent = EditorGroupsToolWindowContent(project, editorGroupManager)
    
    // Store reference for external access
    toolWindowContents[project] = toolWindowContent
    
    val content = ContentFactory.getInstance().createContent(toolWindowContent.component, "", false)
    toolWindow.contentManager.addContent(content)
    
    // Add toolbar with Switch Editor Group action
    setupToolWindowToolbar(toolWindow, project)
  }
  
  private fun setupToolWindowToolbar(toolWindow: ToolWindow, project: Project) {
    // Create action group with Switch Editor Group action
    val actionGroup = DefaultActionGroup().apply {
      add(ActionManager.getInstance().getAction(SwitchGroupAction.ID))
    }
    
    // Create toolbar
    val toolbar = ActionManager.getInstance().createActionToolbar(
      "EditorGroupsToolWindow",
      actionGroup,
      true
    )
    toolbar.targetComponent = toolWindow.component
    
    // Add toolbar to tool window
    toolWindow.setAdditionalGearActions(actionGroup)
  }
  
  override fun shouldBeAvailable(project: Project): Boolean {
    // Always available, we'll control visibility dynamically
    return true
  }
}
