package sendmsg.frame.theme;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

/**
 * 主题管理器 - 定义全局配色方案和UI配置
 */
public class ThemeManager {

    // ===== 主色 =====
    public static final Color PRIMARY = new Color(59, 130, 246);
    public static final Color PRIMARY_HOVER = new Color(37, 99, 235);
    public static final Color PRIMARY_PRESSED = new Color(29, 78, 216);

    // ===== 状态色 =====
    public static final Color SUCCESS = new Color(34, 197, 94);
    public static final Color SUCCESS_HOVER = new Color(22, 163, 74);
    public static final Color WARNING = new Color(251, 191, 36);
    public static final Color WARNING_HOVER = new Color(245, 158, 11);
    public static final Color DANGER = new Color(239, 68, 68);
    public static final Color DANGER_HOVER = new Color(220, 38, 38);

    // ===== 灰度 =====
    public static final Color GRAY_50 = new Color(249, 250, 251);
    public static final Color GRAY_100 = new Color(243, 244, 246);
    public static final Color GRAY_200 = new Color(229, 231, 235);
    public static final Color GRAY_300 = new Color(209, 213, 219);
    public static final Color GRAY_400 = new Color(156, 163, 175);
    public static final Color GRAY_500 = new Color(107, 114, 128);
    public static final Color GRAY_600 = new Color(75, 85, 99);
    public static final Color GRAY_700 = new Color(55, 65, 81);
    public static final Color GRAY_800 = new Color(31, 41, 55);
    public static final Color GRAY_900 = new Color(17, 24, 39);

    // ===== 背景/文本/边框 =====
    public static final Color BACKGROUND = GRAY_50;
    public static final Color CARD_BACKGROUND = Color.WHITE;
    public static final Color TEXT_PRIMARY = GRAY_900;
    public static final Color TEXT_SECONDARY = GRAY_500;
    public static final Color BORDER = GRAY_200;

    private ThemeManager() {}

    /** 初始化FlatLaf主题 */
    public static void init() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.trackArc", 999);
            UIManager.put("Button.margin", new Insets(8, 16, 8, 16));
            UIManager.put("TextField.margin", new Insets(8, 12, 8, 12));
            UIManager.put("defaultFont", new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 颜色变暗 */
    public static Color darker(Color c, float factor) {
        return new Color(
            Math.max((int)(c.getRed() * (1 - factor)), 0),
            Math.max((int)(c.getGreen() * (1 - factor)), 0),
            Math.max((int)(c.getBlue() * (1 - factor)), 0),
            c.getAlpha()
        );
    }

    /** 调整透明度 */
    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
