package sendmsg.frame;

import core.client.SocketClient;
import core.client.SocketClientManager;
import sendmsg.frame.components.ModernButton;
import sendmsg.frame.components.ModernTabbedPaneUI;
import sendmsg.frame.theme.ThemeManager;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 主窗口
 */
public class MainFrame extends JFrame {
    
    private JTabbedPane tabbedPane;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public MainFrame() {
        ThemeManager.init();
        initFrame();
        initComponents();
        layoutComponents();
        startPingScheduler();
    }

    private void initFrame() {
        setTitle("Sunrise 消息发送工具");
        setSize(1000, 750);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
    }

    private void initComponents() {
        // 创建标签页面板
        tabbedPane = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
        tabbedPane.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        
        // 自定义标签页UI
        tabbedPane.setUI(new ModernTabbedPaneUI());
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(0, 0));
        
        // 创建主容器
        JPanel mainContainer = new JPanel(new BorderLayout());
        mainContainer.setBackground(ThemeManager.BACKGROUND);
        
        // 顶部工具栏
        JPanel toolbarPanel = createToolbarPanel();
        mainContainer.add(toolbarPanel, BorderLayout.NORTH);
        
        // 中心标签页区域
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(ThemeManager.BACKGROUND);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        contentPanel.add(tabbedPane, BorderLayout.CENTER);
        mainContainer.add(contentPanel, BorderLayout.CENTER);
        
        add(mainContainer, BorderLayout.CENTER);
        
        // 添加第一个标签页
        addTab();
    }

    private JPanel createToolbarPanel() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(ThemeManager.CARD_BACKGROUND);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        
        // 左侧标题
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);

        // 侧边栏组件
        JLabel titleLabel = new JLabel("消息发送工具");
        titleLabel.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 16));
        titleLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        leftPanel.add(titleLabel);
        
        // 右侧按钮组
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);
        
        ModernButton addButton = new ModernButton("+ 添加窗口", ModernButton.ButtonType.PRIMARY);
        addButton.setPreferredSize(new Dimension(100, 36));
        addButton.addActionListener(e -> addTab());
        
        ModernButton addMoreButton = new ModernButton("+ 批量添加", ModernButton.ButtonType.PRIMARY);
        addMoreButton.setPreferredSize(new Dimension(100, 36));
        addMoreButton.addActionListener(e -> {
            for (int i = 0; i < 5; i++) {
                addTab();
            }
        });
        
        ModernButton removeButton = new ModernButton("- 移除", ModernButton.ButtonType.DANGER);
        removeButton.setPreferredSize(new Dimension(80, 36));
        removeButton.addActionListener(e -> removeTab());
        
        rightPanel.add(addButton);
        rightPanel.add(addMoreButton);
        rightPanel.add(removeButton);
        
        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(rightPanel, BorderLayout.EAST);
        
        return toolbar;
    }

    private void addTab() {
        SendMsgFrame sendMsgFrame = new SendMsgFrame();
        int tabCount = tabbedPane.getTabCount();
        String tabTitle = "玩家 " + (tabCount + 1);
        
        // 添加标签页，带关闭按钮
        tabbedPane.addTab(tabTitle, sendMsgFrame);
        
        // 设置标签页组件
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        tabPanel.setOpaque(false);
        
        JLabel tabLabel = new JLabel(tabTitle);
        tabLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        tabPanel.add(tabLabel);
        
        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
        sendMsgFrame.setTabbedPane(tabbedPane);
    }

    private void removeTab() {
        if (tabbedPane.getTabCount() <= 1) {
            JOptionPane.showMessageDialog(this, 
                    "至少需要保留一个窗口", 
                    "提示", 
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        int selectedIndex = tabbedPane.getSelectedIndex();
        Component selectedComponent = tabbedPane.getComponentAt(selectedIndex);
        if (selectedComponent instanceof SendMsgFrame) {
            ((SendMsgFrame) selectedComponent).close();
            tabbedPane.removeTabAt(selectedIndex);
        }
    }

    private void startPingScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            for (SocketClient client : SocketClientManager.getClients().values()) {
                if (client.isConnectSuccess()) {
                    com.google.protobuf.ByteString pingData = LoginProto.MC2S_ClientPing.newBuilder()
                            .setTime(System.currentTimeMillis())
                            .build()
                            .toByteString();
                    client.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN,
                            LoginProto.FROM_CLIENT.C2S_ClientPing_VALUE, pingData);
                }
            }
        }, 5, 10, TimeUnit.SECONDS);
    }

    public static void start() {
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}
