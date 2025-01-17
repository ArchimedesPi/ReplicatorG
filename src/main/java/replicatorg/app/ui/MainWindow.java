/*
 Part of the ReplicatorG project - http://www.replicat.org
 Copyright (c) 2008 Zach Smith

 Forked from Arduino: http://www.arduino.cc

 Based on Processing http://www.processing.org
 Copyright (c) 2004-05 Ben Fry and Casey Reas
 Copyright (c) 2001-04 Massachusetts Institute of Technology

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 $Id: MainWindow.java 370 2008-01-19 16:37:19Z mellis $
 */

package replicatorg.app.ui;

import com.apple.mrj.*;
import net.iharder.dnd.FileDrop;
import net.miginfocom.swing.MigLayout;
import replicatorg.app.Base;
import replicatorg.app.Base.InitialOpenBehavior;
import replicatorg.app.MRUList;
import replicatorg.app.gcode.GCodeEnumeration;
import replicatorg.app.gcode.MutableGCodeSource;
import replicatorg.app.syntax.*;
import replicatorg.app.ui.controlpanel.ControlPanelWindow;
import replicatorg.app.ui.modeling.EditingModel;
import replicatorg.app.ui.modeling.PreviewPanel;
import replicatorg.app.ui.onboard.OnboardParametersWindow;
import replicatorg.app.util.PythonUtils;
import replicatorg.app.util.StreamLoggerThread;
import replicatorg.app.util.SwingPythonSelector;
import replicatorg.app.util.serial.Name;
import replicatorg.app.util.serial.Serial;
import replicatorg.drivers.*;
import replicatorg.machine.*;
import replicatorg.machine.model.BuildVolume;
import replicatorg.machine.model.MachineType;
import replicatorg.machine.model.ToolheadAlias;
import replicatorg.model.*;
import replicatorg.plugin.toolpath.ToolpathGenerator;
import replicatorg.plugin.toolpath.ToolpathGenerator.GeneratorEvent;
import replicatorg.plugin.toolpath.ToolpathGeneratorFactory;
import replicatorg.plugin.toolpath.ToolpathGeneratorFactory.ToolpathGeneratorDescriptor;
import replicatorg.plugin.toolpath.ToolpathGeneratorThread;
import replicatorg.plugin.toolpath.miraclegrue.MiracleGrueGenerator;
import replicatorg.plugin.toolpath.miraclegrue.MiracleGruePostProcessor;
import replicatorg.plugin.toolpath.skeinforge.SkeinforgeGenerator;
import replicatorg.plugin.toolpath.skeinforge.SkeinforgePostProcessor;
import replicatorg.uploader.FirmwareUploader;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainWindow extends JFrame implements MRJAboutHandler, MRJQuitHandler,
  MRJPrefsHandler, MRJOpenDocumentHandler,
  MachineListener, ChangeListener,
  ToolpathGenerator.GeneratorListener {
  /**
   *
   */
  private static final long serialVersionUID = 4144538738677712284L;

  static final String WINDOW_TITLE = "ReplicatorG" + " - " + Base.VERSION_NAME;


  static final String MODEL_TAB_KEY = "MODEL";
  static final String GCODE_TAB_KEY = "GCODE";

  Image icon;

  MachineLoader machineLoader;

  public static final KeyStroke WINDOW_CLOSE_KEYSTROKE =
    KeyStroke.getKeyStroke('W', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());

  static final int HANDLE_NEW = 1;

  static final int HANDLE_OPEN = 2;

  static final int HANDLE_QUIT = 3;

  int checkModifiedMode;

  String handleOpenPath;

  boolean handleNewShift;

  PageFormat pageFormat;

  PrinterJob printerJob;

  MainButtonPanel buttons;

  CardLayout cardLayout = new CardLayout();
  JPanel cardPanel = new JPanel(cardLayout);
  EditorHeader header = new EditorHeader(this);
  {
    header.setChangeListener(this);
  }
  MachineStatusPanel machineStatusPanel;

  MessagePanel console;

  JSplitPane splitPane;

  JLabel lineNumberComponent;

  // currently opened program
  public Build build;

  public JEditTextArea textarea;
  public PreviewPanel previewPanel;

  public SimulationThread simulationThread;

  public EstimationThread estimationThread;

  JMenuItem saveMenuItem;
  JMenuItem saveAsMenuItem;
  JMenuItem generateItem;
  JMenuItem stopItem;
  JMenuItem pauseItem;
  JMenuItem controlPanelItem;
  JMenuItem buildMenuItem;
  JMenuItem profilesMenuItem;
  JMenuItem dualstrusionItem;
  JMenuItem combineItem;
//	JMenuItem editDualstartItem;
//	JMenuItem editStartItem;
//	JMenuItem editEndItem;
  JMenu changeToolheadMenu = new JMenu("Swap Toolhead in .gcode");


  JMenu machineMenu;
  MachineMenuListener machineMenuListener;
  SerialMenuListener serialMenuListener;

  public boolean building;
  public boolean simulating;
  public boolean debugging;

  public boolean buildOnComplete = false;

  private boolean preheatMachine = false;

  PreferencesWindow preferences;

  // undo fellers
  JMenuItem undoItem, redoItem;

  protected UndoAction undoAction;
  protected RedoAction redoAction;

  public void updateUndo() {
    undoAction.updateUndoState();
    redoAction.updateRedoState();
  }

  // used internally, and only briefly
  CompoundEdit compoundEdit;

  FindReplace find;

  public Build getBuild() {
    return build;
  }

  public void refreshPreviewPanel() {
    if (previewPanel != null) {
      previewPanel.rebuildScene();
    }
  }

  private PreviewPanel getPreviewPanel() {
    if (previewPanel == null) {
      previewPanel = new PreviewPanel(this);
      cardPanel.add(previewPanel,MODEL_TAB_KEY);
    }
    return previewPanel;
  }

  private MRUList mruList;

  public MainWindow() {
    super(WINDOW_TITLE);
    setLocationByPlatform(true);
    MRJApplicationUtils.registerAboutHandler(this);
    MRJApplicationUtils.registerPrefsHandler(this);
    MRJApplicationUtils.registerQuitHandler(this);
    MRJApplicationUtils.registerOpenDocumentHandler(this);

    PythonUtils.setSelector(new SwingPythonSelector(this));

    machineLoader = Base.getMachineLoader();

    // load up the most recently used files list
    mruList = MRUList.getMRUList();

    // set the window icon
    icon = Base.getImage("images/icon.gif", this);
    setIconImage(icon);

    // add listener to handle window close box hit event
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        handleQuitInternal();
      }
    });

    // don't close the window when clicked, the app will take care
    // of that via the handleQuitInternal() methods
    // http://dev.processing.org/bugs/show_bug.cgi?id=440
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    JMenuBar menubar = new JMenuBar();
    menubar.add(buildFileMenu());
    menubar.add(buildEditMenu());
    menubar.add(buildGCodeMenu());
    menubar.add(buildMachineMenu());
    menubar.add(buildThingiverseMenu());
    menubar.add(buildHelpMenu());

    setJMenuBar(menubar);

    Container pane = getContentPane();
    MigLayout layout = new MigLayout("nocache,fill,flowy,gap 0 0,ins 0");
    pane.setLayout(layout);

    buttons = new MainButtonPanel(this);
    pane.add(buttons,"growx,dock north");

    machineStatusPanel = new MachineStatusPanel();
    pane.add(machineStatusPanel,"growx,dock north");

    pane.add(header,"growx,dock north");

    textarea = new JEditTextArea(new PdeTextAreaDefaults());
    textarea.setRightClickPopup(new TextAreaPopup());
    textarea.setHorizontalOffset(6);

    cardPanel.add(textarea,GCODE_TAB_KEY);
    //cardPanel.add(test)
    console = new MessagePanel(this);
    console.setBorder(null);

    splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cardPanel,
                               console);

    new FileDrop( null, cardPanel, /*dragBorder,*/ new FileDrop.Listener() {
      public void filesDropped( java.io.File[] files ) {
        // for( java.io.File file : files )
        // We can really only handle opening one file, so just try the first one.
        try {
          Base.logger.fine( files[0].getCanonicalPath() + "\n" );
          handleOpen(files[0].getCanonicalPath());
        } catch (IOException e) {
        }
      }   // end filesDropped
    }); // end FileDrop.Listener

    //splitPane.setOneTouchExpandable(true);
    // repaint child panes while resizing: a little heavyweight
    // splitPane.setContinuousLayout(true);
    // if window increases in size, give all of increase to
    // the textarea in the uppper pane
    splitPane.setResizeWeight(0.86);

    // to fix ugliness.. normally macosx java 1.3 puts an
    // ugly white border around this object, so turn it off.
    //splitPane.setBorder(null);

    // the default size on windows is too small and kinda ugly
    int dividerSize = Base.preferences.getInt("editor.divider.size",5);
    if (dividerSize < 5) dividerSize = 5;
    if (dividerSize != 0) {
      splitPane.setDividerSize(dividerSize);
    }

    splitPane.setPreferredSize(new Dimension(600,600));
    pane.add(splitPane,"growx,growy,shrinkx,shrinky");
    pack();

    //		textarea.setTransferHandler(new TransferHandler() {
    //			private static final long serialVersionUID = 2093323078348794384L;
    //
    //			public boolean canImport(JComponent dest, DataFlavor[] flavors) {
    //				// claim that we can import everything
    //				return true;
    //			}
    //
    //			public boolean importData(JComponent src, Transferable transferable) {
    //				DataFlavor[] flavors = transferable.getTransferDataFlavors();
    //
    //				int successful = 0;
    //
    //				for (int i = 0; i < flavors.length; i++) {
    //					try {
    //						// System.out.println(flavors[i]);
    //						// System.out.println(transferable.getTransferData(flavors[i]));
    //						Object stuff = transferable.getTransferData(flavors[i]);
    //						if (!(stuff instanceof java.util.List<?>))
    //							continue;
    //						java.util.List<?> list = (java.util.List<?>) stuff;
    //
    //						for (int j = 0; j < list.size(); j++) {
    //							Object item = list.get(j);
    //							if (item instanceof File) {
    //								File file = (File) item;
    //
    //								// see if this is a .gcode file to be opened
    //								String filename = file.getName();
    //								// FIXME: where did this come from?  Need case insensitivity.
    //								if (filename.endsWith(".gcode") || filename.endsWith(".ngc")) {
    //									handleOpenFile(file);
    //									return true;
    //								}
    //							}
    //						}
    //
    //					} catch (Exception e) {
    //						e.printStackTrace();
    //						return false;
    //					}
    //				}
    //
    //				if (successful == 0) {
    //					error("No files were added to the sketch.");
    //
    //				} else if (successful == 1) {
    //					message("One file added to the sketch.");
    //
    //				} else {
    //					message(successful + " files added to the sketch.");
    //				}
    //				return true;
    //			}
    //		});

    // Have UI elements listen to machine state.
    machineLoader.addMachineListener(this);
    machineLoader.addMachineListener(machineStatusPanel);
    machineLoader.addMachineListener(buttons);
  }

  // ...................................................................

  /**
   * Post-constructor setup for the editor area. Loads the last sketch that
   * was used (if any), and restores other MainWindow settings. The complement to
   * "storePreferences", this is called when the application is first
   * launched.
   */
  public void restorePreferences() {
    if (Base.openedAtStartup != null) {
      handleOpen2(Base.openedAtStartup);
    } else {
      // last sketch that was in use, or used to launch the app
      final String prefName = "replicatorg.initialopenbehavior";
      int ordinal = Base.preferences.getInt(prefName, InitialOpenBehavior.OPEN_LAST.ordinal());
      final InitialOpenBehavior openBehavior = InitialOpenBehavior.values()[ordinal];
      if (openBehavior == InitialOpenBehavior.OPEN_NEW) {
        handleNew2(true);
      } else {
        // Get last path opened; MRU keeps this.
        Iterator<String> i = mruList.iterator();
        if (i.hasNext()) {
          String lastOpened = i.next();
          if (new File(lastOpened).exists()) {
            handleOpen2(lastOpened);
          } else {
            handleNew2(true);
          }
        } else {
          handleNew2(true);
        }
      }
    }

    // read the preferences that are settable in the preferences window
    applyPreferences();
  }

  /**
   * Reset all preferences systemwide. This is a destructive operation and
   * terminates the program, so the user is presented with a confirmation
   * dialog.
   */
  public void resetPreferences() {
    int option = JOptionPane.showConfirmDialog(this,
                 "This will delete all your current preference settings and customizations,\nand immediately exit ReplicatorG. Are you sure?",
                 "Reset preferences and reset?",
                 JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
    if (option == JOptionPane.NO_OPTION) {
      return;
    }
    if (option == JOptionPane.YES_OPTION) {
      Base.resetPreferences();
      System.exit(0);
    }
  }


  /**
   * Read and apply new values from the preferences, either because the app is
   * just starting up, or the user just finished messing with things in the
   * Preferences window.
   */
  public void applyPreferences() {

    textarea.setEditable(true);
    saveMenuItem.setEnabled(true);
    saveAsMenuItem.setEnabled(true);

    TextAreaPainter painter = textarea.getPainter();

    Color color = Base.getColorPref("editor.bgcolor","#ffffff");
    painter.setBackground(color);
    boolean highlight = Base.preferences.getBoolean("editor.linehighlight",true);
    painter.setLineHighlightEnabled(highlight);
    textarea.setCaretVisible(true);

    // apply changes to the font size for the editor
    // TextAreaPainter painter = textarea.getPainter();
    painter.setFont(Base.getFontPref("editor.font","Monospaced,plain,10"));
    // Font font = painter.getFont();
    // textarea.getPainter().setFont(new Font("Courier", Font.PLAIN, 36));
  }

  /**
   * Store preferences about the editor's current state. Called when the
   * application is quitting.
   */
  public void storePreferences() {
    // System.out.println("storing preferences");

    // window location information
    Rectangle bounds = getBounds();
    Base.preferences.putInt("last.window.x", bounds.x);
    Base.preferences.putInt("last.window.y", bounds.y);
    Base.preferences.putInt("last.window.width", bounds.width);
    Base.preferences.putInt("last.window.height", bounds.height);

    // last sketch that was in use
    // Preferences.set("last.sketch.name", sketchName);
    // Preferences.set("last.sketch.name", sketch.name);
    if (build != null) {
      String lastPath = build.getMainFilePath();
      if (lastPath != null) {
        Base.preferences.put("last.sketch.path", build.getMainFilePath());
      }
    }

    // location for the console/editor area divider
    int location = splitPane.getDividerLocation();
    Base.preferences.putInt("last.divider.location", location);

    try {
      Base.preferences.flush();
    } catch (BackingStoreException bse) {
      // Not much we can do about this, so let it go with a stack
      // trace.
      bse.printStackTrace();
    }
  }

  public void handleEditProfiles() {
    ToolpathGenerator generator = ToolpathGeneratorFactory.createSelectedGenerator();
    if( generator != null)
      generator.editProfiles(this);
    else { // if no gcode generator is selected (or defaults changed) generator may be null
      String message = "No Gcode Generator selected. Select a GCode generator \n in the GCode menu, under GCode Generator ";
      JOptionPane.showConfirmDialog(this, message , "No GCode Generator Selected.",
                                    JOptionPane.OK_OPTION, JOptionPane.QUESTION_MESSAGE);
    }
  }

  /**
   * Builds and runs a toolpath generator to slice the model,
   * sets up callbacks so this will be notified when a build is finished.
   *
   * @param skipConfig true if we want to skip skeinforge config, and simply
   * slice the model with the existing settings
   */
  public void runToolpathGenerator(boolean skipConfig) {

    // Check if the model is on the platform
    if (!getPreviewPanel().getModel().isOnPlatform()) {
      String message = "The bottom of the model doesn't appear to be touching the build surface, and attempting to print it could damage your machine. Ok to move it to the build platform?";
      int option = JOptionPane.showConfirmDialog(this, message , "Place model on build surface?",
                   JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
      if (option == JOptionPane.CANCEL_OPTION) {
        return;
      }
      if (option == JOptionPane.YES_OPTION) {
        // put the model on the platform.
        getPreviewPanel().getModel().putOnPlatform();
      }

    }

    // Check for modified STL
    if (build.getModel().isModified()) {
      final String message = "<html>You have made changes to this model.  Any unsaved changes will<br>" +
                             "not be reflected in the generated toolpath.<br>" +
                             "Save the model now?</html>";
      int option = JOptionPane.showConfirmDialog(this, message, "Save model?",
                   JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
      if (option == JOptionPane.CANCEL_OPTION) {
        return;
      }
      if (option == JOptionPane.YES_OPTION) {
        // save model
        handleSave(true);
      }
    }

    // Check for too-big model
    if (modelTooBig()) {
      final String message = "<html>This model is bigger than your machine can build, <br>" +
                             "Are you sure you want to generate code for it?</html>";
      int option = JOptionPane.showConfirmDialog(this, message, "Generate oversize model?",
                   JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
      if (option != JOptionPane.YES_OPTION) {
        return;
      }
    }
    ToolpathGenerator generator = ToolpathGeneratorFactory.createSelectedGenerator();

    if(generator instanceof SkeinforgeGenerator) {
      SkeinforgePostProcessor spp = ((SkeinforgeGenerator)generator).getPostProcessor();

      spp.setMachineType(machineLoader.getMachineInterface().getMachineType());
      spp.setPrependMetaInfo(true);
      spp.setStartCode(new MutableGCodeSource(machineLoader.getMachineInterface().getModel().getStartBookendCode()));
      spp.setEndCode(new MutableGCodeSource(machineLoader.getMachineInterface().getModel().getEndBookendCode()));
      spp.setMultiHead(isDualDriver());
      if((machineLoader.getMachineInterface().getMachineType() == MachineType.THE_REPLICATOR) ||
          (machineLoader.getMachineInterface().getMachineType() == MachineType.REPLICATOR_2) ||
          (machineLoader.getDriver().getDriverName().equals("Makerbot4GSailfish")))
        spp.setAddProgressUpdates(true);
    } else if (generator instanceof MiracleGrueGenerator) {
      MiracleGruePostProcessor spp = ((MiracleGrueGenerator)generator).getPostProcessor();

      spp.setMachineType(machineLoader.getMachineInterface().getMachineType());
      spp.setPrependMetaInfo(true);
      spp.setStartCode(new MutableGCodeSource(machineLoader.getMachineInterface().getModel().getStartBookendCode()));
      spp.setEndCode(new MutableGCodeSource(machineLoader.getMachineInterface().getModel().getEndBookendCode()));
      spp.setMultiHead(isDualDriver());
      spp.setPrependStart(true);
      spp.setAppendEnd(true);
      if((machineLoader.getMachineInterface().getMachineType() == MachineType.THE_REPLICATOR) ||
          (machineLoader.getMachineInterface().getMachineType() == MachineType.REPLICATOR_2) ||
          (machineLoader.getDriver().getDriverName().equals("Makerbot4GSailfish")))
        spp.setAddProgressUpdates(true);

    }


    ToolpathGeneratorThread tgt = new ToolpathGeneratorThread(this, generator, build, skipConfig);
    tgt.addListener(this);
    tgt.start();

  }

  private boolean modelTooBig() {
    /*
     * I'm avoiding using the javax.vecmath classes here because
     * there were some problems with them in the control panel
     */
    EditingModel model = previewPanel.getModel();
    BuildVolume machineVolume = machineLoader.getMachineInterface().getModel().getBuildVolume();

    if(model.getWidth() > machineVolume.getX())
      return true;
    if(model.getDepth() > machineVolume.getY())
      return true;
    if(model.getHeight() > machineVolume.getZ())
      return true;

    return false;
  }

  private JMenu serialMenu = null;

  private void reloadSerialMenu() {

    if (serialMenu == null)
      return;

    serialMenuListener = new SerialMenuListener();

    serialMenu.removeAll();

    String currentName = null;

    currentName = Base.preferences.get("serial.last_selected", null);

    Vector<Name> names = Serial.scanSerialNames();
    Collections.sort(names);

    // Filter /dev/cu. devices on OS X, since they work the same as .tty for our purposes.
    if (Base.isMacOS()) {
      Vector<Name> filteredNames = new Vector<Name>();

      for (Name name : names) {
        if(!(name.getName().startsWith("/dev/cu")
             || name.getName().equals("/dev/tty.Bluetooth-Modem")
             || name.getName().equals("/dev/tty.Bluetooth-PDA-Sync"))) {
          filteredNames.add(name);
        }
      }

      names = filteredNames;
    }


    ButtonGroup radiogroup = new ButtonGroup();
    for (Name name : names) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(name.toString());
      item.setEnabled(name.isAvailable());

      item.setSelected(name.getName().equals(currentName));
      //			final String portName = name.getName();
      //			Base.preferences.put("serial.last_selected", portName);

      item.addActionListener(serialMenuListener);
      radiogroup.add(item);
      serialMenu.add(item);
    }
    if (names.isEmpty()) {
      // Be aware that there is code in machineStateChanged that relies on this string
      // I know it's a hack, but it works
      JMenuItem item = new JMenuItem("No serial ports detected");
      item.setEnabled(false);
      serialMenu.add(item);
    }
    serialMenu.addSeparator();
    JMenuItem item = new JMenuItem("Rescan serial ports");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        reloadSerialMenu();
      }
    });
    serialMenu.add(item);
  }


  private JMenu mruMenu = null;

  private class FileOpenActionListener implements ActionListener {
    public String path;

    FileOpenActionListener(String path) {
      this.path = path;
    }

    public void actionPerformed(ActionEvent e) {
      handleOpen(path);
    }
  }

  private void reloadMruMenu() {
    if (mruMenu == null) {
      return;
    }
    mruMenu.removeAll();
    if (mruList != null) {
      int index = 0;
      for (String fileName : mruList) {
        String entry = Integer.toString(index) + ". "
                       + fileName.substring(fileName.lastIndexOf('/') + 1);
        JMenuItem item = new JMenuItem(entry, KeyEvent.VK_0 + index);
        item.addActionListener(new FileOpenActionListener(fileName));
        mruMenu.add(item);
        index = index + 1;
        if (index >= 9) {
          break;
        }
      }
    }
  }

  protected JMenu buildFileMenu() {
    JMenuItem item;
    JMenu menu = new JMenu("File");

    item = newJMenuItem("New", 'N');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleNew(false);
      }
    });
    menu.add(item);

    item = newJMenuItem("Open...", 'O', false);
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleOpen(null);
      }
    });
    menu.add(item);

    saveMenuItem = newJMenuItem("Save", 'S');
    saveMenuItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleSave(false);
      }
    });
    menu.add(saveMenuItem);

    saveAsMenuItem = newJMenuItem("Save As...", 'S', true);
    saveAsMenuItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleSaveAs();
      }
    });
    menu.add(saveAsMenuItem);

    menu.addSeparator();

    mruMenu = new JMenu("Recent");
    reloadMruMenu();
    menu.add(mruMenu);

    menu.addSeparator();
    menu.add(buildExamplesMenu());
    menu.add(buildScriptsMenu());

    JMenuItem resetParamsItem = new JMenuItem("Reset all preferences");
    resetParamsItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        resetPreferences();
      }
    });
    // macosx already has its own preferences and quit menu
    if (!Base.isMacOS()) {
      menu.addSeparator();

      item = newJMenuItem("Preferences", ',');
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          showPrefsWindow();
        }
      });
      menu.add(item);
      menu.add(resetParamsItem);
      menu.addSeparator();
      item = newJMenuItem("Quit", 'Q');
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleQuitInternal();
        }
      });
      menu.add(item);
    } else {
      menu.add(resetParamsItem);
    }

    return menu;
  }

  /* Creates a menu item 'Thingiverse'
   * @returns a JMenu populated with Thingiverse items
   */
  protected JMenu buildThingiverseMenu() {
    JMenuItem item;
    JMenu menu = new JMenu("Thingiverse");

    item = new JMenuItem("What's New?");
    item.addActionListener( new ActionListener() {
      //do bare bones launch
      @Override
      public void actionPerformed(ActionEvent arg0) {
        MainWindow.this.launchBrowser("http://www.thingiverse.com/newest?source=repg");
      }
    });
    menu.add(item);

    item = new JMenuItem("What's Popular?");
    item.addActionListener( new ActionListener() {
      //do bare bones launch
      @Override
      public void actionPerformed(ActionEvent arg0) {
        MainWindow.this.launchBrowser("http://www.thingiverse.com/popular?source=repg");
      }
    });
    menu.add(item);

    item = new JMenuItem("Dual Extrusion Models!");
    item.addActionListener( new ActionListener() {
      //do bare bones launch
      @Override
      public void actionPerformed(ActionEvent arg0) {
        MainWindow.this.launchBrowser("http://www.thingiverse.com/tag:dualstrusion?source=repg");
      }
    });
    menu.add(item);


    return menu;
  }

  protected void launchBrowser(String url) {
    if(java.awt.Desktop.isDesktopSupported()) {
      try {
        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
      } catch (IOException e) {
        Base.logger.log(Level.WARNING, "Could not load URL.");
      } catch (java.net.URISyntaxException e) {
        Base.logger.log(Level.WARNING, "bad URI");
      }
    }
  }

  /*
   * Creates a menu item 'Help'
   * @returrns a JMenu Item containing help items
   */
  protected JMenu buildHelpMenu() {
    JMenuItem item;
    JMenu menu = new JMenu("Help");

    item = new JMenuItem("Offline Documentation");
    item.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        // open up the local copy of replicat.org
        if (java.awt.Desktop.isDesktopSupported()) {
          try {
            File toOpen = Base.getApplicationFile("docs/replicat.org/index.html");
            URI uri = toOpen.toURI();
            java.awt.Desktop.getDesktop().browse(uri);
          } catch (IOException e) {
            Base.logger.log(Level.WARNING, "Could not load offline documentation.");
          }
        }
      }
    });
    menu.add(item);

    item = new JMenuItem("Supported GCodes");
    item.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        //Display the auto-generated list of codes our enumeration recognises
        Object[] codes = GCodeEnumeration.getDocumentation().toArray();
        JScrollPane displayPane = new JScrollPane(new JList(codes));
        JOptionPane pane = new JOptionPane(displayPane, JOptionPane.PLAIN_MESSAGE,
          JOptionPane.DEFAULT_OPTION, null, null, null);
        pane.setInitialValue(null);
        pane.setComponentOrientation(MainWindow.this.getComponentOrientation());
        JDialog dialog = pane.createDialog(MainWindow.this, "Supported GCodes");
        pane.selectInitialValue();
        dialog.setResizable(true); // since the list is long, make the dialog resizeable
        dialog.setVisible(true);
        dialog.dispose();
      }
    });
    menu.add(item);

    item = new JMenuItem("About ReplicatorG");
    item.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        handleAbout();
      }
    });
    menu.add(item);

    return menu;
  }

  private JMenuItem buildMenuFromPath(File path, Pattern pattern) {
    if (!path.exists()) {
      return null;
    }
    if (path.isDirectory()) {
      File[] files = path.listFiles();
      Vector<JMenuItem> items = new Vector<JMenuItem>();
      for (File f : files) {
        JMenuItem i = buildMenuFromPath(f,pattern);
        if (i != null) {
          items.add(i);
        }
      }
      Collections.sort(items,new Comparator<JMenuItem>() {
        public int compare(JMenuItem o1, JMenuItem o2) {
          if (o1 instanceof JMenu) {
            if (!(o2 instanceof JMenu)) {
              return 1; // o1 is JMenu and o2 is not, display second
            }
          } else if (o2 instanceof JMenu) {
            return -1; // o2 is JMenu and o1 is not, display first
          }
          return o1.getText().compareTo(o2.getText());
        }
      });
      if (items.size() == 0) {
        return null;
      }
      JMenu menu = new JMenu(path.getName());
      for (JMenuItem i : items) {
        menu.add(i);
      }
      return menu;
    } else {
      Matcher m = pattern.matcher(path.getName());
      if (m.matches()) {
        try {
          FileOpenActionListener l = new FileOpenActionListener(path.getCanonicalPath());
          JMenuItem item = new JMenuItem(path.getName());
          item.addActionListener(l);
          return item;
        } catch (IOException ioe) {
          return null;
        }
      }
      return null;
    }
  }

  private JMenuItem buildExamplesMenu() {
    File examplesDir = Base.getApplicationFile("examples");
    Pattern p = Pattern.compile("[^\\.]*\\.[sS][tT][lL]$");
    JMenuItem m = buildMenuFromPath(examplesDir,p);
    if(m == null) {
      JMenuItem m2 = new JMenu("Examples");
      m2.add(new JMenuItem("No example dirs found."));
      m2.add(new JMenuItem("Check if this dir exists:" + Base.getApplicationFile("examples")));
      return m2;
    } else {
      m.setText("Examples");
      return m;
    }
  }

  private JMenuItem buildScriptsMenu() {
    File examplesDir = Base.getApplicationFile("scripts");
    Pattern p = Pattern.compile("[^\\.]*\\.[gG][cC][oO][dD][eE]$");
    JMenuItem m = buildMenuFromPath(examplesDir,p);
    if(m == null) {
      JMenuItem m2 = new JMenu("Scripts");
      m2.add(new JMenuItem("No scripts found."));
      m2.add(new JMenuItem("Check if this dir exists:" + Base.getApplicationFile("scripts")));
      return m2;
    } else {
      m.setText("Scripts");
      return m;
    }
  }

  protected JMenu buildGCodeMenu() {
    JMenuItem item;
    JMenu menu = new JMenu("GCode");

    item = newJMenuItem("Estimate", 'E');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleEstimate();
      }
    });
    menu.add(item);

    item = newJMenuItem("Simulate", 'L');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleSimulate();
      }
    });
    item.setEnabled(false);
    menu.add(item);

    generateItem = newJMenuItem("Generate", 'G', true);
    generateItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        runToolpathGenerator(false);
      }
    });
    menu.add(generateItem);

    buildMenuItem = newJMenuItem("Build", 'B');
    buildMenuItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleBuild();
      }
    });
    menu.add(buildMenuItem);

    pauseItem = newJMenuItem("Pause", 'E');
    pauseItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handlePause();
      }
    });
    pauseItem.setEnabled(false);
    menu.add(pauseItem);

    stopItem = newJMenuItem("Stop", '.');
    stopItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleStop();
      }
    });
    stopItem.setEnabled(false);
    menu.add(stopItem);

    // Create gcode generator menu item(s)
    JMenu genMenu = new JMenu("GCode Generator");
    Vector<ToolpathGeneratorDescriptor> generators = ToolpathGeneratorFactory.getGeneratorList();
    String name = ToolpathGeneratorFactory.getSelectedName(); /// <get configured 'current' slicer
    ButtonGroup group = new ButtonGroup();
    for (ToolpathGeneratorDescriptor tgd : generators) {
      JRadioButtonMenuItem i = new JRadioButtonMenuItem(tgd.name);
      group.add(i);
      final String n = tgd.name;
      i.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          ToolpathGeneratorFactory.setSelectedName(n);
        }
      });
      if (name.equals(tgd.name)) {
        i.setSelected(true);
      }
      genMenu.add(i);
    }
    menu.add(genMenu);

    // BASE PROFILES
    profilesMenuItem = newJMenuItem("Edit Slicing Profiles...", 'R');
    profilesMenuItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleEditProfiles();
      }
    });
    profilesMenuItem.setEnabled(true);
    menu.add(profilesMenuItem);

    menu.addSeparator();

    //Change Toolhead of GCode
    JMenuItem left = new JMenuItem("to use T1 (aka Left/A)");
    left.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        //:TODO: check here for 2+ tool changes ( G45, G55) to find dual-extrusion files,
        // and in those cases, send a message box 'dual heads used, cannot convert'
        MutableGCodeSource code = new MutableGCodeSource(build.getCode().file);
        code.changeToolhead(ToolheadAlias.LEFT);
        code.writeToFile(build.getCode().file);

        //TODO: is this redundant?
        handleOpenFile(build.getCode().file);
        try {
          build.getCode().load();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    });
    JMenuItem right = new JMenuItem("to use T0 (aka Right/B)");
    right.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        //TODO: check here for 2+ tool changes ( G45, G55) to find dual-extrusion files,
        // and in those cases, send a message box 'dual heads used, cannot convert'
        MutableGCodeSource code = new MutableGCodeSource(build.getCode().file);
        code.changeToolhead(ToolheadAlias.RIGHT);
        code.writeToFile(build.getCode().file);

        //TODO: is this redundant?
        handleOpenFile(build.getCode().file);
        try {
          build.getCode().load();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

      }
    });
    changeToolheadMenu.add(left);
    changeToolheadMenu.add(right);
    menu.add(changeToolheadMenu);
    dualstrusionItem = newJMenuItem("Merge .stl for DualExtrusion", 'D');
    dualstrusionItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        handleDualStrusion();
      }
    });
    menu.add(dualstrusionItem);
    setDualStrusionGUI(building);
    /*
    		combineItem = new JMenuItem("Row Combine (experimental)");
    		combineItem.addActionListener(new ActionListener(){
    			@Override
    			public void actionPerformed(ActionEvent arg0) {
    				handleCombination();
    			}
    		});
    		menu.add(combineItem);
    		combineItem.setEnabled(true);
    */
    menu.addSeparator();

    /// Removed. Too late to add to build
