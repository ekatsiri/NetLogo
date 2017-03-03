// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.workspace;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import scala.collection.mutable.WeakHashMap;
import org.nlogo.agent.Agent;
import org.nlogo.api.*;
import org.nlogo.core.CompilerException;
import org.nlogo.core.Femto;
import org.nlogo.core.FileModeJ;
import org.nlogo.core.File;
import org.nlogo.core.CompilerException;
import org.nlogo.core.Token;
import org.nlogo.core.TokenType;
import org.nlogo.core.UpdateMode;
import org.nlogo.core.UpdateModeJ;
import org.nlogo.agent.ImporterJ;
import org.nlogo.nvm.Activation;
import org.nlogo.nvm.Command;
import org.nlogo.nvm.EditorWorkspace;
import org.nlogo.nvm.FileManager;
import org.nlogo.nvm.Job;
import org.nlogo.nvm.JobManagerInterface;
import org.nlogo.nvm.LoggingWorkspace;
import org.nlogo.nvm.MutableLong;
import org.nlogo.nvm.PresentationCompilerInterface;
import org.nlogo.nvm.Procedure;
import org.nlogo.nvm.Workspace;

public abstract strictfp class AbstractWorkspace
    implements Workspace,
    EditorWorkspace,
    ExtendableWorkspace,
    LoggingWorkspace,
    org.nlogo.api.LogoThunkFactory,
    org.nlogo.api.HubNetWorkspaceInterface {

  /// globals
  /// (some of these probably should be changed not to be public - ST 12/11/01)

  public final org.nlogo.agent.World _world;

  protected final ExtensionManager extensionManager;

  public abstract WeakHashMap<Job, WeakHashMap<Agent, WeakHashMap<Command, MutableLong>>> lastRunTimes();

  //public final WorldLoader worldLoader ;

  /// startup

  protected AbstractWorkspace(org.nlogo.agent.World world) {
    this._world = world;
    world.compiler_$eq(this);
    extensionManager = new ExtensionManager(this, new JarLoader(this));
  }

  public org.nlogo.workspace.ExtensionManager getExtensionManager() {
    return extensionManager;
  }

  public boolean isExtensionName(String name) {
    return extensionManager.isExtensionName(name);
  }

  public void importExtensionData(String name, List<String[]> data, org.nlogo.api.ImportErrorHandler handler)
      throws org.nlogo.api.ExtensionException {
    extensionManager.importExtensionData(name, data, handler);
  }

  /**
   * Shuts down the background thread associated with this workspace,
   * allowing resources to be freed.
   */
  public void dispose()
      throws InterruptedException {
    getExtensionManager().reset();
  }

  /**
   * Displays a warning to the user, and determine whether to continue.
   * The default (non-GUI) implementation is to print the warning and
   * always continue.
   */
  public boolean warningMessage(String message) {
    System.err.println();
    System.err.println("WARNING: " + message);
    System.err.println();

    // always continue.
    return true;
  }

  /// isApp/isApplet

  // Note that if using the embedding API, both isApp and isApplet are false.

  private static boolean isApp = false;

  public static boolean isApp() {
    return isApp;
  }

  public static void isApp(boolean isApp) {
    AbstractWorkspace.isApp = isApp;
  }

  private static boolean isApplet = true;

  public static boolean isApplet() {
    return isApplet;
  }

  public static void isApplet(boolean isApplet) {
    AbstractWorkspace.isApplet = isApplet;
  }

  /// hubnet



  public org.nlogo.api.WorldPropertiesInterface getPropertiesInterface() {
    return null;
  }

  /// model name utilities

  // for 4.1 we have too much fragile, difficult-to-understand,
  // under-tested code involving URLs -- we can't get rid of our
  // uses of toURL() until 4.2, the risk of breakage is too high.
  // so for now, at least we make this a separate method so the
  // SuppressWarnings annotation is narrowly targeted. - ST 12/7/09
  @SuppressWarnings("deprecation")
  public static java.net.URL toURL(java.io.File file)
      throws java.net.MalformedURLException {
    return file.toURL();
  }

  public abstract scala.collection.immutable.ListMap<String, Procedure> procedures();
  public abstract void setProcedures(scala.collection.immutable.ListMap<String, Procedure> procedures);

  public abstract void init();

  @Override
  public abstract PresentationCompilerInterface compiler();

  public abstract AggregateManagerInterface aggregateManager();

  /// methods that may be called from the job thread by prims

  public abstract void joinForeverButtons(org.nlogo.agent.Agent agent);

  public abstract void addJobFromJobThread(org.nlogo.nvm.Job job);

  public abstract void magicOpen(String name);

  /// misc

  // we shouldn't need "Workspace." lampsvn.epfl.ch/trac/scala/ticket/1409 - ST 4/6/09
  private UpdateMode updateMode = UpdateModeJ.CONTINUOUS();

  public UpdateMode updateMode() {
    return updateMode;
  }

  public void updateMode(UpdateMode updateMode) {
    this.updateMode = updateMode;
  }

  // called from an "other" thread (neither event thread nor job thread)
  public abstract void open(String path)
      throws java.io.IOException, CompilerException, LogoException;

  public abstract void openString(String modelContents)
      throws CompilerException, LogoException;

  public void halt() {
    _world.displayOn(true);
  }

  // called by _display from job thread
  public abstract void requestDisplayUpdate(boolean force);

  // called when the engine comes up for air
  public abstract void breathe();

  public void breathe(org.nlogo.nvm.Context context) {
    breathe();
  }

  /// output

  // called from job thread - ST 10/1/03
  protected abstract void sendOutput(org.nlogo.agent.OutputObject oo,
                                     boolean toOutputArea)
      throws LogoException;

  /// importing

  public void setOutputAreaContents(String text) {
    try {
      clearOutput();
      if (text.length() > 0) {
        sendOutput(new org.nlogo.agent.OutputObject(
            "", text, false, false), true);
      }
    } catch (LogoException e) {
      org.nlogo.api.Exceptions.handle(e);
    }
  }

  public abstract void clearDrawing();

  protected abstract class FileImporter {
    public String filename;

    FileImporter(String filename) {
      this.filename = filename;
    }

    public abstract void doImport(org.nlogo.core.File reader)
        throws java.io.IOException;
  }

  public abstract void clearAll();

  protected abstract org.nlogo.agent.ImporterJ.ErrorHandler importerErrorHandler();

  public void importWorld(String filename)
      throws java.io.IOException {
    // we need to clearAll before we import in case
    // extensions are hanging on to old data. ev 4/10/09
    clearAll();
    doImport
        (new BufferedReaderImporter(filename) {
          @Override
          public void doImport(java.io.BufferedReader reader)
              throws java.io.IOException {
            _world.importWorld
                (importerErrorHandler(), AbstractWorkspace.this,
                    stringReader(), reader);
          }
        });
  }

  public void importWorld(java.io.Reader reader)
      throws java.io.IOException {
    // we need to clearAll before we import in case
    // extensions are hanging on to old data. ev 4/10/09
    clearAll();
    _world.importWorld
        (importerErrorHandler(), AbstractWorkspace.this,
            stringReader(), new java.io.BufferedReader(reader));
  }

  private final ImporterJ.StringReader stringReader() {
    return new ImporterJ.StringReader() {
      public Object readFromString(String s)
          throws ImporterJ.StringReaderException {
        try {
          return compiler().readFromString(s, _world, extensionManager);
        } catch (CompilerException ex) {
          throw new ImporterJ.StringReaderException
              (ex.getMessage());
        }
      }
    };
  }

  public void importDrawing(String filename)
      throws java.io.IOException {
    doImport
        (new FileImporter(filename) {
          @Override
          public void doImport(org.nlogo.core.File file)
              throws java.io.IOException {

            importDrawing(file);
          }
        });
  }

  protected abstract void importDrawing(org.nlogo.core.File file)
      throws java.io.IOException;

  // overridden in subclasses - ST 9/8/03, 3/1/11
  public void doImport(BufferedReaderImporter importer)
      throws java.io.IOException {
    org.nlogo.core.File file = new org.nlogo.api.LocalFile(importer.filename());
    try {
      file.open(org.nlogo.core.FileModeJ.READ());
      importer.doImport(file.reader());
    } finally {
      try {
        file.close(false);
      } catch (java.io.IOException ex2) {
        org.nlogo.api.Exceptions.ignore(ex2);
      }
    }
  }


  // protected because GUIWorkspace will override - ST 9/8/03
  protected void doImport(FileImporter importer)
      throws java.io.IOException {
    final org.nlogo.core.File newFile;

    if (AbstractWorkspace.isApplet()) {
      newFile = new org.nlogo.api.RemoteFile(importer.filename);
    } else {
      newFile = new org.nlogo.api.LocalFile(importer.filename);
    }

    importer.doImport(newFile);
  }

  /// exporting

  public String guessExportName(String defaultName) {
    String modelName = getModelFileName();
    int index;

    if (modelName == null) {
      return defaultName;
    }

    index = modelName.lastIndexOf(".nlogo");
    if (index > -1) {
      modelName = modelName.substring(0, index);
    }

    return modelName + " " + defaultName;
  }

  /// BehaviorSpace

  public org.nlogo.api.MersenneTwisterFast auxRNG() {
    return _world.auxRNG();
  }

  public org.nlogo.api.MersenneTwisterFast mainRNG() {
    return _world.mainRNG();
  }
}
