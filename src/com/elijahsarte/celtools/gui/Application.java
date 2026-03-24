package com.elijahsarte.celtools.gui;

import com.elijahsarte.celtools.gui.pages.*;
import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.colormodel.HSVBounds;
import com.elijahsarte.celtools.main.framefactory.FrameParser;
import com.elijahsarte.celtools.main.util.OptionalEx;
import com.elijahsarte.celtools.main.util.ProgrammingEx;
import com.elijahsarte.celtools.main.util.ReflectionEx;
import com.elijahsarte.celtools.main.util.StreamEx;
import com.elijahsarte.celtools.main.util.iterators.CoordIterator;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.mainex.IntegrityVerifier;
import com.elijahsarte.celtools.mainex.PrintInfo;
import com.elijahsarte.celtools.mainex.TaskTracker;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.noExcept;

public final class Application extends JFrame {

    private final List<ProgramPage> pages = List.of(new CLPP(), new CPC(), new CPMF(), new CAF());

    private final Border border = BorderFactory.createEmptyBorder(20, 20, 20, 20);
    private final CardLayout cardLayout;
    private final JPanel cardsPanel, rightPanel;
    private final JTextArea consoleArea;
    private final JScrollPane consoleScroll;
    private final JComboBox<String> combo;

    private final JButton runBtn = new JButton("Run"), stopBtn = new JButton("Stop");
    private SwingWorker<Void, String> worker;

    private boolean consoleAutoScrollLeft = true;
    private boolean consoleAdjustingHorizontally = false;



    public static final Preferences prefs = Preferences.userNodeForPackage(Application.class);