//		editDualstartItem = new JMenuItem("Edit Dual-Start gcode");
//		editDualstartItem.addActionListener(new ActionListener() {
//			public void actionPerformed(ActionEvent e) {
//				if(machineLoader != null && machineLoader.getMachineInterface() != null) {
//					File dualstart = machineLoader.getMachineInterface().getModel().getDualstartBookendCode();
//					if(dualstart != null)
//						handleOpenFile(dualstart);
//				}
//			}
//		});
//		menu.add(editDualstartItem);

//		editStartItem = new JMenuItem("Edit Start gcode");
//		editStartItem.addActionListener(new ActionListener() {
//			public void actionPerformed(ActionEvent e) {
//				if(machineLoader != null && machineLoader.getMachineInterface() != null) {
//					File start = machineLoader.getMachineInterface().getModel().getStartBookendCode();
//					if(start != null)
//						handleOpenFile(start);
//				}
//			}
//		});
//		menu.add(editStartItem);

//		editEndItem = new JMenuItem("Edit End gcode");
//		editEndItem.addActionListener(new ActionListener() {
//			public void actionPerformed(ActionEvent e) {
//				if(machineLoader != null && machineLoader.getMachineInterface() != null) {
//					File end = machineLoader.getMachineInterface().getModel().getEndBookendCode();
//					if(end != null)
//						handleOpenFile(end);
//				}
//			}
//		});
//		menu.add(editEndItem);

    return menu;
  }

  JMenuItem onboardParamsItem = new JMenuItem("Onboard Preferences...");
