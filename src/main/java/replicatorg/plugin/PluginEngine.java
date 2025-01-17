package replicatorg.plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import replicatorg.app.gcode.GCodeCommand;
import replicatorg.model.GCodeSource;

public class PluginEngine implements GCodeSource {
  GCodeSource parent = null;

  void setParentSource(GCodeSource parent) {
    this.parent = parent;
  }

  Vector<PluginEntry> plugins;
  void setPlugins(Vector<PluginEntry> plugins) {
    this.plugins = plugins;
  }

  public int getLineCount() {
    return parent.getLineCount();
  }

  private void processLine(String line) {
    GCodeCommand mcode = new GCodeCommand(line);
    if( mcode.hasCode('M')) {
      double code = mcode.getCodeValue('M');

      for (PluginEntry plugin : plugins) {
        if (plugin instanceof MCodePlugin) {
          MCodePlugin mcp = (MCodePlugin)plugin;
          int codes[] = mcp.getAcceptedMCodes();
          for (int acceptedCode : codes) {
            if (code == acceptedCode) {
              mcp.processMCode(mcode);
            }
          }
        }
      }
    }
  }

  class GCodeIterator implements Iterator<String> {
    private Iterator<String> parent;
    public GCodeIterator(Iterator<String> parent) {
      this.parent = parent;
    }
    public boolean hasNext() {
      return parent.hasNext();
    }

    public String next() {
      String next = parent.next();
      processLine(next);
      return next;
    }

    public void remove() {
      parent.remove();
    }
  }

  public Iterator<String> iterator() {
    return new GCodeIterator(parent.iterator());
  }

  @Override
  public List<String> asList() {
    List<String> result = new ArrayList<String>();
    for(Iterator<String> i = iterator(); i.hasNext();)
      result.add(i.next());
    return result;
  }

}
