package com.cscec.dumu.util;

// 新建文件：DateTimeRangePicker.java

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期时间范围选择器（双日历面板）
 * 支持：快捷选择、时分秒选择、双月历显示
 */
public class DateTimeRangePicker extends JDialog {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 当前显示的月份（左面板和右面板）
    private LocalDate leftMonthDate;
    private LocalDate rightMonthDate;

    // 选中的开始和结束日期时间
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;

    // UI组件 - 左日历
    private JPanel leftCalendarPanel;
    private JLabel leftMonthLabel;
    private JButton leftPrevBtn;
    private JButton leftNextBtn;
    private JPanel leftDaysPanel;

    // UI组件 - 右日历
    private JPanel rightCalendarPanel;
    private JLabel rightMonthLabel;
    private JButton rightPrevBtn;
    private JButton rightNextBtn;
    private JPanel rightDaysPanel;

    // 时间选择组件
    private JComboBox<String> startHourCombo;
    private JComboBox<String> startMinuteCombo;
    private JComboBox<String> startSecondCombo;
    private JComboBox<String> endHourCombo;
    private JComboBox<String> endMinuteCombo;
    private JComboBox<String> endSecondCombo;

    // 选中状态的标识（正在选择开始时间还是结束时间）
    private boolean isSelectingStart = true;

    // 回调接口
    private OnDateTimeRangeSelectedListener listener;

    // 存储选中的日期（用于高亮显示）
    private LocalDate selectedStartDate;
    private LocalDate selectedEndDate;

    // 存储日期按钮，用于更新样式
    private List<JButton> leftDateButtons = new ArrayList<>();
    private List<JButton> rightDateButtons = new ArrayList<>();

    public DateTimeRangePicker(Window owner) {
        super(owner, "选择时间范围", ModalityType.APPLICATION_MODAL);
        init();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }

