package sendmsg.frame.components;

import sendmsg.frame.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;

/**
 * 现代化面板组件
 * 支持圆角、阴影、渐变背景
 */
public class ModernPanel extends JPanel {
    
    public enum Style {
        CARD,       // 卡片样式（白色背景+阴影）
        GRADIENT,   // 渐变背景
        TRANSPARENT // 透明背景
    }
    
    private Style style;
    private int arc = 12;
    private boolean showShadow = true;
    private Color gradientStart;
    private Color gradientEnd;
    private int shadowSize = 6;
    private float shadowOpacity = 0.1f;
    
    public ModernPanel(LayoutManager layout, Style style) {
        super(layout);
        this.style = style;
        init();
    }
    
    private void init() {
        setOpaque(false);
        
        switch (style) {
            case CARD:
                setBackground(ThemeManager.CARD_BACKGROUND);
                break;
            case GRADIENT:
                gradientStart = ThemeManager.PRIMARY;
                gradientEnd = ThemeManager.darker(ThemeManager.PRIMARY, 0.2f);
                setBackground(ThemeManager.CARD_BACKGROUND);
                break;
            case TRANSPARENT:
                setBackground(new Color(0, 0, 0, 0));
                break;
        }
    }
    
    /**
     * 设置是否显示阴影
     */
    public void setShowShadow(boolean show) {
        this.showShadow = show;
        repaint();
    }
    
    /**
     * 设置圆角大小
     */
    public void setArc(int arc) {
        this.arc = arc;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        int width = getWidth();
        int height = getHeight();
        
        // 绘制阴影
        if (showShadow && style == Style.CARD) {
            paintShadow(g2, width, height);
        }
        
        // 绘制背景
        switch (style) {
            case CARD:
                paintCardBackground(g2, width, height);
                break;
            case GRADIENT:
                paintGradientBackground(g2, width, height);
                break;
            case TRANSPARENT:
                // 不绘制背景
                break;
        }
        
        g2.dispose();
        
        // 绘制子组件
        super.paintComponent(g);
    }
    
    private void paintShadow(Graphics2D g2, int width, int height) {
        // 多层阴影效果
        for (int i = 0; i < shadowSize; i++) {
            float alpha = shadowOpacity * (1 - (float) i / shadowSize);
            g2.setColor(ThemeManager.withAlpha(Color.BLACK, (int) (255 * alpha)));
            g2.fillRoundRect(i, i + 2, width - i * 2, height - i * 2, arc, arc);
        }
    }
    
    private void paintCardBackground(Graphics2D g2, int width, int height) {
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, width, height, arc, arc);
        
        // 边框
        g2.setColor(ThemeManager.BORDER);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
    }
    
    private void paintGradientBackground(Graphics2D g2, int width, int height) {
        Color start = gradientStart != null ? gradientStart : ThemeManager.PRIMARY;
        Color end = gradientEnd != null ? gradientEnd : ThemeManager.darker(ThemeManager.PRIMARY, 0.2f);
        
        GradientPaint gradient = new GradientPaint(0, 0, start, width, height, end);
        g2.setPaint(gradient);
        g2.fillRoundRect(0, 0, width, height, arc, arc);
    }
    
    @Override
    public Insets getInsets() {
        Insets insets = super.getInsets();
        // 为阴影留出空间
        if (showShadow && style == Style.CARD) {
            return new Insets(insets.top, insets.left, insets.bottom + shadowSize, insets.right + shadowSize);
        }
        return insets;
    }
}
