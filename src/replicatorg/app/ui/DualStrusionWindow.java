package replicatorg.app.ui;
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
/**
 * @author Noah Levy
 * 
 * <class>DualStrusionWindow</class> is a Swing class designed to integrate DualStrusion into the existing ReplicatorG GUI
 */

/**
 * 
 */
import java.awt.Container;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;
import replicatorg.app.Base;
import replicatorg.app.gcode.DualStrusionConstruction;
import replicatorg.model.Build;
import replicatorg.plugin.toolpath.ToolpathGenerator;
import replicatorg.plugin.toolpath.ToolpathGenerator.GeneratorEvent;
import replicatorg.plugin.toolpath.ToolpathGenerator.GeneratorListener;
import replicatorg.plugin.toolpath.ToolpathGeneratorFactory;
import replicatorg.plugin.toolpath.ToolpathGeneratorThread;
import replicatorg.plugin.toolpath.skeinforge.SkeinforgeGenerator;
import replicatorg.plugin.toolpath.skeinforge.SkeinforgePostProcessor;


/**
 * This is the window that shows you Dualstrusion options, and also prepares everything for combination
 * I'd like to improve this in the future, adapting it prepare build plates, etc.
 * 
 * Also, because this is still very new (and potentially buggy) code, I've thrown a whole lot of logging calls in,
 * Those can be removed in a future release.
 */

/*
 * NOTE: to self(Ted):
 * to test: generate each individual, generate merged, merge individual for each dualstrusion thing that we have

   We can have a more reliable thing that combines STLs, and a less reliable thing that combines gcodes
   (Start code removal without getting it straight from skeinforge is a little less certain)

   We should set up the post-processor before starting the generator, and the generator should just call the post-processor itself
 */
public class DualStrusionWindow extends JFrame{
	private static final long serialVersionUID = 2548421042732389328L; //Generated serial

	// why is there a dest and a result?
	File dest, result;

	boolean repStart, repEnd, uWipe;
	
	Queue<File> stls, gcodes;
	
	// I know for the current DualStrusion we'll always have two gcodes,
	//   but that's just for now...
	CountDownLatch numStls, numGcodes;

	boolean aborted = false;
	
	/**
	 * 
	 * This is the default constructor, it is only invoked if the ReplicatorG window did not already have a piece of gcode open
	 */
	public DualStrusionWindow()
	{
		this(null);
	}
	
	/**
	 * This method creates and shows the DualStrusionWindow GUI, this window is a MigLayout with 3 JFileChooser-TextBox Pairs, the first two being source gcodes and the last being the combined gcode destination.
	 * It also links to online DualStrusion Documentation NOTE: This may be buggy, it uses getDesktop() which is JDK 1.6 and scary.
	 * This method also invokes the thread in which the gcode combining operations run in, I would like to turn this into a SwingWorker soon.

	 * This is a constructor that takes the filepath of the gcode open currently in ReplicatorG
	 * @param s the path of the gcode currently open in RepG
	 */
	
	/*
	 * 
	 */
	
