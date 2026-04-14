package bot.frame;

import core.client.BotManager;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import sendmsg.frame.components.ModernButton;
import sendmsg.frame.components.ModernTextField;
import sendmsg.frame.theme.ThemeManager;
import core.message.MessageUtil;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * 压测机器人主界面
 */
public class BotFrame extends JFrame {

    private ModernTextField botCountField;
    private ModernButton addBotsButton;
    private ModernButton removeBotsButton;
    private ModernButton stopAllButton;
    
    private JLabel totalLabel;
    private JLabel onlineLabel;
    private JLabel successLabel;
    private JLabel failedLabel;
    
    private JComboBox<String> typeComboBox;
    private JComboBox<String> idComboBox;
    private JPanel messagePanel;
    private JScrollPane messageScrollPane;
    private ModernTextField intervalField;
    private ModernTextField timesField;
    private ModernButton sendButton;
    
    private JTextArea logArea;
    private JScrollPane logScrollPane;

    public BotFrame() {
        ThemeManager.init();
        initFrame();
        initComponents();
        layoutComponents();
        setupCallbacks();
    }

    private void initFrame() {
        setTitle("Sunrise 压测机器人");
        setSize(1200, 850);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        getContentPane().setBackground(ThemeManager.BACKGROUND);

    }

    private void initComponents() {
        botCountField = new ModernTextField("10", 8);
        addBotsButton = new ModernButton("添加机器人", ModernButton.ButtonType.PRIMARY);
        addBotsButton.addActionListener(e -> handleAddBots());
        removeBotsButton = new ModernButton("移除机器人", ModernButton.ButtonType.WARNING);
        removeBotsButton.addActionListener(e -> handleRemoveBots());
        stopAllButton = new ModernButton("停止全部", ModernButton.ButtonType.DANGER);
        stopAllButton.addActionListener(e -> handleStopAll());
        
        totalLabel = new JLabel("0");
        onlineLabel = new JLabel("0");
        successLabel = new JLabel("0");
        failedLabel = new JLabel("0");

        typeComboBox = new JComboBox<>(MessageUtil.getTopicNames());
        typeComboBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        typeComboBox.setPreferredSize(new Dimension(0, 38));
        typeComboBox.addActionListener(e -> updateIdComboBox());

        idComboBox = new JComboBox<>();
        idComboBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        idComboBox.setPreferredSize(new Dimension(0, 38));
        idComboBox.addActionListener(e -> updateMessageFields());
        
        messagePanel = new JPanel(new GridBagLayout());
        messagePanel.setBackground(ThemeManager.CARD_BACKGROUND);
        messageScrollPane = new JScrollPane(messagePanel);
        messageScrollPane.setBorder(BorderFactory.createEmptyBorder());
        messageScrollPane.getViewport().setBackground(ThemeManager.CARD_BACKGROUND);
        
        intervalField = new ModernTextField("1000", 8);
        timesField = new ModernTextField("1", 8);
        sendButton = new ModernButton("开始发送", ModernButton.ButtonType.SUCCESS);
        sendButton.addActionListener(e -> handleSend());
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(212, 212, 212));
        logArea.setMargin(new Insets(8, 8, 8, 8));
        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50)));
        updateIdComboBox();
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // 顶部标题
        add(createHeader(), BorderLayout.NORTH);
        
        // 主内容：上半部分（控制+统计），下半部分（消息+日志）
        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(ThemeManager.BACKGROUND);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        
        // 上半部分：控制面板 + 统计面板 水平排列
        JPanel topPanel = new JPanel(new BorderLayout(12, 0));
        topPanel.setOpaque(false);
        topPanel.add(createControlPanel(), BorderLayout.CENTER);
        topPanel.add(createStatsPanel(), BorderLayout.EAST);
        
        // 下半部分：消息配置 + 日志 水平排列
        JPanel bottomPanel = new JPanel(new BorderLayout(12, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(createMessagePanel(), BorderLayout.WEST);
        bottomPanel.add(createLogPanel(), BorderLayout.CENTER);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(bottomPanel, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private JPanel createHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 12));
        header.setBackground(ThemeManager.CARD_BACKGROUND);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER));
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

        // 标题
        JLabel titleLabel = new JLabel("压测机器人");
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        titleLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(titleLabel, gbc);

        // 分隔线
        gbc.gridy = 1; gbc.insets = new Insets(6, 0, 12, 0);
        panel.add(new JSeparator(), gbc);
        
        // 数量标签 + 输入框
        gbc.gridy = 2; gbc.gridwidth = 1; gbc.insets = new Insets(6, 8, 6, 8);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        JLabel countLabel = new JLabel("数量:");
        countLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        panel.add(countLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0.3; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(botCountField, gbc);
        
        // 按钮：添加
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(addBotsButton, gbc);
        
        // 按钮：移除
        gbc.gridx = 3;
        panel.add(removeBotsButton, gbc);
        
        // 按钮：停止全部（独占一行）
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(12, 0, 0, 0);
        panel.add(stopAllButton, gbc);
        
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
        panel.add(makeStatCard("在线", onlineLabel, new Color(220, 252, 231), ThemeManager.SUCCESS));
        panel.add(makeStatCard("成功", successLabel, new Color(219, 234, 254), ThemeManager.PRIMARY));
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

    private JPanel createMessagePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(ThemeManager.CARD_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ThemeManager.BORDER),
            BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));
        panel.setPreferredSize(new Dimension(480, 0));
        
        // 标题
        JLabel titleLabel = new JLabel("消息配置");
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        titleLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // 内容
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        
        // 协议类型行
        content.add(makeFieldRow("协议类型:", typeComboBox));
        content.add(Box.createVerticalStrut(8));
        
        // 消息ID行
        content.add(makeFieldRow("消息ID:", idComboBox));
        content.add(Box.createVerticalStrut(8));
        
        // 消息字段区域
        JPanel fieldBox = new JPanel(new BorderLayout());
        fieldBox.setOpaque(false);
        fieldBox.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.BORDER),
            "消息字段", 0, 0,
            new Font("Microsoft YaHei UI", Font.PLAIN, 11),
            ThemeManager.TEXT_SECONDARY
        ));
        fieldBox.setPreferredSize(new Dimension(0, 140));
        fieldBox.add(messageScrollPane, BorderLayout.CENTER);
        content.add(fieldBox);
        content.add(Box.createVerticalStrut(8));
        
        // 间隔和次数
        JPanel paramsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        paramsPanel.setOpaque(false);
        
        JPanel intervalBox = new JPanel(new BorderLayout(8, 0));
        intervalBox.setOpaque(false);
        JLabel il = new JLabel("间隔(ms):");
        il.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        intervalBox.add(il, BorderLayout.WEST);
        intervalBox.add(intervalField, BorderLayout.CENTER);
        
        JPanel timesBox = new JPanel(new BorderLayout(8, 0));
        timesBox.setOpaque(false);
        JLabel tl = new JLabel("发送次数:");
        tl.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        timesBox.add(tl, BorderLayout.WEST);
        timesBox.add(timesField, BorderLayout.CENTER);
        
        paramsPanel.add(intervalBox);
        paramsPanel.add(timesBox);
        content.add(paramsPanel);
        content.add(Box.createVerticalStrut(12));
        
        // 发送按钮
        sendButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(sendButton);
        
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }
    
    private JPanel makeFieldRow(String labelText, JComponent comp) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        label.setPreferredSize(new Dimension(70, 32));
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
        
        // 标题 + 清除按钮
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
        BotManager.setLogCallback(this::appendLog);
        BotManager.setStatsCallback(stats -> {
            SwingUtilities.invokeLater(() -> {
                totalLabel.setText(String.valueOf(stats.getTotal()));
                onlineLabel.setText(String.valueOf(stats.getConnected()));
                successLabel.setText(String.valueOf(stats.getLoginSuccess()));
                failedLabel.setText(String.valueOf(stats.getLoginFailed()));
            });
        });
    }

    private void handleAddBots() {
        try {
            int count = Integer.parseInt(botCountField.getText().trim());
            if (count <= 0) { showError("请输入有效的机器人数量"); return; }
            addBotsButton.setEnabled(false);
            addBotsButton.setTemporaryState("添加中...", ThemeManager.GRAY_400, Color.WHITE);
            new Thread(() -> {
                BotManager.addBots(count);
                SwingUtilities.invokeLater(() -> { addBotsButton.setEnabled(true); addBotsButton.restoreDefaultState(); });
            }).start();
        } catch (NumberFormatException e) { showError("请输入有效的数字"); }
    }

    private void handleRemoveBots() {
        try {
            int count = Integer.parseInt(botCountField.getText().trim());
            if (count <= 0) { showError("请输入有效的机器人数量"); return; }
            BotManager.removeBots(count);
        } catch (NumberFormatException e) { showError("请输入有效的数字"); }
    }

    private void handleStopAll() {
        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, "确定要停止所有机器人吗？", "确认", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)) {
            BotManager.stopAll();
        }
    }

    private void handleSend() {
        String selectedTopic = (String) typeComboBox.getSelectedItem();
        String selectedId = (String) idComboBox.getSelectedItem();
        if (selectedTopic == null || selectedId == null) { showError("请选择协议类型和消息ID"); return; }
        
        try {
            int interval = Integer.parseInt(intervalField.getText().trim());
            int times = Integer.parseInt(timesField.getText().trim());
            if (interval < 0 || times <= 0) { showError("请输入有效的间隔和次数"); return; }
            
            int pkgType = MessageUtil.getTopicNumMap().get(selectedTopic);
            Class<?> messageClass = MessageUtil.getIdClassMap().get(pkgType + selectedId);
            Message message = null;
            if (messageClass != null) {
                Method newBuilderMethod = messageClass.getMethod("newBuilder");
                Message.Builder builder = (Message.Builder) newBuilderMethod.invoke(null);
                for (Component c : messagePanel.getComponents()) {
                    if (c instanceof JTextField tf) {
                        String fn = tf.getName();
                        Descriptors.FieldDescriptor fd = builder.getDescriptorForType().findFieldByName(fn);
                        if (fd != null) MessageUtil.invoke(builder, fd, tf.getText());
                    }
                }
                message = builder.build();
            }
            
            int packetId = MessageUtil.getIdNumMap().get(pkgType + selectedId);
            BotManager.sendToAllBots(pkgType, packetId, message == null ? null : message.toByteString(), interval, times);
            appendLog("[系统] 已发送: topic=" + selectedTopic + ", packetId=" + packetId);
            sendButton.setTemporaryState("已发送", ThemeManager.SUCCESS, Color.WHITE);
            new Timer(800, e -> { sendButton.restoreDefaultState(); ((Timer)e.getSource()).stop(); }).start();
        } catch (NumberFormatException e) { showError("请输入有效的数字"); }
        catch (Exception e) { appendLog("[错误] " + e.getMessage()); }
    }

    private void updateIdComboBox() {
        idComboBox.removeAllItems();
        String topic = (String) typeComboBox.getSelectedItem();
        if (topic == null) return;
        int pkgId = MessageUtil.getTopicNumMap().get(topic);
        Map<Integer, Class<?>> reg = MessageUtil.getRegisterTopic();
        if (reg.containsKey(pkgId)) {
            for (Enum<?> v : ((Class<? extends Enum<?>>) reg.get(pkgId)).getEnumConstants()) {
                if (!"UNRECOGNIZED".equals(v.name())) idComboBox.addItem(v.name());
            }
        }
    }

    private void updateMessageFields() {
        messagePanel.removeAll();
        String topic = (String) typeComboBox.getSelectedItem();
        String id = (String) idComboBox.getSelectedItem();
        if (topic == null || id == null) { messagePanel.revalidate(); messagePanel.repaint(); return; }
        
        Class<?> protoClass = MessageUtil.getIdClassMap().get(MessageUtil.getTopicNumMap().get(topic) + id);
        if (protoClass != null) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 8, 6, 8);
            gbc.anchor = GridBagConstraints.WEST;
            int row = 0;
            for (Descriptors.FieldDescriptor field : MessageUtil.getFields(protoClass)) {
                JLabel label = new JLabel(field.getName() + ":");
                label.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
                label.setForeground(ThemeManager.TEXT_SECONDARY);
                
                ModernTextField tf = new ModernTextField(20);
                tf.setName(field.getName());
                
                gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
                messagePanel.add(label, gbc);
                gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
                messagePanel.add(tf, gbc);
                row++;
            }
            // 底部弹性空间
            gbc.gridy = row; gbc.weighty = 1.0;
            messagePanel.add(Box.createGlue(), gbc);
        }
        messagePanel.revalidate();
        messagePanel.repaint();
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
            BotFrame frame = new BotFrame();
            frame.setVisible(true);
            Runtime.getRuntime().addShutdownHook(new Thread(BotManager::shutdown));
        });
    }
}
