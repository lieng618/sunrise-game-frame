package sendmsg.frame.components;

import sendmsg.frame.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;

/**
 * 现代化状态标签组件
 * 支持不同状态类型和图标
 */
public class StatusLabel extends JLabel {
    
    public enum StatusType {
        SUCCESS, ERROR, WARNING, INFO, LOADING
    }
    
    private StatusType currentType = StatusType.INFO;

    // 状态图标（简单绘制）
    private static final int ICON_SIZE = 16;
    
    public StatusLabel() {
        super(" ");
        init();
    }
    
    public StatusLabel(String text) {
        super(text);
        init();
    }
    
    private void init() {
        setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        setForeground(ThemeManager.TEXT_SECONDARY);
        setHorizontalAlignment(SwingConstants.LEFT);
        setIconTextGap(8);
    }
    
    /**
     * 设置成功状态
     */
    public void setSuccess(String message) {
        setText(message);
        currentType = StatusType.SUCCESS;
        setForeground(ThemeManager.SUCCESS);
        repaint();
    }
    
    /**
     * 设置错误状态
     */
    public void setError(String message) {
        setText(message);
        currentType = StatusType.ERROR;
        setForeground(ThemeManager.DANGER);
        repaint();
    }
    
    /**
     * 设置警告状态
     */
    public void setWarning(String message) {
        setText(message);
        currentType = StatusType.WARNING;
        setForeground(ThemeManager.WARNING);
        repaint();
    }
    
    /**
     * 设置信息状态
     */
    public void setInfo(String message) {
        setText(message);
        currentType = StatusType.INFO;
        setForeground(ThemeManager.PRIMARY);
        repaint();
    }
    
    /**
     * 设置加载状态
     */
    public void setLoading(String message) {
        setText(message);
        currentType = StatusType.LOADING;
        setForeground(ThemeManager.TEXT_SECONDARY);
        repaint();
    }
    
    /**
     * 清除状态
     */
    public void clear() {
        setText("");
        currentType = StatusType.INFO;
        setForeground(ThemeManager.TEXT_SECONDARY);
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        // 先绘制图标
        if (!getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int iconX = 0;
            int iconY = (getHeight() - ICON_SIZE) / 2;
            
            switch (currentType) {
                case SUCCESS:
                    paintSuccessIcon(g2, iconX, iconY);
                    break;
                case ERROR:
                    paintErrorIcon(g2, iconX, iconY);
                    break;
                case WARNING:
                    paintWarningIcon(g2, iconX, iconY);
                    break;
                case INFO:
                    paintInfoIcon(g2, iconX, iconY);
                    break;
                case LOADING:
                    paintLoadingIcon(g2, iconX, iconY);
                    break;
            }
            
            g2.dispose();
            
            // 调整文字位置
            Insets insets = getInsets();
            setBorder(BorderFactory.createEmptyBorder(
                insets.top, 
                insets.left + ICON_SIZE + 8, 
                insets.bottom, 
                insets.right
            ));
        }
        
        super.paintComponent(g);
    }
    
    private void paintSuccessIcon(Graphics2D g2, int x, int y) {
        g2.setColor(ThemeManager.SUCCESS);
        g2.fillOval(x, y, ICON_SIZE, ICON_SIZE);
        
        // 绘制勾号
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x + 4, y + 8, x + 7, y + 11);
        g2.drawLine(x + 7, y + 11, x + 12, y + 5);
    }
    
    private void paintErrorIcon(Graphics2D g2, int x, int y) {
        g2.setColor(ThemeManager.DANGER);
        g2.fillOval(x, y, ICON_SIZE, ICON_SIZE);
        
        // 绘制X号
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x + 5, y + 5, x + 11, y + 11);
        g2.drawLine(x + 11, y + 5, x + 5, y + 11);
    }
    
    private void paintWarningIcon(Graphics2D g2, int x, int y) {
        g2.setColor(ThemeManager.WARNING);
        // 绘制三角形
        int[] xPoints = {x + 8, x + 16, x};
        int[] yPoints = {y, y + 16, y + 16};
        g2.fillPolygon(xPoints, yPoints, 3);
        
        // 绘制感叹号
        g2.setColor(ThemeManager.GRAY_800);
        g2.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 12));
        g2.drawString("!", x + 6, y + 13);
    }
    
    private void paintInfoIcon(Graphics2D g2, int x, int y) {
        g2.setColor(ThemeManager.PRIMARY);
        g2.fillOval(x, y, ICON_SIZE, ICON_SIZE);
        
        // 绘制i号
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 6, y + 4, 4, 4);
        g2.fillRect(x + 7, y + 10, 2, 4);
    }
    
    private void paintLoadingIcon(Graphics2D g2, int x, int y) {
        g2.setColor(ThemeManager.PRIMARY);
        g2.setStroke(new BasicStroke(2));
        
        // 绘制旋转圆弧
        long time = System.currentTimeMillis() / 100;
        int startAngle = (int) (time * 30) % 360;
        g2.drawArc(x, y, ICON_SIZE, ICON_SIZE, startAngle, 270);
    }
}