    // for performance in cold starts
    private static void warmUpLambdas() {
        noExcept(() -> {
            Class.forName("java.awt.Point");
            Class.forName("com.elijahsarte.celtools.main.framefactory.FrameParser");
            Class.forName("com.elijahsarte.celtools.main.util.function.blocks.forloop.ForLoopBase");
            Class.forName("com.elijahsarte.celtools.main.util.function.blocks.forloop.ForEach");
            Class.forName("com.elijahsarte.celtools.main.util.function.blocks.forloop.ForIncrement");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.TriConsumer");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.OctaConsumer");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.QuadConsumer");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.ThrowableSupplier");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.ThrowableRunnable");
            Class.forName("com.elijahsarte.celtools.main.util.iterators.CoordIterator");
            Class.forName("com.elijahsarte.celtools.main.util.iterators.ListMapIterator");
            Class.forName("com.elijahsarte.celtools.main.util.iterators.PixelIterator");
            Class.forName("com.elijahsarte.celtools.main.util.structures.collections.IntList");
            Class.forName("com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection");
            Class.forName("com.elijahsarte.celtools.main.util.MathEx");
            Class.forName("com.elijahsarte.celtools.main.util.CollectionsEx");
            Class.forName("com.elijahsarte.celtools.main.util.ProgrammingEx");
            Class.forName("com.elijahsarte.celtools.main.util.OptionalEx");
        });
        FrameParser.filterHSV(new int[400], 20, 20, new HSVBounds(new double[] { 0, 0, 0 }, new double[] { 1, 0, 0}));
        new CoordIterator(1, 2, 1, 2).execute((x, y) -> {});
        ProgrammingEx.varOper(6, (y) -> "8");
        ProgrammingEx.varMutate("a", b -> {});
        noExcept(() -> {});
        OptionalEx.ofCond(6, true).thenRun(() ->{}).orElse(() -> {});
        Main.groupPoints(new PointCollection(IntStream.rangeClosed(0, 10).mapToObj(i -> new Point(i, i + 1)).toList()), 4);
    }


    public Application() {

        super("CelTools");
        if (!IntegrityVerifier.verify()) {
            IntegrityVerifier.fail();
        }
        this.setTitle("CelTools - " + PrintInfo.print());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        combo = new JComboBox<>(pages.stream().map(ProgramPage::name).toArray(String[]::new));
        combo.setSelectedItem(prefs.get("last_page", pages.get(0).name()));
        combo.addActionListener(this::onSelectionChanged);
        JPanel dropdownWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        dropdownWrapper.add(combo);

        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBorder(border);
        pages.forEach(p -> cardsPanel.add(p, p.name()));
        this.onSelectionChanged(null);

        JPanel leftPanel = new JPanel(new BorderLayout());
        this.rightPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.add(dropdownWrapper, BorderLayout.NORTH);
        leftPanel.add(cardsPanel, BorderLayout.CENTER);


        runBtn.addActionListener(e -> {
            rightPanel.add(stopBtn, BorderLayout.NORTH);
            rightPanel.remove(runBtn);
            runBtn.setEnabled(false);
            runBtn.setVisible(false);
            stopBtn.setVisible(true);
            stopBtn.setEnabled(true);
            noExcept(this::onRun);
        });
        stopBtn.addActionListener(e -> {
            stopBtn.setEnabled(false);
//            if (!worker.cancel(true)) {
//                stopBtn.setEnabled(true);
//                stopBtn.setVisible(true);
//                return;
//            }
//            ReflectionEx.callMethod(worker, "commonDone");
            OptionalEx.ofNonNullable(noExcept(() -> (Thread) ReflectionEx.getField(worker, "execThread"))).thenRun(Thread::stop);
        });
        stopBtn.setVisible(false);
        rightPanel.add(runBtn, BorderLayout.NORTH);


        consoleArea = new JTextArea(12, 30) {
            @Override
            public void scrollRectToVisible(Rectangle aRect) {
                if (consoleAutoScrollLeft) aRect = new Rectangle(0, aRect.y, aRect.width, aRect.height);
                super.scrollRectToVisible(aRect);
            }
        };
        consoleArea.setEditable(false);
        this.consoleScroll = new JScrollPane(consoleArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        ((DefaultCaret) consoleArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        consoleScroll.getHorizontalScrollBar().addAdjustmentListener(e -> {
            if (consoleAdjustingHorizontally) return;
            consoleAutoScrollLeft = (e.getValue() == consoleScroll.getHorizontalScrollBar().getMinimum());
        });

        rightPanel.add(consoleScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setResizeWeight(0.6);
        split.setOneTouchExpandable(true);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(split, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }


    private ProgramPage getCurrPage() {
        return pages.get(combo.getSelectedIndex());
    }
    private void onSelectionChanged(ActionEvent e) {
        String sel = getCurrPage().name();
        cardLayout.show(cardsPanel, sel);
        prefs.put("last_page", sel);
    }
    private void onRun() {
        consoleArea.setText("");

        this.worker = new SwingWorker<>() {
            private PrintStream consoleOut = System.out;
            Thread execThread;
            @Override
            protected Void doInBackground() {
                this.consoleOut = System.out;
                this.execThread = Thread.currentThread();
                PrintStream ps = new PrintStream(new OutputStream() {
                    @Override
                    public void write(int b) {
                        publish(String.valueOf((char)b));
                    }
                    @Override
                    public void write(byte[] b, int off, int len) {
                        publish(new String(b, off, len));
                    }
                }, true);
                System.out.println("\n" + getCurrPage().name() + "\n---------------------------------");
                System.setOut(ps);

                try {
                    getCurrPage().program().get().run();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.out.print("\n");
                    e.printStackTrace(ps);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                StreamEx.forEachMulti(chunks.stream(), consoleArea::append, consoleOut::append);
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
//                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }

            @Override
            protected void done() {

                try {
                    get();
                } catch (InterruptedException ie) {
                    System.out.println("cancelled");
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    commonDone();
                }
            }

            private void commonDone() {
                TaskTracker.endTrack();
                stopBtn.setVisible(false);
                stopBtn.setEnabled(false);
                runBtn.setVisible(true);
                runBtn.setEnabled(true);
                rightPanel.remove(stopBtn);
                rightPanel.add(runBtn, BorderLayout.NORTH);
                System.out.println("done");
                System.setOut(this.consoleOut);
            }
        };
        worker.execute();
    }

    public static void main(String[] args) {
        Main.async(Application::warmUpLambdas);
        SwingUtilities.invokeLater(() -> new Application().setVisible(true));
    }
}

