/***************************************************************************
 * MIT License                                                             *
 * Copyright (c) 2020 AKIYAMA Isao                                         *
 *                                                                         *
 * Permission is hereby granted, free of charge, to any person obtaining   *
 * a copy of this software and associated documentation files (the         *
 * "Software"), to deal in the Software without restriction, including     *
 * without limitation the rights to use, copy, modify, merge, publish,     *
 * distribute, sublicense, and/or sell copies of the Software, and to      *
 * permit persons to whom the Software is furnished to do so, subject to   *
 * the following conditions:                                               *
 *                                                                         *
 * The above copyright notice and this permission notice shall be          *
 * included in all copies or substantial portions of the Software.         *
 *                                                                         *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,         *
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF      *
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  *
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY    *
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,    *
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE       *
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.                  *
 ***************************************************************************/
package sni_sgswing;
 
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import org.sango_lang.Cstr;
import org.sango_lang.RArrayItem;
import org.sango_lang.RClientHelper;
import org.sango_lang.RClosureItem;
import org.sango_lang.RDataConstr;
import org.sango_lang.RExcInfoItem;
import org.sango_lang.RFrame;
import org.sango_lang.RIntItem;
import org.sango_lang.Module;
import org.sango_lang.RNativeImplHelper;
import org.sango_lang.RListItem;
import org.sango_lang.RObjItem;
import org.sango_lang.RRealItem;
import org.sango_lang.RStructItem;
import org.sango_lang.RType;
import org.sango_lang.RuntimeEngine;

public class SNIswing {
  static final Cstr MOD_NAME = new Cstr("sgswing.swing");

  RuntimeEngine theEngine;
  RClosureItem shutdownAction;
  ContextHItem initiatorContext;
  boolean[] keep;
  Runnable callbackEndTask;  // special constant
  Hashtable<String, CursorHItem> cursorTab;

  public static SNIswing getInstance(RuntimeEngine e) {
    SNIswing s = new SNIswing();
    s.theEngine = e;
    s.initiatorContext = s.createIContextImpl();
    s.keep = new boolean[] { false };
    s.callbackEndTask = new Runnable() { public void run() {} };
    s.cursorTab = new Hashtable<String, CursorHItem>();
    return s;
  }


// === Swing bridge framework ===

// -- context --

  public void sni_initiator_context_impl(RNativeImplHelper helper, RClosureItem self) {
    helper.setReturnValue(this.initiatorContext);
  }

// implementation

  ContextHItem createContextImpl(EDTCallback edt, EventGroup eg) {
    ContextImpl impl = new ContextImpl(edt, eg);
    ContextHItem h = new ContextHItem(impl);
    return h;
  }

  class ContextImpl {
    EDTCallback edt;
    EventGroup eventGroup;
    boolean ended;

    ContextImpl(EDTCallback edt, EventGroup eg) {
      this.edt = edt;
      this.eventGroup = eg;
    }

    void request(Runnable r) {
      synchronized (this) {
        if (this.ended) {
          throw new IllegalStateException("Context already ended.");
        }
        this.edt.request(r);
      }
    }

    void end() {
      synchronized (this) {
        this.ended = true;
        this.eventGroup.ended();
      }
    }
  }

  ContextHItem createIContextImpl() {
    ContextImpl impl = new IContextImpl();
    ContextHItem h = new ContextHItem(impl);
    return h;
  }

  class IContextImpl extends ContextImpl {
    IContextImpl() {
      super(null, null);
    }

    void request(Runnable r) {
      SwingUtilities.invokeLater(r);
    }

    void end() {
      throw new RuntimeException("Cannot call to initator context.");
    }
  }

  class ContextHItem extends RObjItem {
    ContextImpl impl;

    ContextHItem(ContextImpl c) {
      super(SNIswing.this.theEngine);
      this.impl = c;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "context_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// -- listener --

  public void sni_create_listener_impl(RNativeImplHelper helper, RClosureItem self, RObjItem eventProc, RObjItem fwd) {
    helper.setReturnValue(this.createListenerImpl((RClosureItem)eventProc, (RClosureItem)fwd));
  }

  public void sni_listener_event_proc(RNativeImplHelper helper, RClosureItem self, RObjItem lis) {
    helper.setReturnValue(((ListenerHItem)lis).impl.proc);
  }

// implementation

  ListenerHItem createListenerImpl(RClosureItem proc, RClosureItem fwd) {
    ListenerImpl impl = new ListenerImpl(proc, fwd);
    return new ListenerHItem(impl);
  }

  class ListenerImpl {
    RClosureItem proc;
    RClosureItem fwd;
   
    ListenerImpl(RClosureItem proc, RClosureItem fwd) {
      this.proc = proc;
      this.fwd = fwd;
    }
  }

  public class ListenerHItem extends RObjItem {
    ListenerImpl impl;

    ListenerHItem(ListenerImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean equals(Object o) {
      return o == this;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "listener_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// -- Swing invocation --

  interface SwingRequest {
    RObjItem run() throws Exception;
  }

  Runnable makeSwingTask(RNativeImplHelper helper, RObjItem ret, SwingRequest req) {
    RExcInfoItem ei = helper.getExcInfo();
    return new Runnable() {
      public void run() {
        RObjItem res;
        try {
          res = SNIswing.this.getResultFinItem(req.run());
        } catch (Exception ex) {
          res = SNIswing.this.getResultExcSwingErrorItem(ex, ei);
        }
        SNIswing.this.getClientHelper().apply(new RObjItem[] { res }, (RClosureItem)ret);
      }
    } ;
  }

  interface PaintRequest {
    void run(JPanel p, Graphics g) throws Exception;
  }


// -- callback --

  EDTCallback createEDTCallback() {
    EDTCallback c = new EDTCallback();
    return c;
  }

  class EDTCallback {
    List<Runnable> tasks;

    EDTCallback() {
      this.tasks = new ArrayList<Runnable>();
    }

    void start(EventGroup eg) {
      eg.start();
      this.processRequests();
      // EDT returns
    }

    void request(Runnable r) {
      synchronized (this) {
        this.tasks.add(r);
        this.notify();
      }
    }

    void end() {
      this.request(SNIswing.this.callbackEndTask);
    }

    void processRequests() {
      boolean stop = false;
      while (!stop) {
        Runnable r = null;
        synchronized (this) {
          if (this.tasks.isEmpty()) {
            try {
              this.wait();
            } catch (InterruptedException ex) {}
          } else {
            r = this.tasks.remove(0);
          }
        }
        if (r == SNIswing.this.callbackEndTask) {
          stop = true;
        } else if (r != null) {
          r.run();
        } else {
          ;
        }
      }
    }
  }

  class EventGroup {
    EDTCallback edt;
    List<ListenerHItem> listeners;
    RObjItem srcSwingObjItem;
    RObjItem eventNameItem;
    RListItem eventInfoList;
    int next;

    EventGroup(
        EDTCallback edt,
        List<ListenerHItem> listeners,
        RObjItem srcSwingObjItem,
        RObjItem eventNameItem,
        RListItem eventInfoList) {
      this.edt = edt;
      this.listeners = new ArrayList<ListenerHItem>(listeners);  // make copy
      this.srcSwingObjItem = srcSwingObjItem;
      this.eventNameItem = eventNameItem;
      this.eventInfoList = eventInfoList;  // without common infos
    }

    void start() {
      RClientHelper ch = SNIswing.this.getClientHelper();
      this.eventInfoList = this.appendCommonInfo(this.eventInfoList);
      synchronized (this) {
        this.fire();  // guaranteed at least one listeners
      }
    }

    void ended() {
      synchronized (this) {
        if (this.fire()) {
          ;
        } else {
          this.edt.end();
        }
      }
    }

    boolean fire() {
      boolean fired;
      if (this.next < this.listeners.size()) {
        RClientHelper ch = SNIswing.this.getClientHelper();
        ContextHItem cxh = SNIswing.this.createContextImpl(this.edt, this);
          ch.apply(
            new RObjItem[] { cxh, this.eventNameItem, this.eventInfoList },
            this.listeners.get(this.next).impl.fwd);
        this.next++;
        fired = true;
      } else {
        fired = false;
      }
      return fired;
    }

    RListItem appendCommonInfo(RListItem L) {
      RClientHelper ch = SNIswing.this.getClientHelper();
      // "source" : <swing_obj> obj_eid$
      RDataConstr dcObjEid = ch.getDataConstr(MOD_NAME, "obj_eid$");
      RObjItem oObjEid = ch.getStructItem(dcObjEid, new RObjItem[] { this.srcSwingObjItem });
      RObjItem srcInfo = ch.getTupleItem(
        new RObjItem[] { ch.cstrToArrayItem(new Cstr("source")), oObjEid });
      RListItem.Cell c = ch.createListCellItem();
      c.head = srcInfo;
      c.tail = L;
      L = c;
      return L;
    }
  }

  public void sni_callback_ended(RNativeImplHelper helper, RClosureItem self, RObjItem ecx) {
    ((ContextHItem)ecx).impl.end();
  }


// === Swing objects ===

  interface SwingObj {
    SwingObjAdapter getSwingObjAdapter();
  }

  class SwingObjAdapter {
    SwingObj swingObj;  // the object
    RObjItem handleItem;  // <xx_h>
    RObjItem swingObjItem;  // <siwng_obj>

    SwingObjAdapter(SwingObj swingObj, RObjItem handleItem, RObjItem swingObjItem) {
      this.swingObj = swingObj;
      this.handleItem = handleItem;
      this.swingObjItem = swingObjItem;
    }

    void init() {}
  }


// Swing object - component

  public void sni_a_component_instance(RNativeImplHelper helper, RClosureItem self, RObjItem comp) {
    helper.setReturnValue(((AComponentHItem)comp).impl.getSwingObjAdapter().swingObjItem);
  }

  public void sni_a_component_impl_install_focus_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem lis) {
    AComponentImpl i = ((AComponentHItem)comp).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAComponentImplAdapter().installFocusListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_uninstall_focus_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem lis) {
    AComponentImpl i = ((AComponentHItem)comp).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAComponentImplAdapter().uninstallFocusListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_install_mouse_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem lis) {
    AComponentImpl i = ((AComponentHItem)comp).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAComponentImplAdapter().installMouseListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_uninstall_mouse_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem lis) {
    AComponentImpl i = ((AComponentHItem)comp).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAComponentImplAdapter().uninstallMouseListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_install_mouse_motion_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem lis) {
    AComponentImpl i = ((AComponentHItem)comp).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAComponentImplAdapter().installMouseMotionListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_uninstall_mouse_motion_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem lis) {
    AComponentImpl i = ((AComponentHItem)comp).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAComponentImplAdapter().uninstallMouseMotionListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_install_key_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem lis) {
    AComponentImpl i = ((AComponentHItem)comp).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAComponentImplAdapter().installKeyListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_uninstall_key_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem lis) {
    AComponentImpl i = ((AComponentHItem)comp).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAComponentImplAdapter().uninstallKeyListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_set_size(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem dim) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    RStructItem d = (RStructItem)dim;
    int w = ((RIntItem)d.getFieldAt(0)).getValue();
    int h = ((RIntItem)d.getFieldAt(1)).getValue();
    Dimension dd = new Dimension(w, h);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setSize(dd);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_set_minimum_size(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem dim_) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    RObjItem dx = sni_sango.SNIlang.unwrapMaybeItem(helper, dim_);
    Dimension dd;
    if (dx != null) {
      RStructItem d = (RStructItem)dx;
      int w = ((RIntItem)d.getFieldAt(0)).getValue();
      int h = ((RIntItem)d.getFieldAt(1)).getValue();
      dd = new Dimension(w, h);
    } else {
      dd = null;
    }
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setMinimumSize(dd);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_set_maximum_size(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem dim_) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    RObjItem dx = sni_sango.SNIlang.unwrapMaybeItem(helper, dim_);
    Dimension dd;
    if (dx != null) {
      RStructItem d = (RStructItem)dx;
      int w = ((RIntItem)d.getFieldAt(0)).getValue();
      int h = ((RIntItem)d.getFieldAt(1)).getValue();
      dd = new Dimension(w, h);
    } else {
      dd = null;
    }
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setMaximumSize(dd);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_set_preferred_size(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem dim_) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    RObjItem dx = sni_sango.SNIlang.unwrapMaybeItem(helper, dim_);
    Dimension dd;
    if (dx != null) {
      RStructItem d = (RStructItem)dx;
      int w = ((RIntItem)d.getFieldAt(0)).getValue();
      int h = ((RIntItem)d.getFieldAt(1)).getValue();
      dd = new Dimension(w, h);
    } else {
      dd = null;
    }
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setPreferredSize(dd);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_enabled_Q_(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return SNIswing.this.getClientHelper().getBoolItem(i.isEnabled());
      }
    }));
  }

