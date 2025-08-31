package krasa.editorGroups.tools

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import krasa.editorGroups.EditorGroupManager
import krasa.editorGroups.model.EditorGroup
import krasa.editorGroups.model.SwitchRequest
import krasa.editorGroups.settings.EditorGroupsSettings
import krasa.editorGroups.settings.EditorGroupsSettings.Companion.TOPIC
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class EditorGroupsToolWindowContent(
  private val project: Project,
  private val editorGroupManager: EditorGroupManager
) {
  
  private val tree: Tree
  private val treeModel: DefaultTreeModel
  private val rootNode: DefaultMutableTreeNode
  lateinit var component: JComponent
  
  init {
    rootNode = DefaultMutableTreeNode("Editor Groups")
    treeModel = DefaultTreeModel(rootNode)
    tree = Tree(treeModel)
    
    setupTree()
    setupComponent()
    refreshTree()
    
    // Listen for settings changes
    ApplicationManager.getApplication().messageBus.connect().subscribe(
      TOPIC,
      object : EditorGroupsSettings.SettingsNotifier {
        override fun configChanged(config: EditorGroupsSettings) {
          refreshTree()
        }
      }
    )
    
    // Listen for file editor changes to refresh the tree
    ApplicationManager.getApplication().messageBus.connect().subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          refreshTree()
        }
        
        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          refreshTree()
        }
        
        override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
          refreshTree()
        }
      }
    )
  }
  
  private fun setupTree() {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.cellRenderer = EditorGroupsTreeCellRenderer(project)
    
    // Handle double-click to open file
    tree.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2) {
          val path = tree.getPathForLocation(e.x, e.y)
          if (path != null) {
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is VirtualFile) {
              openFile(userObject)
            }
          }
        }
      }
    })
  }
  
  private fun setupComponent() {
    val scrollPane = JBScrollPane(tree)
    scrollPane.border = BorderFactory.createEmptyBorder()
    
    component = JPanel(BorderLayout())
    component.add(scrollPane, BorderLayout.CENTER)
  }
  
  private fun refreshTree() {
    rootNode.removeAllChildren()
    
    try {
      // Get the currently active file (like TOP panel does)
      val fileEditorManager = FileEditorManager.getInstance(project)
      val currentFile = fileEditorManager.selectedFiles.firstOrNull()
      
      if (currentFile != null) {
        thisLogger().debug("EditorGroupsToolWindow: Refreshing for active file: ${currentFile.name}")
        
        // Get the group for the current active file (same logic as TOP panel)
        val groups = editorGroupManager.getGroups(currentFile)
        thisLogger().debug("EditorGroupsToolWindow: Active file ${currentFile.name} has ${groups.size} groups")
        
        if (groups.isNotEmpty()) {
          // Show the primary group for the active file
          val primaryGroup = groups.first()
          val groupNode = DefaultMutableTreeNode(primaryGroup.title)
          groupNode.userObject = primaryGroup
          
          val links = primaryGroup.getLinks(project)
          for (link in links) {
            val linkFile = link.virtualFile
            if (linkFile != null) {
              val fileNode = DefaultMutableTreeNode(linkFile.name)
              fileNode.userObject = linkFile
              groupNode.add(fileNode)
            }
          }
          
          rootNode.add(groupNode)
        } else {
          // Fallback: show all indexed groups if no groups for current file
          val allGroups = editorGroupManager.allIndexedGroups
          thisLogger().debug("EditorGroupsToolWindow: No groups for active file, showing ${allGroups.size} indexed groups")
          
          for (group in allGroups) {
            val groupNode = DefaultMutableTreeNode(group.title)
            groupNode.userObject = group
            
            val links = group.getLinks(project)
            for (link in links) {
              val file = link.virtualFile
              if (file != null) {
                val fileNode = DefaultMutableTreeNode(file.name)
                fileNode.userObject = file
                groupNode.add(fileNode)
              }
            }
            
            rootNode.add(groupNode)
          }
        }
      } else {
        // No active file, show all indexed groups
        val allGroups = editorGroupManager.allIndexedGroups
        thisLogger().debug("EditorGroupsToolWindow: No active file, showing ${allGroups.size} indexed groups")
        
        for (group in allGroups) {
          val groupNode = DefaultMutableTreeNode(group.title)
          groupNode.userObject = group
          
          val links = group.getLinks(project)
          for (link in links) {
            val file = link.virtualFile
            if (file != null) {
              val fileNode = DefaultMutableTreeNode(file.name)
              fileNode.userObject = file
              groupNode.add(fileNode)
            }
          }
          
          rootNode.add(groupNode)
        }
      }
    } catch (e: Exception) {
      thisLogger().warn("EditorGroupsToolWindow: Error refreshing tree", e)
      val errorNode = DefaultMutableTreeNode("Error: ${e.message}")
      rootNode.add(errorNode)
    }
    
    treeModel.reload()
    TreeUtil.expandAll(tree)
  }
  
  private fun openFile(file: VirtualFile) {
    val groups = editorGroupManager.getGroups(file)
    val group = groups.firstOrNull() ?: return
    
    val switchRequest = SwitchRequest(group, file)
    editorGroupManager.startSwitching(switchRequest)
    
    // Use the same logic as TOP panel - check reuseCurrentTab setting
    val fileEditorManager = FileEditorManager.getInstance(project)
    val currentFile = fileEditorManager.selectedFiles.firstOrNull()
    val reuseCurrentTab = EditorGroupsSettings.instance.reuseCurrentTab
    
    // If it's the same file, just focus it (like TOP panel does)
    if (currentFile == file) {
      thisLogger().debug("EditorGroupsToolWindow: Same file selected, just focusing")
      return
    }
    
    // Check if file is already open in any editor
    val openFiles = fileEditorManager.openFiles
    if (openFiles.contains(file)) {
      thisLogger().debug("EditorGroupsToolWindow: File already open, switching to it")
      // File is already open, just switch to it
      fileEditorManager.openFile(file, true)
    } else {
      thisLogger().debug("EditorGroupsToolWindow: Opening new file")
      
      // Open the new file
      fileEditorManager.openFile(file, true)
      
      // If reuseCurrentTab is enabled and we have a current file, close it
      if (reuseCurrentTab && currentFile != null) {
        thisLogger().debug("EditorGroupsToolWindow: Closing previous file due to reuseCurrentTab setting")
        fileEditorManager.closeFile(currentFile)
      }
    }
  }
  
  fun refresh() {
    refreshTree()
  }
}

private class EditorGroupsTreeCellRenderer(private val project: Project) : DefaultTreeCellRenderer() {
  
  override fun getTreeCellRendererComponent(
    tree: JTree,
    value: Any,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ): java.awt.Component {
    super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    
    val node = value as DefaultMutableTreeNode
    val userObject = node.userObject
    
    when (userObject) {
      is EditorGroup -> {
        icon = AllIcons.Nodes.Folder
        text = userObject.title
        toolTipText = "Group: ${userObject.title} (${userObject.size(project)} files)"
      }
      is VirtualFile -> {
        icon = AllIcons.FileTypes.Any_type
        text = userObject.name
        toolTipText = userObject.presentableUrl
      }
      else -> {
        icon = AllIcons.Nodes.Folder
        text = value.toString()
      }
    }
    
    return this
  }
}