    private void init() {
        // 初始化默认值
        LocalDateTime now = LocalDateTime.now();
        startDateTime = now.withHour(0).withMinute(0).withSecond(0);
        endDateTime = now.plusDays(1).withHour(0).withMinute(0).withSecond(0);
        selectedStartDate = startDateTime.toLocalDate();
        selectedEndDate = endDateTime.toLocalDate();

        leftMonthDate = LocalDate.now();
        rightMonthDate = leftMonthDate.plusMonths(1);

        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ==================== 顶部快捷选择区域 ====================
        JPanel topPanel = createQuickSelectPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // ==================== 双日历区域 ====================
        JPanel doubleCalendarPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        doubleCalendarPanel.add(createLeftCalendarPanel());
        doubleCalendarPanel.add(createRightCalendarPanel());
        mainPanel.add(doubleCalendarPanel, BorderLayout.CENTER);

        // ==================== 时间选择区域 ====================
        JPanel timePanel = createTimePanel();
        mainPanel.add(timePanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);

        // ==================== 底部按钮区域 ====================
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getOwner());
        setSize(850, 580);
    }

    /**
     * 快捷选择面板
     */
    private JPanel createQuickSelectPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "快捷选择",
                TitledBorder.LEFT, TitledBorder.TOP));

        JButton todayBtn = createQuickButton("当前一天");
        todayBtn.addActionListener(e -> selectCurrentDay());

        JButton weekBtn = createQuickButton("最近一周");
        weekBtn.addActionListener(e -> selectRecentWeek());

        JButton yesterdayBtn = createQuickButton("昨天");
        yesterdayBtn.addActionListener(e -> selectYesterday());

        JButton thisMonthBtn = createQuickButton("本月");
        thisMonthBtn.addActionListener(e -> selectThisMonth());

        panel.add(todayBtn);
        panel.add(weekBtn);
        panel.add(yesterdayBtn);
        panel.add(thisMonthBtn);

        // 提示标签
        JLabel tipLabel = new JLabel("点击日期选择范围，再次点击结束日期");
        tipLabel.setForeground(Color.GRAY);
        tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        panel.add(tipLabel);

        return panel;
    }

    private JButton createQuickButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * 快捷选择：当前一天
     */
    private void selectCurrentDay() {
        LocalDateTime now = LocalDateTime.now();
        startDateTime = now.withHour(0).withMinute(0).withSecond(0);
        endDateTime = now.plusDays(1).withHour(0).withMinute(0).withSecond(0);
        selectedStartDate = startDateTime.toLocalDate();
        selectedEndDate = endDateTime.toLocalDate();

        updateTimeComboBoxes();
        refreshCalendars();
    }

    /**
     * 快捷选择：最近一周（今天往前7天）
     */
    private void selectRecentWeek() {
        LocalDateTime now = LocalDateTime.now();
        startDateTime = now.minusDays(7).withHour(0).withMinute(0).withSecond(0);
        endDateTime = now.plusDays(1).withHour(0).withMinute(0).withSecond(0);
        selectedStartDate = startDateTime.toLocalDate();
        selectedEndDate = endDateTime.toLocalDate();

        // 调整显示的月份以包含选中日期
        adjustMonthToShowDates();

        updateTimeComboBoxes();
        refreshCalendars();
    }

    /**
     * 快捷选择：昨天
     */
    private void selectYesterday() {
        LocalDateTime now = LocalDateTime.now();
        startDateTime = now.minusDays(1).withHour(0).withMinute(0).withSecond(0);
        endDateTime = now.withHour(0).withMinute(0).withSecond(0);
        selectedStartDate = startDateTime.toLocalDate();
        selectedEndDate = endDateTime.toLocalDate();

        updateTimeComboBoxes();
        refreshCalendars();
    }

    /**
     * 快捷选择：本月
     */
    private void selectThisMonth() {
        LocalDateTime now = LocalDateTime.now();
        startDateTime = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        endDateTime = now.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).withHour(0).withMinute(0).withSecond(0);
        selectedStartDate = startDateTime.toLocalDate();
        selectedEndDate = endDateTime.toLocalDate();

        adjustMonthToShowDates();
        updateTimeComboBoxes();
        refreshCalendars();
    }

    /**
     * 调整显示的月份以包含选中日期
     */
    private void adjustMonthToShowDates() {
        if (selectedStartDate != null) {
            leftMonthDate = selectedStartDate.withDayOfMonth(1);
            rightMonthDate = leftMonthDate.plusMonths(1);
        }
    }

    /**
     * 创建左侧日历面板
     */
    private JPanel createLeftCalendarPanel() {
        leftCalendarPanel = new JPanel(new BorderLayout(5, 5));
        leftCalendarPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        // 标题栏
        JPanel headerPanel = new JPanel(new BorderLayout());
        leftPrevBtn = new JButton("<");
        leftPrevBtn.setFocusPainted(false);
        leftPrevBtn.addActionListener(e -> {
            // 只切换左侧月份，不影响右侧
            leftMonthDate = leftMonthDate.minusMonths(1);
            refreshCalendars();
        });

        leftNextBtn = new JButton(">");
        leftNextBtn.setFocusPainted(false);
        leftNextBtn.addActionListener(e -> {
            // 只切换左侧月份，不影响右侧
            leftMonthDate = leftMonthDate.plusMonths(1);
            refreshCalendars();
        });

        leftMonthLabel = new JLabel("", SwingConstants.CENTER);
        leftMonthLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        navPanel.add(leftPrevBtn);
        navPanel.add(leftMonthLabel);
        navPanel.add(leftNextBtn);
        headerPanel.add(navPanel, BorderLayout.CENTER);

        // 星期标题
        JPanel weekPanel = createWeekPanel();
        headerPanel.add(weekPanel, BorderLayout.SOUTH);
        leftCalendarPanel.add(headerPanel, BorderLayout.NORTH);

        // 日期面板
        leftDaysPanel = new JPanel(new GridLayout(6, 7, 5, 5));
        leftDaysPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        leftCalendarPanel.add(leftDaysPanel, BorderLayout.CENTER);

        return leftCalendarPanel;
    }

    /**
     * 创建右侧日历面板
     */
    private JPanel createRightCalendarPanel() {
        rightCalendarPanel = new JPanel(new BorderLayout(5, 5));
        rightCalendarPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        // 标题栏
        JPanel headerPanel = new JPanel(new BorderLayout());
        rightPrevBtn = new JButton("<");
        rightPrevBtn.setFocusPainted(false);
        rightPrevBtn.addActionListener(e -> {
            // 只切换右侧月份，不影响左侧
            rightMonthDate = rightMonthDate.minusMonths(1);
            refreshCalendars();
        });

        rightNextBtn = new JButton(">");
        rightNextBtn.setFocusPainted(false);
        rightNextBtn.addActionListener(e -> {
            // 只切换右侧月份，不影响左侧
            rightMonthDate = rightMonthDate.plusMonths(1);
            refreshCalendars();
        });

        rightMonthLabel = new JLabel("", SwingConstants.CENTER);
        rightMonthLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        navPanel.add(rightPrevBtn);
        navPanel.add(rightMonthLabel);
        navPanel.add(rightNextBtn);
        headerPanel.add(navPanel, BorderLayout.CENTER);

        // 星期标题
        JPanel weekPanel = createWeekPanel();
        headerPanel.add(weekPanel, BorderLayout.SOUTH);
        rightCalendarPanel.add(headerPanel, BorderLayout.NORTH);

        // 日期面板
        rightDaysPanel = new JPanel(new GridLayout(6, 7, 5, 5));
        rightDaysPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rightCalendarPanel.add(rightDaysPanel, BorderLayout.CENTER);

        return rightCalendarPanel;
    }

    /**
     * 创建星期标题面板
     */
    private JPanel createWeekPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 7, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        String[] weeks = {"日", "一", "二", "三", "四", "五", "六"};
        Color[] colors = {Color.RED, Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, new Color(0, 100, 0)};

        for (int i = 0; i < weeks.length; i++) {
            JLabel label = new JLabel(weeks[i], SwingConstants.CENTER);
            label.setFont(new Font("微软雅黑", Font.BOLD, 12));
            label.setForeground(colors[i]);
            panel.add(label);
        }
        return panel;
    }

    /**
     * 刷新日历面板
     */
    private void refreshCalendars() {
        refreshCalendar(leftDaysPanel, leftMonthDate, leftDateButtons, leftMonthLabel);
        refreshCalendar(rightDaysPanel, rightMonthDate, rightDateButtons, rightMonthLabel);

        // 更新月份显示
        leftMonthLabel.setText(leftMonthDate.getYear() + "年 " + leftMonthDate.getMonthValue() + "月");
        rightMonthLabel.setText(rightMonthDate.getYear() + "年 " + rightMonthDate.getMonthValue() + "月");
    }

    /**
     * 刷新单个日历面板
     */
    private void refreshCalendar(JPanel daysPanel, LocalDate monthDate, List<JButton> dateButtons, JLabel monthLabel) {
        daysPanel.removeAll();
        dateButtons.clear();

        // 获取当月第一天
        LocalDate firstDay = monthDate.withDayOfMonth(1);
        int daysInMonth = monthDate.lengthOfMonth();
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue() % 7; // 转为周日=0

        // 添加上个月的日期（填充空白）
        LocalDate prevMonthDate = monthDate.minusMonths(1);
        int prevMonthDays = prevMonthDate.lengthOfMonth();
        for (int i = firstDayOfWeek - 1; i >= 0; i--) {
            int day = prevMonthDays - i;
            JButton btn = createDateButton(prevMonthDate.withDayOfMonth(day), true);
            daysPanel.add(btn);
            dateButtons.add(btn);
        }

        // 添加当月日期
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate currentDate = monthDate.withDayOfMonth(day);
            JButton btn = createDateButton(currentDate, false);
            daysPanel.add(btn);
            dateButtons.add(btn);
        }

        // 添加下个月的日期（填充剩余格子，总共6行42格）
        int remaining = 42 - (firstDayOfWeek + daysInMonth);
        LocalDate nextMonthDate = monthDate.plusMonths(1);
        for (int day = 1; day <= remaining; day++) {
            JButton btn = createDateButton(nextMonthDate.withDayOfMonth(day), true);
            daysPanel.add(btn);
            dateButtons.add(btn);
        }

        daysPanel.revalidate();
        daysPanel.repaint();

        // 强制刷新所有按钮的显示（解决颜色不更新问题）
        SwingUtilities.invokeLater(() -> {
            for (Component comp : daysPanel.getComponents()) {
                if (comp instanceof JButton) {
                    comp.repaint();
                }
            }
        });
    }

    /**
     * 创建日期按钮
     */
    private JButton createDateButton(LocalDate date, boolean isOtherMonth) {
        JButton btn = new JButton(String.valueOf(date.getDayOfMonth()));
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 非当前月按钮透明度
        if (isOtherMonth) {
            btn.setForeground(Color.GRAY);
            btn.setBackground(null);
        } else {
            btn.setForeground(Color.BLACK);
            btn.setBackground(null);
        }

        // 判断是否在选中范围内
        boolean inRange = isInSelectedRange(date);
        boolean isStart = selectedStartDate != null && date.equals(selectedStartDate);
        boolean isEnd = selectedEndDate != null && date.equals(selectedEndDate);

        if (isStart || isEnd) {
            // 选中的开始/结束日期 - 深蓝色背景 + 白色文字
            btn.setBackground(new Color(0, 120, 215));
            btn.setForeground(Color.BLUE);  // 白色文字
            btn.setFont(new Font("微软雅黑", Font.BOLD, 12));
            btn.setOpaque(true);
            btn.setBorderPainted(false);
        } else if (inRange) {
            // 范围内的日期 - 浅蓝色背景 + 黑色文字
            btn.setBackground(new Color(200, 220, 240));
            btn.setForeground(Color.BLACK);
            btn.setOpaque(true);
            btn.setBorderPainted(false);
        } else {
            // 普通日期 - 透明背景 + 黑色文字
            btn.setBackground(null);
            btn.setOpaque(false);
            btn.setBorderPainted(true);
        }

        btn.addActionListener(e -> onDateSelected(date));
        return btn;
    }

    /**
     * 判断日期是否在选中范围内
     */
    private boolean isInSelectedRange(LocalDate date) {
        if (selectedStartDate == null || selectedEndDate == null) {
            return false;
        }
        return !date.isBefore(selectedStartDate) && !date.isAfter(selectedEndDate);
    }

    /**
     * 日期选择处理
     */
    private void onDateSelected(LocalDate date) {
        if (isSelectingStart || selectedStartDate == null) {
            // 选择开始日期
            selectedStartDate = date;
            selectedEndDate = null;
            isSelectingStart = false;
        } else {
            // 选择结束日期
            if (date.isBefore(selectedStartDate)) {
                // 如果选择的结束日期早于开始日期，则交换
                selectedEndDate = selectedStartDate;
                selectedStartDate = date;
            } else {
                selectedEndDate = date;
            }
            isSelectingStart = true;

            // 更新日期时间对象
            startDateTime = selectedStartDate.atTime(getStartTime());
            endDateTime = selectedEndDate.plusDays(1).atTime(getEndTime());
        }
        refreshCalendars();
        updateTimeComboBoxes();
    }

    /**
     * 创建时间选择面板
     */
    private JPanel createTimePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("时间"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);

        // 开始时间
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("开始时间:"), gbc);

        startHourCombo = createTimeCombo(0, 23);
        startMinuteCombo = createTimeCombo(0, 59);
        startSecondCombo = createTimeCombo(0, 59);

        gbc.gridx = 1;
        panel.add(startHourCombo, gbc);
        gbc.gridx = 2;
        panel.add(new JLabel(":"), gbc);
        gbc.gridx = 3;
        panel.add(startMinuteCombo, gbc);
        gbc.gridx = 4;
        panel.add(new JLabel(":"), gbc);
        gbc.gridx = 5;
        panel.add(startSecondCombo, gbc);

        // 分隔符
        gbc.gridx = 6;
        panel.add(new JLabel(" 至 "), gbc);

        // 结束时间
        gbc.gridx = 7;
        panel.add(new JLabel("结束时间:"), gbc);

        endHourCombo = createTimeCombo(0, 23);
        endMinuteCombo = createTimeCombo(0, 59);
        endSecondCombo = createTimeCombo(0, 59);

        gbc.gridx = 8;
        panel.add(endHourCombo, gbc);
        gbc.gridx = 9;
        panel.add(new JLabel(":"), gbc);
        gbc.gridx = 10;
        panel.add(endMinuteCombo, gbc);
        gbc.gridx = 11;
        panel.add(new JLabel(":"), gbc);
        gbc.gridx = 12;
        panel.add(endSecondCombo, gbc);

        // 时间变更监听
        startHourCombo.addActionListener(e -> updateStartDateTime());
        startMinuteCombo.addActionListener(e -> updateStartDateTime());
        startSecondCombo.addActionListener(e -> updateStartDateTime());
        endHourCombo.addActionListener(e -> updateEndDateTime());
        endMinuteCombo.addActionListener(e -> updateEndDateTime());
        endSecondCombo.addActionListener(e -> updateEndDateTime());

        // 初始化时间值
        updateTimeComboBoxes();

        return panel;
    }

    private JComboBox<String> createTimeCombo(int min, int max) {
        JComboBox<String> combo = new JComboBox<>();
        for (int i = min; i <= max; i++) {
            combo.addItem(String.format("%02d", i));
        }
        combo.setEditable(true);
        combo.setPreferredSize(new Dimension(60, 28));
        return combo;
    }

    private LocalTime getStartTime() {
        return LocalTime.of(
                Integer.parseInt((String) startHourCombo.getSelectedItem()),
                Integer.parseInt((String) startMinuteCombo.getSelectedItem()),
                Integer.parseInt((String) startSecondCombo.getSelectedItem())
        );
    }

    private LocalTime getEndTime() {
        return LocalTime.of(
                Integer.parseInt((String) endHourCombo.getSelectedItem()),
                Integer.parseInt((String) endMinuteCombo.getSelectedItem()),
                Integer.parseInt((String) endSecondCombo.getSelectedItem())
        );
    }

    private void updateStartDateTime() {
        if (selectedStartDate != null) {
            startDateTime = selectedStartDate.atTime(getStartTime());
        }
    }

    private void updateEndDateTime() {
        if (selectedEndDate != null) {
            endDateTime = selectedEndDate.plusDays(1).atTime(getEndTime());
        }
    }

    private void updateTimeComboBoxes() {
        if (startDateTime != null) {
            startHourCombo.setSelectedItem(String.format("%02d", startDateTime.getHour()));
            startMinuteCombo.setSelectedItem(String.format("%02d", startDateTime.getMinute()));
            startSecondCombo.setSelectedItem(String.format("%02d", startDateTime.getSecond()));
        }
        if (endDateTime != null) {
            endHourCombo.setSelectedItem(String.format("%02d", endDateTime.getHour()));
            endMinuteCombo.setSelectedItem(String.format("%02d", endDateTime.getMinute()));
            endSecondCombo.setSelectedItem(String.format("%02d", endDateTime.getSecond()));
        }
    }

    /**
     * 创建底部按钮面板
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JButton clearBtn = new JButton("清空");
        clearBtn.setPreferredSize(new Dimension(80, 32));
        clearBtn.addActionListener(e -> {
            selectedStartDate = null;
            selectedEndDate = null;
            startDateTime = null;
            endDateTime = null;
            isSelectingStart = true;
            refreshCalendars();
            startHourCombo.setSelectedItem("00");
            startMinuteCombo.setSelectedItem("00");
            startSecondCombo.setSelectedItem("00");
            endHourCombo.setSelectedItem("00");
            endMinuteCombo.setSelectedItem("00");
            endSecondCombo.setSelectedItem("00");
        });

        JButton confirmBtn = new JButton("确定");
        confirmBtn.setPreferredSize(new Dimension(80, 32));
        confirmBtn.setBackground(new Color(0, 120, 215));
        confirmBtn.setForeground(Color.BLUE);
        confirmBtn.addActionListener(e -> {
            if (listener != null && startDateTime != null && endDateTime != null) {
                listener.onSelected(startDateTime, endDateTime);
            }
            dispose();
        });

        panel.add(clearBtn);
        panel.add(confirmBtn);

        return panel;
    }

    public void setOnDateTimeRangeSelectedListener(OnDateTimeRangeSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * 预设开始和结束时间
     */
    public void setSelectedRange(LocalDateTime start, LocalDateTime end) {
        this.startDateTime = start;
        this.endDateTime = end;
        this.selectedStartDate = start.toLocalDate();
        this.selectedEndDate = end.toLocalDate().minusDays(1);
        adjustMonthToShowDates();
        updateTimeComboBoxes();
        refreshCalendars();
    }

    /**
     * 获取选中的开始时间
     */
    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    /**
     * 获取选中的结束时间
     */
    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    /**
     * 显示选择器
     */
    public void showPicker() {
        setVisible(true);
    }

    /**
     * 回调接口
     */
    public interface OnDateTimeRangeSelectedListener {
        void onSelected(LocalDateTime startDateTime, LocalDateTime endDateTime);
    }
}
