package spinat.codecoverage.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import oracle.jdbc.OracleConnection;
import spinat.codecoverage.cover.CodeCoverage;
import spinat.codecoverage.cover.CoverageInfo;
import spinat.codecoverage.cover.CoveredStatement;
import spinat.codecoverage.cover.DBObjectsInstallation;
import spinat.codecoverage.cover.PackInfo;
import spinat.codecoverage.cover.ProcedureAndRange;
import spinat.oraclelogin.OraConnectionDesc;
import spinat.oraclelogin.OracleLogin;

public class Gui2 {

    private CodeCoverage codeCoverage;
    public final JFrame frame;

    private OraConnectionDesc connectionDesc;
    private OracleConnection connection;

    private final DefaultListModel<PackInfo> packModel;
    private final DefaultListModel<ProcedureAndRange> procedureModel;

    private final JLabel current_package;

    private final JList<PackInfo> packList;
    private final JList<ProcedureAndRange> procList;

    private final JTextPane sourceTextPane;
    private final JLabel lblPackinfo;

    private PackInfo currentPackinfo = null;

    Style defStyle;
    Style hotStyle;
    Style greenStyle;

    final void styles() {
        Style defaultStyle = StyleContext.getDefaultStyleContext().
                getStyle(StyleContext.DEFAULT_STYLE);
        defStyle = StyleContext.getDefaultStyleContext().addStyle("hot", defaultStyle);
        StyleConstants.setFontFamily(defStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(defStyle, 12);
        StyleConstants.setForeground(defStyle, Color.black);
        hotStyle = StyleContext.getDefaultStyleContext().addStyle("hot", defStyle);
        StyleConstants.setBackground(hotStyle, Color.red);
        greenStyle = StyleContext.getDefaultStyleContext().addStyle("green", defStyle);
        StyleConstants.setBackground(greenStyle, Color.lightGray);
    }

    public Gui2() {
        this.codeCoverage = null;
        this.connection = null;
        this.connectionDesc = null;

        frame = new JFrame();
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setFrameIconFromResource(frame, "/cc-bild.png");
        frame.setPreferredSize(new Dimension(800, 600));

        frame.setLayout(new BorderLayout());
        JPanel left = new JPanel();
        frame.add(left, BorderLayout.WEST);
        left.setLayout(new GridBagLayout());
        left.setPreferredSize(new Dimension(200, 100));
        left.setBackground(Color.red);
        this.packList = new JList<>();
        this.packModel = new DefaultListModel<>();
        packList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        packList.setModel(this.packModel);
        packList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            c.weighty = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.BOTH;
            c.anchor = GridBagConstraints.PAGE_START;
            JScrollPane jsp = new JScrollPane(packList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            left.add(jsp, c);
        }
        packList.setCellRenderer(this.pack_cellrenderer);
        packList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                pack_selection_change(e);
            }
        });

        this.procList = new JList<>();
        this.procedureModel = new DefaultListModel<>();
        procList.setModel(procedureModel);
        {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 1;
            c.weighty = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.BOTH;
            c.anchor = GridBagConstraints.PAGE_START;
            JScrollPane jsp = new JScrollPane(procList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            left.add(jsp, c);
        }
        procList.setCellRenderer(proc_cellrenderer);
        
        procList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                procedureSelectionChanged(e);
            }
        });
        //this.procedureModel.add(0, new ProcedureInfo("Test"));
        JPanel right = new JPanel();
        right.setLayout(new BorderLayout());
        frame.add(right, BorderLayout.CENTER);
        JPanel top = new JPanel();
        top.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));

        top.setBackground(Color.green);
        //top.setMinimumSize(new Dimension(200, 100));
        //top.setPreferredSize(new Dimension(200, 100));

        JLabel l = new JLabel("Package");
        top.add(l);
        this.current_package = new JLabel();

        top.add(current_package);

        JButton b1 = new JButton(startCoverage);
        top.add(b1);
        startCoverage.setEnabled(false);
        

        JButton b2 = new JButton(stopCoverage);
        top.add(b2);
        stopCoverage.setEnabled(false);
        
        lblPackinfo = new JLabel();

        top.add(lblPackinfo);
        right.add(top, BorderLayout.NORTH);

        sourceTextPane = new JTextPane();
        // must be here, otherwise too late
        sourceTextPane.setEditorKit(new SourceEditorKit(20));
        styles();

        // AbstractDocument adocl = (AbstractDocument) sourceDocument;
        JScrollPane jspl = new JScrollPane(sourceTextPane);
        jspl.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        jspl.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        jspl.setPreferredSize(new Dimension(250, 145));
        jspl.setMinimumSize(new Dimension(10, 10));
        sourceTextPane.setEditable(false);
        right.add(jspl, BorderLayout.CENTER);
    }

    static void setFrameIconFromResource(JFrame frame, String resourceName) {
        try {
            InputStream ins = Gui2.class.getResourceAsStream(resourceName);
            if (ins != null) {
                BufferedImage img = ImageIO.read(ins);
                frame.setIconImage(img);
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
    }

    public void tryConnect() throws SQLException {
        OracleLogin lo = new OracleLogin("Login", this.getClass().toString());
        OracleLogin.OracleLoginResult res = lo.doLogin();
        if (res == null) {
            return;
        }
        if (!setConnection(res.connectionDesc, res.connection)) {
            res.connection.close();
        }
    }

    public boolean setConnection(OraConnectionDesc cd, OracleConnection connection) throws SQLException {
        connection.setAutoCommit(false);
        DBObjectsInstallation inst = new DBObjectsInstallation(connection);
        final boolean dbOk;
        switch (inst.checkDBObjects()) {
            case NOTHING:
                Object[] options = {"Yes",
                    "No"};
                int x = JOptionPane.showOptionDialog(null,
                        "The database objects needed to run code coverage do not exist.\n"
                        + "Do you want to create them now?",
                        "Recreate DB objects?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[1]);
                if (x == JOptionPane.YES_OPTION) {
                    inst.createDBOBjects();
                    dbOk = true;
                } else {
                    dbOk = false;
                }
                break;
            case MIXUP:
                if (askUserForRecreation()) {
                    inst.dropCodeCoverageDBObjects();
                    inst.createDBOBjects();
                    dbOk = true;
                } else {
                    dbOk = false;
                }
                break;
            case OK:
                dbOk = true;
                break;
            default:
                throw new Error("BUG");
        }
        if (!dbOk) {
            return false;
        }
        this.connection = connection;
        this.connectionDesc = cd;
        frame.setTitle("Code Coverage: " + this.connectionDesc.display());
        final String user;
        try (Statement stm = connection.createStatement();
                ResultSet rs = stm.executeQuery("select user from dual")) {
            rs.next();
            user = rs.getString(1);
        }
        this.codeCoverage = new CodeCoverage(this.connection, user);
        this.refresh();
        return true;
    }

    public void refresh() {
        if (this.connection == null) {
            this.packModel.clear();
            return;
        }
        try {
            ArrayList<PackInfo> pis = this.codeCoverage.getCCInfo();
            this.packModel.clear();
            int i = 0;
            for (PackInfo pi : pis) {
                this.packModel.add(i, pi);
                i++;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private DefaultListCellRenderer pack_cellrenderer = new javax.swing.DefaultListCellRenderer() {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value != null) {
                PackInfo pi = (PackInfo) value;
                c.setText(pi.name);
            } else {
                c.setText("?");
            }
            return c;
        }
    };

    private final DefaultListCellRenderer proc_cellrenderer = new javax.swing.DefaultListCellRenderer() {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value != null) {
                ProcedureAndRange pi = (ProcedureAndRange) value;
                c.setText(pi.name);
            } else {
                c.setText("?");
            }
            return c;
        }
    };

    private void pack_selection_change(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            PackInfo pi = (PackInfo) this.packList.getSelectedValue();
            setNewPackInfo(pi);
        }
    }
    
    private void procedureSelectionChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            ProcedureAndRange par = this.procList.getSelectedValue();
            if (par!=null) {
               this.sourceTextPane.setCaretPosition(par.range.start);
            }
        }
    }

    void setNewPackInfo(PackInfo pi) {
        try {
            currentPackinfo = pi;
            this.lblPackinfo.setText("" + pi);
            if (pi != null) {
                String source;
                this.current_package.setText(pi.name);
                if (pi.id > 0) {
                    CoverageInfo ci = this.codeCoverage.getCoverInfo(pi.id);
                    StyledDocument sd = this.sourceTextPane.getStyledDocument();
                    sd.remove(0, sd.getLength());
                    sd.insertString(0, ci.source, this.defStyle);
                    source = ci.source;
                    new Styler().setStyles(sd, ci.entries);
                    this.sourceTextPane.setCaretPosition(0);
                } else {
                    String s = this.codeCoverage.getPackageBodySource(pi.name);
                    StyledDocument sd = this.sourceTextPane.getStyledDocument();
                    sd.remove(0, sd.getLength());
                    sd.insertString(0, s, this.defStyle);
                    source = s;
                    this.sourceTextPane.setCaretPosition(0);
                }
                List<ProcedureAndRange> prl = this.codeCoverage.getProcedureRanges(source);
                this.procedureModel.clear();
                int i = 0;
                for (ProcedureAndRange pr : prl) {
                    this.procedureModel.add(i, pr);
                    i++;
                }
                if (pi.isValid && !pi.isCovered) {
                    this.startCoverage.setEnabled(false);
                } else {
                    this.startCoverage.setEnabled(false);
                }

                if (pi.isCovered) {
                    this.stopCoverage.setEnabled(true);
                } else {
                    this.stopCoverage.setEnabled(false);
                }
                if (!pi.isCovered && pi.isValid) {
                    this.startCoverage.setEnabled(true);
                } else {
                    this.startCoverage.setEnabled(false);
                }
            } else {
                this.startCoverage.setEnabled(false);
                this.stopCoverage.setEnabled(false);
                this.procedureModel.clear();
                StyledDocument sd = this.sourceTextPane.getStyledDocument();
                sd.remove(0, sd.getLength());
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        }
    }

    static java.util.Comparator<CoveredStatement> cmpEntry = new java.util.Comparator<CoveredStatement>() {
        @Override
        public int compare(CoveredStatement o1, CoveredStatement o2) {
            if (o1.start < o2.start) {
                return -1;
            }
            if (o1.start > o2.start) {
                return 1;
            }
            if (o1.end < o2.end) {
                return -1;
            }
            if (o1.end > o2.end) {
                return 1;
            }
            // should not happen unless o1==o2
            return 0;

        }
    };

    private class Styler {

        private StyledDocument doc;
        private ArrayList<CoveredStatement> sortedList;

        public void setStyles(StyledDocument doc, List<CoveredStatement> statements) {
            this.doc = doc;
            sortedList = new ArrayList<>();
            sortedList.addAll(statements);
            Collections.sort(sortedList, cmpEntry);
            for(CoveredStatement r :sortedList) {
                System.out.println(r);
            }
            int pos = 0;
            while (pos < sortedList.size()) {
                pos = setStyles(pos);
            }
        }

        private void setStyle(int from, int to, Style style) {
            System.out.println(" " + from + "-" + to + " " + style);
            if (from == to) {
                return;
            }
            this.doc.setCharacterAttributes(from, to - from, style, true);
        }

        private int setStyles(final int poss) {
            // einstieg mit pos
            final CoveredStatement cs = sortedList.get(poss);
            System.out.println("setStyles " + poss + " / " + cs);
            final Style myStyle;
            if (cs.hit) {
                myStyle = Gui2.this.greenStyle;
            } else {
                myStyle = Gui2.this.hotStyle;
            }
            int last_end = cs.start;
            int pos = poss + 1;
            while (true) {
                if (pos >= sortedList.size()) {
                    // last entry no contained elements set your style and return pos+1
                    setStyle(last_end, cs.end, myStyle);
                    return pos;
                }
                CoveredStatement next_cs = sortedList.get(pos);
                System.out.println("next_cs: " +next_cs);
                if (next_cs.start <= cs.end) {
                    // contained
                    setStyle(last_end, next_cs.start, myStyle);
                    last_end = next_cs.end;
                    pos = setStyles(pos);
                } else {
                    setStyle(last_end, cs.end, myStyle);
                    return pos;
                }
            }
        }
    }

    // once the selected package is changed:
// fetch the packinfo from the database, update the list entry
// fetch the original source from the database
// fetch the coverage info from the database
// fetch the procedures from the database
//  fill the procedure info list
//  fill the source text control
//  at least the database access should not be done on the EDT.
    Action startCoverage = new AbstractAction("Start Coverage") {

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                PackInfo pi = currentPackinfo;
                if (pi != null) {
                    if (pi.start == null || pi.end != null) {

                        CodeCoverage.StartCoverageResult res
                                = codeCoverage.startCoverage(pi.name);
                        if (res instanceof CodeCoverage.StartCoverageSuccess) {
                            // OK, nothing todo
                        } else if (res instanceof CodeCoverage.StartCoverageFailure) {
                            CodeCoverage.StartCoverageFailure fres = (CodeCoverage.StartCoverageFailure) res;
                            String msg = fres.errormsg;
                            JOptionPane.showMessageDialog(frame.getRootPane(), msg, "Error", JOptionPane.ERROR_MESSAGE);
                        }
                        PackInfo pi2 = codeCoverage.getPackInfo(pi.name);
                        DefaultListModel<PackInfo> lm = Gui2.this.packModel;
                        for (int i = 0; i < lm.size(); i++) {
                            if (lm.getElementAt(i).name.equals(pi2.name)) {
                                lm.setElementAt(pi2, i);
                                setNewPackInfo(pi2);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(Gui2.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }
    };

    Action stopCoverage = new AbstractAction("Stop Coverage") {

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                PackInfo pi = currentPackinfo;

                boolean ok = codeCoverage.stopCoverage(pi.name, false);
                if (!ok) {
                    Object[] options = {"Yes do it anyway!",
                        "Leave it as it is.",
                        "Just mark it as stopped"};
                    int n = JOptionPane.showOptionDialog(frame,
                            "The source has been changed, set old source again?",
                            "Replace Sourcè",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null, //do not use a custom Icon
                            options, //the titles of buttons
                            options[1]); //default button title

                    if (n == 0) {
                        boolean dummy = codeCoverage.stopCoverage(pi.name, true);
                    }
                }
                PackInfo pi2 = codeCoverage.getPackInfo(pi.name);
                DefaultListModel<PackInfo> lm = Gui2.this.packModel;
                for (int i = 0; i < lm.size(); i++) {
                    if (lm.getElementAt(i).name.equals(pi2.name)) {
                        lm.setElementAt(pi2, i);
                        setNewPackInfo(pi2);
                        break;
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    };
    
     public static boolean askUserForRecreation() {

        Object[] options = {"Yes",
            "No"};
        int x = JOptionPane.showOptionDialog(null,
                "The state of the db objects for code coverage in your DB is messy.\nRecreate them?",
                "Recreate DB objects?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1]);
        return x == JOptionPane.YES_OPTION;
    }
}