  public void sni_a_component_impl_set_enabled(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem sw) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    boolean b = helper.boolItemToBoolean((RStructItem)sw);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setEnabled(b);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_repaint(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.repaint();
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_component_impl_request_focus(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return SNIswing.this.getClientHelper().getBoolItem(i.requestFocusInWindow());
      }
    }));
  }

  public void sni_a_component_impl_set_cursor(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem cursor) {
    Component i = ((AComponentHItem)comp).impl.getZComponent();
    Cursor c = ((CursorHItem)cursor).impl.cursor;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setCursor(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  interface AComponentImpl extends SwingObj {
    Component getZComponent();
    AComponentImplAdapter getAComponentImplAdapter();
  }

  class AComponentHItem extends RObjItem {
    AComponentImpl impl;

    AComponentHItem(AComponentImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "a_component_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }

  class AComponentImplAdapter extends SwingObjAdapter {
    AComponentImpl aComponentImpl;
    AComponentHItem aComponentHItem;
    FocusEventMgr focusEventMgr;
    MouseEventMgr mouseEventMgr;
    MouseMotionEventMgr mouseMotionEventMgr;
    KeyEventMgr keyEventMgr;

    AComponentImplAdapter(AComponentImpl aComponentImpl, RObjItem handleItem, RObjItem swingObjItem) {
      super(aComponentImpl, handleItem, swingObjItem);
      this.aComponentImpl = aComponentImpl;
      this.aComponentHItem = new AComponentHItem(aComponentImpl);
    }

    void init() {
      super.init();
      this.focusEventMgr = new FocusEventMgr(this.swingObj);
      this.mouseEventMgr = new MouseEventMgr(this.swingObj);
      this.mouseMotionEventMgr = new MouseMotionEventMgr(this.swingObj);
      this.keyEventMgr = new KeyEventMgr(this.swingObj);
      this.aComponentImpl.getZComponent().addMouseListener(this.mouseEventMgr);
      this.aComponentImpl.getZComponent().addMouseMotionListener(this.mouseMotionEventMgr);
      this.aComponentImpl.getZComponent().addKeyListener(this.keyEventMgr);
    }

    void installFocusListener(ListenerHItem L) {
      this.focusEventMgr.installListener(L);
    }

    void uninstallFocusListener(ListenerHItem L) {
      this.focusEventMgr.uninstallListener(L);
    }

    void installMouseListener(ListenerHItem L) {
      this.mouseEventMgr.installListener(L);
    }

    void uninstallMouseListener(ListenerHItem L) {
      this.mouseEventMgr.uninstallListener(L);
    }

    void installMouseMotionListener(ListenerHItem L) {
      this.mouseMotionEventMgr.installListener(L);
    }

    void uninstallMouseMotionListener(ListenerHItem L) {
      this.mouseMotionEventMgr.uninstallListener(L);
    }

    void installKeyListener(ListenerHItem L) {
      this.keyEventMgr.installListener(L);
    }

    void uninstallKeyListener(ListenerHItem L) {
      this.keyEventMgr.uninstallListener(L);
    }
  }


// Swing object - jcomponent

  public void sni_a_jcomponent_instance(RNativeImplHelper helper, RClosureItem self, RObjItem comp) {
    helper.setReturnValue(((AJComponentHItem)comp).impl.getSwingObjAdapter().swingObjItem);
  }

  public void sni_a_jcomponent_impl_set_opaque(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem sw) {
    JComponent c = ((AJComponentHItem)comp).impl.getZJComponent();
    boolean  b = helper.boolItemToBoolean((RStructItem)sw);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        c.setOpaque(b);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_jcomponent_impl_set_background(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem color) {
    JComponent c = ((AJComponentHItem)comp).impl.getZJComponent();
    Color cc = this.colorItemToColor(color);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        c.setBackground(cc);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_jcomponent_impl_set_border(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem comp, RObjItem border) {
    JComponent c = ((AJComponentHItem)comp).impl.getZJComponent();
    Border b = ((ABorderHItem)border).impl.getZBorder();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        c.setBorder(b);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  interface AJComponentImpl extends AComponentImpl {
    JComponent getZJComponent();
    AJComponentImplAdapter getAJComponentImplAdapter();
  }

  class AJComponentHItem extends RObjItem {
    AJComponentImpl impl;

    AJComponentHItem(AJComponentImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "a_jcomponent_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }

  class AJComponentImplAdapter extends AComponentImplAdapter {
    AJComponentImpl aJComponentImpl;
    AJComponentHItem aJComponentHItem;

    AJComponentImplAdapter(AJComponentImpl aJComponentImpl, RObjItem handleItem, RObjItem swingObjItem) {
      super(aJComponentImpl, handleItem, swingObjItem);
      this.aJComponentImpl = aJComponentImpl;
      this.aJComponentHItem = new AJComponentHItem(aJComponentImpl);
    }

    void init() {
      super.init();
    }
  }


// Swing object - window

  public void sni_a_window_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem window) {
    helper.setReturnValue(((AWindowHItem)window).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_a_window_instance(RNativeImplHelper helper, RClosureItem self, RObjItem window) {
    helper.setReturnValue(((AWindowHItem)window).impl.getSwingObjAdapter().swingObjItem);
  }

  public void sni_a_window_impl_install_window_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem window, RObjItem lis) {
    AWindowImpl i = ((AWindowHItem)window).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAWindowImplAdapter().installWindowListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_window_impl_uninstall_window_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem window, RObjItem lis) {
    AWindowImpl i = ((AWindowHItem)window).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAWindowImplAdapter().uninstallWindowListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_window_impl_pack(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem window) {
    Window i = ((AWindowHItem)window).impl.getZWindow();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.pack();
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_window_impl_set_visible(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem window, RObjItem sw) {
    Window i = ((AWindowHItem)window).impl.getZWindow();
    boolean b = helper.boolItemToBoolean((RStructItem)sw);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setVisible(b);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_window_impl_dispose(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem window) {
    Window i = ((AWindowHItem)window).impl.getZWindow();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.dispose();
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_window_impl_set_location(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem window, RObjItem loc) {
    Window i = ((AWindowHItem)window).impl.getZWindow();
    RStructItem L = (RStructItem)loc;
    RDataConstr dc = L.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a window location.");
    }
    int a = 0;
    int x = -1;
    int y = -1;
    Component b = null;
    String constr = dc.getName();
    if (constr.equals("in_screen$")) {
      a = 1;
      x = ((RIntItem)L.getFieldAt(0)).getValue();
      y = ((RIntItem)L.getFieldAt(1)).getValue();
    } else if (constr.equals("center_of_screen$")) {
      a = 2;
    } else if (constr.equals("relative_to_component$")) {
      b = ((AComponentHItem)L.getFieldAt(0)).impl.getZComponent();
      a = 3;
    } else if (constr.equals("platform_default_location$")) {
      a = 4;
    } else {
      throw new IllegalArgumentException("Not a window location.");
    }
    int aa = a;  // make effectively final
    Component bb = b;  // make effectively final
    int xx = x;   // make effectively final
    int yy = y;   // make effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        switch (aa) {
        case 1:
          i.setLocation(xx, yy);
          break;
        case 2:
          i.setLocationRelativeTo(null);
          break;
        case 3:
          i.setLocationRelativeTo(bb);
          break;
        case 4:
          i.setLocationByPlatform(true);
          break;
        }
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  interface AWindowImpl extends AComponentImpl {
    Window getZWindow();
    AWindowImplAdapter getAWindowImplAdapter();
  }

  class AWindowHItem extends RObjItem {
    AWindowImpl impl;

    AWindowHItem(AWindowImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "a_window_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }

  class AWindowImplAdapter extends AComponentImplAdapter {
    AWindowImpl aWindowImpl;
    AWindowHItem aWindowHItem;
    WindowEventMgr windowEventMgr;

    AWindowImplAdapter(AWindowImpl aWindowImpl, RObjItem handleItem, RObjItem swingObjItem) {
      super(aWindowImpl, handleItem, swingObjItem);
      this.aWindowImpl = aWindowImpl;
      this.aWindowHItem = new AWindowHItem(aWindowImpl);
    }

    void init() {
      super.init();
      this.windowEventMgr = (this.swingObj instanceof FrameImpl)?
        new WindowEventMgrForFrame(this.swingObj):
        new WindowEventMgr(this.swingObj);
      this.aWindowImpl.getZWindow().addWindowListener(this.windowEventMgr);
    }

    void installWindowListener(ListenerHItem L) {
      this.windowEventMgr.installListener(L);
    }

    void uninstallWindowListener(ListenerHItem L) {
      this.windowEventMgr.uninstallListener(L);
    }
  }


// Swing object - frame

  public void sni_frame_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem frame) {
    helper.setReturnValue(((FrameHItem)frame).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_frame_as_window(RNativeImplHelper helper, RClosureItem self, RObjItem frame) {
    helper.setReturnValue(((FrameHItem)frame).impl.getAWindowImplAdapter().aWindowHItem);
  }

  public void sni_create_frame_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem title) {
    String t = helper.arrayItemToCstr((RArrayItem)title).toJavaString();
    helper.setReturnValue(this.createFrameImpl(t));
  }

  public void sni_frame_impl_set_default_close_operation(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem frame, RObjItem op) {
    FrameImpl i = ((FrameHItem)frame).impl;
    RStructItem o = (RStructItem)op;
    RDataConstr dc = o.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a position.");
    }
    int x = 0;
    String constr = dc.getName();
    if (constr.equals("do_nothing_on_frame_close$")) {
      x = JFrame.DO_NOTHING_ON_CLOSE;
    } else if (constr.equals("hide_on_frame_close$")) {
      x = JFrame.HIDE_ON_CLOSE;
    } else if (constr.equals("dispose_on_frame_close$")) {
      x = JFrame.DISPOSE_ON_CLOSE;
    } else if (constr.equals("exit_on_frame_close$")) {
      x = JFrame.EXIT_ON_CLOSE;
    } else {
      throw new IllegalArgumentException("Not a position.");
    }
    int ox = x;  // make effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.actualCloseOperation = ox;
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_frame_impl_get_content_pane(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem frame) {
    FrameImpl i = ((FrameHItem)frame).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return ((APanelImpl)i.getContentPane()).getAPanelImplAdapter().aPanelHItem;
      }
    }));
  }

  public void sni_frame_impl_set_title(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem frame, RObjItem title) {
    FrameImpl i = ((FrameHItem)frame).impl;
    String t = helper.arrayItemToCstr((RArrayItem)title).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setTitle(t);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_frame_impl_set_menu_bar(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem frame, RObjItem menuBar) {
    FrameImpl i = ((FrameHItem)frame).impl;
    JMenuBar b = ((MenuBarHItem)menuBar).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setJMenuBar(b);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  FrameHItem createFrameImpl(String title) {
    RClientHelper ch = this.getClientHelper();
    FrameImpl impl = new FrameImpl(title);
    FrameHItem h = new FrameHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "frame_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AWindowImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class FrameImpl extends JFrame implements AWindowImpl {
    AWindowImplAdapter implAdapter;
    int actualCloseOperation;

    FrameImpl(String title) {
      super(title);
      BorderLayoutPanelImpl p = SNIswing.this.createBorderLayoutPanelImpl().impl;
      this.setContentPane(p);
      this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public Window getZWindow() { return this; }
    public AWindowImplAdapter getAWindowImplAdapter() { return this.implAdapter; }
  }

  public class FrameHItem extends RObjItem {
    FrameImpl impl;

    FrameHItem(FrameImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "frame_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - dialog

  public void sni_dialog_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem dialog) {
    helper.setReturnValue(((DialogHItem)dialog).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_dialog_as_window(RNativeImplHelper helper, RClosureItem self, RObjItem dialog) {
    helper.setReturnValue(((DialogHItem)dialog).impl.getAWindowImplAdapter().aWindowHItem);
  }

  public void sni_create_dialog_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem owner, RObjItem title, RObjItem modality) {
    Window o = ((AWindowHItem)owner).impl.getZWindow();
    String t = helper.arrayItemToCstr((RArrayItem)title).toJavaString();
    RStructItem m = (RStructItem)modality;
    RDataConstr dc = m.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a modality.");
    }
    Dialog.ModalityType x = null;
    String constr = dc.getName();
    if (constr.equals("application_modal$")) {
      x = Dialog.ModalityType.APPLICATION_MODAL;
    } else if (constr.equals("document_modal$")) {
      x = Dialog.ModalityType.DOCUMENT_MODAL;
    } else if (constr.equals("modeless$")) {
      x = Dialog.ModalityType.MODELESS;
    } else {
      throw new IllegalArgumentException("Not a modalithy.");
    }
    helper.setReturnValue(this.createDialogImpl(o, t, x));
  }

  public void sni_dialog_impl_set_default_close_operation(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem dialog, RObjItem op) {
    DialogImpl i = ((DialogHItem)dialog).impl;
    RStructItem o = (RStructItem)op;
    RDataConstr dc = o.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a position.");
    }
    int x = 0;
    String constr = dc.getName();
    if (constr.equals("do_nothing_on_dialog_close$")) {
      x = JDialog.DO_NOTHING_ON_CLOSE;
    } else if (constr.equals("hide_on_dialog_close$")) {
      x = JDialog.HIDE_ON_CLOSE;
    } else if (constr.equals("dispose_on_dialog_close$")) {
      x = JDialog.DISPOSE_ON_CLOSE;
    } else {
      throw new IllegalArgumentException("Not a position.");
    }
    int ox = x;  // make effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setDefaultCloseOperation(ox);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_dialog_impl_get_content_pane(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem dialog) {
    DialogImpl i = ((DialogHItem)dialog).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return ((APanelImpl)i.getContentPane()).getAPanelImplAdapter().aPanelHItem;
      }
    }));
  }

// implementation

  DialogHItem createDialogImpl(Window owner, String title, Dialog.ModalityType modality) {
    RClientHelper ch = this.getClientHelper();
    DialogImpl impl = new DialogImpl(owner, title, modality);
    DialogHItem h = new DialogHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "dialog_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AWindowImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class DialogImpl extends JDialog implements AWindowImpl {
    AWindowImplAdapter implAdapter;

    DialogImpl(Window owner, String title, Dialog.ModalityType modality) {
      super(owner, title, modality);
      BorderLayoutPanelImpl p = SNIswing.this.createBorderLayoutPanelImpl().impl;
      this.setContentPane(p);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public Window getZWindow() { return this; }
    public AWindowImplAdapter getAWindowImplAdapter() { return this.implAdapter; }
  }

  public class DialogHItem extends RObjItem {
    DialogImpl impl;

    DialogHItem(DialogImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "dialog_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - menu bar

  public void sni_create_menu_bar_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createMenuBarImpl());
  }

  public void sni_menu_bar_impl_add_menu(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem menuBar, RObjItem menu) {
    MenuBarImpl i = ((MenuBarHItem)menuBar).impl;
    JMenu m = ((MenuHItem)menu).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.add(m);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  MenuBarHItem createMenuBarImpl() {
    RClientHelper ch = this.getClientHelper();
    MenuBarImpl impl = new MenuBarImpl();
    MenuBarHItem h = new MenuBarHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "menu_bar_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AJComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class MenuBarImpl extends JMenuBar implements AJComponentImpl {
    AJComponentImplAdapter implAdapter;

    MenuBarImpl() {
      super();
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
  }

  public class MenuBarHItem extends RObjItem {
    MenuBarImpl impl;

    MenuBarHItem(MenuBarImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "menu_bar_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - menu

  public void sni_create_menu_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem text) {
    String t= helper.arrayItemToCstr((RArrayItem)text).toJavaString();
    helper.setReturnValue(this.createMenuImpl(t));
  }

  public void sni_menu_impl_add_menu_item(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem menu, RObjItem menuItem) {
    MenuImpl i = ((MenuHItem)menu).impl;
    JMenuItem m = ((MenuItemHItem)menuItem).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.add(m);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_menu_impl_add_menu(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem menu, RObjItem submenu) {
    MenuImpl i = ((MenuHItem)menu).impl;
    JMenu m = ((MenuHItem)submenu).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.add(m);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  MenuHItem createMenuImpl(String text) {
    RClientHelper ch = this.getClientHelper();
    MenuImpl impl = new MenuImpl(text);
    MenuHItem h = new MenuHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "menu_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AJComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class MenuImpl extends JMenu implements AJComponentImpl {
    AJComponentImplAdapter implAdapter;

    MenuImpl(String text) {
      super(text);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; } 
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; } 
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
  }

  public class MenuHItem extends RObjItem {
    MenuImpl impl;

    MenuHItem(MenuImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "menu_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - popup menu

  public void sni_create_popup_menu_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createPopupMenuImpl());
  }

  public void sni_popup_menu_impl_add_menu_item(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem menu, RObjItem menuItem) {
    PopupMenuImpl i = ((PopupMenuHItem)menu).impl;
    JMenuItem m = ((MenuItemHItem)menuItem).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.add(m);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_popup_menu_impl_add_menu(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem menu, RObjItem submenu) {
    PopupMenuImpl i = ((PopupMenuHItem)menu).impl;
    JMenu m = ((MenuHItem)submenu).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.add(m);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_popup_menu_impl_set_visible(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem window, RObjItem sw) {
    JPopupMenu i = ((PopupMenuHItem)window).impl;
    boolean b = helper.boolItemToBoolean((RStructItem)sw);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setVisible(b);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  PopupMenuHItem createPopupMenuImpl() {
    RClientHelper ch = this.getClientHelper();
    PopupMenuImpl impl = new PopupMenuImpl();
    PopupMenuHItem h = new PopupMenuHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "popup_menu_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AJComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class PopupMenuImpl extends JPopupMenu implements AJComponentImpl {
    AJComponentImplAdapter implAdapter;

    PopupMenuImpl() {
      super();
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; } 
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; } 
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
  }

  public class PopupMenuHItem extends RObjItem {
    PopupMenuImpl impl;

    PopupMenuHItem(PopupMenuImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "popup_menu_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - menu item

  public void sni_menu_item_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem menuItem) {
    helper.setReturnValue(((MenuItemHItem)menuItem).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_menu_item_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem menuItem) {
    helper.setReturnValue(((MenuItemHItem)menuItem).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_menu_item_as_abutton(RNativeImplHelper helper, RClosureItem self, RObjItem menuItem) {
    helper.setReturnValue(((MenuItemHItem)menuItem).impl.getAButtonImplAdapter().aAButtonHItem);
  }

  public void sni_create_menu_item_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem text) {
    String t = helper.arrayItemToCstr((RArrayItem)text).toJavaString();
    helper.setReturnValue(this.createMenuItemImpl(t));
  }

// implementation

  MenuItemHItem createMenuItemImpl(String text) {
    RClientHelper ch = this.getClientHelper();
    MenuItemImpl impl = new MenuItemImpl(text);
    MenuItemHItem h = new MenuItemHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "menu_item_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AButtonImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class MenuItemImpl extends JMenuItem implements AButtonImpl {
    AButtonImplAdapter implAdapter;

    MenuItemImpl(String text) {
      super(text);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
    public AbstractButton getZAButton() { return this; }
    public AButtonImplAdapter getAButtonImplAdapter() { return this.implAdapter; }
  }

  public class MenuItemHItem extends RObjItem {
    MenuItemImpl impl;

    MenuItemHItem(MenuItemImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "menu_item_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


//  Swing object - panel

  public void sni_a_panel_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((APanelHItem)panel).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_a_panel_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((APanelHItem)panel).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_a_panel_instance(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((APanelHItem)panel).impl.getSwingObjAdapter().swingObjItem);
  }

  public void sni_a_panel_impl_add_component(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem panel, RObjItem comp) {
    APanelImpl i = ((APanelHItem)panel).impl;
    Component c = ((AComponentHItem)comp).impl.getZComponent();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getZPanel().add(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  interface APanelImpl extends AJComponentImpl {
    JPanel getZPanel();
    APanelImplAdapter getAPanelImplAdapter();
  }

  class APanelHItem extends RObjItem {
    APanelImpl impl;

    APanelHItem(APanelImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "a_panel_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }

  class APanelImplAdapter extends AJComponentImplAdapter {
    APanelImpl aPanelImpl;
    APanelHItem aPanelHItem;

    APanelImplAdapter(APanelImpl aPanelImpl, RObjItem handleItem, RObjItem swingObjItem) {
      super(aPanelImpl, handleItem, swingObjItem);
      this.aPanelImpl = aPanelImpl;
      this.aPanelHItem = new APanelHItem(aPanelImpl);
    }

    void init() {
      super.init();
    }
  }


// Swing object - border layout panel

  public void sni_border_layout_panel_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((BorderLayoutPanelHItem)panel).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_border_layout_panel_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((BorderLayoutPanelHItem)panel).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_border_layout_panel_as_panel(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((BorderLayoutPanelHItem)panel).impl.getAPanelImplAdapter().aPanelHItem);
  }

  public void sni_create_border_layout_panel_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createBorderLayoutPanelImpl());
  }

  public void sni_border_layout_panel_impl_add_component(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem panel, RObjItem comp, RObjItem pos) {
    BorderLayoutPanelImpl i = ((BorderLayoutPanelHItem)panel).impl;
    Component c = ((AComponentHItem)comp).impl.getZComponent();
    RStructItem p = (RStructItem)pos;
    RDataConstr dc = p.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a position.");
    }
    String x = null;
    String constr = dc.getName();
    if (constr.equals("border_layout_center$")) {
      x = BorderLayout.CENTER;
    } else if (constr.equals("border_layout_north$")) {
      x = BorderLayout.NORTH;
    } else if (constr.equals("border_layout_south$")) {
      x = BorderLayout.SOUTH;
    } else if (constr.equals("border_layout_east$")) {
      x = BorderLayout.EAST;
    } else if (constr.equals("border_layout_west$")) {
      x = BorderLayout.WEST;
    } else {
      throw new IllegalArgumentException("Not a position.");
    }
    String px = x;  // make effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.add(c, px);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  BorderLayoutPanelHItem createBorderLayoutPanelImpl() {
    RClientHelper ch = this.getClientHelper();
    BorderLayoutPanelImpl impl = new BorderLayoutPanelImpl();
    BorderLayoutPanelHItem h = new BorderLayoutPanelHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "border_layout_panel_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new APanelImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class BorderLayoutPanelImpl extends JPanel implements APanelImpl {
    APanelImplAdapter implAdapter;

    BorderLayoutPanelImpl() {
      super(new BorderLayout());
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
    public JPanel getZPanel() { return this; }
    public APanelImplAdapter getAPanelImplAdapter() { return this.implAdapter; }

    public void addZComponent(Component c) {
      this.add(c, BorderLayout.CENTER);
    }
  }

  public class BorderLayoutPanelHItem extends RObjItem {
    BorderLayoutPanelImpl impl;

    BorderLayoutPanelHItem(BorderLayoutPanelImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "border_layout_panel_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - grid layout panel

  public void sni_grid_layout_panel_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((GridLayoutPanelHItem)panel).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_grid_layout_panel_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((GridLayoutPanelHItem)panel).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_grid_layout_panel_as_panel(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((GridLayoutPanelHItem)panel).impl.getAPanelImplAdapter().aPanelHItem);
  }

  public void sni_create_grid_layout_panel_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem rows, RObjItem columns) {
    helper.setReturnValue(this.createGridLayoutPanelImpl(((RIntItem)rows).getValue(), ((RIntItem)columns).getValue()));
  }

// implementation

  GridLayoutPanelHItem createGridLayoutPanelImpl(int rows, int columns) {
    RClientHelper ch = this.getClientHelper();
    GridLayoutPanelImpl impl = new GridLayoutPanelImpl(rows, columns);
    GridLayoutPanelHItem h = new GridLayoutPanelHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "grid_layout_panel_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new APanelImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class GridLayoutPanelImpl extends JPanel implements APanelImpl {
    APanelImplAdapter implAdapter;

    GridLayoutPanelImpl(int rows, int columns) {
      super(new GridLayout(rows, columns));
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
    public JPanel getZPanel() { return this; }
    public APanelImplAdapter getAPanelImplAdapter() { return this.implAdapter; }

    public void addZComponent(Component c) {
      this.add(c);
    }
  }

  public class GridLayoutPanelHItem extends RObjItem {
    GridLayoutPanelImpl impl;

    GridLayoutPanelHItem(GridLayoutPanelImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "grid_layout_panel_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - scroll pane

  public void sni_scroll_pane_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem pane) {
    helper.setReturnValue(((ScrollPaneHItem)pane).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_scroll_pane_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem panel) {
    helper.setReturnValue(((ScrollPaneHItem)panel).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_create_scroll_pane_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createScrollPaneImpl());
  }

  public void sni_scroll_pane_impl_add_component(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem pane, RObjItem comp) {
    ScrollPaneImpl i = ((ScrollPaneHItem)pane).impl;
    Component c = ((AComponentHItem)comp).impl.getZComponent();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getViewport().add(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_scroll_pane_impl_set_horizontal_scroll_bar_policy(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem pane, RObjItem pol) {
    ScrollPaneImpl i = ((ScrollPaneHItem)pane).impl;
    RDataConstr dc = ((RStructItem)pol).getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a scroll bar policy.");
    }
    int p = 0;
    String constr = dc.getName();
    if (constr.equals("scroll_bar_never$")) {
      p = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;
    } else if (constr.equals("scroll_bar_as_needed$")) {
      p = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED;
    } else if (constr.equals("scroll_bar_always$")) {
      p = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS;
    } else {
      throw new IllegalArgumentException("Not a scroll bar policy.");
    }
    int px = p;  // effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setHorizontalScrollBarPolicy(px);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_scroll_pane_impl_set_vertical_scroll_bar_policy(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem pane, RObjItem pol) {
    ScrollPaneImpl i = ((ScrollPaneHItem)pane).impl;
    RDataConstr dc = ((RStructItem)pol).getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a scroll bar policy.");
    }
    int p = 0;
    String constr = dc.getName();
    if (constr.equals("scroll_bar_never$")) {
      p = JScrollPane.VERTICAL_SCROLLBAR_NEVER;
    } else if (constr.equals("scroll_bar_as_needed$")) {
      p = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED;
    } else if (constr.equals("scroll_bar_always$")) {
      p = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS;
    } else {
      throw new IllegalArgumentException("Not a scroll bar policy.");
    }
    int px = p;  // effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setVerticalScrollBarPolicy(px);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  ScrollPaneHItem createScrollPaneImpl() {
    RClientHelper ch = this.getClientHelper();
    ScrollPaneImpl impl = new ScrollPaneImpl();
    ScrollPaneHItem h = new ScrollPaneHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "scroll_pane_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AJComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class ScrollPaneImpl extends JScrollPane implements AJComponentImpl {
    AJComponentImplAdapter implAdapter;

    ScrollPaneImpl() {
      super();
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
  }

  public class ScrollPaneHItem extends RObjItem {
    ScrollPaneImpl impl;

    ScrollPaneHItem(ScrollPaneImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "scroll_pane_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - option pane

  public void sni_show_message_dialog_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem owner, RObjItem title, RObjItem msgType, RObjItem msg) {
    Component o = ((AComponentHItem)owner).impl.getZComponent();
    String t = helper.arrayItemToCstr((RArrayItem)title).toJavaString();
    int mt = this.msgTypeItemToInt(msgType);
    Component m = ((AComponentHItem)msg).impl.getZComponent();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        JOptionPane.showMessageDialog(o, m, t, mt);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_show_confirm_dialog_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem owner, RObjItem title, RObjItem msgType, RObjItem msg, RObjItem optType) {
    Component o = ((AComponentHItem)owner).impl.getZComponent();
    String t = helper.arrayItemToCstr((RArrayItem)title).toJavaString();
    int mt = this.msgTypeItemToInt(msgType);
    Component m = ((AComponentHItem)msg).impl.getZComponent();
    int ot = this.optionTypeItemToInt(optType);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        int a = JOptionPane.showConfirmDialog(o, m, t, ot, mt);
        RClientHelper ch = SNIswing.this.getClientHelper();
        RObjItem sel;
        switch (a) {
        case JOptionPane.YES_OPTION:
          sel = ch.getStructItem(helper.getDataConstr(MOD_NAME, "confirmed_yes$"), new RObjItem[0]);
          break;
        case JOptionPane.NO_OPTION:
          sel = ch.getStructItem(helper.getDataConstr(MOD_NAME, "confirmed_no$"), new RObjItem[0]);
          break;
        default:
          sel = ch.getStructItem(helper.getDataConstr(MOD_NAME, "confirmed_cancel$"), new RObjItem[0]);
          break;
        }
        return sel;
      }
    }));
  }


// Swing object - border

  public void sni_a_border_instance(RNativeImplHelper helper, RClosureItem self, RObjItem border) {
    helper.setReturnValue(((ABorderHItem)border).impl.getSwingObjAdapter().swingObjItem);
  }

// implementation

  interface ABorderImpl extends SwingObj {
    Border getZBorder();
    ABorderImplAdapter getABorderImplAdapter();
  }

  class ABorderHItem extends RObjItem {
    ABorderImpl impl;

    ABorderHItem(ABorderImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "a_border_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }

  class ABorderImplAdapter extends SwingObjAdapter {
    ABorderImpl aBorderImpl;
    ABorderHItem aBorderHItem;

    ABorderImplAdapter(ABorderImpl aBorderImpl, RObjItem handleItem, RObjItem swingObjItem) {
      super(aBorderImpl, handleItem, swingObjItem);
      this.aBorderImpl = aBorderImpl;
      this.aBorderHItem = new ABorderHItem(aBorderImpl);
    }

    void init() {
      super.init();
    }
  }


// Swing object - empty border

  public void sni_empty_border_as_border(RNativeImplHelper helper, RClosureItem self, RObjItem border) {
    helper.setReturnValue(((EmptyBorderHItem)border).impl.getABorderImplAdapter().aBorderHItem);
  }

  public void sni_create_empty_border_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem insets) {
    int[] ii = this.unpackInsetsItem(insets);
    helper.setReturnValue(this.createEmptyBorderImpl(ii[0], ii[1], ii[2], ii[3]));
  }

// implementation

  EmptyBorderHItem createEmptyBorderImpl(int top, int left, int bottom, int right) {
    RClientHelper ch = this.getClientHelper();
    EmptyBorderImpl impl = new EmptyBorderImpl(top, left, bottom, right);
    EmptyBorderHItem h = new EmptyBorderHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "empty_border_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new ABorderImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class EmptyBorderImpl extends EmptyBorder implements ABorderImpl {
    ABorderImplAdapter implAdapter;

    EmptyBorderImpl(int top, int left, int bottom, int right) {
      super(top, left, bottom, right);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Border getZBorder() { return this; }
    public ABorderImplAdapter getABorderImplAdapter() { return implAdapter; }
  }

  public class EmptyBorderHItem extends RObjItem {
    EmptyBorderImpl impl;

    EmptyBorderHItem(EmptyBorderImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "empty_border_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - etched border

  public void sni_etched_border_as_border(RNativeImplHelper helper, RClosureItem self, RObjItem border) {
    helper.setReturnValue(((EtchedBorderHItem)border).impl.getABorderImplAdapter().aBorderHItem);
  }

  public void sni_create_etched_border_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem etchType) {
    RStructItem et = (RStructItem)etchType;
    RDataConstr dc = et.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a etch type.");
    }
    int x = 0;
    String constr = dc.getName();
    if (constr.equals("raised_etch$")) {
      x = EtchedBorder.RAISED;
    } else if (constr.equals("lowered_etch$")) {
      x = EtchedBorder.LOWERED;
    } else {
      throw new IllegalArgumentException("Not a etch type.");
    }
    helper.setReturnValue(this.createEtchedBorderImpl(x));
  }

// implementation

  EtchedBorderHItem createEtchedBorderImpl(int etchType) {
    RClientHelper ch = this.getClientHelper();
    EtchedBorderImpl impl = new EtchedBorderImpl(etchType);
    EtchedBorderHItem h = new EtchedBorderHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "etched_border_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new ABorderImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class EtchedBorderImpl extends EtchedBorder implements ABorderImpl {
    ABorderImplAdapter implAdapter;

    EtchedBorderImpl(int etchType) {
      super(etchType);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Border getZBorder() { return this; }
    public ABorderImplAdapter getABorderImplAdapter() { return implAdapter; }
  }

  public class EtchedBorderHItem extends RObjItem {
    EtchedBorderImpl impl;

    EtchedBorderHItem(EtchedBorderImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "etched_border_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - titled border

  public void sni_titled_border_as_border(RNativeImplHelper helper, RClosureItem self, RObjItem border) {
    helper.setReturnValue(((TitledBorderHItem)border).impl.getABorderImplAdapter().aBorderHItem);
  }

  public void sni_create_titled_border_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem border, RObjItem title) {
    Border b = ((ABorderHItem)border).impl.getZBorder();
    String t = helper.arrayItemToCstr((RArrayItem)title).toJavaString();
    helper.setReturnValue(this.createTitledBorderImpl(b, t));
  }

// implementation

  TitledBorderHItem createTitledBorderImpl(Border border, String title) {
    RClientHelper ch = this.getClientHelper();
    TitledBorderImpl impl = new TitledBorderImpl(border, title);
    TitledBorderHItem h = new TitledBorderHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "titled_border_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new ABorderImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class TitledBorderImpl extends TitledBorder implements ABorderImpl {
    ABorderImplAdapter implAdapter;

    TitledBorderImpl(Border border, String title) {
      super(border, title);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Border getZBorder() { return this; }
    public ABorderImplAdapter getABorderImplAdapter() { return implAdapter; }
  }

  public class TitledBorderHItem extends RObjItem {
    TitledBorderImpl impl;

    TitledBorderHItem(TitledBorderImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "titled_border_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - abstract button

  public void sni_a_abutton_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem button) {
    helper.setReturnValue(((AAButtonHItem)button).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_a_abutton_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem button) {
    helper.setReturnValue(((AAButtonHItem)button).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_a_abutton_instance(RNativeImplHelper helper, RClosureItem self, RObjItem button) {
    helper.setReturnValue(((AAButtonHItem)button).impl.getSwingObjAdapter().swingObjItem);
  }

  public void sni_a_abutton_impl_install_action_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem button, RObjItem lis) {
    AButtonImpl i = ((AAButtonHItem)button).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAButtonImplAdapter().installActionListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_abutton_impl_uninstall_action_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem button, RObjItem lis) {
    AButtonImpl i = ((AAButtonHItem)button).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getAButtonImplAdapter().uninstallActionListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_abutton_impl_set_action_command(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem button, RObjItem command) {
    AbstractButton i = ((AAButtonHItem)button).impl.getZAButton();
    String c = helper.arrayItemToCstr((RArrayItem)command).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setActionCommand(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_abutton_impl_get_text(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem button) {
    AbstractButton i = ((AAButtonHItem)button).impl.getZAButton();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return SNIswing.this.getClientHelper().cstrToArrayItem(new Cstr(i.getText()));
      }
    }));
  }

  public void sni_a_abutton_impl_set_text(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem button, RObjItem text) {
    AbstractButton i = ((AAButtonHItem)button).impl.getZAButton();
    String t = helper.arrayItemToCstr((RArrayItem)text).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setText(t);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_abutton_impl_selected_Q_(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem button) {
    AbstractButton i = ((AAButtonHItem)button).impl.getZAButton();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return SNIswing.this.getClientHelper().getBoolItem(i.isSelected());
      }
    }));
  }

  public void sni_a_abutton_impl_set_selected(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem button, RObjItem sw) {
    AbstractButton i = ((AAButtonHItem)button).impl.getZAButton();
    boolean b = helper.boolItemToBoolean((RStructItem)sw);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setSelected(b);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  interface AButtonImpl extends AJComponentImpl {
    AbstractButton getZAButton();
    AButtonImplAdapter getAButtonImplAdapter();
  }

  class AAButtonHItem extends RObjItem {
    AButtonImpl impl;

    AAButtonHItem(AButtonImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "a_button_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }

  class AButtonImplAdapter extends AJComponentImplAdapter {
    AButtonImpl aButtonImpl;
    AAButtonHItem aAButtonHItem;
    ActionEventMgr actionEventMgr;

    AButtonImplAdapter(AButtonImpl aButtonImpl, RObjItem handleItem, RObjItem swingObjItem) {
      super(aButtonImpl, handleItem, swingObjItem);
      this.aButtonImpl = aButtonImpl;
      this.aAButtonHItem = new AAButtonHItem(aButtonImpl);
    }

    void init() {
      super.init();
      this.actionEventMgr = new ActionEventMgr(this.swingObj);
      this.aButtonImpl.getZAButton().addActionListener(this.actionEventMgr);
    }

    void installActionListener(ListenerHItem L) {
      this.actionEventMgr.installListener(L);
    }

    void uninstallActionListener(ListenerHItem L) {
      this.actionEventMgr.uninstallListener(L);
    }
  }


// Swing object - button

  public void sni_button_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem button) {
    helper.setReturnValue(((ButtonHItem)button).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_button_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem button) {
    helper.setReturnValue(((ButtonHItem)button).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_button_as_abutton(RNativeImplHelper helper, RClosureItem self, RObjItem button) {
    helper.setReturnValue(((ButtonHItem)button).impl.getAButtonImplAdapter().aAButtonHItem);
  }

  public void sni_create_button_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createButtonImpl());
  }

// implementation

  ButtonHItem createButtonImpl() {
    RClientHelper ch = this.getClientHelper();
    ButtonImpl impl = new ButtonImpl();
    ButtonHItem h = new ButtonHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "button_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AButtonImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class ButtonImpl extends JButton implements AButtonImpl {
    AButtonImplAdapter implAdapter;

    ButtonImpl() {
      super("");  // ensure text != null
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
    public AbstractButton getZAButton() { return this; }
    public AButtonImplAdapter getAButtonImplAdapter() { return this.implAdapter; }
  }

  public class ButtonHItem extends RObjItem {
    ButtonImpl impl;

    ButtonHItem(ButtonImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "button_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - check box

  public void sni_check_box_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem checkBox) {
    helper.setReturnValue(((CheckBoxHItem)checkBox).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_check_box_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem checkBox) {
    helper.setReturnValue(((CheckBoxHItem)checkBox).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_check_box_as_abutton(RNativeImplHelper helper, RClosureItem self, RObjItem checkBox) {
    helper.setReturnValue(((CheckBoxHItem)checkBox).impl.getAButtonImplAdapter().aAButtonHItem);
  }

  public void sni_create_check_box_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createCheckBoxImpl());
  }

// implementation

  CheckBoxHItem createCheckBoxImpl() {
    RClientHelper ch = this.getClientHelper();
    CheckBoxImpl impl = new CheckBoxImpl();
    CheckBoxHItem h = new CheckBoxHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "check_box_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AButtonImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class CheckBoxImpl extends JCheckBox implements AButtonImpl {
    AButtonImplAdapter implAdapter;

    CheckBoxImpl() {
      super("");  // ensure text != null
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
    public AbstractButton getZAButton() { return this; }
    public AButtonImplAdapter getAButtonImplAdapter() { return this.implAdapter; }
  }

  public class CheckBoxHItem extends RObjItem {
    CheckBoxImpl impl;

    CheckBoxHItem(CheckBoxImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "check_box_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - radio button

  public void sni_radio_button_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem radioButton) {
    helper.setReturnValue(((RadioButtonHItem)radioButton).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_radio_button_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem radioButton) {
    helper.setReturnValue(((RadioButtonHItem)radioButton).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_radio_button_as_abutton(RNativeImplHelper helper, RClosureItem self, RObjItem radioButton) {
    helper.setReturnValue(((RadioButtonHItem)radioButton).impl.getAButtonImplAdapter().aAButtonHItem);
  }

  public void sni_create_radio_button_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createRadioButtonImpl());
  }

// implementation

  RadioButtonHItem createRadioButtonImpl() {
    RClientHelper ch = this.getClientHelper();
    RadioButtonImpl impl = new RadioButtonImpl();
    RadioButtonHItem h = new RadioButtonHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "radio_button_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AButtonImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class RadioButtonImpl extends JRadioButton implements AButtonImpl {
    AButtonImplAdapter implAdapter;

    RadioButtonImpl() {
      super("");  // ensure text != null
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
    public AbstractButton getZAButton() { return this; }
    public AButtonImplAdapter getAButtonImplAdapter() { return this.implAdapter; }
  }

  public class RadioButtonHItem extends RObjItem {
    RadioButtonImpl impl;

    RadioButtonHItem(RadioButtonImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "radio_button_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - button group

  public void sni_create_button_group_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createButtonGroupImpl());
  }

  public void sni_button_group_impl_add_button(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem buttonGroup, RObjItem radioButton) {
    ButtonGroup i = ((ButtonGroupHItem)buttonGroup).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.add(((RadioButtonHItem)radioButton).impl);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  ButtonGroupHItem createButtonGroupImpl() {
    RClientHelper ch = this.getClientHelper();
    ButtonGroupImpl impl = new ButtonGroupImpl();
    ButtonGroupHItem h = new ButtonGroupHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "button_group_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new SwingObjAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class ButtonGroupImpl extends ButtonGroup implements SwingObj {
    SwingObjAdapter implAdapter;

    ButtonGroupImpl() {
      super();
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
  }

  public class ButtonGroupHItem extends RObjItem {
    ButtonGroupImpl impl;

    ButtonGroupHItem(ButtonGroupImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "button_group_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - label

  public void sni_label_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem label) {
    helper.setReturnValue(((LabelHItem)label).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_label_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem label) {
    helper.setReturnValue(((LabelHItem)label).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_create_label_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem text) {
    String t= helper.arrayItemToCstr((RArrayItem)text).toJavaString();
    helper.setReturnValue(this.createLabelImpl(t));
  }

  public void sni_label_impl_get_text(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem label) {
    LabelImpl i = ((LabelHItem)label).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return SNIswing.this.getClientHelper().cstrToArrayItem(new Cstr(i.getText()));
      }
    }));
  }

  public void sni_label_impl_set_text(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem label, RObjItem text) {
    LabelImpl i = ((LabelHItem)label).impl;
    String t = helper.arrayItemToCstr((RArrayItem)text).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setText(t);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_label_impl_set_horizontal_alignment(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem label, RObjItem align) {
    LabelImpl i = ((LabelHItem)label).impl;
    RDataConstr dc = ((RStructItem)align).getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a horizontal alignment.");
    }
    int p = 0;
    String constr = dc.getName();
    if (constr.equals("horizontal_left$")) {
      p = SwingConstants.LEFT;
    } else if (constr.equals("horizontal_center$")) {
      p = SwingConstants.CENTER;
    } else if (constr.equals("horizontal_right$")) {
      p = SwingConstants.RIGHT;
    } else if (constr.equals("horizontal_leading$")) {
      p = SwingConstants.LEADING;
    } else if (constr.equals("horizontal_trailing$")) {
      p = SwingConstants.TRAILING;
    } else {
      throw new IllegalArgumentException("Not a horizontal alignment.");
    }
    int px = p;  // effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setHorizontalAlignment(px);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_label_impl_set_vertical_alignment(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem label, RObjItem align) {
    LabelImpl i = ((LabelHItem)label).impl;
    RDataConstr dc = ((RStructItem)align).getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a vertical alignment.");
    }
    int p = 0;
    String constr = dc.getName();
    if (constr.equals("vertical_top$")) {
      p = SwingConstants.TOP;
    } else if (constr.equals("vertical_center$")) {
      p = SwingConstants.CENTER;
    } else if (constr.equals("vertical_bottom$")) {
      p = SwingConstants.BOTTOM;
    } else {
      throw new IllegalArgumentException("Not a vertical alignment.");
    }
    int px = p;  // effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setVerticalAlignment(px);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  LabelHItem createLabelImpl(String text) {
    RClientHelper ch = this.getClientHelper();
    LabelImpl impl = new LabelImpl(text);
    LabelHItem h = new LabelHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "label_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AJComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class LabelImpl extends JLabel implements AJComponentImpl {
    AJComponentImplAdapter implAdapter;

    LabelImpl(String text) {
      super(text);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
  }

  public class LabelHItem extends RObjItem {
    LabelImpl impl;

    LabelHItem(LabelImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "label_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - image

  public void sni_create_image_impl_from_file(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem filePath) {
    String p = helper.arrayItemToCstr((RArrayItem)filePath).toJavaString();
    try {
      helper.setReturnValue(this.createImageImpl(p));
    } catch (Exception ex) {
      helper.setException(this.convertExceptionToSwingErrorExceptionItem(ex, helper.getExcInfo()));
    }
  }

// implementation

  ImageHItem createImageImpl(String filePath) throws IOException {
    Image i = ImageIO.read(new File(filePath));
    if (i == null) {
      throw new IOException("Image creation error. - " + filePath);
    }
    return new ImageHItem(i);
    // return new ImageHItem(ImageIO.read(new File(filePath)));
  }

  public class ImageHItem extends RObjItem {
    Image impl;

    ImageHItem(Image i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "image_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - icon

  public void sni_create_icon_impl(RNativeImplHelper helper, RClosureItem self, RObjItem image, RObjItem cx) {
    Image i = ((ImageHItem)image).impl;
    try {
      helper.setReturnValue(this.createIconImpl(i));
    } catch (Exception ex) {
      helper.setException(this.convertExceptionToSwingErrorExceptionItem(ex, helper.getExcInfo()));
    }
  }

// implementation

  IconHItem createIconImpl(Image image) {
    return new IconHItem(new ImageIcon(image));
  }

  public class IconHItem extends RObjItem {
    Icon impl;

    IconHItem(Icon i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "icon_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - canvas

  public void sni_canvas_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem canvas) {
    helper.setReturnValue(((CanvasHItem)canvas).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_canvas_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem canvas) {
    helper.setReturnValue(((CanvasHItem)canvas).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_create_canvas_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createCanvasImpl());
  }

  public void sni_canvas_impl_set_paint_actions(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem canvas, RObjItem paintActions) {
    CanvasImpl i = ((CanvasHItem)canvas).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setPaintActions(paintActions);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  CanvasHItem createCanvasImpl() {
    RClientHelper ch = this.getClientHelper();
    CanvasImpl impl = new CanvasImpl();
    CanvasHItem h = new CanvasHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "canvas_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AJComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class CanvasImpl extends JPanel implements AJComponentImpl {
    AJComponentImplAdapter implAdapter;
    List<PaintRequest> paintActions;

    CanvasImpl() {
      super();
      this.paintActions = new ArrayList<PaintRequest>();
      this.setOpaque(true);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }

    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      try {
        for (int i = 0; i < this.paintActions.size(); i++) {
          this.paintActions.get(i).run(this, g);
        }
      } catch (Exception ex) {
        ex.printStackTrace(System.err);
      }
    }

    void setPaintActions(RObjItem paintActions) {
      this.paintActions = SNIswing.this.parsePaintActions(paintActions);
      this.repaint();
    }
  }

  public class CanvasHItem extends RObjItem {
    CanvasImpl impl;

    CanvasHItem(CanvasImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "canvas_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - text component

  public void sni_a_text_component_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem textComp) {
    helper.setReturnValue(((ATextComponentHItem)textComp).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_a_text_component_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem textComp) {
    helper.setReturnValue(((ATextComponentHItem)textComp).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_a_text_component_instance(RNativeImplHelper helper, RClosureItem self, RObjItem textComp) {
    helper.setReturnValue(((ATextComponentHItem)textComp).impl.getSwingObjAdapter().swingObjItem);
  }

  public void sni_a_text_component_impl_get_document(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textComp) {
    JTextComponent i = ((ATextComponentHItem)textComp).impl.getZTextComponent();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return ((ADocumentImpl)i.getDocument()).getADocumentImplAdapter().aDocumentHItem;
      }
    }));
  }

  public void sni_a_text_component_impl_set_document(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textComp, RObjItem doc) {
    JTextComponent i = ((ATextComponentHItem)textComp).impl.getZTextComponent();
    Document d = ((ADocumentHItem)doc).impl.getZDocument();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setDocument(d);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_text_component_impl_get_text(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textComp) {
    JTextComponent i = ((ATextComponentHItem)textComp).impl.getZTextComponent();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return SNIswing.this.getClientHelper().cstrToArrayItem(new Cstr(i.getText()));
      }
    }));
  }

  public void sni_a_text_component_impl_set_text(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textComp, RObjItem text) {
    JTextComponent i = ((ATextComponentHItem)textComp).impl.getZTextComponent();
    String t = helper.arrayItemToCstr((RArrayItem)text).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setText(t);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  interface ATextComponentImpl extends AJComponentImpl {
    JTextComponent getZTextComponent();
    ATextComponentImplAdapter getATextComponentImplAdapter();
  }

  class ATextComponentHItem extends RObjItem {
    ATextComponentImpl impl;

    ATextComponentHItem(ATextComponentImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "a_text_component_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }

  class ATextComponentImplAdapter extends AJComponentImplAdapter {
    ATextComponentImpl aTextComponentImpl;
    ATextComponentHItem aTextComponentHItem;

    ATextComponentImplAdapter(ATextComponentImpl aTextComponentImpl, RObjItem handleItem, RObjItem swingObjItem) {
      super(aTextComponentImpl, handleItem, swingObjItem);
      this.aTextComponentImpl = aTextComponentImpl;
      this.aTextComponentHItem = new ATextComponentHItem(aTextComponentImpl);
    }

    void init() {
      super.init();
    }
  }


// Swing object - text field

  public void sni_text_field_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem textField) {
    helper.setReturnValue(((TextFieldHItem)textField).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_text_field_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem textField) {
    helper.setReturnValue(((TextFieldHItem)textField).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_text_field_as_text_component(RNativeImplHelper helper, RClosureItem self, RObjItem textField) {
    helper.setReturnValue(((TextFieldHItem)textField).impl.getATextComponentImplAdapter().aTextComponentHItem);
  }

  public void sni_create_text_field_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem doc) {
    Document d = ((ADocumentHItem)doc).impl.getZDocument();
    helper.setReturnValue(this.createTextFieldImpl(d));
  }

  public void sni_text_field_impl_install_action_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textField, RObjItem lis) {
    TextFieldImpl i = ((TextFieldHItem)textField).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.installActionListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_text_field_impl_uninstall_action_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textField, RObjItem lis) {
    TextFieldImpl i = ((TextFieldHItem)textField).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.uninstallActionListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_text_field_impl_set_action_command(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textField, RObjItem command) {
    TextFieldImpl i = ((TextFieldHItem)textField).impl;
    String c = helper.arrayItemToCstr((RArrayItem)command).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setActionCommand(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_text_field_impl_set_columns(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textField, RObjItem columns) {
    TextFieldImpl i = ((TextFieldHItem)textField).impl;
    int c = ((RIntItem)columns).getValue();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setColumns(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  TextFieldHItem createTextFieldImpl(Document doc) {
    RClientHelper ch = this.getClientHelper();
    TextFieldImpl impl = new TextFieldImpl(doc);
    TextFieldHItem h = new TextFieldHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "text_field_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new ATextComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class TextFieldImpl extends JTextField implements ATextComponentImpl {
    ATextComponentImplAdapter implAdapter;
    ActionEventMgr actionEventMgr;

    TextFieldImpl(Document doc) {
      super();
      this.setDocument(doc);
      this.actionEventMgr = new ActionEventMgr(this);
      this.addActionListener(this.actionEventMgr);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
    public JTextComponent getZTextComponent() { return this; }
    public ATextComponentImplAdapter getATextComponentImplAdapter() { return this.implAdapter; }

    void installActionListener(ListenerHItem L) {
      this.actionEventMgr.installListener(L);
    }

    void uninstallActionListener(ListenerHItem L) {
      this.actionEventMgr.uninstallListener(L);
    }
  }

  public class TextFieldHItem extends RObjItem {
    TextFieldImpl impl;

    TextFieldHItem(TextFieldImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "text_field_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - text area

  public void sni_text_area_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem textArea) {
    helper.setReturnValue(((TextAreaHItem)textArea).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_text_area_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem textArea) {
    helper.setReturnValue(((TextAreaHItem)textArea).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_text_area_as_text_component(RNativeImplHelper helper, RClosureItem self, RObjItem textArea) {
    helper.setReturnValue(((TextAreaHItem)textArea).impl.getATextComponentImplAdapter().aTextComponentHItem);
  }

  public void sni_create_text_area_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem doc) {
    Document d = ((ADocumentHItem)doc).impl.getZDocument();
    helper.setReturnValue(this.createTextAreaImpl(d));
  }

  public void sni_text_area_impl_set_columns(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textArea, RObjItem columns) {
    TextAreaImpl i = ((TextAreaHItem)textArea).impl;
    int c = ((RIntItem)columns).getValue();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setColumns(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_text_area_impl_set_rows(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textArea, RObjItem rows) {
    TextAreaImpl i = ((TextAreaHItem)textArea).impl;
    int r = ((RIntItem)rows).getValue();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setRows(r);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_text_area_impl_set_line_wrap(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textArea, RObjItem lineWrap) {
    TextAreaImpl i = ((TextAreaHItem)textArea).impl;
    RDataConstr dc = ((RStructItem)lineWrap).getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a line-wrap policy.");
    }
    boolean wrap = false;
    boolean wrapWord = false;
    String constr = dc.getName();
    if (constr.equals("no_wrap$")) {
      wrap = false;
      wrapWord = false;
    } else if (constr.equals("char_wrap$")) {
      wrap = true;
      wrapWord = false;
    } else if (constr.equals("word_wrap$")) {
      wrap = true;
      wrapWord = true;
    } else {
      throw new IllegalArgumentException("Not a line-wrap policy.");
    }
    boolean wx = wrap;  // make effectively final
    boolean wwx = wrapWord;  // make effectively final
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setLineWrap(wx);
        i.setWrapStyleWord(wwx);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_text_area_impl_get_document(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textArea) {
    TextAreaImpl i = ((TextAreaHItem)textArea).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return ((ADocumentImpl)i.getDocument()).getADocumentImplAdapter().aDocumentHItem;
      }
    }));
  }

  public void sni_text_area_impl_set_document(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textArea, RObjItem doc) {
    TextAreaImpl i = ((TextAreaHItem)textArea).impl;
    Document d = ((ADocumentHItem)doc).impl.getZDocument();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setDocument(d);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_text_area_impl_append(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem textArea, RObjItem text) {
    TextAreaImpl i = ((TextAreaHItem)textArea).impl;
    String t = helper.arrayItemToCstr((RArrayItem)text).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.append(t);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  TextAreaHItem createTextAreaImpl(Document doc) {
    RClientHelper ch = this.getClientHelper();
    TextAreaImpl impl = new TextAreaImpl(doc);
    TextAreaHItem h = new TextAreaHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "text_area_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new ATextComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class TextAreaImpl extends JTextArea implements ATextComponentImpl {
    ATextComponentImplAdapter implAdapter;

    TextAreaImpl(Document doc) {
      super();
      this.setDocument(doc);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }
    public JTextComponent getZTextComponent() { return this; }
    public ATextComponentImplAdapter getATextComponentImplAdapter() { return this.implAdapter; }
  }

  public class TextAreaHItem extends RObjItem {
    TextAreaImpl impl;

    TextAreaHItem(TextAreaImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "text_area_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - document

  public void sni_a_document_instance(RNativeImplHelper helper, RClosureItem self, RObjItem doc) {
    helper.setReturnValue(((ADocumentHItem)doc).impl.getSwingObjAdapter().swingObjItem);
  }

  public void sni_a_document_impl_install_document_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem doc, RObjItem lis) {
    ADocumentImpl i = ((ADocumentHItem)doc).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getADocumentImplAdapter().installDocumentListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_document_impl_uninstall_document_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem doc, RObjItem lis) {
    ADocumentImpl i = ((ADocumentHItem)doc).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.getADocumentImplAdapter().uninstallDocumentListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_a_document_impl_get_length(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem doc) {
    Document i = ((ADocumentHItem)doc).impl.getZDocument();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return SNIswing.this.getClientHelper().getIntItem(i.getLength());
      }
    }));
  }

  public void sni_a_document_impl_get_text(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem doc, RObjItem ofst, RObjItem len) {
    Document i = ((ADocumentHItem)doc).impl.getZDocument();
    int o = ((RIntItem)ofst).getValue();
    int L = ((RIntItem)len).getValue();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        return SNIswing.this.getClientHelper().cstrToArrayItem(new Cstr(i.getText(o, L)));
      }
    }));
  }

// implementation

  interface ADocumentImpl extends SwingObj {
    Document getZDocument();
    ADocumentImplAdapter getADocumentImplAdapter();
  }

  class ADocumentHItem extends RObjItem {
    ADocumentImpl impl;

    ADocumentHItem(ADocumentImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "a_document_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }

  class ADocumentImplAdapter extends SwingObjAdapter {
    ADocumentImpl aDocumentImpl;
    ADocumentHItem aDocumentHItem;
    DocumentEventMgr documentEventMgr;

    ADocumentImplAdapter(ADocumentImpl aDocumentImpl, RObjItem handleItem, RObjItem swingObjItem) {
      super(aDocumentImpl, handleItem, swingObjItem);
      this.aDocumentImpl = aDocumentImpl;
      this.aDocumentHItem = new ADocumentHItem(aDocumentImpl);
    }

    void init() {
      super.init();
      this.documentEventMgr = new DocumentEventMgr(this.swingObj);
      this.aDocumentImpl.getZDocument().addDocumentListener(this.documentEventMgr);
    }

    void installDocumentListener(ListenerHItem L) {
      this.documentEventMgr.installListener(L);
    }

    void uninstallDocumentListener(ListenerHItem L) {
      this.documentEventMgr.uninstallListener(L);
    }
  }


// Swing object - plain document

  public void sni_plain_document_as_document(RNativeImplHelper helper, RClosureItem self, RObjItem doc) {
    helper.setReturnValue(((PlainDocumentHItem)doc).impl.getADocumentImplAdapter().aDocumentHItem);
  }

  public void sni_create_plain_document_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createPlainDocumentImpl());
  }

// implementation

  PlainDocumentHItem createPlainDocumentImpl() {
    RClientHelper ch = this.getClientHelper();
    PlainDocumentImpl impl = new PlainDocumentImpl();
    PlainDocumentHItem h = new PlainDocumentHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "plain_document_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new ADocumentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class PlainDocumentImpl extends PlainDocument implements ADocumentImpl {
    ADocumentImplAdapter implAdapter;

    PlainDocumentImpl() {
      super();
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Document getZDocument() { return this; }
    public ADocumentImplAdapter getADocumentImplAdapter() { return this.implAdapter; }
  }

  public class PlainDocumentHItem extends RObjItem {
    PlainDocumentImpl impl;

    PlainDocumentHItem(PlainDocumentImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "plain_document_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - cursor

  public void sni_get_cursor_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem name) {
    String n = helper.arrayItemToCstr((RArrayItem)name).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        CursorHItem h = SNIswing.this.cursorTab.get(n);
        if (h == null) {
          Cursor c;
          if (n.equals("*default")) {
            c = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
          } else if (n.equals("*hand")) {
            c = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
          } else if (n.equals("*crosshair")) {
            c = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
          } else if (n.equals("*text")) {
            c = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
          } else if (n.equals("*move" )) {
            c = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
          } else if (n.equals("*wait")) {
            c = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
          } else if (n.equals("*north-resize")) {
            c = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
          } else if (n.equals("*northeast-resize")) {
            c = Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
          } else if (n.equals("*east-resize")) {
            c = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
          } else if (n.equals("*southeast-resize")) {
            c = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
          } else if (n.equals("*south-resize")) {
            c = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
          } else if (n.equals("*southwest-resize")) {
            c = Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
          } else if (n.equals("*west-resize")) {
            c = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
          } else if (n.equals("*northwest-resize")) {
            c = Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
          } else {
            c = Cursor.getSystemCustomCursor(n);
          }
          if (c == null) {
            throw new IllegalArgumentException("Invalid name. - " + n);
          }
          h = SNIswing.this.createCursorImpl(c);
          SNIswing.this.cursorTab.put(n, h);
        }
        return h;
      }
    }));
  }

// implementation

  CursorHItem createCursorImpl(Cursor cursor) {
    RClientHelper ch = this.getClientHelper();
    CursorImpl impl = new CursorImpl(cursor);
    CursorHItem h = new CursorHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "cursor_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new SwingObjAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class CursorImpl implements SwingObj {
    SwingObjAdapter implAdapter;
    Cursor cursor;

    CursorImpl(Cursor cursor) {
      super();
      this.cursor = cursor;
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
  }

  public class CursorHItem extends RObjItem {
    CursorImpl impl;

    CursorHItem(CursorImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "cursor_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - timer

  public void sni_create_timer_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem delay, RObjItem repeatInterval) {
    int d = ((RIntItem)delay).getValue();
    RObjItem i = this.getValueOfMaybeItem(repeatInterval);
    Integer ii = (i != null)? ((RIntItem)i).getValue(): null;
    helper.setReturnValue(this.createTimerImpl(d, ii));
  }

  public void sni_timer_impl_install_action_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem timer, RObjItem lis) {
    TimerImpl i = ((TimerHItem)timer).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.installActionListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_timer_impl_uninstall_action_listener(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem timer, RObjItem lis) {
    TimerImpl i = ((TimerHItem)timer).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.uninstallActionListener((ListenerHItem)lis);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_timer_impl_set_action_command(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem timer, RObjItem command) {
    TimerImpl i = ((TimerHItem)timer).impl;
    String c = helper.arrayItemToCstr((RArrayItem)command).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setActionCommand(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_timer_impl_start(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem timer) {
    TimerImpl i = ((TimerHItem)timer).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.start();
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_timer_impl_stop(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem timer) {
    TimerImpl i = ((TimerHItem)timer).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.stop();
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

// implementation

  TimerHItem createTimerImpl(int delay, Integer repeatInterval) {
    RClientHelper ch = this.getClientHelper();
    TimerImpl impl = new TimerImpl(delay, repeatInterval);
    TimerHItem h = new TimerHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "timer_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new SwingObjAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class TimerImpl extends Timer implements SwingObj {
    SwingObjAdapter implAdapter;
    ActionEventMgr actionEventMgr;

    TimerImpl(int delay, Integer repeatInterval) {
      super(delay, null);
      if (repeatInterval != null) {
        this.setRepeats(true);
        this.setDelay(repeatInterval);
      } else {
        this.setRepeats(false);
      }
      this.actionEventMgr = new ActionEventMgr(this);
      this.addActionListener(this.actionEventMgr);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }

    void installActionListener(ListenerHItem L) {
      this.actionEventMgr.installListener(L);
    }

    void uninstallActionListener(ListenerHItem L) {
      this.actionEventMgr.uninstallListener(L);
    }
  }

  public class TimerHItem extends RObjItem {
    TimerImpl impl;

    TimerHItem(TimerImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "timer_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// Swing object - file chooser

  public void sni_file_chooser_as_component(RNativeImplHelper helper, RClosureItem self, RObjItem fileChooser) {
    helper.setReturnValue(((FileChooserHItem)fileChooser).impl.getAComponentImplAdapter().aComponentHItem);
  }

  public void sni_file_chooser_as_jcomponent(RNativeImplHelper helper, RClosureItem self, RObjItem fileChooser) {
    helper.setReturnValue(((FileChooserHItem)fileChooser).impl.getAJComponentImplAdapter().aJComponentHItem);
  }

  public void sni_create_file_chooser_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx) {
    helper.setReturnValue(this.createFileChooserImpl());
  }

  public void sni_file_chooser_set_approve_button_text_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem fileChooser, RObjItem text) {
    FileChooserImpl i = ((FileChooserHItem)fileChooser).impl;
    String t = helper.arrayItemToCstr((RArrayItem)text).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setApproveButtonText(t);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_file_chooser_set_accessory_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem fileChooser, RObjItem compMaybe) {
    FileChooserImpl i = ((FileChooserHItem)fileChooser).impl;
    RObjItem comp = getValueOfMaybeItem(compMaybe);
    JComponent c = (comp != null)? ((AJComponentHItem)comp).impl.getZJComponent(): null;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setAccessory(c);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_file_chooser_set_file_selection_mode_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem fileChooser, RObjItem mode) {
    FileChooserImpl i = ((FileChooserHItem)fileChooser).impl;
    RStructItem m = (RStructItem)mode;
    RDataConstr dc = m.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(new Cstr("sgswing.swing"))) {
      throw new IllegalArgumentException("Not a mode item.");
    }
    String mn = dc.getName();
    int v = 0;
    if (mn.equals("file_chooser_files_only$")) {
      v = JFileChooser.FILES_ONLY;
    } else if (mn.equals("file_chooser_directories_only$")) {
      v = JFileChooser.DIRECTORIES_ONLY;
    } else if (mn.equals("file_chooser_files_and_directories$")) {
      v = JFileChooser.FILES_AND_DIRECTORIES;
    } else {
      throw new IllegalArgumentException("Not a file_chooser_selection_mode item.");
    }
    final int vv = v;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setFileSelectionMode(vv);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_file_chooser_set_multi_selection_enabled_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem fileChooser, RObjItem sw) {
    FileChooserImpl i = ((FileChooserHItem)fileChooser).impl;
    boolean  b = helper.boolItemToBoolean((RStructItem)sw);
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setMultiSelectionEnabled(b);
        return SNIswing.this.getClientHelper().getVoidItem();
      }
    }));
  }

  public void sni_file_chooser_show_dialog_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem fileChooser, RObjItem owner, RObjItem title) {
    FileChooserImpl i = ((FileChooserHItem)fileChooser).impl;
    Component o = ((AComponentHItem)owner).impl.getZComponent();
    String t = helper.arrayItemToCstr((RArrayItem)title).toJavaString();
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        i.setDialogTitle(t);
        int r = i.showDialog(o, i.getApproveButtonText());
        RClientHelper ch = SNIswing.this.getClientHelper();
        RObjItem sel;
        switch (r) {
        case JFileChooser.APPROVE_OPTION:
          sel = ch.getStructItem(helper.getDataConstr(MOD_NAME, "file_chooser_approved$"), new RObjItem[0]);
          break;
        case JFileChooser.CANCEL_OPTION:
          sel = ch.getStructItem(helper.getDataConstr(MOD_NAME, "file_chooser_canceled$"), new RObjItem[0]);
          break;
        default:
          sel = ch.getStructItem(helper.getDataConstr(MOD_NAME, "file_chooser_error$"), new RObjItem[0]);
          break;
        }
        return sel;
      }
    }));
  }

  public void sni_file_chooser_get_selected_files_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem ret, RObjItem fileChooser) {
    FileChooserImpl i = ((FileChooserHItem)fileChooser).impl;
    ((ContextHItem)cx).impl.request(this.makeSwingTask(helper, ret, new SwingRequest() {
      public RObjItem run() throws Exception {
        RClientHelper ch = SNIswing.this.getClientHelper();
        File[] fs;
        if (i.isMultiSelectionEnabled()) {
          fs = i.getSelectedFiles();
          fs = (fs != null)? fs: new File[0];
        } else {
          File f = i.getSelectedFile();
          fs = (f != null)?  new File[] { f }: new File[0];
        }
        RListItem L = ch.getListNilItem();
        for (int j = fs.length - 1; j >= 0; j--) {
          RListItem.Cell c = ch.createListCellItem();
          c.head = ch.cstrToArrayItem(new Cstr(fs[j].getAbsolutePath()));
          c.tail = L;
          L = c;
        }
        return L;
      }
    }));
  }

// implementation

  FileChooserHItem createFileChooserImpl() {
    RClientHelper ch = this.getClientHelper();
    FileChooserImpl impl = new FileChooserImpl();
    FileChooserHItem h = new FileChooserHItem(impl);
    RDataConstr dc = ch.getDataConstr(MOD_NAME, "file_chooser_obj$");
    RObjItem swingObjItem = ch.getStructItem(dc, new RObjItem[] { h } );
    impl.implAdapter = new AJComponentImplAdapter(impl, h, swingObjItem);
    impl.implAdapter.init();
    return h;
  }

  class FileChooserImpl extends JFileChooser implements AJComponentImpl {
    AJComponentImplAdapter implAdapter;
    ActionEventMgr actionEventMgr;

    FileChooserImpl() {
      super();
      this.actionEventMgr = new ActionEventMgr(this);
      this.addActionListener(this.actionEventMgr);
    }

    public SwingObjAdapter getSwingObjAdapter() { return this.implAdapter; }
    public Component getZComponent() { return this; }
    public AComponentImplAdapter getAComponentImplAdapter() { return this.implAdapter; }
    public JComponent getZJComponent() { return this; }
    public AJComponentImplAdapter getAJComponentImplAdapter() { return this.implAdapter; }

    void installActionListener(ListenerHItem L) {
      this.actionEventMgr.installListener(L);
    }

    void uninstallActionListener(ListenerHItem L) {
      this.actionEventMgr.uninstallListener(L);
    }
  }

  public class FileChooserHItem extends RObjItem {
    FileChooserImpl impl;

    FileChooserHItem(FileChooserImpl i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      return item == this;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "file_chooser_h", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// native object - font

  public void sni_create_font_impl(RNativeImplHelper helper, RClosureItem self, RObjItem cx, RObjItem name, RObjItem style, RObjItem points) {
    String n = helper.arrayItemToCstr((RArrayItem)name).toJavaString();
    RDataConstr dcStyle = ((RStructItem)style).getDataConstr();
    Cstr modName = dcStyle.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a font style.");
    }
    int s = 0;
    String constr = dcStyle.getName();
    if (constr.equals("plain_style$")) {
      s = Font.PLAIN;
    } else if (constr.equals("italic_style$")) {
      s = Font.ITALIC;
    } else if (constr.equals("bold_style$")) {
      s = Font.BOLD;
    } else if (constr.equals("bold_italic_style$")) {
      s = Font.BOLD + Font.ITALIC;
    } else {
      throw new IllegalArgumentException("Not a font style.");
    }
    int p = ((RIntItem)points).getValue();
    helper.setReturnValue(this.createFontImpl(n, s, p));
  }

// implementation

  FontItem createFontImpl(String name, int style, int points) {
    return new FontItem(new Font(name, style, points));
  }

  public class FontItem extends RObjItem {
    Font impl;

    FontItem(Font i) {
      super(SNIswing.this.theEngine);
      this.impl = i;
    }

    public boolean objEquals(RFrame frame, RObjItem item) {
      boolean b;
      if (item == this) {
        b = true;
      } else if (item instanceof FontItem) {
        FontItem f = (FontItem)item;
        b = f.impl.equals(this.impl); 
      } else {
        b = false;
      }
      return b;
    }

    public RType.Sig getTsig() {
      return RType.createTsig(MOD_NAME, "font", 0);
    }

    public Cstr debugReprOfContents() {
      return new Cstr(this.toString());
    }
  }


// -- event processing --

//  event manager

  abstract class EventMgr<E> {
    SwingObj srcSwingObj;
    List<ListenerHItem> listeners;

    EventMgr(SwingObj srcSwingObj) {
      this.srcSwingObj = srcSwingObj;
      this.listeners = new ArrayList<ListenerHItem>();
    }

    void installListener(ListenerHItem L) {
      this.listeners.add(L);
    }

    void uninstallListener(ListenerHItem L) {
      this.listeners.remove(L);
    }

    void processEvent(String s, E e) {
      if (this.listeners.size() > 0) {
        EDTCallback c = SNIswing.this.createEDTCallback();
        EventGroup eg = this.createEventGroup(c, s, e);
        c.start(eg);
      }
    }

    EventGroup createEventGroup(EDTCallback edt, String s, E e) {
      return new EventGroup(
        edt,
        this.listeners,
        this.srcSwingObj.getSwingObjAdapter().swingObjItem,
        SNIswing.this.getClientHelper().cstrToArrayItem(new Cstr(s)),
        this.convertEvent(e));
    }

    abstract RListItem convertEvent(E e);
  }

// evengt - action

  class ActionEventMgr extends EventMgr<ActionEvent> implements ActionListener {
    ActionEventMgr(SwingObj src) {
      super(src);
    }

    public void actionPerformed(ActionEvent e) {
      this.processEvent("action.performed", e);
    }

    RListItem convertEvent(ActionEvent e) {
      RClientHelper ch = SNIswing.this.getClientHelper();
      RListItem L = ch.getListNilItem();
      RListItem.Cell c = ch.createListCellItem();
      String ac = e.getActionCommand();
      Cstr acs = new Cstr((ac == null)? "": ac);
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("action_command")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "cstr_eid$"),
          new RObjItem[] { ch.cstrToArrayItem(acs) }) });
      c.tail = L;
      L = c;
      return L;
    }
  }

// event - document

  class DocumentEventMgr extends EventMgr<DocumentEvent> implements DocumentListener {
    DocumentEventMgr(SwingObj src) {
      super(src);
    }

    public void changedUpdate(DocumentEvent e) {
      this.processEvent("document.changed", e);
    }

    public void insertUpdate(DocumentEvent e) {
      this.processEvent("document.insert", e);
    }

    public void removeUpdate(DocumentEvent e) {
      this.processEvent("document.remove", e);
    }

    RListItem convertEvent(DocumentEvent e) {
      return SNIswing.this.getClientHelper().getListNilItem();
    }
  }

// event - focus

  class FocusEventMgr extends EventMgr<FocusEvent> implements FocusListener {
    FocusEventMgr(SwingObj src) {
      super(src);
    }

    public void focusGained(FocusEvent e) {
      this.processEvent("focus.gained", e);
    }

    public void focusLost(FocusEvent e){
      this.processEvent("focus.lost", e);
    }

    RListItem convertEvent(FocusEvent e) {
      RClientHelper ch = SNIswing.this.getClientHelper();
      RListItem L = ch.getListNilItem();
      return L;
    }
  }

// event - key

  class KeyEventMgr extends EventMgr<KeyEvent> implements KeyListener {
    KeyEventMgr(SwingObj src) {
      super(src);
    }

    public void keyPressed(KeyEvent e) {
      this.processEvent("key.pressed", e);
    }

    public void keyReleased(KeyEvent e) {
      this.processEvent("key.released", e);
    }

    public void keyTyped(KeyEvent e) {
      this.processEvent("key.typed", e);
    }

    RListItem convertEvent(KeyEvent e) {
      switch (e.getID()) {
      case KeyEvent.KEY_TYPED:
        return this.convertEvent1(e);
      default:
        return this.convertEvent2(e);
      }
    }

    RListItem convertEvent1(KeyEvent e) {
      RClientHelper ch = SNIswing.this.getClientHelper();
      RListItem L = ch.getListNilItem();
      RListItem.Cell c = ch.createListCellItem();
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("key_char")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "char_eid$"),
          new RObjItem[] { ch.getCharItem(e.getKeyChar()) })
      });
      c.tail = L;
      L = c;
      return L;
    }

    RListItem convertEvent2(KeyEvent e) {
      RClientHelper ch = SNIswing.this.getClientHelper();
      RListItem L = ch.getListNilItem();
      RListItem.Cell c = ch.createListCellItem();
      RDataConstr dc = ch.getDataConstr(MOD_NAME, SNIswing.this.vkToDCName(e.getKeyCode()));
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("key_code")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "vk_eid$"),
          new RObjItem[] { ch.getStructItem(dc, new RObjItem[0]) })
      });
      c.tail = L;
      L = c;
      return L;
    }
  }

// event - mouse

  class MouseEventMgr extends EventMgr<MouseEvent> implements MouseListener {
    MouseEventMgr(SwingObj src) {
      super(src);
    }

    public void mouseClicked(MouseEvent e) {
      this.processEvent("mouse.clicked", e);
    }

    public void mouseEntered(MouseEvent e){
      this.processEvent("mouse.entered", e);
    }

    public void mouseExited(MouseEvent e){
      this.processEvent("mouse.exited", e);
    }

    public void mousePressed(MouseEvent e){
      this.processEvent("mouse.pressed", e);
    }

    public void mouseReleased(MouseEvent e){
      this.processEvent("mouse.released", e);
    }

    RListItem convertEvent(MouseEvent e) {
      RClientHelper ch = SNIswing.this.getClientHelper();
      RListItem L = ch.getListNilItem();
      RListItem.Cell c = ch.createListCellItem();
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("click_count")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "int_eid$"),
          new RObjItem[] { ch.getIntItem(e.getClickCount()) }) });
      c.tail = L;
      L = c;
      c = ch.createListCellItem();
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("popup_trigger?")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "bool_eid$"),
          new RObjItem[] { ch.getBoolItem(e.isPopupTrigger()) }) });
      c.tail = L;
      L = c;
      c = ch.createListCellItem();
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("x")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "int_eid$"),
          new RObjItem[] { ch.getIntItem(e.getX()) }) });
      c.tail = L;
      L = c;
      c = ch.createListCellItem();
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("y")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "int_eid$"),
          new RObjItem[] { ch.getIntItem(e.getY()) }) });
      c.tail = L;
      L = c;
      return L;
    }
  }

// event - mouse motion

  class MouseMotionEventMgr extends EventMgr<MouseEvent> implements MouseMotionListener {
    MouseMotionEventMgr(SwingObj src) {
      super(src);
    }

    public void mouseDragged(MouseEvent e) {
      this.processEvent("mouse.dragged", e);
    }

    public void mouseMoved(MouseEvent e){
      this.processEvent("mouse.moved", e);
    }

    RListItem convertEvent(MouseEvent e) {
      RClientHelper ch = SNIswing.this.getClientHelper();
      RListItem L = ch.getListNilItem();
      RListItem.Cell c = ch.createListCellItem();
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("x")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "int_eid$"),
          new RObjItem[] { ch.getIntItem(e.getX()) }) });
      c.tail = L;
      L = c;
      c = ch.createListCellItem();
      c.head = ch.getTupleItem(new RObjItem[] {
        ch.cstrToArrayItem(new Cstr("y")),
        ch.getStructItem(ch.getDataConstr(MOD_NAME, "int_eid$"),
          new RObjItem[] { ch.getIntItem(e.getY()) }) });
      c.tail = L;
      L = c;
      return L;
    }
  }

// event - window

  class WindowEventMgr extends EventMgr<WindowEvent> implements WindowListener {
    WindowEventMgr(SwingObj src) {
      super(src);
    }

    public void windowActivated(WindowEvent e) {
      this.processEvent("window.activated", e);
    }

    public void windowClosed(WindowEvent e) {
      this.processEvent("window.closed", e);
    }

    public void windowClosing(WindowEvent e) {
      this.processEvent("window.closing", e);
    }

    public void windowDeactivated(WindowEvent e) {
      this.processEvent("window.deactivated", e);
    }

    public void windowDeiconified(WindowEvent e) {
      this.processEvent("window.deiconified", e);
    }

    public void windowIconified(WindowEvent e) {
      this.processEvent("window.iconified", e);
    }

    public void windowOpened(WindowEvent e) {
      this.processEvent("window.opened", e);
    }

    RListItem convertEvent(WindowEvent e) {
      return SNIswing.this.getClientHelper().getListNilItem();
    }
  }

  class WindowEventMgrForFrame extends WindowEventMgr {
    WindowEventMgrForFrame(SwingObj src) {
      super(src);
    }

    public void windowClosing(WindowEvent e) {
      FrameImpl frame = (FrameImpl)this.srcSwingObj;
      switch (frame.actualCloseOperation) {
      case JFrame.DO_NOTHING_ON_CLOSE:
        super.windowClosing(e);
        break;
      case JFrame.HIDE_ON_CLOSE:
        frame.setVisible(false);
        break;
      case JFrame.DISPOSE_ON_CLOSE:
        frame.dispose();
        break;
      case JFrame.EXIT_ON_CLOSE:
        SNIswing.this.getClientHelper().apply(new RObjItem[0], SNIswing.this.shutdownAction);
        break;
      }
    }
  }


// -- utilities --

  RClientHelper getClientHelper() { return this.theEngine.getClientHelper(); }

  RObjItem getResultFinItem(RObjItem o) {
    RClientHelper ch = this.getClientHelper();
    RDataConstr dcFin = ch.getDataConstr(Module.MOD_LANG, "fin$");
    return ch.getStructItem(dcFin, new RObjItem[] { o });
  }

  RObjItem getResultExcSwingErrorItem(Exception ex, RExcInfoItem ei) {
    RClientHelper ch = this.getClientHelper();
    RDataConstr dcExc = ch.getDataConstr(Module.MOD_LANG, "exc$");
    return ch.getStructItem(dcExc,
      new RObjItem[] { this.convertExceptionToSwingErrorExceptionItem(ex, ei) });
  }

  RObjItem convertExceptionToSwingErrorExceptionItem(Exception ex, RExcInfoItem ei) {
    RClientHelper ch = this.getClientHelper();
    RDataConstr dcNone = ch.getDataConstr(Module.MOD_LANG, "none$");
    RObjItem oNone = ch.getStructItem(dcNone, new RObjItem[0]);
    RDataConstr dcException = ch.getDataConstr(Module.MOD_LANG, "exception$");
    return ch.getStructItem(dcException,
      new RObjItem[] {
        this.convertExceptionToSwingErrorItem(ex),
        ch.cstrToArrayItem(new Cstr("Swing error")),
        ei,
        oNone });
  }

  RObjItem convertExceptionToSwingErrorItem(Exception ex) {
    RClientHelper ch = this.getClientHelper();
    RDataConstr dcSwingError = ch.getDataConstr(MOD_NAME, "swing_error$");
    return ch.getStructItem(dcSwingError,
      new RObjItem[] { ch.cstrToArrayItem(new Cstr(ex.toString())) });
  }

  RObjItem getValueOfMaybeItem(RObjItem maybe) {
    RStructItem m = (RStructItem)maybe;
    RDataConstr dc = m.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(new Cstr("sango.lang"))) {
      throw new IllegalArgumentException("Not a maybe item.");
    }
    return dc.getName().equals("value$")? m.getFieldAt(0): null;
  }

  Color colorItemToColor(RObjItem color) {
    RStructItem c = (RStructItem)color;
    RDataConstr dc = c.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a color.");
    }
    String constr = dc.getName();
    if (!constr.equals("color$")) {
      throw new IllegalArgumentException("Not a color.");
    }
    int a = ((RIntItem)c.getFieldAt(0)).getValue();
    int r = ((RIntItem)c.getFieldAt(1)).getValue();
    int g = ((RIntItem)c.getFieldAt(2)).getValue();
    int b = ((RIntItem)c.getFieldAt(3)).getValue();
    return new Color(r, g, b, a);
  }

  int[] unpackInsetsItem(RObjItem insets) {
    RStructItem i = (RStructItem)insets;
    RDataConstr dc = i.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a insets.");
    }
    String constr = dc.getName();
    if (!constr.equals("insets$")) {
      throw new IllegalArgumentException("Not a insets.");
    }
    int[] ii = new int[4];
    ii[0] = ((RIntItem)i.getFieldAt(0)).getValue();
    ii[1] = ((RIntItem)i.getFieldAt(1)).getValue();
    ii[2] = ((RIntItem)i.getFieldAt(2)).getValue();
    ii[3] = ((RIntItem)i.getFieldAt(3)).getValue();
    return ii;
  }

  List<PaintRequest> parsePaintActions(RObjItem paintActions) {
    List<PaintRequest> as = new ArrayList<PaintRequest>();
    RListItem L = (RListItem)paintActions;
    while (L instanceof RListItem.Cell) {
      RListItem.Cell c = (RListItem.Cell)L;
      as.add(this.parsePaintAction((RStructItem)c.head));
      L = c.tail;
    }
    return as;
  }

  PaintRequest parsePaintAction(RStructItem action) {
    RDataConstr dc = action.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a paint action.");
    }
    String constr = dc.getName();
    PaintRequest req = null;
    if (constr.equals("pa_set_color$")) {
      req = this.parseSetColor(action);
    } else if (constr.equals("pa_set_font$")) {
      req = this.parseSetFont(action);
    } else if (constr.equals("pa_draw_line$")) {
      req = this.parseDrawline(action);
    } else if (constr.equals("pa_draw_polyline$")) {
      req = this.parseDrawPolyline(action);
    } else if (constr.equals("pa_draw_rect$")) {
      req = this.parseDrawRect(action);
    } else if (constr.equals("pa_fill_rect$")) {
      req = this.parseFillRect(action);
    } else if (constr.equals("pa_draw_polygon$")) {
      req = this.parseDrawPolygon(action);
    } else if (constr.equals("pa_fill_polygon$")) {
      req = this.parseFillPolygon(action);
    } else if (constr.equals("pa_draw_oval$")) {
      req = this.parseDrawOval(action);
    } else if (constr.equals("pa_fill_oval$")) {
      req = this.parseFillOval(action);
    } else if (constr.equals("pa_draw_arc$")) {
      req = this.parseDrawArc(action);
    } else if (constr.equals("pa_fill_arc$")) {
      req = this.parseFillArc(action);
    } else if (constr.equals("pa_draw_image$")) {
      req = this.parseDrawImage(action);
    } else if (constr.equals("pa_draw_string$")) {
      req = this.parseDrawString(action);
    } else if (constr.equals("pa_draw_string2$")) {
      req = this.parseDrawString2(action);
    } else if (constr.equals("pa_draw_string3$")) {
      req = this.parseDrawString3(action);
    } else {
      throw new IllegalArgumentException("Not a paint action.");
    }
    return req;
  }

  PaintRequest parseSetColor(RStructItem action) {
    Color c = this.colorItemToColor(action.getFieldAt(0));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.setColor(c);
      }
    };
  }

  PaintRequest parseSetFont(RStructItem action) {
    Font f = ((FontItem)action.getFieldAt(0)).impl;
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.setFont(f);
      }
    };
  }

  PaintRequest parseDrawline(RStructItem action) {
    Point point1 = this.pointItemToPoint((RStructItem)action.getFieldAt(0));
    Point point2 = this.pointItemToPoint((RStructItem)action.getFieldAt(1));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.drawLine(point1.x, point1.y, point2.x, point2.y);
      }
    };
  }

  PaintRequest parseDrawPolyline(RStructItem action) {
    List<Point> points = this.pointListItemToPoints((RListItem)action.getFieldAt(0));
    int[][] xsys = this.pointsToXsYs(points);
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.drawPolyline(xsys[0], xsys[1], points.size());
      }
    };
  }

  PaintRequest parseDrawRect(RStructItem action) {
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(0));
    Dimension dim = this.dimensionItemToDimension((RStructItem)action.getFieldAt(1));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.drawRect(point.x, point.y, dim.width, dim.height);
      }
    };
  }

  PaintRequest parseFillRect(RStructItem action) {
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(0));
    Dimension dim = this.dimensionItemToDimension((RStructItem)action.getFieldAt(1));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.fillRect(point.x, point.y, dim.width, dim.height);
      }
    };
  }

  PaintRequest parseDrawPolygon(RStructItem action) {
    List<Point> points = this.pointListItemToPoints((RListItem)action.getFieldAt(0));
    int[][] xsys = this.pointsToXsYs(points);
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.drawPolygon(xsys[0], xsys[1], points.size());
      }
    };
  }

  PaintRequest parseFillPolygon(RStructItem action) {
    List<Point> points = this.pointListItemToPoints((RListItem)action.getFieldAt(0));
    int[][] xsys = this.pointsToXsYs(points);
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.fillPolygon(xsys[0], xsys[1], points.size());
      }
    };
  }

  PaintRequest parseDrawOval(RStructItem action) {
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(0));
    Dimension dim = this.dimensionItemToDimension((RStructItem)action.getFieldAt(1));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.drawOval(point.x, point.y, dim.width, dim.height);
      }
    };
  }

  PaintRequest parseFillOval(RStructItem action) {
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(0));
    Dimension dim = this.dimensionItemToDimension((RStructItem)action.getFieldAt(1));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.fillOval(point.x, point.y, dim.width, dim.height);
      }
    };
  }

  PaintRequest parseDrawArc(RStructItem action) {
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(0));
    Dimension dim = this.dimensionItemToDimension((RStructItem)action.getFieldAt(1));
    int startAngle = ((RIntItem)action.getFieldAt(2)).getValue();
    int arcAngle = ((RIntItem)action.getFieldAt(3)).getValue();
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.drawArc(point.x, point.y, dim.width, dim.height, startAngle, arcAngle);
      }
    };
  }

  PaintRequest parseFillArc(RStructItem action) {
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(0));
    Dimension dim = this.dimensionItemToDimension((RStructItem)action.getFieldAt(1));
    int startAngle = ((RIntItem)action.getFieldAt(2)).getValue();
    int arcAngle = ((RIntItem)action.getFieldAt(3)).getValue();
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.fillArc(point.x, point.y, dim.width, dim.height, startAngle, arcAngle);
      }
    };
  }

  PaintRequest parseDrawImage(RStructItem action) {
    Image image = ((ImageHItem)action.getFieldAt(0)).impl;
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(1));
    RObjItem maybeDim = this.getValueOfMaybeItem(action.getFieldAt(2));
    Dimension dim = (maybeDim != null)? this.dimensionItemToDimension((RStructItem)maybeDim): null;
    RObjItem maybeColor = this.getValueOfMaybeItem(action.getFieldAt(3));
    Color color = (maybeColor != null)? this.colorItemToColor(maybeColor): null;
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        if (dim != null) {
          if (color != null) {
            g.drawImage(image, point.x, point.y, dim.width, dim.height, color, p);
          } else {
            g.drawImage(image, point.x, point.y, dim.width, dim.height, p);
          }
        } else {
          if (color != null) {
            g.drawImage(image, point.x, point.y, color, p);
          } else {
            g.drawImage(image, point.x, point.y, p);
          }
        }
      }
    };
  }

  PaintRequest parseDrawString(RStructItem action) {
    RClientHelper ch = SNIswing.this.getClientHelper();
    String s = ch.arrayItemToCstr((RArrayItem)action.getFieldAt(0)).toJavaString();
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(1));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        g.drawString(s, point.x, point.y);
      }
    };
  }

  PaintRequest parseDrawString2(RStructItem action) {
    RClientHelper ch = SNIswing.this.getClientHelper();
    String s = ch.arrayItemToCstr((RArrayItem)action.getFieldAt(0)).toJavaString();
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(1));
    int width = ((RIntItem)action.getFieldAt(2)).getValue();
    int hAlignment = this.horizontalAlignmentItemToInt((RStructItem)action.getFieldAt(3));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        int a;
        switch (hAlignment) {
        case SwingConstants.LEADING:
          a = SwingConstants.LEFT;  // TODO
          break;
        case SwingConstants.TRAILING:
          a = SwingConstants.RIGHT;  // TODO
          break;
        default:
          a = hAlignment;
          break;
        }
        int dx;
        switch (a) {
        case SwingConstants.LEFT:
          dx = 0;
          break;
        case SwingConstants.CENTER:
          dx = (width - g.getFontMetrics().stringWidth(s)) / 2;
          break;
        default:
          dx = width - g.getFontMetrics().stringWidth(s);
          break;
        }
        g.drawString(s, point.x + dx, point.y);
      }
    };
  }

  PaintRequest parseDrawString3(RStructItem action) {
    RClientHelper ch = SNIswing.this.getClientHelper();
    String s = ch.arrayItemToCstr((RArrayItem)action.getFieldAt(0)).toJavaString();
    Point point = this.pointItemToPoint((RStructItem)action.getFieldAt(1));
    Dimension dim = this.dimensionItemToDimension((RStructItem)action.getFieldAt(2));
    int hAlignment = this.horizontalAlignmentItemToInt((RStructItem)action.getFieldAt(3));
    int vAlignment = this.verticalAlignmentItemToInt((RStructItem)action.getFieldAt(4));
    return new PaintRequest() {
      public void run(JPanel p, Graphics g) {
        int ha;
        switch (hAlignment) {
        case SwingConstants.LEADING:
          ha = SwingConstants.LEFT;  // TODO
          break;
        case SwingConstants.TRAILING:
          ha = SwingConstants.RIGHT;  // TODO
          break;
        default:
          ha = hAlignment;
          break;
        }
        int dx;
        switch (ha) {
        case SwingConstants.LEFT:
          dx = 0;
          break;
        case SwingConstants.CENTER:
          dx = (dim.width - g.getFontMetrics().stringWidth(s)) / 2;
          break;
        default:
          dx = dim.width - g.getFontMetrics().stringWidth(s);
          break;
        }
        int dy = g.getFontMetrics().getAscent();
        switch (vAlignment) {
        case SwingConstants.TOP:
          break;
        case SwingConstants.CENTER:
          dy += (dim.height - g.getFontMetrics().getHeight()) / 2;
          break;
        default:
          dy += dim.height - g.getFontMetrics().getHeight();
          break;
        }
        g.drawString(s, point.x + dx, point.y + dy);
      }
    };
  }

  int horizontalAlignmentItemToInt(RStructItem a) {
    RDataConstr dcHA = a.getDataConstr();
    Cstr modName = dcHA.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a horizontal alignment.");
    }
    String constr = dcHA.getName();
    int i;
    if (constr.equals("horizontal_left$")) {
      i = SwingConstants.LEFT;
    } else if (constr.equals("horizontal_center$")) {
      i = SwingConstants.CENTER;
    } else if (constr.equals("horizontal_right$")) {
      i = SwingConstants.RIGHT;
    } else if (constr.equals("horizontal_leading$")) {
      i = SwingConstants.LEADING;
    } else if (constr.equals("horizontal_trailing$")) {
      i = SwingConstants.TRAILING;
    } else {
      throw new IllegalArgumentException("Not a horizontal alignment.");
    }
    return i;
  }

  int verticalAlignmentItemToInt(RStructItem a) {
    RDataConstr dcVA = a.getDataConstr();
    Cstr modName = dcVA.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a vertical alignment.");
    }
    String constr = dcVA.getName();
    int i;
    if (constr.equals("vertical_top$")) {
      i = SwingConstants.TOP;
    } else if (constr.equals("vertical_center$")) {
      i = SwingConstants.CENTER;
    } else if (constr.equals("vertical_bottom$")) {
      i = SwingConstants.BOTTOM;
    } else {
      throw new IllegalArgumentException("Not a vertical alignment.");
    }
    return i;
  }

  List<Point> pointListItemToPoints(RListItem L) {
    List<Point> points = new ArrayList<Point>();
    while (L instanceof RListItem.Cell) {
      RListItem.Cell c = (RListItem.Cell)L;
      points.add(this.pointItemToPoint((RStructItem)c.head));
      L = c.tail;
    }
    return points;
  }

  int[][] pointsToXsYs(List<Point> points) {
    int count = points.size();
    int[] xs = new int[count];
    int[] ys = new int[count];
    for (int i = 0; i < count; i++) {
      xs[i] = points.get(i).x;
      ys[i] = points.get(i).y;
    }
    return new int[][] { xs, ys };
  }

  Point pointItemToPoint(RStructItem point) {
    RDataConstr dc = point.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a point.");
    }
    String constr = dc.getName();
    if (!constr.equals("point$")) {
      throw new IllegalArgumentException("Not a point.");
    }
    return new Point(
      ((RIntItem)point.getFieldAt(0)).getValue(),
      ((RIntItem)point.getFieldAt(1)).getValue());
  }

  Dimension dimensionItemToDimension(RStructItem dim) {
    RDataConstr dc = dim.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a dimension.");
    }
    String constr = dc.getName();
    if (!constr.equals("dimension$")) {
      throw new IllegalArgumentException("Not a dimension.");
    }
    return new Dimension(
      ((RIntItem)dim.getFieldAt(0)).getValue(),
      ((RIntItem)dim.getFieldAt(1)).getValue());
  }

  String vkToDCName(int vk) {
    String dc;
    switch (vk) {
    case KeyEvent.VK_ENTER: dc = "vk_enter$"; break;
    case KeyEvent.VK_BACK_SPACE: dc = "vk_back_space$"; break;
    case KeyEvent.VK_TAB: dc = "vk_tab$"; break;
    case KeyEvent.VK_CANCEL: dc = "vk_cancel$"; break;
    case KeyEvent.VK_CLEAR: dc = "vk_clear$"; break;
    case KeyEvent.VK_SHIFT: dc = "vk_shift$"; break;
    case KeyEvent.VK_CONTROL: dc = "vk_control$"; break;
    case KeyEvent.VK_ALT: dc = "vk_alt$"; break;
    case KeyEvent.VK_PAUSE: dc = "vk_pause$"; break;
    case KeyEvent.VK_CAPS_LOCK: dc = "vk_caps_lock$"; break;
    case KeyEvent.VK_ESCAPE: dc = "vk_escape$"; break;
    case KeyEvent.VK_SPACE: dc = "vk_space$"; break;
    case KeyEvent.VK_PAGE_UP: dc = "vk_page_up$"; break;
    case KeyEvent.VK_PAGE_DOWN: dc = "vk_page_down$"; break;
    case KeyEvent.VK_END: dc = "vk_end$"; break;
    case KeyEvent.VK_HOME: dc = "vk_home$"; break;
    case KeyEvent.VK_LEFT: dc = "vk_left$"; break;
    case KeyEvent.VK_UP: dc = "vk_up$"; break;
    case KeyEvent.VK_RIGHT: dc = "vk_right$"; break;
    case KeyEvent.VK_DOWN: dc = "vk_down$"; break;
    case KeyEvent.VK_COMMA: dc = "vk_comma$"; break;
    case KeyEvent.VK_MINUS: dc = "vk_minus$"; break;
    case KeyEvent.VK_PERIOD: dc = "vk_period$"; break;
    case KeyEvent.VK_SLASH: dc = "vk_slash$"; break;
    case KeyEvent.VK_0: dc = "vk_0$"; break;
    case KeyEvent.VK_1: dc = "vk_1$"; break;
    case KeyEvent.VK_2: dc = "vk_2$"; break;
    case KeyEvent.VK_3: dc = "vk_3$"; break;
    case KeyEvent.VK_4: dc = "vk_4$"; break;
    case KeyEvent.VK_5: dc = "vk_5$"; break;
    case KeyEvent.VK_6: dc = "vk_6$"; break;
    case KeyEvent.VK_7: dc = "vk_7$"; break;
    case KeyEvent.VK_8: dc = "vk_8$"; break;
    case KeyEvent.VK_9: dc = "vk_9$"; break;
    case KeyEvent.VK_SEMICOLON: dc = "vk_semicolon$"; break;
    case KeyEvent.VK_EQUALS: dc = "vk_equals$"; break;
    case KeyEvent.VK_A: dc = "vk_a$"; break;
    case KeyEvent.VK_B: dc = "vk_b$"; break;
    case KeyEvent.VK_C: dc = "vk_c$"; break;
    case KeyEvent.VK_D: dc = "vk_d$"; break;
    case KeyEvent.VK_E: dc = "vk_e$"; break;
    case KeyEvent.VK_F: dc = "vk_f$"; break;
    case KeyEvent.VK_G: dc = "vk_g$"; break;
    case KeyEvent.VK_H: dc = "vk_h$"; break;
    case KeyEvent.VK_I: dc = "vk_i$"; break;
    case KeyEvent.VK_J: dc = "vk_j$"; break;
    case KeyEvent.VK_K: dc = "vk_k$"; break;
    case KeyEvent.VK_L: dc = "vk_l$"; break;
    case KeyEvent.VK_M: dc = "vk_m$"; break;
    case KeyEvent.VK_N: dc = "vk_n$"; break;
    case KeyEvent.VK_O: dc = "vk_o$"; break;
    case KeyEvent.VK_P: dc = "vk_p$"; break;
    case KeyEvent.VK_Q: dc = "vk_q$"; break;
    case KeyEvent.VK_R: dc = "vk_r$"; break;
    case KeyEvent.VK_S: dc = "vk_s$"; break;
    case KeyEvent.VK_T: dc = "vk_t$"; break;
    case KeyEvent.VK_U: dc = "vk_u$"; break;
    case KeyEvent.VK_V: dc = "vk_v$"; break;
    case KeyEvent.VK_W: dc = "vk_w$"; break;
    case KeyEvent.VK_X: dc = "vk_x$"; break;
    case KeyEvent.VK_Y: dc = "vk_y$"; break;
    case KeyEvent.VK_Z: dc = "vk_z$"; break;
    case KeyEvent.VK_OPEN_BRACKET: dc = "vk_open_bracket$"; break;
    case KeyEvent.VK_BACK_SLASH: dc = "vk_back_slash$"; break;
    case KeyEvent.VK_CLOSE_BRACKET: dc = "vk_close_bracket$"; break;
    case KeyEvent.VK_NUMPAD0: dc = "vk_numpad0$"; break;
    case KeyEvent.VK_NUMPAD1: dc = "vk_numpad1$"; break;
    case KeyEvent.VK_NUMPAD2: dc = "vk_numpad2$"; break;
    case KeyEvent.VK_NUMPAD3: dc = "vk_numpad3$"; break;
    case KeyEvent.VK_NUMPAD4: dc = "vk_numpad4$"; break;
    case KeyEvent.VK_NUMPAD5: dc = "vk_numpad5$"; break;
    case KeyEvent.VK_NUMPAD6: dc = "vk_numpad6$"; break;
    case KeyEvent.VK_NUMPAD7: dc = "vk_numpad7$"; break;
    case KeyEvent.VK_NUMPAD8: dc = "vk_numpad8$"; break;
    case KeyEvent.VK_NUMPAD9: dc = "vk_numpad9$"; break;
    case KeyEvent.VK_MULTIPLY: dc = "vk_multiply$"; break;
    case KeyEvent.VK_ADD: dc = "vk_add$"; break;
    case KeyEvent.VK_SEPARATOR: dc = "vk_separator$"; break;
    case KeyEvent.VK_SUBTRACT: dc = "vk_subtract$"; break;
    case KeyEvent.VK_DECIMAL: dc = "vk_decimal$"; break;
    case KeyEvent.VK_DIVIDE: dc = "vk_divide$"; break;
    case KeyEvent.VK_DELETE: dc = "vk_delete$"; break;
    case KeyEvent.VK_NUM_LOCK: dc = "vk_num_lock$"; break;
    case KeyEvent.VK_SCROLL_LOCK: dc = "vk_scroll_lock$"; break;
    case KeyEvent.VK_F1: dc = "vk_f1$"; break;
    case KeyEvent.VK_F2: dc = "vk_f2$"; break;
    case KeyEvent.VK_F3: dc = "vk_f3$"; break;
    case KeyEvent.VK_F4: dc = "vk_f4$"; break;
    case KeyEvent.VK_F5: dc = "vk_f5$"; break;
    case KeyEvent.VK_F6: dc = "vk_f6$"; break;
    case KeyEvent.VK_F7: dc = "vk_f7$"; break;
    case KeyEvent.VK_F8: dc = "vk_f8$"; break;
    case KeyEvent.VK_F9: dc = "vk_f9$"; break;
    case KeyEvent.VK_F10: dc = "vk_f10$"; break;
    case KeyEvent.VK_F11: dc = "vk_f11$"; break;
    case KeyEvent.VK_F12: dc = "vk_f12$"; break;
    case KeyEvent.VK_F13: dc = "vk_f13$"; break;
    case KeyEvent.VK_F14: dc = "vk_f14$"; break;
    case KeyEvent.VK_F15: dc = "vk_f15$"; break;
    case KeyEvent.VK_F16: dc = "vk_f16$"; break;
    case KeyEvent.VK_F17: dc = "vk_f17$"; break;
    case KeyEvent.VK_F18: dc = "vk_f18$"; break;
    case KeyEvent.VK_F19: dc = "vk_f19$"; break;
    case KeyEvent.VK_F20: dc = "vk_f20$"; break;
    case KeyEvent.VK_F21: dc = "vk_f21$"; break;
    case KeyEvent.VK_F22: dc = "vk_f22$"; break;
    case KeyEvent.VK_F23: dc = "vk_f23$"; break;
    case KeyEvent.VK_F24: dc = "vk_f24$"; break;
    case KeyEvent.VK_PRINTSCREEN: dc = "vk_printscreen$"; break;
    case KeyEvent.VK_INSERT: dc = "vk_insert$"; break;
    case KeyEvent.VK_HELP: dc = "vk_help$"; break;
    case KeyEvent.VK_META: dc = "vk_meta$"; break;
    case KeyEvent.VK_BACK_QUOTE: dc = "vk_back_quote$"; break;
    case KeyEvent.VK_QUOTE: dc = "vk_quote$"; break;
    case KeyEvent.VK_KP_UP: dc = "vk_kp_up$"; break;
    case KeyEvent.VK_KP_DOWN: dc = "vk_kp_down$"; break;
    case KeyEvent.VK_KP_LEFT: dc = "vk_kp_left$"; break;
    case KeyEvent.VK_KP_RIGHT: dc = "vk_kp_right$"; break;
    case KeyEvent.VK_DEAD_GRAVE: dc = "vk_dead_grave$"; break;
    case KeyEvent.VK_DEAD_ACUTE: dc = "vk_dead_acute$"; break;
    case KeyEvent.VK_DEAD_CIRCUMFLEX: dc = "vk_dead_circumflex$"; break;
    case KeyEvent.VK_DEAD_TILDE: dc = "vk_dead_tilde$"; break;
    case KeyEvent.VK_DEAD_MACRON: dc = "vk_dead_macron$"; break;
    case KeyEvent.VK_DEAD_BREVE: dc = "vk_dead_breve$"; break;
    case KeyEvent.VK_DEAD_ABOVEDOT: dc = "vk_dead_abovedot$"; break;
    case KeyEvent.VK_DEAD_DIAERESIS: dc = "vk_dead_diaeresis$"; break;
    case KeyEvent.VK_DEAD_ABOVERING: dc = "vk_dead_abovering$"; break;
    case KeyEvent.VK_DEAD_DOUBLEACUTE: dc = "vk_dead_doubleacute$"; break;
    case KeyEvent.VK_DEAD_CARON: dc = "vk_dead_caron$"; break;
    case KeyEvent.VK_DEAD_CEDILLA: dc = "vk_dead_cedilla$"; break;
    case KeyEvent.VK_DEAD_OGONEK: dc = "vk_dead_ogonek$"; break;
    case KeyEvent.VK_DEAD_IOTA: dc = "vk_dead_iota$"; break;
    case KeyEvent.VK_DEAD_VOICED_SOUND: dc = "vk_dead_voiced_sound$"; break;
    case KeyEvent.VK_DEAD_SEMIVOICED_SOUND: dc = "vk_dead_semivoiced_sound$"; break;
    case KeyEvent.VK_AMPERSAND: dc = "vk_ampersand$"; break;
    case KeyEvent.VK_ASTERISK: dc = "vk_asterisk$"; break;
    case KeyEvent.VK_QUOTEDBL: dc = "vk_quotedbl$"; break;
    case KeyEvent.VK_LESS: dc = "vk_less$"; break;
    case KeyEvent.VK_GREATER: dc = "vk_greater$"; break;
    case KeyEvent.VK_BRACELEFT: dc = "vk_braceleft$"; break;
    case KeyEvent.VK_BRACERIGHT: dc = "vk_braceright$"; break;
    case KeyEvent.VK_AT: dc = "vk_at$"; break;
    case KeyEvent.VK_COLON: dc = "vk_colon$"; break;
    case KeyEvent.VK_CIRCUMFLEX: dc = "vk_circumflex$"; break;
    case KeyEvent.VK_DOLLAR: dc = "vk_dollar$"; break;
    case KeyEvent.VK_EURO_SIGN: dc = "vk_euro_sign$"; break;
    case KeyEvent.VK_EXCLAMATION_MARK: dc = "vk_exclamation_mark$"; break;
    case KeyEvent.VK_INVERTED_EXCLAMATION_MARK: dc = "vk_inverted_exclamation_mark$"; break;
    case KeyEvent.VK_LEFT_PARENTHESIS: dc = "vk_left_parenthesis$"; break;
    case KeyEvent.VK_NUMBER_SIGN: dc = "vk_number_sign$"; break;
    case KeyEvent.VK_PLUS: dc = "vk_plus$"; break;
    case KeyEvent.VK_RIGHT_PARENTHESIS: dc = "vk_right_parenthesis$"; break;
    case KeyEvent.VK_UNDERSCORE: dc = "vk_underscore$"; break;
    case KeyEvent.VK_FINAL: dc = "vk_final$"; break;
    case KeyEvent.VK_CONVERT: dc = "vk_convert$"; break;
    case KeyEvent.VK_NONCONVERT: dc = "vk_nonconvert$"; break;
    case KeyEvent.VK_ACCEPT: dc = "vk_accept$"; break;
    case KeyEvent.VK_MODECHANGE: dc = "vk_modechange$"; break;
    case KeyEvent.VK_KANA: dc = "vk_kana$"; break;
    case KeyEvent.VK_KANJI: dc = "vk_kanji$"; break;
    case KeyEvent.VK_ALPHANUMERIC: dc = "vk_alphanumeric$"; break;
    case KeyEvent.VK_KATAKANA: dc = "vk_katakana$"; break;
    case KeyEvent.VK_HIRAGANA: dc = "vk_hiragana$"; break;
    case KeyEvent.VK_FULL_WIDTH: dc = "vk_full_width$"; break;
    case KeyEvent.VK_HALF_WIDTH: dc = "vk_half_width$"; break;
    case KeyEvent.VK_ROMAN_CHARACTERS: dc = "vk_roman_characters$"; break;
    case KeyEvent.VK_ALL_CANDIDATES: dc = "vk_all_candidates$"; break;
    case KeyEvent.VK_PREVIOUS_CANDIDATE: dc = "vk_previous_candidate$"; break;
    case KeyEvent.VK_CODE_INPUT: dc = "vk_code_input$"; break;
    case KeyEvent.VK_JAPANESE_KATAKANA: dc = "vk_japanese_katakana$"; break;
    case KeyEvent.VK_JAPANESE_HIRAGANA: dc = "vk_japanese_hiragana$"; break;
    case KeyEvent.VK_JAPANESE_ROMAN: dc = "vk_japanese_roman$"; break;
    case KeyEvent.VK_KANA_LOCK: dc = "vk_kana_lock$"; break;
    case KeyEvent.VK_INPUT_METHOD_ON_OFF: dc = "vk_input_method_on_off$"; break;
    case KeyEvent.VK_CUT: dc = "vk_cut$"; break;
    case KeyEvent.VK_COPY: dc = "vk_copy$"; break;
    case KeyEvent.VK_PASTE: dc = "vk_paste$"; break;
    case KeyEvent.VK_UNDO: dc = "vk_undo$"; break;
    case KeyEvent.VK_AGAIN: dc = "vk_again$"; break;
    case KeyEvent.VK_FIND: dc = "vk_find$"; break;
    case KeyEvent.VK_PROPS: dc = "vk_props$"; break;
    case KeyEvent.VK_STOP: dc = "vk_stop$"; break;
    case KeyEvent.VK_COMPOSE: dc = "vk_compose$"; break;
    case KeyEvent.VK_ALT_GRAPH: dc = "vk_alt_graph$"; break;
    case KeyEvent.VK_BEGIN: dc = "vk_begin$"; break;
    case KeyEvent.VK_CONTEXT_MENU: dc = "vk_context_menu$"; break;
    case KeyEvent.VK_WINDOWS: dc = "vk_windows$"; break;
    default: dc = "vk_undefined$"; break;
    }
    return dc;
  }

  int msgTypeItemToInt(RObjItem msgType) {
    RStructItem mt = (RStructItem)msgType;
    RDataConstr dc = mt.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a message type.");
    }
    int x = 0;
    String constr = dc.getName();
    if (constr.equals("information_message$")) {
      x = JOptionPane.INFORMATION_MESSAGE;
    } else if (constr.equals("warning_message$")) {
      x = JOptionPane.WARNING_MESSAGE;
    } else if (constr.equals("error_message$")) {
      x = JOptionPane.ERROR_MESSAGE;
    } else if (constr.equals("question_message$")) {
      x = JOptionPane.QUESTION_MESSAGE;
    } else if (constr.equals("plain_message$")) {
      x = JOptionPane.PLAIN_MESSAGE;
    } else {
      throw new IllegalArgumentException("Not a message type.");
    }
    return x;
  }

  int optionTypeItemToInt(RObjItem optType) {
    RStructItem o = (RStructItem)optType;
    RDataConstr dc = o.getDataConstr();
    Cstr modName = dc.getModName();
    if (!modName.equals(MOD_NAME)) {
      throw new IllegalArgumentException("Not a option type.");
    }
    int x = 0;
    String constr = dc.getName();
    if (constr.equals("yes_no_option$")) {
      x = JOptionPane.YES_NO_OPTION;
    } else if (constr.equals("yes_no_cancel_option$")) {
      x = JOptionPane.YES_NO_CANCEL_OPTION;
    } else {
      throw new IllegalArgumentException("Not a option type.");
    }
    return x;
  }


// === system management ===

// -- initialization --

  public void sni_bridge_init(RNativeImplHelper helper, RClosureItem self, RObjItem shutdownAction) {
    this.shutdownAction = (RClosureItem)shutdownAction;
  }


// -- keeper --

  public void sni_start_keep(RNativeImplHelper helper, RClosureItem self) {
    helper.mayRunLong();
    synchronized (this.keep) {
      this.keep[0] = true;
      while (this.keep[0]) {
        try {
          this.keep.wait();
        } catch (InterruptedException ex) {}
      }
    }
  }

  public void sni_end_keep(RNativeImplHelper helper, RClosureItem self) {
    synchronized (this.keep) {
      this.keep[0] = false;
      this.keep.notifyAll();
    }
  }
}
