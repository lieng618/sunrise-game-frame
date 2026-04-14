package sendmsg.frame.components;

import sendmsg.frame.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 现代化按钮组件
 * 支持渐变、阴影、动画效果
 */
public class ModernButton extends JButton {
    
    public enum ButtonType {
        PRIMARY, SUCCESS, WARNING, DANGER, OUTLINE, GHOST
    }
    
    private Color normalColor;
    private Color hoverColor;
    private Color pressedColor;
    private Color borderColor;
    
    private ButtonType buttonType;
    private boolean isTemporaryState = false;
    private boolean isHover = false;
    private boolean isPressed = false;
    private String defaultText;
    
    // 动画相关
    private float hoverProgress = 0f;
    private Timer animationTimer;
    
    // 阴影相关
    private boolean showShadow = true;
    private int shadowSize = 4;
    private float shadowOpacity = 0.15f;
    
    public ModernButton(String text, ButtonType type) {
        super(text);
        this.defaultText = text;
        this.buttonType = type;
        init();
    }
    
    private void init() {
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        
        // 根据类型设置颜色
        setupColors();
        
        setForeground(buttonType == ButtonType.OUTLINE || buttonType == ButtonType.GHOST 
            ? normalColor : Color.WHITE);
        setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(100, 38));
        
        // 鼠标事件
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isTemporaryState && isEnabled()) {
                    isHover = true;
                    startAnimation();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (!isTemporaryState && isEnabled()) {
                    isHover = false;
                    isPressed = false;
                    startAnimation();
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isTemporaryState && isEnabled()) {
                    isPressed = true;
                    hoverProgress = 1f;
                    repaint();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isTemporaryState && isEnabled()) {
                    isPressed = false;
                    if (isHover) {
                        hoverProgress = 0.8f;
                    }
                    repaint();
                }
            }
        });
        
        // 初始化动画定时器
        animationTimer = new Timer(16, e -> { // ~60fps
            if (isHover && hoverProgress < 1f) {
                hoverProgress = Math.min(hoverProgress + 0.08f, 1f);
            } else if (!isHover && hoverProgress > 0f) {
                hoverProgress = Math.max(hoverProgress - 0.08f, 0f);
            } else {
                animationTimer.stop();
            }
            repaint();
        });
    }
    
    private void setupColors() {
        switch (buttonType) {
            case SUCCESS:
                normalColor = ThemeManager.SUCCESS;
                hoverColor = ThemeManager.SUCCESS_HOVER;
                pressedColor = ThemeManager.darker(ThemeManager.SUCCESS_HOVER, 0.1f);
                borderColor = ThemeManager.SUCCESS;
                break;
            case WARNING:
                normalColor = ThemeManager.WARNING;
                hoverColor = ThemeManager.WARNING_HOVER;
                pressedColor = ThemeManager.darker(ThemeManager.WARNING_HOVER, 0.1f);
                borderColor = ThemeManager.WARNING;
                break;
            case DANGER:
                normalColor = ThemeManager.DANGER;
                hoverColor = ThemeManager.DANGER_HOVER;
                pressedColor = ThemeManager.darker(ThemeManager.DANGER_HOVER, 0.1f);
                borderColor = ThemeManager.DANGER;
                break;
            case OUTLINE:
            case GHOST:
                normalColor = ThemeManager.CARD_BACKGROUND;
                hoverColor = ThemeManager.GRAY_50;
                pressedColor = ThemeManager.GRAY_100;
                borderColor = ThemeManager.PRIMARY;
                break;
            default: // PRIMARY
                normalColor = ThemeManager.PRIMARY;
                hoverColor = ThemeManager.PRIMARY_HOVER;
                pressedColor = ThemeManager.PRIMARY_PRESSED;
                borderColor = ThemeManager.PRIMARY;
                break;
        }
    }
    
    private void startAnimation() {
        if (animationTimer.isRunning()) {
            animationTimer.stop();
        }
        animationTimer.start();
    }
    
    /**
     * 设置临时状态（用于显示成功/失败状态）
     */
    public void setTemporaryState(String text, Color backgroundColor, Color foregroundColor) {
        isTemporaryState = true;
        setText(text);
        setBackground(backgroundColor);
        setForeground(foregroundColor);
        repaint();
    }
    
    /**
     * 恢复默认状态
     */
    public void restoreDefaultState() {
        isTemporaryState = false;
        setText(defaultText);
        setForeground(buttonType == ButtonType.OUTLINE || buttonType == ButtonType.GHOST 
            ? normalColor : Color.WHITE);
        hoverProgress = 0;
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        int width = getWidth();
        int height = getHeight();
        int arc = 8;
        
        // 计算阴影偏移
        int shadowOffset = showShadow ? shadowSize : 0;
        int btnX = 0;
        int btnY = isPressed ? 1 : 0;
        int btnWidth = width;
        int btnHeight = height - shadowOffset;
        
        // 绘制阴影
        if (showShadow && !isPressed && isEnabled()) {
            g2.setColor(ThemeManager.withAlpha(Color.BLACK, (int)(255 * shadowOpacity)));
            g2.fillRoundRect(btnX + 2, 3, btnWidth - 4, btnHeight, arc, arc);
        }
        
        // 计算渐变颜色
        Color topColor, bottomColor;
        if (isTemporaryState) {
            topColor = getBackground();
            bottomColor = ThemeManager.darker(getBackground(), 0.1f);
        } else if (isPressed) {
            topColor = pressedColor;
            bottomColor = ThemeManager.darker(pressedColor, 0.05f);
        } else if (isHover) {
            // 动画插值
            Color interpTop = interpolateColor(normalColor, hoverColor, hoverProgress);
            Color interpBottom = interpolateColor(
                ThemeManager.darker(normalColor, 0.05f), 
                ThemeManager.darker(hoverColor, 0.05f), 
                hoverProgress
            );
            topColor = interpTop;
            bottomColor = interpBottom;
        } else {
            topColor = normalColor;
            bottomColor = ThemeManager.darker(normalColor, 0.05f);
        }
        
        // 绘制按钮背景（渐变）
        if (buttonType == ButtonType.OUTLINE) {
            g2.setColor(ThemeManager.withAlpha(normalColor, (int)(255 * 0.1f * hoverProgress)));
            g2.fillRoundRect(btnX, btnY, btnWidth, btnHeight, arc, arc);
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(btnX, btnY, btnWidth - 1, btnHeight - 1, arc, arc);
        } else if (buttonType == ButtonType.GHOST) {
            g2.setColor(ThemeManager.withAlpha(normalColor, (int)(255 * 0.1f * hoverProgress)));
            g2.fillRoundRect(btnX, btnY, btnWidth, btnHeight, arc, arc);
        } else {
            // 渐变填充
            GradientPaint gradient = new GradientPaint(
                0, btnY, topColor,
                0, btnY + btnHeight, bottomColor
            );
            g2.setPaint(gradient);
            g2.fillRoundRect(btnX, btnY, btnWidth, btnHeight, arc, arc);
            
            // 添加高光效果（顶部）
            if (!isPressed && isEnabled()) {
                GradientPaint highlight = new GradientPaint(
                    0, btnY, ThemeManager.withAlpha(Color.WHITE, 30),
                    0, btnY + btnHeight / 3, ThemeManager.withAlpha(Color.WHITE, 0)
                );
                g2.setPaint(highlight);
                g2.fillRoundRect(btnX, btnY, btnWidth, btnHeight / 3, arc, arc);
            }
        }
        
        // 绘制文字
        g2.setColor(getForeground());
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int textX = (width - fm.stringWidth(getText())) / 2;
        int textY = btnY + (btnHeight + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(getText(), textX, textY);
        
        g2.dispose();
    }
    
    /**
     * 颜色插值
     */
    private Color interpolateColor(Color c1, Color c2, float t) {
        int r = (int)(c1.getRed() + t * (c2.getRed() - c1.getRed()));
        int g = (int)(c1.getGreen() + t * (c2.getGreen() - c1.getGreen()));
        int b = (int)(c1.getBlue() + t * (c2.getBlue() - c1.getBlue()));
        int a = (int)(c1.getAlpha() + t * (c2.getAlpha() - c1.getAlpha()));
        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)),
            Math.max(0, Math.min(255, a))
        );
    }
    
    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, 38);
        return size;
    }
}