//	JMenuItem toolheadIndexingItem = new JMenuItem("Set Toolhead Index...");
  JMenuItem realtimeControlItem = new JMenuItem("Open real time controls window...");
  JMenuItem infoPanelItem = new JMenuItem("Machine information...");
  JMenuItem preheatItem;

  protected JMenu buildMachineMenu() {
    JMenuItem item;
    JMenu menu = new JMenu("Machine");

    machineMenu = new JMenu("Machine Type (Driver)");
    populateMachineMenu();
    menu.add(machineMenu);

    serialMenu = new JMenu("Connection (Serial Port)");
    reloadSerialMenu();
    menu.add(serialMenu);

    controlPanelItem = newJMenuItem("Control Panel", 'J');
//		controlPanelItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J,ActionEvent.CTRL_MASK));
    controlPanelItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleControlPanel();
      }
    });
    menu.add(controlPanelItem);

    onboardParamsItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        handleOnboardPrefs();
      }
    });
    onboardParamsItem.setVisible(false);
    menu.add(onboardParamsItem);

//		toolheadIndexingItem.addActionListener(new ActionListener(){
//			public void actionPerformed(ActionEvent arg0) {
//				handleToolheadIndexing();
//			}
//		});
//
//		toolheadIndexingItem.setVisible(false);
//		menu.add(toolheadIndexingItem);

    realtimeControlItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        handleRealTimeControl();
      }
    });
    if (machineLoader.getDriver() instanceof RealtimeControl) {
      realtimeControlItem.setVisible(false);
      menu.add(realtimeControlItem);
    }

    item = new JMenuItem("Upload new firmware...");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        FirmwareUploader.startUploader(MainWindow.this);
      }
    });
    menu.add(item);

    infoPanelItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        handleInfoPanel();
      }
    });

    infoPanelItem.setVisible(true);
    menu.add(infoPanelItem);

    preheatItem = new JMenuItem("preheat Not Set");
    preheatItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        handlePreheat();
      }
    });
    menu.add(preheatItem);
    preheatItem.setEnabled(false);

    // to put the correct text in the menu, we'll call preheat here
    doPreheat(false);

    return menu;
  }

  ///  called when the preheat button is toggled
  protected void handlePreheat() {
    preheatMachine = !preheatMachine;
    doPreheat(preheatMachine);
  }

  /**
   * Function enables/disables preheat and updates gui to reflect the state of preheat.
   * @param preheat true/false to indicate if we want preheat running
   */
  protected void doPreheat(boolean preheat) {
    int tool0Target = 0;
    int tool1Target = 0;
    int platTarget = 0;

    preheatMachine = preheat;

    // update preheat menu info
    if(preheatMachine) {
      preheatItem.setText("Turn off preheat");
      preheatItem.setToolTipText("Allows the machine to cool down (i.e. not maintain temperature)");
    } else {
      preheatItem.setText("Preheat Machine");
      preheatItem.setToolTipText("Tells the machine to begin warming up to the temperature specified in preferences");
    }

    MachineInterface machine = Base.getMachineLoader().getMachineInterface();

    if(machine != null && !building) {
      if(preheatMachine) {
        tool0Target = Base.preferences.getInt("build.preheatTool0", 75);
        platTarget = Base.preferences.getInt("build.preheatPlatform", 75);
        if(isDualDriver())
          tool1Target = Base.preferences.getInt("build.preheatTool1", 75);
      }
      machine.runCommand(new replicatorg.drivers.commands.SelectTool(0)); /// for paranoia to get the right tool
      machine.runCommand(new replicatorg.drivers.commands.SetTemperature(tool0Target,0));
      machine.runCommand(new replicatorg.drivers.commands.SetPlatformTemperature(platTarget,0));
      if(isDualDriver()) {
        machine.runCommand(new replicatorg.drivers.commands.SelectTool(1)); /// for paranoia to get the right tool
        machine.runCommand(new replicatorg.drivers.commands.SetTemperature(tool1Target,1));
      }
    }

  }


  protected void handleToolheadIndexing() {
    if (!(machineLoader.getDriver() instanceof MultiTool)) {
      JOptionPane.showMessageDialog( this,
                                     "ReplicatorG can't connect to your machine or toolhead index setting is not supported.\nTry checking your settings and resetting your machine.",
                                     "Can't run toolhead indexing", JOptionPane.ERROR_MESSAGE);
      return;
    } else {
      ToolheadIndexer indexer = new ToolheadIndexer(this,machineLoader.getDriver());
      if(isDualDriver()) {
        JOptionPane.showMessageDialog( this,
                                       "WARNING: Toolhead Index must be set one at a time on DualStrusion machines.  " +
                                       "See documentation at: http://www.makerbot.com/docs/dualstrusion for full instructions",
                                       "Dualstrusion Extruder Board Warning:",
                                       JOptionPane.WARNING_MESSAGE);
      }

      indexer.setVisible(true);
    }
  }

  protected void handleInfoPanel() {
    InfoPanel infoPanel = new InfoPanel();
    infoPanel.setVisible(true);
  }

  public boolean supportsRealTimeControl() {
    if (!(machineLoader.getDriver() instanceof RealtimeControl)) {
      return false;
    }
    Base.logger.info("Supports RC");
    return true;
  }

  protected void handleRealTimeControl() {
    if(!this.supportsRealTimeControl()) {
      JOptionPane.showMessageDialog(
        this,
        "Real time control is not supported for your machine's driver.",
        "Can't enabled real time control", JOptionPane.ERROR_MESSAGE);
    } else {
      RealtimePanel window = RealtimePanel.getRealtimePanel(machineLoader.getMachineInterface());
      if (window != null) {
        window.pack();
        window.setVisible(true);
        window.toFront();
      }
    }
  }

  /**
   * @returns True  if the selected machine has 2 toolheads
   */
  public boolean isDualDriver() {
    try {
      //TRICKY: machieLoader may not be loaded yet 'naturally' so we force an early load
      String mname = Base.preferences.get("machine.name", "error");

      MachineInterface machineInter = machineLoader.getMachineInterface(mname);
      if(machineInter == null) {
        Base.logger.fine("no valid machine for " + mname);
        return false; //assume it's a single extruder
      }

      //HEREHERE

      System.out.println("test" + machineInter.getModel().getTools().size());
      if( machineInter.getModel().getTools().size() == 2 ) {
        return true;
      }
    } catch(NullPointerException e) {
      System.err.println("Error");
      e.printStackTrace();
    }
    return false;
  }


  /**
   *  Enable dual extrusion items in the GUI
   */
  private void setDualStrusionGUI(boolean isBuilding) {
    boolean enable = isDualDriver() & ! isBuilding;

    dualstrusionItem.setEnabled(enable);
    changeToolheadMenu.setEnabled(enable);
  }

  /**
   * Class for handling Machine Menu actions
   */
  class MachineMenuListener implements ActionListener {

    /* a quick case insensitive match function.
     * @returns true of subString is in baseString (case insensitive), false otherwise
     **/
    public boolean containsIgnoreCase(String baseString, String subString) {
      if(baseString == null || subString == null)
        return false;
      return baseString.toLowerCase().contains( subString.toLowerCase() );
    }
    public void actionPerformed(ActionEvent e) {
      if (machineMenu == null) {
        System.out.println("machineMenu is null");
        return;
      }

      //			int count = machineMenu.getItemCount();
      //			for (int i = 0; i < count; i++) {
      //				((JCheckBoxMenuItem) machineMenu.getItem(i)).setState(false);
      //			}

      //			item.setState(true);
      if (e.getSource() instanceof JRadioButtonMenuItem) {
        JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
        final String name = item.getText();

        //if new machine driver name have "Mk5" and the previous driver name does not
        if(containsIgnoreCase(name, "MK5" ) &&
            (containsIgnoreCase( Base.preferences.get("machine.name", null), "MK5") ==  false ) ) {
          String msg = "MK6 or newer  downgrading to MK5 requires manual changes.\n Search 'Mk5 Extruder Downgrade' on http://wiki.makerbot.com for instructions.";
          JOptionPane.showMessageDialog(null, msg,  "Warning:Manual Downgrade to MK5 Needed", JOptionPane.WARNING_MESSAGE);

        }

        Base.preferences.put("machine.name", name);
        loadMachine(name, false);
      }
    }
  }
  class SerialMenuListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      if (serialMenu == null) {
        System.out.println("serialMenu is null");
        return;
      }

      if (e.getSource() instanceof JRadioButtonMenuItem) {
        JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
        final String name = item.getText().split(" ")[0];
        Base.preferences.put("serial.last_selected", name);
      }
    }
  }

  /** Function to generate a list of
   * supported machines to be displayed in the Driver menu item.
   */
  protected void populateMachineMenu() {
    machineMenu.removeAll();
    machineMenuListener = new MachineMenuListener();

    Vector<String> names = new Vector<String>();
    try {
      for (String name : MachineFactory.getMachineNames() ) {
        names.add(name);
      }
    } catch (Exception exception) {
      System.out.println("error retrieving machine list");
      exception.printStackTrace();
    }


    String[] mBots = {"Cupcake", "Thingomatic", "Replicator"};
    for(String bot : mBots) {
      moveTypeToHead(names, bot);
    }

    ButtonGroup botButtons = new ButtonGroup();
    JMenu otherBotMenu = new JMenu("Other Bots");
    for (String name : names ) {

      JRadioButtonMenuItem item = new JRadioButtonMenuItem(name);
      item.setSelected(name.equals(Base.preferences.get("machine.name","The Replicator Dual")));
      item.addActionListener(machineMenuListener);

      botButtons.add(item);

      if(isMBot(mBots, name)) machineMenu.add(item);

      else otherBotMenu.add(item);
    }
    machineMenu.add(new JSeparator());
    machineMenu.add(otherBotMenu);
  }

  private boolean isMBot(String[] mBots, String name) {
    for(String bot : mBots) {
      if(name.contains(bot)) return true;
    }
    return false;
  }

  /***
   * Sorts all bots of a certain type in a given vector to the head of the list
   * @param v; a vector of bot names
   * @param type; a type of bot to be sorted
   */
  private void moveTypeToHead(Vector<String> v, String type) {
    Vector<String> temp = (Vector<String>) v.clone();
    for(String name : temp) {
      if(name.contains(type)) {
        moveElementToHead(v, name);
      }
    }
  }

  private void moveElementToHead(Vector<String> v, String s) {
    if(!v.contains(s)) return;
    v.remove(s);
    v.add(0, s);
  }

  /**
   * Constructs and returns the menu under 'Edit'
   */
  public JMenu buildEditMenu() {
    JMenu menu = new JMenu("Edit");
    JMenuItem item;

    undoItem = newJMenuItem("Undo", 'Z');
    undoItem.addActionListener(undoAction = new UndoAction());
    menu.add(undoItem);

    redoItem = newJMenuItem("Redo", 'Y');
    redoItem.addActionListener(redoAction = new RedoAction());
    menu.add(redoItem);

    menu.addSeparator();

    // TODO "cut" and "copy" should really only be enabled
    // if some text is currently selected
    item = newJMenuItem("Cut", 'X');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        textarea.cut();
        build.getCode().setModified(true);
      }
    });
    menu.add(item);

    item = newJMenuItem("Copy", 'C');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        textarea.copy();
      }
    });
    menu.add(item);

    item = newJMenuItem("Paste", 'V');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        textarea.paste();
        build.getCode().setModified(true);
      }
    });
    menu.add(item);

    item = newJMenuItem("Select All", 'A');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        textarea.selectAll();
      }
    });
    menu.add(item);

    menu.addSeparator();

    item = newJMenuItem("Find...", 'F');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (find == null) {
          find = new FindReplace(MainWindow.this);
        }
        // new FindReplace(MainWindow.this).setVisible(true);
        find.setVisible(true);
        // find.setVisible(true);
      }
    });
    menu.add(item);

    // TODO find next should only be enabled after a
    // search has actually taken place
    item = newJMenuItem("Find Next", 'G');
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (find != null) {
          // find.find(true);
          // FindReplace find = new FindReplace(MainWindow.this);
          // //.setVisible(true);
          find.find(true);
        }
      }
    });
    menu.add(item);

    return menu;
  }

  /**
   * Convenience method, see below.
   */
  static public JMenuItem newJMenuItem(String title, int what) {
    return newJMenuItem(title, what, false);
  }

  /**
   * A software engineer, somewhere, needs to have his abstraction taken away.
   * In some countries they jail or beat people for writing the sort of API
   * that would require a five line helper function just to set the command
   * key for a menu item.
   */
  static public JMenuItem newJMenuItem(String title, int what, boolean shift) {
    JMenuItem menuItem = new JMenuItem(title);
    int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    if (shift)
      modifiers |= ActionEvent.SHIFT_MASK;
    menuItem.setAccelerator(KeyStroke.getKeyStroke(what, modifiers));
    return menuItem;
  }

  // ...................................................................

  class UndoAction extends AbstractAction {
    private static final long serialVersionUID = 7800704765553895387L;

    public UndoAction() {
      super("Undo");
      this.setEnabled(false);
    }

    public void actionPerformed(ActionEvent e) {
      try {
        if (currentElement == null) {
          return;
        }
        currentElement.getUndoManager().undo();
      } catch (CannotUndoException ex) {
        // System.out.println("Unable to undo: " + ex);
        // ex.printStackTrace();
      }
      updateUndo();
    }

    protected void updateUndoState() {
      if (currentElement == null) {
        return;
      }
      UndoManager undo = currentElement.getUndoManager();
      boolean canUndo = undo.canUndo();
      this.setEnabled(canUndo);
      undoItem.setEnabled(canUndo);
      currentElement.setModified(canUndo);//[1]

      if (canUndo) {
        undoItem.setText(undo.getUndoPresentationName());
        putValue(Action.NAME, undo.getUndoPresentationName());
      } else {
        undoItem.setText("Undo");
        putValue(Action.NAME, "Undo");
      }
    }
  }
  //[1] this causes a BUG: This assumes your canUndo buffer is exatly as old as your last save, not older.
  // which means you can't 'undo' into the past beyond a filesave. So if you change, save, change tabs, and return
  // to a tab, that tab will (wrongly) throw up a 'modified' asterix on the name


  class RedoAction extends AbstractAction {
    private static final long serialVersionUID = -2427139178653072745L;

    public RedoAction() {
      super("Redo");
      this.setEnabled(false);
    }

    public void actionPerformed(ActionEvent e) {
      try {
        currentElement.getUndoManager().redo();
      } catch (CannotRedoException ex) {
        // System.out.println("Unable to redo: " + ex);
        // ex.printStackTrace();
      }
      updateUndo();
    }

    protected void updateRedoState() {
      if (currentElement == null) {
        return;
      }
      UndoManager undo = currentElement.getUndoManager();
      if (undo.canRedo()) {
        redoItem.setEnabled(true);
        redoItem.setText(undo.getRedoPresentationName());
        putValue(Action.NAME, undo.getRedoPresentationName());
      } else {
        this.setEnabled(false);
        redoItem.setEnabled(false);
        redoItem.setText("Redo");
        putValue(Action.NAME, "Redo");
      }
    }
  }

  // ...................................................................

  // interfaces for MRJ Handlers, but naming is fine
  // so used internally for everything else

  public void handleAbout() {
    final Image image = Base.getImage("images/about.png", this);
    int w = image.getWidth(this);
    int h = image.getHeight(this);
    final Window window = new Window(this) {
      public void paint(Graphics g) {
        g.drawImage(image, 0, 0, null);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(Color.white);
        FontMetrics fm = g.getFontMetrics();
        String version = Base.VERSION_NAME;
        Rectangle2D r = fm.getStringBounds(version,g);
        g.drawString(version, (int)(364-r.getWidth()), (int)(95-r.getMinY()));

        AttributedString text = new AttributedString("\u00a9 2008, 2009, 2010 by Zach Smith, Adam Mayer, and numerous contributors. " +
          "See Contributors.txt for a full list.  \n\r" +
          "This program is free software; you can redistribute it and/or modify "+
          "it under the terms of the GNU General Public License as published by "+
          "the Free Software Foundation; either version 2 of the License, or "+
          "(at your option) any later version.");
        AttributedCharacterIterator iterator = text.getIterator();
        FontRenderContext frc = g2.getFontRenderContext();
        LineBreakMeasurer measurer = new LineBreakMeasurer(text.getIterator(), frc);
        measurer.setPosition(iterator.getBeginIndex());
        final int margins = 32;
        float wrappingWidth = image.getWidth(this) - (margins*2);
        float x = margins;
        float y = 155;
        while (measurer.getPosition() < iterator.getEndIndex()) {
          TextLayout layout = measurer.nextLayout(wrappingWidth);
          y += (layout.getAscent());
          layout.draw(g2, x, y);
          y += layout.getDescent() + layout.getLeading();
        }
      }
    };
    window.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        window.dispose();
      }
    });
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    window.setBounds((screen.width - w) / 2, (screen.height - h) / 2, w, h);
    window.setVisible(true);
  }

  public void handleControlPanel() {
    if (!machineLoader.isLoaded()) {
      JOptionPane.showMessageDialog(
        this,
        "ReplicatorG can't connect to your machine.\nTry checking your settings and resetting your machine.",
        "Can't find machine", JOptionPane.ERROR_MESSAGE);
    } else if(!building) {
      ControlPanelWindow window = ControlPanelWindow.getControlPanel(machineLoader.getMachineInterface());
      if (window != null) {
        window.pack();
        window.setVisible(true);
        window.toFront();
      }
    }
  }

  /**
   * Handles the functionality of 'disconnect' buttons
   * @param leavePreheatRunning Do not turn of the heat on the bot, leave bot warm/running/hot.
   * @param clearMLSingleton Clear the MachineLoader singleton (ie, totally dispose the local model of the bot state/settings
   */
  public void handleDisconnect(boolean leavePreheatRunning, boolean clearMLSingleton) {
    if(building) {
      int choice = JOptionPane.showConfirmDialog(this, "You are attempting to disconnect the printer while it is printing \n are you sure?", "Disconnect Warning", JOptionPane.YES_NO_OPTION);
      if(choice == 1) {
        return;
      }
    }
    //HEREHERE
    //doPreheat(leavePreheatRunning);
    machineLoader.disconnect();
    if(clearMLSingleton) /// this causes the singleton to reload (so post-eeprom-reset/write bots reload data)
      machineLoader.clearSingleton();
  }

  /**
   * Function to connect to a bot. handleConnect means, 'if we aren't already connected to a machine, make
   * a new one and connect to it'. This has the side effect of destroying
   * any machine that might have been loaded but not connected
  * TODO: eh?
  */

  public void handleConnect() {
    // If we are already connected, don't try to connect again.
    if (machineLoader.isConnected()) {
      return;
    }

    String name = Base.preferences.get("machine.name", "The Replicator Dual");
    if ( name != null ) {
      loadMachine(name, true);
    }

  }

  /**
   * Displays Machine Onboard Preferences dialog
   */
  public void handleOnboardPrefs() {
    if (!(machineLoader.getDriver() instanceof OnboardParameters)) {
      JOptionPane.showMessageDialog(
        this,
        "ReplicatorG can't connect to your machine or onboard preferences are not supported.\n"+
        "Try checking your settings and resetting your machine.",
        "Can't run onboard prefs", JOptionPane.ERROR_MESSAGE);
      return;
    }

    OnboardParametersWindow mop = new OnboardParametersWindow((OnboardParameters)machineLoader.getDriver(),machineLoader.getDriver(),this);
    mop.setVisible(true);
  }

  /**
   *  Function to handle apple stype prefernces access via MJRPrefsHandler. Opens preferences window.
   */
  public void handlePrefs() {
    showPrefsWindow();
  }

  /**
   * Show the preferences window, creating a new copy of the window if necessassary.
   */
  public void showPrefsWindow() {
    if(preferences == null)
      preferences = new PreferencesWindow(machineLoader.getMachineInterface());
    preferences.showFrame(this);
  }

  // ...................................................................

  /**
   * Get the contents of the current buffer. Used by the Sketch class.
   */
  public String getText() {
    return textarea.getText();
  }

  /**
   * Called to update the text but not switch to a different set of code
   * (which would affect the undo manager).
   */
  public void setText(String what, int selectionStart, int selectionEnd) {
    beginCompoundEdit();
    textarea.setText(what);
    endCompoundEdit();

    // make sure that a tool isn't asking for a bad location
    selectionStart = Math.max(0, Math.min(selectionStart, textarea
                                          .getDocumentLength()));
    selectionEnd = Math.max(0, Math.min(selectionStart, textarea
                                        .getDocumentLength()));
    textarea.select(selectionStart, selectionEnd);

    textarea.requestFocus(); // get the caret blinking
  }

  /**
   * Switch between tabs, this swaps out the Document object that's currently
   * being manipulated.
   */
  public void setCode(BuildCode code) {
    if (code == null) return;
    if (code.document == null) { // this document not yet inited
      code.document = new SyntaxDocument();

      // turn on syntax highlighting
      code.document.setTokenMarker(new PdeKeywords());

      // insert the program text into the document object
      try {
        code.document.insertString(0, code.program, null);
      } catch (BadLocationException bl) {
        bl.printStackTrace();
      }

      final UndoManager undo = code.getUndoManager();
      // connect the undo listener to the editor
      code.document.addUndoableEditListener(new UndoableEditListener() {
        public void undoableEditHappened(UndoableEditEvent e) {
          if (compoundEdit != null) {
            compoundEdit.addEdit(e.getEdit());

          } else if (undo != null) {
            undo.addEdit(e.getEdit());
            updateUndo();
          }
        }
      });
    }

    // update the document object that's in use
    textarea.setDocument(code.document, code.selectionStart,
                         code.selectionStop, code.scrollPosition);

    textarea.requestFocus(); // get the caret blinking
  }

  public void setModel(BuildModel model) {
    if (model != null) {
      getPreviewPanel().setModel(model);
    }
  }

  public void beginCompoundEdit() {
    compoundEdit = new CompoundEdit();
  }

  public void endCompoundEdit() {
    compoundEdit.end();
    currentElement.getUndoManager().addEdit(compoundEdit);
    updateUndo();
    compoundEdit = null;
  }

  // ...................................................................

  public void handleEstimate() {
    if (building)
      return;
    if (simulating)
      return;

    // load our simulator machine
    // loadSimulator();

    // fire off our thread.
    estimationThread = new EstimationThread(this);
    estimationThread.start();
  }

  public void handleSimulate() {
    if (building)
      return;
    if (simulating)
      return;

    // buttons/status.
    simulating = true;
    //buttons.activate(MainButtonPanel.SIMULATE);

    // load our simulator machine
    // loadSimulator();
    setEditorBusy(true);

    // fire off our thread.
    simulationThread = new SimulationThread(this);
    simulationThread.start();
  }

  public void simulationOver() {
    message("Done simulating.");
    simulating = false;
    setEditorBusy(false);
  }

  /// Enum to indicate target build intention
  /// generate-from-stl and build, cancel build, or siply build from gcode
  enum BuildFlag {
    NONE(0), /// Canceled or software error
    GEN_AND_BUILD(1), //genrate new gcode and build
    JUST_BUILD(2); //expect someone checked for existing gcode, and build that

    public final int number;

    /// standard constructor.
    private BuildFlag(int n) {
      number = n;
    }
  };

  /**
   * Checks some enviroment settings and the user interface state to detect the type of build
   * desired by clicking on the 'Build' button.
   *
   * @return a build flag to indicate build type/settings/etc as determined by the program state.
   */
  public BuildFlag detectBuildIntention() {
    BuildFlag flag = BuildFlag.NONE;

    BuildElement elementInView = header.getSelectedElement();
    if(elementInView.getType() == BuildElement.Type.GCODE) {
      flag = BuildFlag.JUST_BUILD;
    }

    else if(elementInView.getType() == BuildElement.Type.MODEL) {
      // if our gcode exist and model was not changed, no re-gen needed
      if(build.getCode() != null &&
          build.getModel().isModified() == false) {
        flag = BuildFlag.JUST_BUILD;
      } else if(Base.preferences.getBoolean("build.autoGenerateGcode", true))
        flag = BuildFlag.GEN_AND_BUILD;
    }

    if(flag == BuildFlag.NONE && getBuild() != null) {
      if(getBuild().getCode() != null)
        flag = BuildFlag.JUST_BUILD;
      else
        flag = BuildFlag.GEN_AND_BUILD;
    }

    ///fail.
    return flag;
  }


  public void handleBuild() {
    if (building)
      return;
    if (simulating)
      return;

    BuildFlag buildFlag = detectBuildIntention();

    if(buildFlag == BuildFlag.NONE) {
      return; //exit ro cancel clicked
    }

    machineLoader.getDriver().setBuildToFileVersion(0);

    if(buildFlag == BuildFlag.GEN_AND_BUILD) {
      //'rewrite' clicked
      buildOnComplete = true;
      doPreheat(Base.preferences.getBoolean("build.doPreheat", false));
      runToolpathGenerator(Base.preferences.getBoolean("build.autoGenerateGcode", true));
      //TRICKY: runToolpathGenerator will fire a 'slice done'
      // which MainWindows listens for, and fires doBuild from
    }
    if(buildFlag == BuildFlag.JUST_BUILD) {
      //'use existing' clicked
      doBuild();
    }
  }

  public void doBuild() {
    if (!machineLoader.isLoaded()) {
      Base.logger.severe("Not ready to build yet.");
    } else if(!machineLoader.isConnected()) {
      Base.logger.severe("Cannot build, not connected to a machine!");
    } else {
      // First, stop machines (but don't tweak gui states)
      if (machineLoader.isLoaded()) {
        machineLoader.getMachineInterface().stopAll();
      }

      // build specific stuff
      building = true;
      setEditorBusy(true);
      doPreheat(true);

      // start our building thread.

      message("Building...");
      buildStart = new Date();

      machineLoader.getMachineInterface().buildDirect(new JEditTextAreaSource(textarea));
      //doing this check allows us to recover from pre-build stuff
//			if(machineLoader.getMachineInterface().buildDirect(new JEditTextAreaSource(textarea)) == false)
//			{
//				buildStart = null;
//				setEditorBusy(false);
//				building = false;
//			}
      maybeRunScript("scripts/start.sh", handleOpenPath, String.valueOf(buildStart.getTime()));
    }
  }

  public void handleUpload() {
    if (building)
      return;
    if (simulating)
      return;

    if (! (machineLoader.getDriver() instanceof SDCardCapture)) {
      Base.logger.severe("Not ready to build yet.");
      return;
    }

    BuildNamingDialog bsd = new BuildNamingDialog(this,build.getName());
    bsd.setVisible(true);
    String path = bsd.getPath();
    if (path != null) {

      // build specific stuff
      building = true;
      //buttons.activate(MainButtonPanel.BUILD);

      setEditorBusy(true);

      // start our building thread.

      message("Uploading...");
      buildStart = new Date();
      machineLoader.getMachineInterface().upload(new JEditTextAreaSource(textarea), path);
    }
  }

  private class ExtensionFilter extends FileFilter {
    private LinkedList<String> extensions = new LinkedList<String>();
    private String description;
    public ExtensionFilter(String extension,String description) {
      this.extensions.add(extension);
      this.description = description;
    }
    public ExtensionFilter(String[] extensions,String description) {
      for (String e : extensions) {
        this.extensions.add(e);
      }
      this.description = description;
    }

    public boolean accept(File f) {
      if (f.isDirectory()) {
        return !f.isHidden();
      }
      for (String extension : extensions) {
        if (f.getPath().toLowerCase().endsWith(extension)) {
          return true;
        }
      }
      return false;
    }

    public String getDescription() {
      return description;
    }

    public String getFirstExtension() {
      return extensions.getFirst();
    }
  };

  private String getExtension(String s) {
    String extension = null;

    int pos = s.lastIndexOf('.');

    if ( pos > 0 && pos < (s.length() - 1))
      extension = s.substring(pos).toLowerCase();

    return extension;
  }

  private String selectOutputFile(String defaultName) {
    File directory = null;
    String loadDir = Base.preferences.get("ui.open_output_dir", null);
    if (loadDir != null) {
      directory = new File(loadDir);
    }

    final JFileChooser fc;
    if (directory != null) {
      fc = new JFileChooser(directory);
    } else {
      fc = new JFileChooser();
    }
    fc.setAcceptAllFileFilterUsed(false);

    ExtensionFilter s3gFilter;
    ExtensionFilter x3gFilter;

    if ((machineLoader.getMachineInterface().getMachineType() == MachineType.THE_REPLICATOR) || (machineLoader.getMachineInterface().getMachineType() == MachineType.REPLICATOR_2)) {
      s3gFilter = new ExtensionFilter(".s3g"," .s3g  (For use with firmware earlier than v7.0)");
      x3gFilter = new ExtensionFilter(".x3g"," .x3g  (For use with firmware v7.0 or later)");
    } else {
      s3gFilter = new ExtensionFilter(".s3g"," .s3g  (For use with firmware v3.5 or earlier)");
      x3gFilter = new ExtensionFilter(".x3g"," .x3g  (For use with firmware v4.1 or later)");
    }
    fc.addChoosableFileFilter(s3gFilter);
    fc.addChoosableFileFilter(x3gFilter);
    if(((OnboardParameters)machineLoader.getDriver()).hasJettyAcceleration() ||
        (machineLoader.getDriver().getBuildToFileVersion() >= 4) ) {
      fc.setFileFilter(x3gFilter);
    } else {
      fc.setFileFilter(s3gFilter);
    }

    fc.setDialogTitle("Save Makerbot build as...");
    fc.setDialogType(JFileChooser.SAVE_DIALOG);
    fc.setFileHidingEnabled(false);
    fc.setSelectedFile(new File(directory,defaultName));

    int rv = fc.showSaveDialog(this);
    if (rv == JFileChooser.APPROVE_OPTION) {

      //Changes the file name to have s3g/x3g extensions and checks if that is
      //what the user selected

      File currentFile = fc.getSelectedFile();
      FileFilter filter = fc.getFileFilter();
      File newFile = new File(currentFile.getAbsolutePath() + ".s3g");
      if(filter.accept(newFile)) {
        System.out.println("\n############s3g");
        Base.preferences.put("ui.open_output_dir",fc.getCurrentDirectory().
                             getAbsolutePath());
        return newFile.getAbsolutePath();
      }

      newFile = new File(currentFile.getAbsolutePath() + ".x3g");

      if(filter.accept(newFile)) {
        System.out.println("\n############x3g");
        Base.preferences.put("ui.open_output_dir",fc.getCurrentDirectory().
                             getAbsolutePath());
        return newFile.getAbsolutePath();
      }
      return null;
    } else {
      return null;
    }
  }

  public void handleBuildToFile() {
    if (building || simulating)
      return;
    if (!machineLoader.isLoaded()) {
      String name = Base.preferences.get("machine.name", null);
      if ( name != null ) {
        machineLoader.getMachineInterface(name);
      }
    }

    if (!(machineLoader.getDriver() instanceof SDCardCapture)) {
      Base.logger.severe("Can't build: Machine not loaded, or current machine doesn't support build to file.");
      return;
    }

    String sourceName;
    System.out.println("\n######:" + machineLoader.getDriver());

    sourceName = build.getName();

    final String sXgVersion_pref = "replicatorg.last.choosen.sxg.format";

    String path = selectOutputFile(sourceName);
    System.out.println("\n#####:" + path);

    String extension = getExtension(path);

    if (path != null) {
      //Save prferences for sXg format
      if(getExtension(path).equals(".x3g"))
        Base.preferences.putInt(sXgVersion_pref,4);
      else if(getExtension(path).equals(".s3g"))
        Base.preferences.putInt(sXgVersion_pref, 3);
      else
        Base.preferences.putInt(sXgVersion_pref,3);

      // build specific stuff
      building = true;
      //buttons.activate(MainButtonPanel.BUILD);

      setEditorBusy(true);

      // start our building thread.
      buildStart = new Date();
      machineLoader.getDriver().setBuildToFileVersion((getExtension(path).equals(".x3g")) ? 4 : 3);
      machineLoader.getMachineInterface().buildToFile(new JEditTextAreaSource(textarea), path);
    }
  }

  public void handlePlayback() {
    if (building)
      return;
    if (simulating)
      return;

    if (! (machineLoader.getDriver() instanceof SDCardCapture)) {
      Base.logger.severe("Not ready to build yet.");
      return;
    }

    SDCardCapture sdcc = (SDCardCapture)machineLoader.getDriver();
    List<String> files = sdcc.getFileList();
    //for (String filename : files) { System.out.println("File "+filename); }
    BuildSelectionDialog bsd = new BuildSelectionDialog(this,files);
    bsd.setVisible(true);
    String path = bsd.getSelectedPath();
    Base.logger.info("Selected path is "+path);
    if (path != null) {
      // build specific stuff
      building = true;
      //buttons.activate(MainButtonPanel.BUILD);

      setEditorBusy(true);

      // start our building thread.
      message("Building...");
      buildStart = new Date();
      machineLoader.getMachineInterface().buildRemote(path);
    }
  }

  private Date buildStart = null;

  public void machineStateChanged(MachineStateChangeEvent evt) {

    if (Base.logger.isLoggable(Level.FINE)) {
      Base.logger.finest("Machine state changed to " + evt.getState().getState());
    }

    if (building) {
      if (evt.getState().canPrint()) {
        final MachineState endState = evt.getState();
        building = false;
        SwingUtilities.invokeLater(new Runnable() {
          // TODO: Does this work?
          public void run() {
            if (endState.canPrint()) {
              notifyBuildComplete(buildStart, new Date());
            } else {
              notifyBuildAborted(buildStart, new Date());
            }
            buildingOver();
          }
        });
      } else if (evt.getState().getState() == MachineState.State.NOT_ATTACHED) {
        building = false; // Don't keep the building state when disconnecting from the machine
        buildingOver();
      }
    }
    if (evt.getState().canPrint()) {
      // TODO: What?
      reloadSerialMenu();
    }
    boolean showParams = evt.getState().isConfigurable()
                         && machineLoader.getDriver() instanceof OnboardParameters
                         && ((OnboardParameters)machineLoader.getDriver()).hasFeatureOnboardParameters();

    if (Base.logger.isLoggable(Level.FINE)) {
      if (!showParams) {
        String cause = "";
        if (evt.getState().isConfigurable()) {
          if (!machineLoader.isLoaded()) cause += "[no machine] ";
          else {
            if (!(machineLoader.getDriver() instanceof OnboardParameters)) {
              cause += "[driver doesn't implement onboard parameters] ";
            } else if (!machineLoader.getDriver().isInitialized()) {
              cause += "[machine not initialized] ";
            } else if (!((OnboardParameters)machineLoader.getDriver()).hasFeatureOnboardParameters()) {
              cause += "[firmware doesn't support onboard parameters]";
            }
          }
          Base.logger.finest("Couldn't show onboard parameters: " + cause);
        }
      }
    }

    // Enable the machine select and serial select menus only when the machine is not connected
    for (int itemIndex = 0; itemIndex < serialMenu.getItemCount(); itemIndex++) {
      JMenuItem item = serialMenu.getItem(itemIndex);
      // The ignore case is a little hacky, and is based on code in reloadSerialMenu()
      if  (item != null && !("No serial ports detected".equals(item.getText()))) {
        item.setEnabled(!evt.getState().isConnected());
      }
    }

    for (int itemIndex = 0; itemIndex < machineMenu.getItemCount(); itemIndex++) {
      JMenuItem item = machineMenu.getItem(itemIndex);
      if  (item!= null) {
        item.setEnabled(!evt.getState().isConnected());
      }
    }

    boolean hasGcode = getBuild().getCode() != null;
    boolean hasModel = getBuild().getModel() != null;

    //		serialMenu.setEnabled(!evt.getState().isConnected());
    //		machineMenu.setEnabled(!evt.getState().isConnected());

    // enable the control panel menu item when the machine is ready
    controlPanelItem.setEnabled(evt.getState().isConfigurable());

    // enable the build menu item when the machine is ready and there is gcode in the editor
    buildMenuItem.setEnabled(hasGcode && evt.getState().isConfigurable());
    onboardParamsItem.setVisible(showParams);
    onboardParamsItem.setEnabled(showParams);
    preheatItem.setEnabled(evt.getState().isConnected() && !building);
    generateItem.setEnabled(hasModel && !building);

//		if(machineLoader.getMachineInterface() != null) {
//			File dualstart = machineLoader.getMachineInterface().getModel().getDualstartBookendCode();
//			editDualstartItem.setEnabled(dualstart != null);
//		}
//		if(machineLoader.getMachineInterface() != null) {
//			File start = machineLoader.getMachineInterface().getModel().getStartBookendCode();
//			editStartItem.setEnabled(start != null);
//		}
//		if(machineLoader.getMachineInterface() != null) {
//			File end = machineLoader.getMachineInterface().getModel().getEndBookendCode();
//			editEndItem.setEnabled(end != null);
//		}

//		boolean showIndexing =
//			evt.getState().isConfigurable() &&
//			machineLoader.getDriver() instanceof MultiTool &&
//			((MultiTool)machineLoader.getDriver()).toolsCanBeReindexed() &&
//			machineLoader.getMachineInterface().getMachineType() != MachineType.THE_REPLICATOR;
//		toolheadIndexingItem.setVisible(showIndexing);

    boolean showRealtimeTuning =
      evt.getState().isConnected() &&
      machineLoader.getDriver() instanceof RealtimeControl &&
      ((RealtimeControl)machineLoader.getDriver()).hasFeatureRealtimeControl();
    realtimeControlItem.setVisible(showRealtimeTuning);
    realtimeControlItem.setEnabled(showRealtimeTuning);

    // TODO: When should this be enabled?
    infoPanelItem.setEnabled(true);

    // Advertise machine name
    String name = "Not Connected";
    if (evt.getState().isConnected() && machineLoader.isLoaded()) {
      name = machineLoader.getMachineInterface().getMachineName();
    }
    if (name != null) {
      this.setTitle(name + " - " + WINDOW_TITLE);
    } else {
      this.setTitle(WINDOW_TITLE);
    }
  }

  public void setEditorBusy(boolean isBusy) {
    // variables and stuff.
    stopItem.setEnabled(isBusy);
    pauseItem.setEnabled(isBusy);

    // clear the console on each build, unless the user doesn't want to
    if (isBusy && Base.preferences.getBoolean("console.auto_clear",true)) {
      console.clear();
    }

    // prepare editor window.
    setVisible(true);
    textarea.setEnabled(!isBusy);
    textarea.setEditable(!isBusy);

    setDualStrusionGUI(isBusy);

    if (isBusy) {
      textarea.selectNone();
      textarea.scrollTo(0, 0);
    }
  }


  /**
   * give a prompt and stuff about the build being done with elapsed time,
   * etc.
   */
  private void notifyBuildComplete(Date started, Date finished) {
    assert started != null;
    assert finished != null;

    long elapsed = finished.getTime() - started.getTime();

    String time_string = EstimationDriver.getBuildTimeString(elapsed);
    String message = "Build finished.\n\nCompleted in "	+ time_string;

    maybeRunScript("scripts/complete.sh", handleOpenPath, String.valueOf(elapsed / 1000), time_string);

    Base.showMessage("Build finished", message);
  }

  private void notifyBuildAborted(Date started, Date aborted) {
    assert started != null;
    assert aborted != null;

    long elapsed = aborted.getTime() - started.getTime();

    String message = "Build aborted.\n\n";
    message += "Stopped after "
               + EstimationDriver.getBuildTimeString(elapsed);

    // Highlight the line at which the user aborted...
    int atWhichLine = machineLoader.getMachineInterface().getLinesProcessed();
    highlightLine(atWhichLine);

    Base.showMessage("Build aborted (line "+ atWhichLine+")", message);
  }

  // synchronized public void buildingOver()
  public void buildingOver() {
    message("Done building.");

    // re-enable the gui and shit.
    textarea.setEnabled(true);

    // update buttons & menu's
    doPreheat(false);

    building = false;
    if (machineLoader.isLoaded()) {
      if (machineLoader.getMachineInterface().getSimulatorDriver() != null)
        machineLoader.getMachineInterface().getSimulatorDriver().destroyWindow();
    }

    setEditorBusy(false);
  }

  class SimulationThread extends Thread {
    MainWindow editor;

    public SimulationThread(MainWindow edit) {
      super("Simulation Thread");

      editor = edit;
    }

    public void run() {
      message("Simulating...");
      machineLoader.getMachineInterface().simulate(new JEditTextAreaSource(textarea));
      EventQueue.invokeLater(new Runnable() {
        public void run() {
          simulationOver();
        }
      });
    }
  }

  /**
   *  Stops the machine from running, and sets gui to
   *  'no build running' mode
   */
  public void handleStop() {
    // called by menu or buttons or during panel ops
    doStop();
    setEditorBusy(false);

    Date started = buildStart;
    Date finished = new Date();
    long elapsed = finished.getTime() - started.getTime();
    String time_string = EstimationDriver.getBuildTimeString(elapsed);
    maybeRunScript("scripts/stop.sh", handleOpenPath,  String.valueOf(elapsed / 1000), time_string);
  }

  class EstimationThread extends Thread {
    MainWindow editor;

    public EstimationThread(MainWindow edit) {
      super("Estimation Thread");

      editor = edit;
    }

    public void run() {
      message("Estimating...");
      machineLoader.getMachineInterface().estimate(new JEditTextAreaSource(textarea));
      editor.estimationOver();
    }
  }

  public void handleDualStrusion() {
    //check if we are using skeinforge.
    String name = Base.preferences.get("replicatorg.generator.name", "Skeinforge (50)");
    if(name.startsWith("Skeinforge") == false) {
      final String message = "<html>Dual Material Extrusion only available using skeinforge engines.<br>" +
                             "Under <b>GCode Generator</b> menu select any <b>Skeinforge</b> slicer and retry.</html>";
      JOptionPane.showMessageDialog(this, message, "Save model?", JOptionPane.WARNING_MESSAGE);
      return;

    }

    if(getBuild().getCode() != null && getBuild().getCode().isModified()) {
      final String message = "<html>In order to dualstrude you need to save<br>" +
                             "Save the model now?</html>";
      int option = JOptionPane.showConfirmDialog(this, message, "Save model?",
                   JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
      if (option == JOptionPane.NO_OPTION) {
        return;
      }
      if (option == JOptionPane.YES_OPTION) {
        // save model
        try {
          getBuild().getCode().save();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } else {
      DualStrusionWindow dsw;

      // this is stuff that DualStrusionConstruction needs, and until there's a better way to get it there...
      MachineType type = machineLoader.getMachineInterface().getMachineType();
      MutableGCodeSource startCode =
        new MutableGCodeSource(machineLoader.getMachineInterface().getModel().getDualstartBookendCode());
      MutableGCodeSource endCode =
        new MutableGCodeSource(machineLoader.getMachineInterface().getModel().getEndBookendCode());

      if(getBuild().getCode() != null)
        dsw = new DualStrusionWindow(type, startCode, endCode, getBuild().getMainFilePath());
      else
        dsw = new DualStrusionWindow(type, startCode, endCode);
      dsw.setVisible(true);
    }

  }

  public void estimationOver() {
    // stopItem.setEnabled(false);
    // pauseItem.setEnabled(false);
    //buttons.clear();
  }

  /**
   *  Send stop commnad to loaded machine,
   *  Disables pre-heating, and sets building values to false/off
   */
  public void doStop() {
    if (machineLoader.isLoaded()) {
      machineLoader.getMachineInterface().stopAll();
    }
    doPreheat(false);
    building = false;
    simulating = false;
    buildOnComplete = false;
  }

  public void handleReset() {
    if (machineLoader.isLoaded()) {
      machineLoader.getMachineInterface().reset();
    }
  }

  public void handlePause() {
    // called by menu or buttons
    // if (building || simulating) // can also be used during control panel
    // ops
    doPause();
  }

  /**
   * Pause the applet but don't kill its window.
   */
  public void doPause() {
    if (machineLoader.getMachineInterface().isPaused()) {
      machineLoader.getMachineInterface().getDriver().unpause();

      if (simulating) {
        message("Simulating...");
      } else if (building) {
        message("Building...");
      }

      //buttons.inactivate(MainButtonPanel.PAUSE);
    } else {
      machineLoader.getMachineInterface().getDriver().pause();
      int atWhichLine = machineLoader.getMachineInterface().getLinesProcessed();
      highlightLine(atWhichLine);
      message("Paused at line "+ atWhichLine +".");
      //buttons.clear();
      //buttons.activate(MainButtonPanel.PAUSE);
    }
  }

  /**
   * Check to see if there have been changes. If so, prompt user whether or
   * not to save first. If the user cancels, just ignore. Otherwise, one of
   * the other methods will handle calling checkModified2() which will get on
   * with business.
   */
  protected void checkModified(int checkModifiedMode) {
    this.checkModifiedMode = checkModifiedMode;
    if (build == null || !build.hasModifiedElements()) {
      checkModified2();
      return;
    }

    String prompt = "Save changes to " + build.getName() + "?  ";

    if (!Base.isMacOS() || Base.javaVersion < 1.5f) {
      int result = JOptionPane.showConfirmDialog(this, prompt,
                   "Quit", JOptionPane.YES_NO_CANCEL_OPTION,
                   JOptionPane.QUESTION_MESSAGE);

      if (result == JOptionPane.YES_OPTION) {
        handleSave(true);
        checkModified2();

      } else if (result == JOptionPane.NO_OPTION) {
        checkModified2();
      }
      // cancel is ignored altogether

    } else {
      // This code is disabled unless Java 1.5 is being used on Mac OS
      // X
      // because of a Java bug that prevents the initial value of the
      // dialog from being set properly (at least on my MacBook Pro).
      // The bug causes the "Don't Save" option to be the highlighted,
      // blinking, default. This sucks. But I'll tell you what doesn't
      // suck--workarounds for the Mac and Apple's snobby attitude
      // about it!

      // adapted from the quaqua guide
      // http://www.randelshofer.ch/quaqua/guide/joptionpane.html
      JOptionPane pane = new JOptionPane("<html> "
                                         + "<head> <style type=\"text/css\">"
                                         + "b { font: 13pt \"Lucida Grande\" }"
                                         + "p { font: 11pt \"Lucida Grande\"; margin-top: 8px }"
                                         + "</style> </head>"
                                         + "<b>Do you want to save changes to this file<BR>"
                                         + " before closing?</b>"
                                         + "<p>If you don't save, your changes will be lost.",
                                         JOptionPane.QUESTION_MESSAGE);

      String[] options = new String[] { "Save", "Cancel",
                                        "Don't Save"
                                      };
      pane.setOptions(options);

      // highlight the safest option ala apple hig
      pane.setInitialValue(options[0]);

      // on macosx, setting the destructive property places this
      // option
      // away from the others at the lefthand side
      pane.putClientProperty("Quaqua.OptionPane.destructiveOption",
                             2);

      JDialog dialog = pane.createDialog(this, null);
      dialog.setVisible(true);

      Object result = pane.getValue();
      if (result == options[0]) { // save (and quit)
        handleSave(true);
        checkModified2();

      } else if (result == options[2]) { // don't save (still quit)
        checkModified2();
      }
    }
  }

  protected boolean confirmBuildAbort() {
    if (machineLoader.isLoaded() && machineLoader.getMachineInterface().getMachineState().isBuilding()) {
      final String message = "<html>You are currently printing from ReplicatorG! Your build will be stopped.<br>" +
                             "Continue and abort print?</html>";
      int option = JOptionPane.showConfirmDialog(this, message, "Abort print?",
                   JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
      if (option == JOptionPane.CANCEL_OPTION) {
        return false;
      }

      // Stop the build.
      doStop();
    }

    return true;
  }
  /**
   * Called by EditorStatus to complete the job and re-dispatch to handleNew,
   * handleOpen, handleQuit.
   */
  public void checkModified2() {
    // This is as good a place as any to check that we don't have an in-progress manual build
    // that could be killed.
    if (!confirmBuildAbort()) return;

    switch (checkModifiedMode) {
    case HANDLE_NEW:
      handleNew2(false);
      break;
    case HANDLE_OPEN:
      handleOpen2(handleOpenPath);
      break;
    case HANDLE_QUIT:
      System.exit(0);
      break;
    }
    checkModifiedMode = 0;
  }

  /**
   * New was called (by buttons or by menu), first check modified and if
   * things work out ok, handleNew2() will be called. <p/> If shift is pressed
   * when clicking the toolbar button, then force the opposite behavior from
   * sketchbook.prompt's setting
   */
  public void handleNew(final boolean shift) {
    //buttons.activate(MainButtonPanel.NEW);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        handleNewShift = shift;
        checkModified(HANDLE_NEW);
      }
    });
  }

  /**
   * Extra public method so that Sketch can call this when a sketch is
   * selected to be deleted, and it won't call checkModified() to prompt for
   * save as.
   */
  public void handleNewUnchecked() {
    handleNewShift = false;
    handleNew2(true);
  }

  /**
   * Does all the plumbing to create a new project then calls handleOpen to
   * load it up.
   *
   * @param noPrompt
   *            true to disable prompting for the sketch name, used when the
   *            app is starting (auto-create a sketch)
   */
  protected void handleNew2(boolean noPrompt) {
    //		try {
    //String pdePath = sketchbook.handleNew(noPrompt, handleNewShift);
    //if (pdePath != null)
    //	handleOpen2(pdePath);

    //		} catch (IOException e) {
    //			// not sure why this would happen, but since there's no way to
    //			// recover (outside of creating another new setkch, which might
    //			// just cause more trouble), then they've gotta quit.
    //			Base.showError("Problem creating a new sketch",
    //					"An error occurred while creating\n"
    //							+ "a new sketch. ReplicatorG must now quit.", e);
    //		}
    handleOpen2(null);
    //buttons.clear();
  }

  /**
   * This is the implementation of the MRJ open document event, and the
   * Windows XP open document will be routed through this too.
   */
  public void handleOpenFile(File file) {
    handleOpen(file.getAbsolutePath());
  }

  private String selectFile() {
    File directory = null;
    String loadDir = Base.preferences.get("ui.open_dir", null);
    if (loadDir != null) {
      directory = new File(loadDir);
    }
    JFileChooser fc = new JFileChooser(directory);
    FileFilter defaultFilter;
    String[] extensions = {".gcode",".ngc",".stl"};
    fc.addChoosableFileFilter(defaultFilter = new ExtensionFilter(extensions,"GCode or STL files"));
    String[] gcodeExtensions = {".gcode",".ngc"};
    fc.addChoosableFileFilter(new ExtensionFilter(gcodeExtensions,"GCode files"));
    fc.addChoosableFileFilter(new ExtensionFilter(".stl","STL files"));
    fc.addChoosableFileFilter(new ExtensionFilter(".obj","OBJ files (experimental)"));
    fc.addChoosableFileFilter(new ExtensionFilter(".dae","Collada files (experimental)"));
    fc.setAcceptAllFileFilterUsed(true);
    fc.setFileFilter(defaultFilter);
    fc.setDialogTitle("Open a gcode or model file...");
    fc.setDialogType(JFileChooser.OPEN_DIALOG);
    fc.setFileHidingEnabled(false);
    int rv = fc.showOpenDialog(this);
    if (rv == JFileChooser.APPROVE_OPTION) {
      fc.getSelectedFile().getName();
      Base.preferences.put("ui.open_dir",fc.getCurrentDirectory().getAbsolutePath());
      return fc.getSelectedFile().getAbsolutePath();
    } else {
      return null;
    }
  }

  /**
   * Open a sketch given the full path to the .gcode file. Pass in 'null' to
   * prompt the user for the name of the sketch.
   */
  public void handleOpen(final String ipath) {
    // haven't run across a case where i can verify that this works
    // because open is usually very fast.
    //		buttons.activate(MainButtonPanel.OPEN);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        String path = ipath;
        if (path == null) { // "open..." selected from the menu
          path = selectFile();
          if (path == null)
            return;
        }
        Base.logger.info("Loading "+path);
        handleOpenPath = path;
        checkModified(HANDLE_OPEN);
      }
    });
  }

  /**
   * Open a sketch from a particular path, but don't check to save changes.
   * Used by Sketch.saveAs() to re-open a sketch after the "Save As"
   */
  public void handleOpenUnchecked(String path, int codeIndex, int selStart,
                                  int selStop, int scrollPos) {
    handleOpen2(path);

    setCode(build.getCode());
    textarea.select(selStart, selStop);
    // textarea.updateScrollBars();
    textarea.setScrollPosition(scrollPos);
  }

  /**
   * Second stage of open, occurs after having checked to see if the
   * modifications (if any) to the previous sketch need to be saved.
   */
  protected void handleOpen2(String path) {
    if (path != null && !new File(path).exists()) {
      JOptionPane.showMessageDialog(this, "The file "+path+" could not be found.", "File not found", JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (path != null) {
      boolean extensionValid = false;

      // Note: Duplication of extension list from selectFile()
      String[] extensions = {".gcode",".ngc",".stl",".obj",".dae"};
      String lowercasePath = path.toLowerCase();
      for (String  extension : extensions) {
        if (lowercasePath.endsWith(extension)) {
          extensionValid = true;
        }
      }

      if (!extensionValid) {
        return;
      }
    }
    try {
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      // loading may take a few moments for large files

      build = new Build(this, path);
      setCode(build.getCode());
      setModel(build.getModel());
      updateBuild();
      buttons.updateFromMachine(machineLoader.getMachineInterface());
      generateItem.setEnabled(build.getModel() != null);
      if (null != path) {
        handleOpenPath = path;
        mruList.update(path);
        reloadMruMenu();
      }
      if (Base.preferences.getBoolean("console.auto_clear",false)) {
        console.clear();
      }
    } catch (Exception e) {
      error(e);
    } finally {
      this.setCursor(Cursor.getDefaultCursor());
    }
  }

  /**
   * Actually handle the save command. If 'force' is set to false, this will
   * happen in another thread so that the message area will update and the
   * save button will stay highlighted while the save is happening. If 'force'
   * is true, then it will happen immediately. This is used during a quit,
   * because invokeLater() won't run properly while a quit is happening.
   */
  public void handleSave(boolean force) {
    Runnable saveWork = new Runnable() {
      public void run() {
        Base.logger.info("Saving...");
        try {
          if (build.save()) {
            Base.logger.info("Save operation complete.");
          } else {
            Base.logger.info("Save operation aborted.");
          }
        } catch (IOException e) {
          // show the error as a message in the window
          error(e);
          // zero out the current action,
          // so that checkModified2 will just do nothing
          checkModifiedMode = 0;
          // this is used when another operation calls a save
        }
      }
    };
    if (force) {
      saveWork.run();
    } else {
      SwingUtilities.invokeLater(saveWork);
    }
  }

  public void handleSaveAs() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        // TODO: lock sketch?
        Base.logger.info("Saving...");
        try {
          if (build.saveAs()) {
            updateBuild();
            Base.logger.info("Save operation complete.");
            mruList.update(build.getMainFilePath());
            // TODO: Add to MRU?
          } else {
            Base.logger.info("Save operation aborted.");
          }
        } catch (IOException e) {
          // show the error as a message in the window
          error(e);
        }
      }
    });
  }

  /**
   * Quit, but first ask user if it's ok. Also store preferences to disk just
   * in case they want to quit. Final exit() happens in MainWindow since it has
   * the callback from EditorStatus.
   */
  public void handleQuitInternal() {
    if (!confirmBuildAbort()) return;
    try {
      if (simulationThread != null) {
        simulationThread.interrupt();
        simulationThread.join();
      }
      if (estimationThread != null) {
        estimationThread.interrupt();
        estimationThread.join();
      }
    } catch (InterruptedException e) {
      assert (false);
    }

    // bring down our machine temperature, don't want it to stay hot
    // 		actually, it has been pointed out that we might want it to stay hot,
    //		so I'm taking this out
//		doPreheat(false);

    // cleanup our machine/driver.
    machineLoader.unload();

    checkModified(HANDLE_QUIT);
  }

  /**
   * Method for the MRJQuitHandler, needs to be dealt with differently than
   * the regular handler because OS X has an annoying implementation <A
   * HREF="http://developer.apple.com/qa/qa2001/qa1187.html">quirk</A> that
   * requires an exception to be thrown in order to properly cancel a quit
   * message.
   */
  public void handleQuit() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        handleQuitInternal();
      }
    });

    // Throw IllegalStateException so new thread can execute.
    // If showing dialog on this thread in 10.2, we would throw
    // upon JOptionPane.NO_OPTION
    throw new IllegalStateException("Quit Pending User Confirmation");
  }

  /**
   * Clean up files and store UI preferences on shutdown.  This is called by
   * the shutdown hook and will be run in virtually all shutdown scenarios.
   * Because it is used in a shutdown hook, there is no reason to call this
   * method explicit.y
   */
  public void onShutdown() {

    storePreferences();
    console.handleQuit();
  }

  public void highlightLine(int lnum) {
    if (lnum < 0) {
      textarea.select(0, 0);
      return;
    }
    // System.out.println(lnum);
    String s = textarea.getText();
    int len = s.length();
    int st = -1;
    int ii = 0;
    int end = -1;
    int lc = 0;
    if (lnum == 0)
      st = 0;
    for (int i = 0; i < len; i++) {
      ii++;
      // if ((s.charAt(i) == '\n') || (s.charAt(i) == '\r')) {
      boolean newline = false;
      if (s.charAt(i) == '\r') {
        if ((i != len - 1) && (s.charAt(i + 1) == '\n')) {
          i++; // ii--;
        }
        lc++;
        newline = true;
      } else if (s.charAt(i) == '\n') {
        lc++;
        newline = true;
      }
      if (newline) {
        if (lc == lnum)
          st = ii;
        else if (lc == lnum + 1) {
          // end = ii;
          // to avoid selecting entire, because doing so puts the
          // cursor on the next line [0090]
          end = ii - 1;
          break;
        }
      }
    }
    if (end == -1)
      end = len;

    // sometimes KJC claims that the line it found an error in is
    // the last line in the file + 1. Just highlight the last line
    // in this case. [dmose]
    if (st == -1)
      st = len;

    textarea.select(st, end);
  }

  // ...................................................................

  /**
   * Show an error int the status bar.
   */
  public void error(String what) {
    Base.logger.severe(what);
  }

  public void error(Exception e) {
    if (e == null) {
      Base.logger.severe("MainWindow.error() was passed a null exception.");
      return;
    }

    // not sure if any RuntimeExceptions will actually arrive
    // through here, but gonna check for em just in case.
    String mess = e.getMessage();
    if (mess != null) {
      String rxString = "RuntimeException: ";
      if (mess.indexOf(rxString) == 0) {
        mess = mess.substring(rxString.length());
      }
      String javaLang = "java.lang.";
      if (mess.indexOf(javaLang) == 0) {
        mess = mess.substring(javaLang.length());
      }
    }
    Base.logger.log(Level.SEVERE,mess,e);
  }

  public void message(String msg) {
    Base.logger.info(msg);
  }

  // ...................................................................

  /**
   *  Class for the Popup menu displayed when one right-clicks on a file .
   */
  class TextAreaPopup extends JPopupMenu {
    // String currentDir = System.getProperty("user.dir");
    String referenceFile = null;

    JMenuItem cutItem, copyItem;

    JMenuItem referenceItem;

    /**
     * Builds a complete pop-up menu, including standard cut/paste/copy items
     * Items that are not usable will be grey'd out
     */
    public TextAreaPopup() {
      JMenuItem item;

      cutItem = new JMenuItem("Cut");
      cutItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.cut();
          build.getCode().setModified(true);
        }
      });
      this.add(cutItem);

      copyItem = new JMenuItem("Copy");
      copyItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.copy();
        }
      });
      this.add(copyItem);

      item = new JMenuItem("Paste");
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.paste();
          build.getCode().setModified(true);
        }
      });
      this.add(item);

      item = new JMenuItem("Select All");
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          textarea.selectAll();
        }
      });
      this.add(item);
    }

    /**
     *  if no text is selected, disable copy and cut menu items
     * @param component parent component
     * @param x location of click at which to display ??
     * @param y location of click at which to display ??
     */
    public void show(Component component, int x, int y) {
      if (textarea.isSelectionActive()) {
        cutItem.setEnabled(true);
        copyItem.setEnabled(true);

        String sel = textarea.getSelectedText().trim();
        referenceFile = PdeKeywords.getReference(sel);
        if(referenceItem !=  null)
          referenceItem.setEnabled(referenceFile != null);

      } else {
        cutItem.setEnabled(false);
        copyItem.setEnabled(false);
        if(referenceItem != null)
          referenceItem.setEnabled(false);
      }
      super.show(component, x, y);
    }
  }


  public MachineInterface getMachineInterface() {
    return this.machineLoader.getMachineInterface();
  }

  boolean canVerifyDeviceType(String targetPort) {
    Vector<Name> names = Serial.scanSerialNames();
    for (Name n : names) {
      if(n.getName().equals(targetPort) && n.isVerified())
        return true;
    }
    return false;
  }


  boolean connectionVerified(String targetPort, String machineName) {
    Vector<Name> names = Serial.scanSerialNames();
    for (Name n : names) {
      if(n.getName().equals(targetPort)) {
        return n.isValidConnectorForMachineName(machineName);
      }
    }
    return false;
  }

  /**
   * Here we want to:
   * 1. Create a new machine using the given profile name
   * 2. If the new machine uses a serial port, connect to the serial port
   * 3. If this is a new machine, record a reference to it
   * 4. Hook the machine to the main window.
   * @param name       name of the machine
   * @param doConnect  perform the connect
   */
  public void loadMachine(String name, boolean doConnect) {

    MachineInterface mi = machineLoader.getMachineInterface(name);

    if(mi == null ) {
      Base.logger.severe("could not load machine '" + name + "' please check Driver-> <Machine Name> ");
      return;
    }

    String targetPort = Base.preferences.get("serial.last_selected", null);

    if (targetPort == null) {
      Base.logger.severe("Couldn't find a port to use!");
      return;
    }
    setDualStrusionGUI(building);

//		if( canVerifyDeviceType(targetPort) )
//		{
//			if ( ! connectionVerified(targetPort,name) ) {
//				Base.logger.severe("We can't connect this machine to that port.");
//				JOptionPane.showMessageDialog( this,
//						"WARNING: You have selected a MightyBoard based machine, " +
//						"but the target USB device is not a MightyBoard based machine. " +
//						"this may cause connection problems.",
//						"Dualstrusion Extruder Board Warning:",
//						JOptionPane.WARNING_MESSAGE);
//			}
//
//		}

    if (doConnect) {
      machineLoader.connect(targetPort);
    }

    if (!machineLoader.isLoaded()) {
      // Buttons will need an explicit null state notification
      buttons.machineStateChanged(new MachineStateChangeEvent(null, new MachineState(MachineState.State.NOT_ATTACHED)));
    }

    if(previewPanel != null) {
      getPreviewPanel().rebuildScene();
      updateBuild();
    }
  }

  public void machineProgress(MachineProgressEvent event) {
  }

  public void toolStatusChanged(MachineToolStatusEvent event) {
  }

  BuildElement currentElement;

  public void setCurrentElement(BuildElement e) {
    currentElement = e;
    if (currentElement != null) {
      CardLayout cl = (CardLayout)cardPanel.getLayout();
      if (currentElement.getType() == BuildElement.Type.MODEL ) {
        cl.show(cardPanel, MODEL_TAB_KEY);
      } else {
        cl.show(cardPanel, GCODE_TAB_KEY);
      }

    }
    updateUndo();
  }

  private void updateBuild() {
    header.setBuild(build);
    header.repaint();
    updateUndo();
  }

  public void stateChanged(ChangeEvent e) {
    // We get a change event when another tab is selected.
    setCurrentElement(header.getSelectedElement());
  }

  /** Function called automatically when new gcode generation completes
   *  does post-processing for newly created gcode
   * @param evt
   */
  @Override
  public void generationComplete(GeneratorEvent evt) {

    // if success, update header and switch to code view
    if (evt.getCompletion() == Completion.SUCCESS) {


      if (build.getCode() != null) {


        build.reloadCode();
        setCode(build.getCode());
      }

      buttons.updateFromMachine(machineLoader.getMachineInterface());

      updateBuild();

      if(buildOnComplete) {
        doBuild();
      }
    }

    if(buildOnComplete) { // for safety, always reset this
      buildOnComplete = false;
    }
  }

  @Override
  public void updateGenerator(GeneratorEvent evt) {
    // ignore
  }

  private void maybeRunScript(String path, String ... arguments) {
    // Run a shell script if possible. Fail silently.
    try {
      List<String> command = new LinkedList<String>();
      command.add(path);
      for (int i = 0; i < arguments.length; i++)
        command.add(arguments[i]);
      ProcessBuilder pb = new ProcessBuilder(command);
      Process process = pb.start();
      Base.logger.log(Level.INFO, "Running script: " + path);
      // Fire off some loggers
      StreamLoggerThread ist = new StreamLoggerThread(process.getInputStream());
      ist.setDefaultLevel(Level.INFO);
      ist.start();
      StreamLoggerThread est = new StreamLoggerThread(process.getErrorStream());
      est.setDefaultLevel(Level.WARNING);
      est.start();
    } catch (java.io.IOException e) {}
  }
}