	public DualStrusionWindow(String path) {
		//TODO: Constructors shouldn't auto-display. Refactor that
		super("DualStrusion Window (EXPERIMENTAL functionality)");

		Base.logger.log(Level.FINE, "Dualstrusion window booting up...");
		
		setResizable(true);
		setLocation(400, 0);
		Container cont = this.getContentPane();
		cont.setLayout(new MigLayout("fill"));
		cont.setVisible(true);
		JTextArea explanation = new JTextArea();
		explanation.setText("This window is used to combine two Gcode files generated by SkeinForge. This allows for multiple objects in one print job or multiple materials or colors in one printed object. The resulting gcode assumes that Toolhead1 is on the left and Toolhead0 is on the right");
		explanation.setOpaque(false);
		explanation.setEditable(false);
		explanation.setWrapStyleWord(true);
		explanation.setSize(700, 200);
		explanation.setLineWrap(true);
		
		cont.add(new JLabel("Extruder A (Left)"), "split");//TOOLHEAD 1

		final JTextField Toolhead1 = new JTextField(60);
		String loadFileName = Base.preferences.get("dualstrusionwindow.1file", path);
		if(loadFileName != null)
			Toolhead1.setText(loadFileName);
		else
			Toolhead1.setText("");
		JButton Toolhead1ChooserButton = new JButton("Browse...");
		Toolhead1ChooserButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				String s = null;
				if(!Toolhead1.getText().equals(""))
				{
					s = GcodeSelectWindow.goString(new File(Toolhead1.getText()));	
				}
				else
				{
					s = GcodeSelectWindow.goString();
				}
				if(s != null)
				{
					Toolhead1.setText(s);
				}
			}
		});
		cont.add(Toolhead1,"split");
		cont.add(Toolhead1ChooserButton, "wrap");

		final JTextField Toolhead0 = new JTextField(60);
		loadFileName = Base.preferences.get("dualstrusionwindow.0file", path);
		if(loadFileName != null)
			Toolhead0.setText(loadFileName);
		else
			Toolhead0.setText("");

		JButton Toolhead0ChooserButton = new JButton("Browse...");
		Toolhead0ChooserButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				String s = null;
				if(!Toolhead0.getText().equals(""))
				{
					s = GcodeSelectWindow.goString(new File(Toolhead0.getText()));	
				}
				else
				{
					s = GcodeSelectWindow.goString();
				}
				if(s != null)
				{
					Toolhead0.setText(s);
				}
			}
		});
		JButton switchItem = new JButton("Switch Toolheads"); //This button switches the contents of the two text fields in order to easily swap Primary and Secondary Toolheads
		switchItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0) {
				String temp = Toolhead1.getText();
				Toolhead1.setText(Toolhead0.getText());
				Toolhead0.setText(temp);

			}
		});
		cont.add(switchItem, "wrap");
		cont.add(new JLabel("Extruder B (Right)"), "split");

		cont.add(Toolhead0,"split");
		cont.add(Toolhead0ChooserButton, "wrap");

		final JTextField DestinationTextField = new JTextField(60);
		loadFileName = Base.preferences.get("dualstrusionwindow.destfile", "");
		DestinationTextField.setText(loadFileName);

		JButton DestinationChooserButton = new JButton("Browse...");
		DestinationChooserButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0) {
				String s = null;
				if(!DestinationTextField.getText().equals(""))
				{
					s = GcodeSaveWindow.goString(new File(DestinationTextField.getText()));	
				}
				else
				{
					if(!Toolhead1.getText().equals(""))
					{
						int i = Toolhead1.getText().lastIndexOf("/");
						String a = Toolhead1.getText().substring(0, i + 1) + "untitled.gcode";
						File untitled = new File(a);
						System.out.println(a);
						s = GcodeSaveWindow.goString(untitled);
					}	
					else
					{
						s = GcodeSaveWindow.goString();
					}
				}

				if(s != null)
				{
					if(s.contains("."))
					{
						int lastp = s.lastIndexOf(".");
						if(!s.substring(lastp + 1, s.length()).equals("gcode"))
						{
							s = s.substring(0, lastp) + ".gcode";
						}
					}
					else
					{
						s = s + ".gcode";
					}
					DestinationTextField.setText(s);
				}

			}

		});
		cont.add(new JLabel("Save As: "), "split");
		cont.add(DestinationTextField, "split");
		cont.add(DestinationChooserButton, "wrap");
		
		//Replace Start/End Checkboxes
		final JCheckBox replaceStart = new JCheckBox();
		replaceStart.setSelected(true);
		cont.add(new JLabel("Use default start.gcode: "), "split");
		cont.add(replaceStart,"wrap");
		
		final JCheckBox replaceEnd = new JCheckBox();
		replaceEnd.setSelected(true);
		cont.add(new JLabel("Use default end.gcode: "), "split");
		cont.add(replaceEnd,"wrap");
		
		//Use Wipes	
		final JCheckBox useWipes = new JCheckBox();
		useWipes.setSelected(true);
		cont.add(new JLabel("Use wipes defined in machines.xml"), "split");
		cont.add(useWipes, "wrap");
		
		//Merge
		JButton merge = new JButton("Merge");

		merge.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(Toolhead1.getText().equals(Toolhead0.getText()))
				{
					int option = JOptionPane.showConfirmDialog(null, "You are trying to combine two of the same file. Are you sure you want to do this?",
							"Continue?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
					System.out.println(option);
					if(option == 1)
						return;
				}

				Base.logger.log(Level.FINE, "Building lists of files to combine");
				
				// Note that these are bing used as queues
				stls = new LinkedList<File>();
				gcodes = new LinkedList<File>();
				
				File test = new File(Toolhead1.getText());
				if(test.exists())
				{
					String ext = getExtension(test.getName());
					if("stl".equalsIgnoreCase(ext))
						stls.add(test);
					else if("gcode".equalsIgnoreCase(ext))
						gcodes.add(test);
					else
					{
						JOptionPane.showConfirmDialog(null, "File for Extruder A not an stl or gcode. Please select something I can understand.",
								"Select a different file.", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE);
						return;
					}
				}
				
				test = new File(Toolhead0.getText());
				if(test.exists())
				{
					String ext = getExtension(test.getName());
					if("stl".equalsIgnoreCase(ext))
						stls.add(test);
					else if("gcode".equalsIgnoreCase(ext))
						gcodes.add(test);
					else
					{
						JOptionPane.showConfirmDialog(null, "File for Extruder B not an stl or gcode. Please select something I can understand.",
								"Select a different file.", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE);
						return;
					}
				}
				
				// Let's record the files and destination so they don't need to be entered every time
				Base.preferences.put("dualstrusionwindow.1file", Toolhead1.getText());
				Base.preferences.put("dualstrusionwindow.0file", Toolhead0.getText());
				Base.preferences.put("dualstrusionwindow.destfile", DestinationTextField.getText());
				
				dest = new File(DestinationTextField.getText());
				
				repStart = replaceStart.isSelected();
				repEnd = replaceEnd.isSelected();
				uWipe = useWipes.isSelected();

				if(!gcodes.isEmpty())
				{
					// we need to make sure these gcodes are properly processed
				}
				if(!stls.isEmpty())
				{
					Base.logger.log(Level.FINE, "stls is not empty, converting STL files to gcode");
					numStls = new CountDownLatch(stls.size());
					stlsToGcode();
				}
				else
				{
					Base.logger.log(Level.FINE, "stls is empty, combining gcode files");
					numGcodes = new CountDownLatch(gcodes.size());
					combineGcodes();
				}
			}

		});
		cont.add(merge, "split");
		final JButton help = new JButton("Help");
		help.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				try {
					Desktop.getDesktop().browse(new URI("http://goo.gl/DV5vn"));
					//That goo.gl redirects to http://www.makerbot.com/docs/dualstrusion I just wanted to build in a convenient to track how many press the help button
				} catch (Exception e)
				{
					Base.logger.log(Level.WARNING, "Could not load online help! See log level FINEST for more details");
					Base.logger.log(Level.FINEST, ""+e);
				}
			}
		});
		cont.add(help);
		pack();
		setVisible(true);	

		Base.logger.log(Level.FINE, "Finishing construction of Dualstrusion window");
	}
	
	private void stlsToGcode()
	{
		try{
			final File toBuild = stls.poll();
			final File buildDest = new File(replaceExtension(toBuild.getAbsolutePath(), "gcode"));
			
			Base.logger.log(Level.FINE, "Initializing stl -> gcode " + toBuild.getAbsolutePath());
			if(!buildDest.exists())
				buildDest.createNewFile();
			gcodes.add(buildDest);
			
			final JFrame progress = new JFrame("STL to GCode Progress");
			final ToolpathGenerator gen = ToolpathGeneratorFactory.createSelectedGenerator();
			
			/*
			 * I think we can change some skeinforge preferences in here:
			 *
			List p = ((SkeinforgeGenerator)gen).getPreferences();
			p.remove(arg0);
			p.add(arg0);
			 //*/

			if(gen instanceof SkeinforgeGenerator) {
				// Here we'll do the setup for the post-processor
				//Let's figure out which post-processing steps need to be taken
				Set<String> postProcessingSteps = new TreeSet<String>();
				
				postProcessingSteps.add(SkeinforgePostProcessor.TARGET_TOOLHEAD_DUAL);
					
				/// a dual extruder machine is selected, start/end gcode must be updated accordingly
				// there should be a condition here?
				//DANGER: don't ship this
				if(true) {
					postProcessingSteps.add(SkeinforgePostProcessor.MACHINE_TYPE_REPLICATOR);
					postProcessingSteps.add(SkeinforgePostProcessor.REPLACE_START);
					postProcessingSteps.add(SkeinforgePostProcessor.REPLACE_END);
				}
				
			}
			
			final Build b = new Build(toBuild.getAbsolutePath());
			final ToolpathGeneratorThread tgt = new ToolpathGeneratorThread(progress, gen, b);
			
			tgt.addListener(new GeneratorListener(){
				@Override
				public void updateGenerator(GeneratorEvent evt) {
					if(evt.getMessage().equals("Config Done") && !stls.isEmpty())
					{
						Base.logger.log(Level.FINE, "Starting next stl > gcode: " + stls.peek().getName());
						stlsToGcode();
					}
				}

				@Override
				public void generationComplete(GeneratorEvent evt) {
					if(evt.getCompletion() == Completion.FAILURE || aborted)
					{
						aborted = true;
						return;
					}
					
					
					
					numStls.countDown();
					if(numStls.getCount() == 0)
					{
						Base.logger.log(Level.FINE, "done converting stl files, on to gcode combination! " + numStls.getCount() + "==0?  " + buildDest.getName());
						numGcodes = new CountDownLatch(gcodes.size());
						combineGcodes();
					}
				}
				
			});
			tgt.setDualStrusionSupportFlag(true, 200, 300, toBuild.getName());

			Base.logger.log(Level.FINE, "Init finished, starting conversion");
			
			tgt.start();
		}
		catch(IOException e)
		{
			Base.logger.log(Level.SEVERE, "cannot read stl! Aborting dualstrusion generation, see log level FINEST for more details.");
			Base.logger.log(Level.FINEST, "", e);
			
		} 
	}
	

	private static String getExtension(String path)
	{
		int i = path.lastIndexOf(".");
		String ext = path.substring(i+1, path.length());
		return ext;
	}
	
	private static String replaceExtension(String s, String newExtension)
	{
		int i = s.lastIndexOf(".");
		s = s.substring(0, i+1);
		s = s + newExtension;
		return s;
	}
	
	private void combineGcodes()
	{
		//For now this should always be exactly two gcodes, let's just check that assumption
		if(numGcodes.getCount() != 2)
		{
			Base.logger.log(Level.SEVERE, "Expected two gcode files, found " + 
					numGcodes.getCount() + " cancelling Dualstrusion combination");
			return;
		}
			
		// the two consecutive poll()s pull what are the only two gcode files
		DualStrusionConstruction dcs = new DualStrusionConstruction(gcodes.poll(), gcodes.poll(), dest, repStart, repEnd, uWipe);
		result = dcs.getCombinedFile();
		
		//TED: do we need to do post-processing here
		// we shouldn't?

		Base.logger.log(Level.FINE, "Finished DualStrusionWindow's part");
		
		removeAll();
		dispose();
		
	}
	/**
	 * This method returns the result of the gcode combining operation.
	 * @return the combined gcode.
	 */

	public File getCombined()
	{
		return result;
	}

}
