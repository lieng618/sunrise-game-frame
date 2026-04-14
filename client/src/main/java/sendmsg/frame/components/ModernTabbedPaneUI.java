package sendmsg.frame.components;

import sendmsg.frame.theme.ThemeManager;

import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;

/**
 * 现代化标签页UI
 */
public class ModernTabbedPaneUI extends BasicTabbedPaneUI {

    @Override
    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
        return 36;
    }

    @Override
    protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
        int width = super.calculateTabWidth(tabPlacement, tabIndex, metrics);
        return Math.max(width + 24, 100);
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (isSelected) {
            g2.setColor(ThemeManager.CARD_BACKGROUND);
            g2.fillRoundRect(x, y + 2, w, h - 2, 8, 8);

            // 选中指示器
            g2.setColor(ThemeManager.PRIMARY);
            g2.fillRoundRect(x + 8, y + h - 3, w - 16, 3, 2, 2);
        } else {
            g2.setColor(ThemeManager.GRAY_50);
            g2.fillRoundRect(x + 2, y + 4, w - 4, h - 6, 6, 6);
        }

        g2.dispose();
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
        // 不绘制默认边框
    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        // 不绘制内容边框
    }
}
