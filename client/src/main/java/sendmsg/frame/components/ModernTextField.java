package sendmsg.frame.components;

import sendmsg.frame.theme.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 现代化文本输入框组件
 * 支持圆角、焦点动画、占位符
 */
public class ModernTextField extends JTextField {
    
    private Color borderColor = ThemeManager.BORDER;
    private Color focusBorderColor = ThemeManager.PRIMARY;
    private Color backgroundColor = ThemeManager.CARD_BACKGROUND;
    private Color placeholderColor = ThemeManager.GRAY_400;
    
    private String placeholder = "";
    private boolean isHover = false;
    private boolean isFocus = false;
    private int arc = 8;
    
    // 动画相关
    private float focusProgress = 0f;
    private Timer animationTimer;
    
    public ModernTextField(int columns) {
        super(columns);
        init();
    }
    
    public ModernTextField(String text, int columns) {
        super(text, columns);
        init();
    }
    
    private void init() {
        setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        setForeground(ThemeManager.TEXT_PRIMARY);
        setBackground(backgroundColor);
        setCaretColor(ThemeManager.PRIMARY);
        setFocusable(false);
        // 移除默认边框
        setBorder(new EmptyBorder(10, 14, 10, 14));
        setOpaque(false);
        
        // 设置首选大小
        setPreferredSize(new Dimension(200, 40));
        
        // 焦点事件
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                isFocus = true;
                startAnimation(true);
            }

            @Override
            public void focusLost(FocusEvent e) {
                isFocus = false;
                startAnimation(false);
            }
        });
        
        // 鼠标事件
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setFocusable(true);
                if (!isFocus) {
                    isHover = true;
                    repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                setFocusable(false);
                if (!isFocus) {
                    isHover = false;
                    repaint();
                }
            }
        });
        
        // 动画定时器
        animationTimer = new Timer(16, e -> {
            if (isFocus && focusProgress < 1f) {
                focusProgress = Math.min(focusProgress + 0.1f, 1f);
            } else if (!isFocus && focusProgress > 0f) {
                focusProgress = Math.max(focusProgress - 0.1f, 0f);
            } else {
                animationTimer.stop();
            }
            repaint();
        });
    }
    
    private void startAnimation(boolean fadeIn) {
        if (animationTimer.isRunning()) {
            animationTimer.stop();
        }
        animationTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        int width = getWidth();
        int height = getHeight();
        
        // 绘制阴影（焦点时）
        if (isFocus) {
            g2.setColor(ThemeManager.withAlpha(ThemeManager.PRIMARY, (int)(30 * focusProgress)));
            g2.fillRoundRect(2, 2, width - 4, height - 4, arc, arc);
        }
        
        // 绘制背景
        g2.setColor(backgroundColor);
        g2.fillRoundRect(0, 0, width, height, arc, arc);
        
        // 绘制边框
        Color currentBorderColor;
        if (isFocus) {
            currentBorderColor = interpolateColor(ThemeManager.BORDER, focusBorderColor, focusProgress);
        } else if (isHover) {
            currentBorderColor = ThemeManager.GRAY_300;
        } else {
            currentBorderColor = borderColor;
        }
        
        g2.setColor(currentBorderColor);
        g2.setStroke(new BasicStroke(isFocus ? 2f : 1f));
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
        g2.dispose();
        
        // 绘制父组件（包括文字）
        super.paintComponent(g);
        
        // 绘制占位符
        if (getText().isEmpty() && !placeholder.isEmpty() && !isFocus) {
            g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(placeholderColor);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textX = 14;
            int textY = (height + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(placeholder, textX, textY);
            g2.dispose();
        }
    }
    
    /**
     * 颜色插值
     */
    private Color interpolateColor(Color c1, Color c2, float t) {
        int r = (int)(c1.getRed() + t * (c2.getRed() - c1.getRed()));
        int g = (int)(c1.getGreen() + t * (c2.getGreen() - c1.getGreen()));
        int b = (int)(c1.getBlue() + t * (c2.getBlue() - c1.getBlue()));
        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b))
        );
    }
    
    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, 40);
        return size;
    }
}
