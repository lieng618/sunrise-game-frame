package stress.frame;

import core.client.StressManager;
import sendmsg.frame.components.ModernButton;
import sendmsg.frame.components.ModernTextField;
import sendmsg.frame.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 压测工具主界面 - 分阶段耗时统计 + 发包 TPS
 */
public class StressFrame extends JFrame {

    private ModernTextField clientCountField;
    private ModernButton addButton;
    private ModernButton removeButton;
    private ModernButton stopAllButton;

    private JLabel totalLabel;
    private JLabel addressLabel;
    private JLabel successLabel;
    private JLabel failedLabel;

    private JRadioButton pingRadio;
    private JRadioButton businessRadio;
    private ModernTextField totalPacketsField;
    private ModernButton sendPacketsButton;

    private JTextArea logArea;
    private JScrollPane logScrollPane;

    public StressFrame() {
        ThemeManager.init();
        initFrame();
        initComponents();
        layoutComponents();
        setupCallbacks();
    }

    private void initFrame() {
        setTitle("Sunrise 压测工具");
        setSize(1200, 850);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        getContentPane().setBackground(ThemeManager.BACKGROUND);
    }

    private void initComponents() {
        clientCountField = new ModernTextField("10", 8);
        addButton = new ModernButton("添加客户端", ModernButton.ButtonType.PRIMARY);
        addButton.addActionListener(e -> handleAddClients());
        removeButton = new ModernButton("移除客户端", ModernButton.ButtonType.WARNING);
        removeButton.addActionListener(e -> handleRemoveClients());
        stopAllButton = new ModernButton("停止全部", ModernButton.ButtonType.DANGER);
        stopAllButton.addActionListener(e -> handleStopAll());

        totalLabel = new JLabel("0");
        addressLabel = new JLabel("0");
        successLabel = new JLabel("0");
        failedLabel = new JLabel("0");

        pingRadio = new JRadioButton("Ping 包", true);
        businessRadio = new JRadioButton("业务包（获取背包）");
        ButtonGroup packetGroup = new ButtonGroup();
        packetGroup.add(pingRadio);
        packetGroup.add(businessRadio);
        Font radioFont = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
        pingRadio.setFont(radioFont);
        businessRadio.setFont(radioFont);
        pingRadio.setOpaque(false);
        businessRadio.setOpaque(false);

        totalPacketsField = new ModernTextField("10000", 12);
        sendPacketsButton = new ModernButton("开始发包压测", ModernButton.ButtonType.SUCCESS);
        sendPacketsButton.addActionListener(e -> handleSendPackets());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(212, 212, 212));
        logArea.setMargin(new Insets(8, 8, 8, 8));
        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50)));
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());
        add(createHeader(), BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(ThemeManager.BACKGROUND);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel topPanel = new JPanel(new BorderLayout(12, 0));
        topPanel.setOpaque(false);
        topPanel.add(createControlPanel(), BorderLayout.CENTER);
        topPanel.add(createStatsPanel(), BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout(12, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(createPacketPanel(), BorderLayout.WEST);
        bottomPanel.add(createLogPanel(), BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(bottomPanel, BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 12));
        header.setBackground(ThemeManager.CARD_BACKGROUND);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER));

        JLabel title = new JLabel("压测工具 — 分阶段耗时统计 / 发包 TPS");
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 15));
        title.setForeground(ThemeManager.TEXT_PRIMARY);
        header.add(title);
        return header;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeManager.CARD_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel titleLabel = new JLabel("客户端控制");
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        titleLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(titleLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(6, 0, 12, 0);
        panel.add(new JSeparator(), gbc);

        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel countLabel = new JLabel("人数:");
        countLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        panel.add(countLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(clientCountField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(addButton, gbc);

        gbc.gridx = 3;
        panel.add(removeButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(12, 0, 0, 0);
        panel.add(stopAllButton, gbc);

        JLabel hint = new JLabel("<html>添加后自动执行：阶段1 HTTP 获取地址 → 阶段2 连接并登录至选角完成</html>");
        hint.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        hint.setForeground(ThemeManager.TEXT_SECONDARY);
        gbc.gridy = 4;
        gbc.insets = new Insets(10, 0, 0, 0);
        panel.add(hint, gbc);

        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBackground(ThemeManager.CARD_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));
        panel.setPreferredSize(new Dimension(360, 0));

        panel.add(makeStatCard("总数", totalLabel, ThemeManager.GRAY_100, ThemeManager.GRAY_700));
        panel.add(makeStatCard("已获地址", addressLabel, new Color(254, 243, 199), new Color(180, 120, 0)));
        panel.add(makeStatCard("登录成功", successLabel, new Color(219, 234, 254), ThemeManager.PRIMARY));
        panel.add(makeStatCard("失败", failedLabel, new Color(254, 226, 226), ThemeManager.DANGER));
        return panel;
    }

    private JPanel makeStatCard(String title, JLabel valueLabel, Color bg, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(bg);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.darker(bg, 0.08f), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        JLabel t = new JLabel(title);
        t.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        t.setForeground(ThemeManager.TEXT_SECONDARY);
        card.add(t, BorderLayout.NORTH);

        valueLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 22));
        valueLabel.setForeground(valueColor);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createPacketPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(ThemeManager.CARD_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));
        panel.setPreferredSize(new Dimension(480, 0));

        JLabel titleLabel = new JLabel("发包压测");
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        titleLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        panel.add(titleLabel, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JPanel typeBox = new JPanel();
        typeBox.setLayout(new BoxLayout(typeBox, BoxLayout.Y_AXIS));
        typeBox.setOpaque(false);
        typeBox.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER),
                "协议类型", 0, 0,
                new Font("Microsoft YaHei UI", Font.PLAIN, 11),
                ThemeManager.TEXT_SECONDARY
        ));
        typeBox.add(pingRadio);
        typeBox.add(Box.createVerticalStrut(4));
        typeBox.add(businessRadio);
        content.add(typeBox);
        content.add(Box.createVerticalStrut(12));

        content.add(makeFieldRow("发包总数:", totalPacketsField));
        content.add(Box.createVerticalStrut(8));

        JLabel hint = new JLabel("<html>所有人合计发送该数量，均分到各连接；每连接最多64个未回包在途；批量写+预序列化；TPS以收齐回包为准</html>");
        hint.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        hint.setForeground(ThemeManager.TEXT_SECONDARY);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(hint);
        content.add(Box.createVerticalStrut(12));

        sendPacketsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(sendPacketsButton);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel makeFieldRow(String labelText, JComponent comp) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        label.setPreferredSize(new Dimension(80, 32));
        row.add(label, BorderLayout.WEST);
        row.add(comp, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return row;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(ThemeManager.CARD_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("运行日志");
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        titleLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JLabel clearBtn = new JLabel("清空日志");
        clearBtn.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        clearBtn.setForeground(ThemeManager.TEXT_SECONDARY);
        clearBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                logArea.setText("");
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                clearBtn.setForeground(ThemeManager.PRIMARY);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                clearBtn.setForeground(ThemeManager.TEXT_SECONDARY);
            }
        });
        headerPanel.add(clearBtn, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(logScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void setupCallbacks() {
        StressManager.setLogCallback(this::appendLog);
        StressManager.setStatsCallback(stats -> SwingUtilities.invokeLater(() -> {
            totalLabel.setText(String.valueOf(stats.getTotal()));
            addressLabel.setText(String.valueOf(stats.getAddressFetched()));
            successLabel.setText(String.valueOf(stats.getLoginSuccess()));
            failedLabel.setText(String.valueOf(stats.getLoginFailed()));
        }));
    }

    private void handleAddClients() {
        try {
            int count = Integer.parseInt(clientCountField.getText().trim());
            if (count <= 0) {
                showError("请输入有效的人数");
                return;
            }
            addButton.setEnabled(false);
            addButton.setTemporaryState("压测中...", ThemeManager.GRAY_400, Color.WHITE);
            new Thread(() -> {
                StressManager.addClients(count);
                SwingUtilities.invokeLater(() -> {
                    addButton.setEnabled(true);
                    addButton.restoreDefaultState();
                });
            }).start();
        } catch (NumberFormatException e) {
            showError("请输入有效的数字");
        }
    }

    private void handleRemoveClients() {
        try {
            int count = Integer.parseInt(clientCountField.getText().trim());
            if (count <= 0) {
                showError("请输入有效的人数");
                return;
            }
            StressManager.removeClients(count);
        } catch (NumberFormatException e) {
            showError("请输入有效的数字");
        }
    }

    private void handleStopAll() {
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this,
                "确定要停止所有压测客户端吗？", "确认", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)) {
            StressManager.stopAll();
        }
    }

    private void handleSendPackets() {
        try {
            long total = Long.parseLong(totalPacketsField.getText().trim());
            if (total <= 0) {
                showError("发包总数必须大于 0");
                return;
            }
            StressManager.PacketMode mode = pingRadio.isSelected()
                    ? StressManager.PacketMode.PING
                    : StressManager.PacketMode.BUSINESS_GET_ITEM_LIST;
            StressManager.startPacketStress(mode, total);
            sendPacketsButton.setTemporaryState("压测中...", ThemeManager.SUCCESS, Color.WHITE);
            new Timer(800, e -> {
                sendPacketsButton.restoreDefaultState();
                ((Timer) e.getSource()).stop();
            }).start();
        } catch (NumberFormatException e) {
            showError("请输入有效的发包总数");
        }
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.WARNING_MESSAGE);
    }

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            StressFrame frame = new StressFrame();
            frame.setVisible(true);
            Runtime.getRuntime().addShutdownHook(new Thread(StressManager::shutdown));
        });
    }
}
