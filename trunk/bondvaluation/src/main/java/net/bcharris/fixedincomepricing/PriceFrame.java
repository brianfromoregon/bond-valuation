package net.bcharris.fixedincomepricing;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.ui.ConsoleTextEditor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.codehaus.groovy.control.CompilationFailedException;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class PriceFrame extends javax.swing.JFrame {

    private XYPlot pricePlot;
    private XYPlot factorPlot;
    private double[] factors;
    private ConsoleTextEditor factorScriptEditor;
    private String DEFAULT_FACTOR_SCRIPT = "1";
    private String factorScript;

    public PriceFrame() {
        super("Bond Valuation");
        initComponents();
        factorScript = DEFAULT_FACTOR_SCRIPT;
        factorScriptEditor = new ConsoleTextEditor();
        factorScriptEditor.getTextEditor().setText(factorScript);
        factorScriptEditor.getTextEditor().addKeyListener(new KeyAdapter() {

            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == '\n' && (e.isShiftDown() || e.isControlDown())) {
                    recomputeButton.doClick();
                }
            }
        });
        factorScriptPanel.add(factorScriptEditor, BorderLayout.CENTER);
        NumberAxis priceAxis = new NumberAxis("Price");
        priceAxis.setAutoRangeIncludesZero(false);
        NumberAxis factorAxis = new NumberAxis("Factor");
        NumberAxis dtmAxis = new NumberAxis("Days To Maturity");
        dtmAxis.setInverted(true);
        NumberAxis periodAxis = new NumberAxis("Period");
        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        XYLineAndShapeRenderer shapeRenderer = new XYLineAndShapeRenderer(false, true);
        pricePlot = new XYPlot();
        pricePlot.setDomainAxis(dtmAxis);
        pricePlot.setRangeAxis(priceAxis);
        pricePlot.setRenderer(0, lineRenderer);
        pricePlot.setRenderer(1, shapeRenderer);
        factorPlot = new XYPlot();
        factorPlot.setDomainAxis(periodAxis);
        factorPlot.setRangeAxis(factorAxis);
        factorPlot.setRenderer(0, new XYStepRenderer());
        priceChartPanel.add(new ChartPanel(new JFreeChart("", null, pricePlot, false)));
        factorChartPanel.add(new ChartPanel(new JFreeChart("", null, factorPlot, false)));
        processFactorScript();
        pack();
    }

    private void redrawPrice() {
        int paymentDelay = (Integer) paymentDelaySpinner.getValue();
        double yield = (Double) yieldSpinner.getValue() * .01;
        double coupon = (Double) couponSpinner.getValue() * .01;
        int couponsPerYear = Integer.valueOf(couponsPerYearSpinner.getValue().toString());
        int daysToMaturity = (Integer) daysToMaturitySpinner.getValue();
        int periodLength = (int) (360d / couponsPerYear);
        double periodCoupon = coupon / couponsPerYear;
        double periodYield = yield / couponsPerYear;

        XYSeries prices = new XYSeries(0, false);
        XYSeries coupons = new XYSeries(0, false);
        for (int i = daysToMaturity; i >= 1; i--) {
            double price = Calc.price(i, factors, periodYield, periodLength, periodCoupon, paymentDelay);
            prices.add(i, price);
            if (i % periodLength == 0) {
                coupons.add(i, price);
            }
        }
        pricePlot.setDataset(0, new XYSeriesCollection(prices));
        pricePlot.setDataset(1, new XYSeriesCollection(coupons));
    }

    private void redrawFactors() {
        XYSeries factorSeries = new XYSeries(0, false);
        for (int i = 0; i < factors.length; i++) {
            factorSeries.add(i, factors[i]);
        }
        factorPlot.setDataset(0, new XYSeriesCollection(factorSeries));
    }

    private void processFactorScript() {
        String newFactorScript = factorScriptEditor.getTextEditor().getText();
        Double newFactor;
        try
        {
            String candidate = newFactorScript.trim();
            if (candidate.startsWith("return "))
                candidate = candidate.substring(7).trim();
            newFactor = Double.parseDouble(candidate);
        }
        catch (NumberFormatException ex)
        {
            newFactor = null;
        }
        int couponsPerYear = Integer.valueOf(couponsPerYearSpinner.getValue().toString());
        int daysToMaturity = (Integer) daysToMaturitySpinner.getValue();
        int periodLength = (int) (360d / couponsPerYear);
        int neededFactors = daysToMaturity / periodLength;
        double[] newFactors = new double[neededFactors + 2];
        newFactors[0] = 1;
        newFactors[newFactors.length - 1] = 0;
        Binding binding = new Binding();
        for (int i = 1; i <= neededFactors; i++) {
            double computedFactor;
            if (newFactor != null)
            {
                computedFactor = newFactor;
            }
            else
            {
                binding.setVariable("period", new Integer(i));
                binding.setVariable("periods", neededFactors + 1);
                GroovyShell shell = new GroovyShell(binding);
                Object result;
                try {
                    result = shell.evaluate(newFactorScript);
                } catch (CompilationFailedException ex) {
                    showError("Groovy compilation error", "Could not compile your Groovy script.  " + ex.getMessage());
                    return;
                } catch (Exception ex) {
                    showError("Groovy execution error", "There was a problem with your Groovy script.  " + ex.getMessage());
                    return;
                }
                if (result == null) {
                    showError("Groovy script error", "Your script did not return a value.");
                    return;
                }
                try {
                    computedFactor = Double.valueOf(result.toString());
                } catch (NumberFormatException ex) {
                    showError("Groovy script error", "Your script did not return a valid floating point number.");
                    return;
                }
            }
            if (computedFactor <= 0) {
                showError("Invalid factor", "Your script returned a factor less than or equal to 0.");
                return;
            }
            if (computedFactor > 1) {
                showError("Invalid factor", "Your script returned a factor greater than 1.");
                return;
            }
            if (computedFactor > newFactors[i - 1]) {
                showError("Invalid factor", "Your script computed non-decreasing factors.");
                return;
            }
            newFactors[i] = computedFactor;
        }

        factors = newFactors;
        factorScript = newFactorScript;
        redrawFactors();
        redrawPrice();
    }

    private void showError(String title, String msg) {
        JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(msg)), title, JOptionPane.ERROR_MESSAGE);
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        daysToMaturitySpinner = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        couponSpinner = new javax.swing.JSpinner();
        jLabel3 = new javax.swing.JLabel();
        yieldSpinner = new javax.swing.JSpinner();
        jLabel4 = new javax.swing.JLabel();
        couponsPerYearSpinner = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        paymentDelaySpinner = new javax.swing.JSpinner();
        priceChartPanel = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jSplitPane1 = new javax.swing.JSplitPane();
        factorScriptPanel = new javax.swing.JPanel();
        jPanel5 = new javax.swing.JPanel();
        recomputeButton = new javax.swing.JButton();
        helpButton = new javax.swing.JButton();
        factorChartPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jEditorPane1 = new javax.swing.JEditorPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setLayout(new java.awt.BorderLayout());

        jPanel3.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Days To Maturity");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 2);
        jPanel3.add(jLabel1, gridBagConstraints);

        daysToMaturitySpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1000), Integer.valueOf(1), null, Integer.valueOf(100)));
        daysToMaturitySpinner.setPreferredSize(new java.awt.Dimension(70, 20));
        daysToMaturitySpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                daysToMaturitySpinnerStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel3.add(daysToMaturitySpinner, gridBagConstraints);

        jLabel2.setText("% Coupon");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 2);
        jPanel3.add(jLabel2, gridBagConstraints);

        couponSpinner.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(4.1d), Double.valueOf(0.0d), null, Double.valueOf(0.1d)));
        couponSpinner.setPreferredSize(new java.awt.Dimension(60, 20));
        couponSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                couponSpinnerStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel3.add(couponSpinner, gridBagConstraints);

        jLabel3.setText("% Yield");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 2);
        jPanel3.add(jLabel3, gridBagConstraints);

        yieldSpinner.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(4.0d), Double.valueOf(0.0d), null, Double.valueOf(0.1d)));
        yieldSpinner.setPreferredSize(new java.awt.Dimension(60, 20));
        yieldSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                yieldSpinnerStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel3.add(yieldSpinner, gridBagConstraints);

        jLabel4.setText("Coupons Per Year");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 2);
        jPanel3.add(jLabel4, gridBagConstraints);

        couponsPerYearSpinner.setModel(new javax.swing.SpinnerListModel(new String[] {"1", "2", "3", "4", "6", "12"}));
        couponsPerYearSpinner.setPreferredSize(new java.awt.Dimension(40, 20));
        couponsPerYearSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                couponsPerYearSpinnerStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        jPanel3.add(couponsPerYearSpinner, gridBagConstraints);

        jLabel5.setText("Payment Delay");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 2);
        jPanel3.add(jLabel5, gridBagConstraints);

        paymentDelaySpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(0), Integer.valueOf(0), null, Integer.valueOf(1)));
        paymentDelaySpinner.setPreferredSize(new java.awt.Dimension(50, 20));
        paymentDelaySpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                paymentDelaySpinnerStateChanged(evt);
            }
        });
        jPanel3.add(paymentDelaySpinner, new java.awt.GridBagConstraints());

        jPanel1.add(jPanel3, java.awt.BorderLayout.SOUTH);

        priceChartPanel.setLayout(new java.awt.BorderLayout());
        jPanel1.add(priceChartPanel, java.awt.BorderLayout.CENTER);

        jTabbedPane1.addTab("Prices", jPanel1);

        jPanel4.setLayout(new java.awt.BorderLayout());

        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane1.setResizeWeight(1.0);

        factorScriptPanel.setLayout(new java.awt.BorderLayout());

        jPanel5.setLayout(new java.awt.GridBagLayout());

        recomputeButton.setText("Recompute");
        recomputeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recomputeButtonActionPerformed(evt);
            }
        });
        jPanel5.add(recomputeButton, new java.awt.GridBagConstraints());

        helpButton.setText("Help");
        helpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        jPanel5.add(helpButton, gridBagConstraints);

        factorScriptPanel.add(jPanel5, java.awt.BorderLayout.EAST);

        jSplitPane1.setBottomComponent(factorScriptPanel);

        factorChartPanel.setLayout(new java.awt.BorderLayout());
        jSplitPane1.setTopComponent(factorChartPanel);

        jPanel4.add(jSplitPane1, java.awt.BorderLayout.CENTER);

        jTabbedPane1.addTab("Factors", jPanel4);

        jEditorPane1.setContentType("text/html");
        jEditorPane1.setText("<html>\n<h2>This is a simplistic Bond valuation tool built to demonstrate the clean price formula.</h2>\nThere are four peculiarities in the price curve which can cause headaches for constant yield amortization:\n<ol>\n<li>Dips between coupon dates caused by the next coupon cash flow being properly discounted but not the subtracted accrued.</li>\n<li>Jumps on coupon dates caused by payment delay when, again, the subtracted accrued is not discounted.</li>\n<li>Jumps on coupon dates caused by early factor drops (paydowns).</li>\n<li>Not converging to par on maturity because of a payment delay.</li>\n</ol>\nThis tool makes a few assumptions:\n<ol>\n<li>A 30/360 day count convention is used, based on days to maturity instead of actual calendar dates.</li>\n<li>Only a fixed rate is supported, no variable/floating rates.</li>\n</ol>\n</html>");
        jScrollPane1.setViewportView(jEditorPane1);

        jTabbedPane1.addTab("Discussion", jScrollPane1);

        getContentPane().add(jTabbedPane1, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void daysToMaturitySpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_daysToMaturitySpinnerStateChanged
        processFactorScript();
    }//GEN-LAST:event_daysToMaturitySpinnerStateChanged

    private void couponSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_couponSpinnerStateChanged
        redrawPrice();
    }//GEN-LAST:event_couponSpinnerStateChanged

    private void yieldSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_yieldSpinnerStateChanged
        redrawPrice();
    }//GEN-LAST:event_yieldSpinnerStateChanged

    private void couponsPerYearSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_couponsPerYearSpinnerStateChanged
        processFactorScript();
    }//GEN-LAST:event_couponsPerYearSpinnerStateChanged

    private void paymentDelaySpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_paymentDelaySpinnerStateChanged
        redrawPrice();
    }//GEN-LAST:event_paymentDelaySpinnerStateChanged

    private void recomputeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recomputeButtonActionPerformed
        processFactorScript();
    }//GEN-LAST:event_recomputeButtonActionPerformed

    private void helpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpButtonActionPerformed
        String helpText = "<html>"
                + "<h2>How to write a factor generation script</h2>"
                + "Use the Groovy programming language to specify factors over time.  "
                + "Your script will be called once for each period, except the first (hard coded to 1) and the last (hard coded to 0).  "
                + "A single session is used to compute an entire factor schedule, so any variables you create can be accessed again.<br/>  "
                + "The predefined variables are:  "
                + "<ul>"
                + "<li><b>period</b>: The period for which a factor is needed</li>"
                + "<li><b>periods</b>: The total number of periods</li>"
                + "</ul>"
                + "Examples:  "
                + "<ul>"
                + "<li><b>1</b>: Pay at maturity.</li>"
                + "<li><b>1/(period+1)</b>: Quickly decreasing factor schedule.</li>"
                + "<li><b>if (period==1) 1; else 0.1</b>: Large drop after period 1.</li>"
                + "<li><b>if (period==1) x=2; else x+=0.1; return 1/x</b>: Demonstrating saving state.</li>"
                + "<li><b>(periods-period)/periods</b>: Stair stepping.</li>"
                + "</ul>"
                + "</html>";
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setText(helpText);
        pane.setEditable(false);
        JDialog dialog = new JDialog(this, "Factor Generation Help");
        dialog.add(new JScrollPane(pane));
        dialog.setPreferredSize(new Dimension(600, 400));
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }//GEN-LAST:event_helpButtonActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                PriceFrame priceFrame = new PriceFrame();
                priceFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                priceFrame.setLocationRelativeTo(null);
                priceFrame.setVisible(true);
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSpinner couponSpinner;
    private javax.swing.JSpinner couponsPerYearSpinner;
    private javax.swing.JSpinner daysToMaturitySpinner;
    private javax.swing.JPanel factorChartPanel;
    private javax.swing.JPanel factorScriptPanel;
    private javax.swing.JButton helpButton;
    private javax.swing.JEditorPane jEditorPane1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JSpinner paymentDelaySpinner;
    private javax.swing.JPanel priceChartPanel;
    private javax.swing.JButton recomputeButton;
    private javax.swing.JSpinner yieldSpinner;
    // End of variables declaration//GEN-END:variables
}
