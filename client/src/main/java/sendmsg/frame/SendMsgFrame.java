package sendmsg.frame;

import core.client.*;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import core.message.MessageUtil;
import sendmsg.frame.components.ModernButton;
import sendmsg.frame.components.ModernPanel;
import sendmsg.frame.components.ModernTextField;
import lombok.Setter;
import sendmsg.frame.components.StatusLabel;
import sendmsg.frame.theme.ThemeManager;
import org.slf4j.LoggerFactory;
import org.sunrise.game.config.ConfigReader;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * 消息发送界面 - 现代化UI设计
 */
public class SendMsgFrame extends JPanel {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SendMsgFrame.class);

    private ModernTextField uidField;
    private JLabel uidLabel;
    private ModernButton loginButton, sendButton;
    private JComboBox<String> typeComboBox, idComboBox;
    private JLabel typeLabel, idLabel;
    private JPanel messagePanel;
    private JScrollPane messageScrollPane;
    @Setter
    private JTabbedPane tabbedPane;
    private SocketClient client;
    private StatusLabel statusLabel;
    private ModernPanel loginPanel;
    private ModernPanel messageControlPanel;

    public SendMsgFrame() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ThemeManager.BACKGROUND);
        initComponents();
        layoutComponents();
    }

    private void initComponents() {
        Properties properties = ConfigReader.getProp();
        String uid = properties.getProperty("default.uid", "player");

        // 登录面板组件
        uidLabel = new JLabel("用户ID");
        uidLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        uidLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        
        uidField = new ModernTextField(uid, 25);
        uidField.setPreferredSize(new Dimension(250, 42));
        
        loginButton = new ModernButton("连接登录", ModernButton.ButtonType.PRIMARY);
        loginButton.setPreferredSize(new Dimension(120, 42));
        loginButton.addActionListener(e -> handleLogin());
        
        statusLabel = new StatusLabel();
        
        // 消息控制面板组件
        typeLabel = new JLabel("协议类型");
        typeLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        typeLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        
        typeComboBox = new JComboBox<>(MessageUtil.getTopicNames());
        typeComboBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        typeComboBox.setPreferredSize(new Dimension(0, 38));
        typeComboBox.addActionListener(e -> updateIdComboBox());
        
        idLabel = new JLabel("消息ID");
        idLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        idLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        
        idComboBox = new JComboBox<>();
        idComboBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        idComboBox.setPreferredSize(new Dimension(0, 38));
        idComboBox.addActionListener(e -> updateMessageFields());
        
        sendButton = new ModernButton("发送消息", ModernButton.ButtonType.SUCCESS);
        sendButton.setPreferredSize(new Dimension(120, 42));
        sendButton.addActionListener(e -> handleSend());
        
        // 消息字段面板
        messagePanel = new JPanel(new GridBagLayout());
        messagePanel.setBackground(ThemeManager.CARD_BACKGROUND);
        
        messageScrollPane = new JScrollPane(messagePanel);
        messageScrollPane.setBorder(BorderFactory.createEmptyBorder());
        messageScrollPane.getViewport().setBackground(ThemeManager.CARD_BACKGROUND);
        messageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        messageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    private void layoutComponents() {
        // 登录面板
        loginPanel = new ModernPanel(new GridBagLayout(), ModernPanel.Style.CARD);
        loginPanel.setShowShadow(true);
        loginPanel.setArc(12);
        loginPanel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_START;
        
        // 用户ID输入
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.insets = new Insets(8, 8, 8, 8);
        loginPanel.add(uidLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        loginPanel.add(uidField, gbc);
        
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        loginPanel.add(loginButton, gbc);

        // 消息控制面板
        messageControlPanel = new ModernPanel(new GridBagLayout(), ModernPanel.Style.CARD);
        messageControlPanel.setShowShadow(true);
        messageControlPanel.setArc(12);
        messageControlPanel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.LINE_START;
        
        // 消息标题
        JLabel messageTitle = new JLabel("消息发送");
        messageTitle.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 16));
        messageTitle.setForeground(ThemeManager.TEXT_PRIMARY);
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        messageControlPanel.add(messageTitle, gbc);
        
        // 分隔线
        JSeparator msgSeparator = new JSeparator();
        msgSeparator.setForeground(ThemeManager.BORDER);
        gbc.gridy = 1;
        gbc.insets = new Insets(8, 0, 16, 0);
        messageControlPanel.add(msgSeparator, gbc);
        
        // 协议类型选择
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.insets = new Insets(8, 8, 8, 8);
        messageControlPanel.add(typeLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        messageControlPanel.add(typeComboBox, gbc);
        
        // 消息ID选择
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        messageControlPanel.add(idLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        messageControlPanel.add(idComboBox, gbc);
        
        // 消息字段面板
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        messageControlPanel.add(messageScrollPane, gbc);
        
        // 发送按钮
        gbc.gridy = 5;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        messageControlPanel.add(sendButton, gbc);
        
        // 主布局
        JPanel wrapperPanel = new JPanel(new BorderLayout(0, 16));
        wrapperPanel.setBackground(ThemeManager.BACKGROUND);
        wrapperPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        wrapperPanel.add(loginPanel, BorderLayout.NORTH);
        wrapperPanel.add(messageControlPanel, BorderLayout.CENTER);
        
        add(wrapperPanel, BorderLayout.CENTER);
        
        // 初始化时隐藏消息组件
        setMessageComponentsVisible(false);
    }

    private void handleLogin() {
        String uid = uidField.getText().trim();
        if (uid.isEmpty()) {
            statusLabel.setError("用户ID不能为空");
            return;
        }
        
        client = SocketClientManager.getClient(uid);
        if (client != null) {
            statusLabel.setError("该用户已在线");
            return;
        }
        
        // 禁用登录按钮
        loginButton.setEnabled(false);
        statusLabel.setLoading("正在连接服务器...");
        
        // 异步登录
        CompletableFuture.runAsync(() -> {
            try {
                String socketType = ConfigReader.getProp().getProperty("client.socket", "tcp");
                switch (socketType) {
                    case "websocket" -> client = new WsClient();
                    case "kcp" -> client = new KcpClientImpl();
                    default -> client = new TcpClient();
                }
                client.setUid(uid);
                SocketClientManager.addClient(client);
                
                // 使用带状态回调的登录方法
                LoginManager.login(client, status -> {
                    SwingUtilities.invokeLater(() -> statusLabel.setInfo(status));
                });
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setSuccess("连接成功！");
                    // 隐藏登录面板
                    loginPanel.setVisible(false);
                    // 显示消息组件
                    setMessageComponentsVisible(true);
                    updateIdComboBox();
                    
                    // 更新标签页标题
                    if (tabbedPane != null) {
                        int index = tabbedPane.indexOfComponent(this);
                        if (index != -1) {
                            tabbedPane.setTitleAt(index, client.getUid());
                        }
                    }
                    loginButton.setEnabled(true);
                });
            } catch (Exception ex) {
                logger.error("Login failed", ex);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setError("连接失败: " + ex.getMessage());
                    loginButton.setEnabled(true);
                    if (client != null) {
                        SocketClientManager.removeClient(client.getUid());
                    }
                });
            }
        });
    }

    private void handleSend() {
        if (client == null || !client.isConnectSuccess()) {
            return;
        }
        
        try {
            String selectedTopic = (String) typeComboBox.getSelectedItem();
            String selectedId = (String) idComboBox.getSelectedItem();
            
            if (selectedTopic == null || selectedId == null) {
                return;
            }
            
            int pkgType = MessageUtil.getTopicNumMap().get(selectedTopic);
            Class<?> messageClass = MessageUtil.getIdClassMap().get(pkgType + selectedId);
            
            Message message = null;
            if (messageClass != null) {
                Method newBuilderMethod = messageClass.getMethod("newBuilder");
                Message.Builder builder = (Message.Builder) newBuilderMethod.invoke(null);
                
                // 设置字段值
                for (Component component : messagePanel.getComponents()) {
                    if (component instanceof JTextField textField) {
                        String fieldName = textField.getName();
                        Descriptors.FieldDescriptor fieldDescriptor = builder.getDescriptorForType().findFieldByName(fieldName);
                        if (fieldDescriptor != null) {
                            MessageUtil.invoke(builder, fieldDescriptor, textField.getText());
                        }
                    }
                }
                
                message = builder.build();
            }
            
            boolean res = client.sendMsg(pkgType, MessageUtil.getIdNumMap().get(pkgType + selectedId),
                    message == null ? null : message.toByteString());
            
            // 改变按钮状态
            if (res) {
                sendButton.setTemporaryState("✓ 发送成功", ThemeManager.SUCCESS, Color.WHITE);
            } else {
                sendButton.setTemporaryState("✗ 发送失败", ThemeManager.DANGER, Color.WHITE);
            }

            Timer timer = new Timer(800, e -> {
                sendButton.restoreDefaultState();
            });
            timer.setRepeats(false);
            timer.start();
        } catch (Exception ex) {
            logger.error("Send message failed", ex);
        }
    }

    public void close() {
        if (client != null) {
            client.close();
            SocketClientManager.removeClient(client.getUid());
        }
    }

    private void setMessageComponentsVisible(boolean visible) {
        if (messageControlPanel == null) {
            return;
        }
        messageControlPanel.setVisible(visible);
        revalidate();
        repaint();
    }

    private void updateIdComboBox() {
        idComboBox.removeAllItems();
        String selectedTopic = (String) typeComboBox.getSelectedItem();
        if (selectedTopic == null) {
            return;
        }
        
        int pkgId = MessageUtil.getTopicNumMap().get(selectedTopic);
        Map<Integer, Class<?>> registerTopic = MessageUtil.getRegisterTopic();
        if (registerTopic.containsKey(pkgId)) {
            Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) registerTopic.get(pkgId);
            for (Enum<?> value : enumClass.getEnumConstants()) {
                if (!value.name().equals("UNRECOGNIZED")) {
                    idComboBox.addItem(value.name());
                }
            }
        }
    }

    private void updateMessageFields() {
        messagePanel.removeAll();
        String selectedTopic = (String) typeComboBox.getSelectedItem();
        String selectedId = (String) idComboBox.getSelectedItem();
        
        if (selectedTopic == null || selectedId == null) {
            revalidate();
            repaint();
            return;
        }
        
        Class<?> protoClass = MessageUtil.getIdClassMap().get(
                MessageUtil.getTopicNumMap().get(selectedTopic) + selectedId);
        if (protoClass != null) {
            Descriptors.FieldDescriptor[] fields = MessageUtil.getFields(protoClass);
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(8, 12, 8, 12);
            constraints.anchor = GridBagConstraints.LINE_START;
            
            for (Descriptors.FieldDescriptor field : fields) {
                JLabel label = new JLabel(field.getName());
                label.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
                label.setForeground(ThemeManager.TEXT_SECONDARY);
                label.setPreferredSize(new Dimension(100, 30));
                
                ModernTextField textField = new ModernTextField(20);
                textField.setName(field.getName());
                textField.setPreferredSize(new Dimension(200, 38));
                
                constraints.gridx = 0;
                constraints.gridy++;
                constraints.weightx = 0;
                messagePanel.add(label, constraints);
                
                constraints.gridx = 1;
                constraints.weightx = 1.0;
                constraints.fill = GridBagConstraints.HORIZONTAL;
                messagePanel.add(textField, constraints);
            }
            
            // 添加底部填充
            constraints.gridy++;
            constraints.weighty = 1.0;
            messagePanel.add(Box.createVerticalGlue(), constraints);
        }
        
        revalidate();
        repaint();
    }
}
