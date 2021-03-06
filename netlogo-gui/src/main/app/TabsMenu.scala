// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.app

import java.awt.event.ActionEvent
import javax.swing.{ Action, AbstractAction }

import org.nlogo.swing.UserAction._

object TabsMenu {
  def tabAction(tabManager: AppTabManager, index: Int): Action =
    new AbstractAction() with MenuAction {
      category    = TabsCategory
      rank        = index
      accelerator = KeyBindings.keystroke(('1' + index).toChar, withMenu = true)
      this.putValue(Action.NAME, tabManager.getTitleAtCombinedIndex(index));
      override def actionPerformed(e: ActionEvent) {
        tabManager.setPanelsSelectedIndex(index)
      }
    }

  def tabActions(tabManager: AppTabManager): Seq[Action] = {
    val totalTabCount = tabManager.getTotalTabCount
    for (i <- 0 until totalTabCount) yield tabAction(tabManager, i)
  }

}

class TabsMenu(name: String, initialActions: Seq[Action]) extends org.nlogo.swing.Menu(name) {
  setMnemonic('A')

  initialActions.foreach(offerAction)

  def this(name: String) =
    this(name, Seq())

  def this(name: String, tabManager: AppTabManager) =
    this(name, TabsMenu.tabActions(tabManager))
}
