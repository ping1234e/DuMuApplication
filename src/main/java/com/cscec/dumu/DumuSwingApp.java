package com.cscec.dumu;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cscec.dumu.entity.RecognitionRecord;
import com.cscec.dumu.util.DateTimeRangePicker;
import com.cscec.dumu.util.RecognitionConfig;
import com.cscec.dumu.util.SQLiteReader;
import com.cscec.dumu.util.TimestampFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * 度目app
 */
public class DumuSwingApp extends JFrame {
    // 配置信息 - 注册表
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(DumuSwingApp.class);

    // sqlite API
    SQLiteReader sqliteReader = new SQLiteReader();

    // 日志
    private JTextArea resultArea;
    // 设备信息
    private JTextField ipField;
    private JTextField portField;
    private JPasswordField passwordField;
    private DumuClient client;
    private JLabel statusLabel;

    private static final int TABLE_ROW_HEIGHT = 65;
    private static final int IMAGE_ICON_SIZE = 60;
    private static final int DIALOG_WIDTH = 550;
    private static final int DIALOG_HEIGHT = 300;
    private static final int MAX_PAGE_SIZE = 9999;

    // 人员管理组件
    private JTextField searchUserIdField;
    private JTextField searchUserNameField;
    private DefaultTableModel userTableModel;

    private JDialog userEditDialog;
    private JTextField dialogUserIdField;
    private JTextField dialogUserNameField;
    private JTextField dialogUserIdCardField;
    private JTextField dialogUserPhoneField;
    private JComboBox<String> dialogUserTypeCombo;
    private JLabel dialogImagePathLabel;
    private String dialogSelectedImageBase64;

    // 识别记录管理
    private DefaultTableModel recordTableModel;
    JTextField recordUserNameField;

    public DumuSwingApp() {
        setTitle("度目智能门禁机管理客户端");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 800);
        setLocationRelativeTo(null);
        initUI();
        loadConfigFromRegistry();

        // db目录不存在则创建
        File dbDir = new File(RecognitionConfig.DB_DIR);
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // ========== 增加工具栏 ==========
        setJMenuBar(createMenuBar());

        // 顶部连接配置面板
        JPanel topPanel = createConnectionPanel();
        add(topPanel, BorderLayout.NORTH);

        // 中间Tab面板
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("人员管理", createUserPanel());
        tabbedPane.addTab("识别记录", createRecordPanel());

        add(tabbedPane, BorderLayout.CENTER);

        // 底部结果输出面板
        JPanel bottomPanel = createResultPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * 创建菜单栏（包含帮助菜单）
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 文件菜单
        JMenu fileMenu = new JMenu("文件");
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.setIcon(resizeIcon(UIManager.getIcon("OptionPane.errorIcon")));
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // 帮助菜单
        JMenu helpMenu = new JMenu("帮助");

        // 使用说明 - 信息图标
        JMenuItem usageItem = new JMenuItem("使用说明");
        usageItem.setIcon(resizeIcon(UIManager.getIcon("OptionPane.informationIcon")));
        usageItem.addActionListener(e -> showUsageDialog());
        helpMenu.add(usageItem);

        helpMenu.addSeparator();

        // 联系作者 - 问号图标
        JMenuItem contactItem = new JMenuItem("联系作者");
        contactItem.setIcon(resizeIcon(UIManager.getIcon("OptionPane.questionIcon")));
        contactItem.addActionListener(e -> showContactDialog());
        helpMenu.add(contactItem);

        // 关于 - 警告图标（作为第三种图标）
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.setIcon(resizeIcon(UIManager.getIcon("OptionPane.warningIcon")));
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);

        return menuBar;
    }

    /**
     * 调整图标大小
     */
    private Icon resizeIcon(Icon icon) {
        if (icon == null) {
            return null;
        }
        // 创建缓冲图像
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(((ImageIcon) icon).getImage(), 0, 0, 16, 16, null);
        g.dispose();
        return new ImageIcon(image);
    }

    /**
     * 显示使用说明对话框
     */
    private void showUsageDialog() {
        String message = "使用说明：\n\n" +
                "1. 设备连接：\n" +
                "   - 输入ip、端口、密码后连接\n" +
                "2. 人员管理：\n" +
                "   - 根据人员编号、姓名查询人员\n" +
                "   - 列表点击可以将人员数据加载到编辑栏，便于更新\n" +
                "   - 也可在编辑栏直接填写人员信息，新增人员\n" +
                "   - 支持人员的导出和批量的导入\n" +
                "3. 识别记录：\n" +
                "   - 根据人员姓名和通行时间查找记录\n" +
                "   - 这里只查询员工通行记录，陌生人不展示\n" +
                "   - 同步按钮逻辑自行实现\n\n";

        JOptionPane.showMessageDialog(this, message, "使用说明",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 显示联系作者对话框
     */
    private void showContactDialog() {
        // 创建自定义面板，使信息更美观
        JPanel contactPanel = new JPanel(new GridBagLayout());
        contactPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 作者信息
        gbc.gridx = 0;
        gbc.gridy = 0;
        contactPanel.add(new JLabel("作者："), gbc);
        gbc.gridx = 1;
        contactPanel.add(new JLabel("ping1234e"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        contactPanel.add(new JLabel("github："), gbc);
        gbc.gridx = 1;
        JLabel emailLabel = new JLabel("ping1234e");
        emailLabel.setForeground(Color.BLUE);
        contactPanel.add(emailLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        contactPanel.add(new JLabel("QQ："), gbc);
        gbc.gridx = 1;
        contactPanel.add(new JLabel("592634988"), gbc);

        // 提示信息
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JLabel tipLabel = new JLabel("如有问题或建议，欢迎联系！");
        tipLabel.setForeground(Color.RED);
        contactPanel.add(tipLabel, gbc);

        JOptionPane.showMessageDialog(this, contactPanel, "联系作者",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 显示关于对话框
     */
    private void showAboutDialog() {
        String message = "度目门禁考勤管理工具\n\n" +
                "版本：V1.0\n" +
                "开发日期：2026年6月\n" +
                "运行环境：Java Swing\n\n" +
                "功能：\n" +
                "• 设备连接\n" +
                "• 人员管理\n" +
                "• 多条件查询识别记录\n\n" +
                "© 2026 版权所有";

        JOptionPane.showMessageDialog(this, message, "关于",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // 回调配置的存储key
    private static final String KEY_CALLBACK_URL = "callback.url";
    private static final String KEY_HEARTBEAT_URL = "heartbeat.url";
    private static final String KEY_HEARTBEAT_INTERVAL = "heartbeat.interval";

    /**
     * 加载回调配置
     */
    private void loadCallbackConfig(JTextField callbackUrlField, JTextField heartbeatUrlField, JTextField heartbeatIntervalField) {
        Preferences prefs = Preferences.userNodeForPackage(DumuSwingApp.class);
        String callbackUrl = prefs.get(KEY_CALLBACK_URL, "");
        String heartbeatUrl = prefs.get(KEY_HEARTBEAT_URL, "");
        String heartbeatInterval = prefs.get(KEY_HEARTBEAT_INTERVAL, "60");

        if (!callbackUrl.isEmpty()) {
            callbackUrlField.setText(callbackUrl);
        }
        if (!heartbeatUrl.isEmpty()) {
            heartbeatUrlField.setText(heartbeatUrl);
        }
        heartbeatIntervalField.setText(heartbeatInterval);
    }

    /**
     * 设置识别记录回调
     */
    private void setRecognitionCallback(String callbackUrl) {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        if (callbackUrl.isEmpty()) {
            appendResult("回调地址不能为空");
            return;
        }

        // 验证URL格式
        if (!callbackUrl.startsWith("http://") && !callbackUrl.startsWith("https://")) {
            appendResult("回调地址必须以 http:// 或 https:// 开头");
            return;
        }

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("正在设置识别记录回调: " + callbackUrl);

                String response = client.setRecognitionCallback(callbackUrl, 60);
                JSONObject result = JSONObject.parseObject(response);

                if (result.getIntValue("code") >= 0) {
                    publish("识别记录回调设置成功");
                    // 保存到配置
                    Preferences prefs = Preferences.userNodeForPackage(DumuSwingApp.class);
                    prefs.put(KEY_CALLBACK_URL, callbackUrl);
                } else {
                    publish("设置失败: " + result.getString("log"));
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("设置回调失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * 设置心跳回调
     */
    private void setHeartbeatCallback(String heartbeatUrl, String intervalStr) {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        if (heartbeatUrl.isEmpty()) {
            appendResult("心跳地址不能为空");
            return;
        }

        // 验证URL格式
        if (!heartbeatUrl.startsWith("http://") && !heartbeatUrl.startsWith("https://")) {
            appendResult("心跳地址必须以 http:// 或 https:// 开头");
            return;
        }

        int interval;
        try {
            interval = Integer.parseInt(intervalStr);
            if (interval < 0 || interval > 3600) {
                appendResult("心跳间隔范围: 0~3600秒");
                return;
            }
        } catch (NumberFormatException e) {
            appendResult("心跳间隔必须是数字");
            return;
        }

        final int finalInterval = interval;

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("正在设置心跳回调: " + heartbeatUrl + ", 间隔: " + finalInterval + "秒");

                String response = client.setDeviceHeartBeat(heartbeatUrl, finalInterval);
                JSONObject result = JSONObject.parseObject(response);

                if (result.getIntValue("code") >= 0) {
                    publish("心跳回调设置成功");
                    // 保存到配置
                    Preferences prefs = Preferences.userNodeForPackage(DumuSwingApp.class);
                    prefs.put(KEY_HEARTBEAT_URL, heartbeatUrl);
                    prefs.put(KEY_HEARTBEAT_INTERVAL, String.valueOf(finalInterval));
                } else {
                    publish("设置失败: " + result.getString("log"));
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("设置心跳失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * 测试回调地址
     */
    private void testCallbackUrl(String callbackUrl) {
        if (callbackUrl.isEmpty()) {
            appendResult("请先输入回调地址");
            return;
        }

        appendResult("正在测试回调地址: " + callbackUrl);

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build();

                    JSONObject testData = new JSONObject();
                    testData.put("test", "connection");
                    testData.put("timestamp", System.currentTimeMillis());

                    okhttp3.RequestBody body = okhttp3.RequestBody.create(
                            okhttp3.MediaType.parse("application/json; charset=utf-8"),
                            testData.toJSONString()
                    );

                    okhttp3.Request request = new okhttp3.Request.Builder()
                            .url(callbackUrl)
                            .post(body)
                            .build();

                    try (okhttp3.Response response = client.newCall(request).execute()) {
                        int code = response.code();
                        if (code >= 200 && code < 300) {
                            publish("✓ 回调地址可用，响应码: " + code);
                        } else {
                            publish("✗ 回调地址返回异常，响应码: " + code);
                        }
                    }
                } catch (Exception e) {
                    publish("✗ 回调地址不可用: " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }
        };
        worker.execute();
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("设备连接"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 第一行：设备IP、端口、密码、连接按钮、保存配置
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("设备IP:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        ipField = new JTextField(15);
        panel.add(ipField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("端口:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        portField = new JTextField("8080", 6);
        // 只能数字
        ((AbstractDocument) portField.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (string == null) {
                    return;
                }
                if (string.matches("\\d*")) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text == null) {
                    return;
                }
                if (text.matches("\\d*")) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
        // 添加焦点丢失时的范围校验
        portField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                String text = portField.getText().trim();
                if (text.isEmpty()) {
                    portField.setText("8080");
                    return;
                }
                int port = Integer.parseInt(text);
                if (port < 1 || port > 65535) {
                    portField.setText("8080");
                    appendResult("端口号范围应为 1-65535，已重置为 8080");
                }
            }
        });
        panel.add(portField, gbc);

        gbc.gridx = 4;
        gbc.weightx = 0;
        panel.add(new JLabel("密码:"), gbc);

        gbc.gridx = 5;
        gbc.weightx = 1;
        passwordField = new JPasswordField(10);
        panel.add(passwordField, gbc);

        gbc.gridx = 6;
        gbc.weightx = 0;
        JButton connectBtn = new JButton("连接设备");
        connectBtn.addActionListener(e -> connectDevice());
        panel.add(connectBtn, gbc);

        gbc.gridx = 7;
        JButton saveConfigBtn = new JButton("保存配置");
        saveConfigBtn.addActionListener(e -> saveConfigToRegistry());
        panel.add(saveConfigBtn, gbc);

        // ========== 使用相同的列宽结构，确保上下对齐 ==========
        // 第二行：识别记录回调
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("识别记录回调:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 4;  // 占用4列，让后面的按钮对齐
        gbc.weightx = 1;
        JTextField callbackUrlField = new JTextField();
        callbackUrlField.setToolTipText("示例: http://192.168.1.100:8080/api/recognition/callback");
        panel.add(callbackUrlField, gbc);

        gbc.gridx = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        JButton setCallbackBtn = new JButton("设置回调");
        setCallbackBtn.addActionListener(e -> setRecognitionCallback(callbackUrlField.getText().trim()));
        panel.add(setCallbackBtn, gbc);

        gbc.gridx = 6;
        gbc.gridwidth = 1;
        JButton testCallbackBtn = new JButton("测试");
        testCallbackBtn.addActionListener(e -> testCallbackUrl(callbackUrlField.getText().trim()));
        panel.add(testCallbackBtn, gbc);

        // 占位，保持列数一致（第7列留空）
        gbc.gridx = 7;
        gbc.weightx = 0;
        panel.add(new JLabel(""), gbc);

        // 第三行：心跳回调配置
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("心跳回调:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;  // 占用2列，让后面的控件对齐到与"设置回调"相同的位置
        gbc.weightx = 1;
        JTextField heartbeatUrlField = new JTextField();
        heartbeatUrlField.setToolTipText("示例: http://192.168.1.100:8080/api/heartbeat");
        panel.add(heartbeatUrlField, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("心跳间隔(秒):"), gbc);

        gbc.gridx = 4;
        gbc.gridwidth = 1;
        JTextField heartbeatIntervalField = new JTextField("60", 6);
        panel.add(heartbeatIntervalField, gbc);

        // 设置心跳按钮 - 放在与"设置回调"相同的列位置（第5列）
        gbc.gridx = 5;
        gbc.gridwidth = 1;
        JButton setHeartbeatBtn = new JButton("设置心跳");
        setHeartbeatBtn.addActionListener(e -> setHeartbeatCallback(heartbeatUrlField.getText().trim(), heartbeatIntervalField.getText().trim()));
        panel.add(setHeartbeatBtn, gbc);

        // 占位，让第6、7列保持空白，与上一行对齐
        gbc.gridx = 6;
        panel.add(new JLabel(""), gbc);
        gbc.gridx = 7;
        panel.add(new JLabel(""), gbc);

        // 第四行：状态显示（跨越所有列）
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 8;
        statusLabel = new JLabel("未连接");
        statusLabel.setForeground(Color.RED);
        panel.add(statusLabel, gbc);

        // 加载保存的回调配置
        loadCallbackConfig(callbackUrlField, heartbeatUrlField, heartbeatIntervalField);

        return panel;
    }

    private JPanel createUserPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 搜索区域
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("人员查询"));

        searchPanel.add(new JLabel("人员ID:"));
        searchUserIdField = new JTextField(12);
        searchPanel.add(searchUserIdField);

        searchPanel.add(new JLabel("姓名:"));
        searchUserNameField = new JTextField(12);
        searchPanel.add(searchUserNameField);

        JButton searchUserBtn = new JButton("查询");
        searchUserBtn.addActionListener(e -> searchUsers());
        searchPanel.add(searchUserBtn);

        JButton exportBtn = new JButton("一键导出人员信息");
        exportBtn.addActionListener(e -> exportUsersToCSV());
        searchPanel.add(exportBtn);

        JButton importBtn = new JButton("一键导入人员编号");
        importBtn.addActionListener(e -> importUsersFromCSV());
        searchPanel.add(importBtn);

        // 新增用户按钮
        JButton addUserBtn = new JButton("新增用户");
        addUserBtn.addActionListener(e -> showUserEditDialog(null));
        searchPanel.add(addUserBtn);

        panel.add(searchPanel, BorderLayout.NORTH);

        // 人员表格 - 操作列改为两个按钮（更新、删除）
        String[] columns = {"序号", "人脸照片", "人员ID", "姓名", "编号", "电话号码", "人员类型", "操作"};
        userTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 7;  // 只有操作列可编辑
            }
        };
        // 操作列改为JPanel容纳两个按钮
        JTable userTable = new JTable(userTableModel) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 1) {
                    return ImageIcon.class;
                }
                if (column == 7) {
                    return JPanel.class;  // 操作列改为JPanel容纳两个按钮
                }
                return String.class;
            }
        };
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.getColumnModel().getColumn(0).setMaxWidth(50);
        userTable.getColumnModel().getColumn(1).setMaxWidth(80);
        userTable.getColumnModel().getColumn(1).setMinWidth(80);
        userTable.setRowHeight(TABLE_ROW_HEIGHT);

        // 设置操作列的自定义渲染器和编辑器（双按钮）
        userTable.getColumn("操作").setCellRenderer(new UserActionsRenderer());
        userTable.getColumn("操作").setCellEditor(new UserActionsEditor());

        JScrollPane tableScroll = new JScrollPane(userTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("人员列表"));
        panel.add(tableScroll, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 显示人员编辑对话框
     *
     * @param row 行索引，为null表示新增
     */
    private void showUserEditDialog(Integer row) {
        // 创建对话框
        if (userEditDialog == null) {
            userEditDialog = new JDialog(this, true);
            userEditDialog.setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
            userEditDialog.setLocationRelativeTo(this);
            userEditDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        }

        // 重新构建对话框内容（每次打开重新创建，避免组件引用问题）
        userEditDialog.getContentPane().removeAll();
        userEditDialog.add(createEditDialogPanel());

        // 清空表单
        clearDialogForm();

        // 如果是编辑模式，加载数据
        if (row != null && row >= 0) {
            userEditDialog.setTitle("编辑人员");
            dialogUserIdField.setText(userTableModel.getValueAt(row, 2).toString());
            dialogUserNameField.setText(userTableModel.getValueAt(row, 3).toString());
            dialogUserIdCardField.setText(userTableModel.getValueAt(row, 4).toString());
            dialogUserPhoneField.setText(userTableModel.getValueAt(row, 5).toString());
            String type = userTableModel.getValueAt(row, 6).toString();
            if ("成员".equals(type)) {
                dialogUserTypeCombo.setSelectedIndex(0);
            } else if ("访客".equals(type)) {
                dialogUserTypeCombo.setSelectedIndex(1);
            } else {
                dialogUserTypeCombo.setSelectedIndex(2);
            }
            // 编辑模式下，人员ID设为不可编辑
            dialogUserIdField.setEditable(false);
        } else {
            userEditDialog.setTitle("新增人员");
            dialogUserIdField.setEditable(true);
            dialogUserIdField.setText("");
        }

        userEditDialog.setVisible(true);
    }

    /**
     * 清空对话框表单
     */
    private void clearDialogForm() {
        if (dialogUserIdField != null) {
            dialogUserIdField.setText("");
        }
        if (dialogUserNameField != null) {
            dialogUserNameField.setText("");
        }
        if (dialogUserIdCardField != null) {
            dialogUserIdCardField.setText("");
        }
        if (dialogUserPhoneField != null) {
            dialogUserPhoneField.setText("");
        }
        if (dialogUserTypeCombo != null) {
            dialogUserTypeCombo.setSelectedIndex(0);
        }
        dialogSelectedImageBase64 = null;
        if (dialogImagePathLabel != null) {
            dialogImagePathLabel.setText("未选择图片");
            dialogImagePathLabel.setForeground(Color.GRAY);
        }
    }

    /**
     * 创建编辑对话框内容面板
     */
    private JPanel createEditDialogPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 第一行：人员ID、姓名、人员类型
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("人员ID:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        dialogUserIdField = new JTextField();
        panel.add(dialogUserIdField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("姓名:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 1;
        dialogUserNameField = new JTextField();
        panel.add(dialogUserNameField, gbc);

        gbc.gridx = 4;
        gbc.weightx = 0;
        panel.add(new JLabel("人员类型:"), gbc);

        gbc.gridx = 5;
        gbc.weightx = 1;
        dialogUserTypeCombo = new JComboBox<>(new String[]{"成员", "访客", "黑名单"});
        panel.add(dialogUserTypeCombo, gbc);

        // 第二行：编号、电话号码
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("编号:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        dialogUserIdCardField = new JTextField();
        panel.add(dialogUserIdCardField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("电话号码:"), gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        dialogUserPhoneField = new JTextField();
        panel.add(dialogUserPhoneField, gbc);

        // 第三行：人脸图片
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("人脸图片:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 5;
        gbc.weightx = 1;

        JPanel imagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        imagePanel.setOpaque(false);

        JButton selectImageBtn = new JButton("选择图片");
        selectImageBtn.addActionListener(e -> selectDialogImageFile());
        imagePanel.add(selectImageBtn);

        dialogImagePathLabel = new JLabel("未选择图片");
        dialogImagePathLabel.setForeground(Color.GRAY);
        imagePanel.add(dialogImagePathLabel);

        JLabel tipLabel = new JLabel("（分辨率160×160~1080×1080，小于1MB）");
        tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        tipLabel.setForeground(Color.GRAY);
        imagePanel.add(tipLabel);

        panel.add(imagePanel, gbc);

        // 第四行：按钮
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 6;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.CENTER;

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));

        JButton saveBtn = new JButton("保存");
        saveBtn.setPreferredSize(new Dimension(100, 32));
        saveBtn.addActionListener(e -> {
            saveOrUpdateUserFromDialog();
            userEditDialog.dispose();
        });
        btnPanel.add(saveBtn);

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setPreferredSize(new Dimension(100, 32));
        cancelBtn.addActionListener(e -> userEditDialog.dispose());
        btnPanel.add(cancelBtn);

        panel.add(btnPanel, gbc);

        return panel;
    }

    /**
     * 对话框中选择图片文件
     */
    private void selectDialogImageFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择人员照片");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "图片文件 (*.jpg)", "jpg"));

        if (fileChooser.showOpenDialog(userEditDialog) == JFileChooser.APPROVE_OPTION) {
            File imageFile = fileChooser.getSelectedFile();
            // 增加文件大小限制（例如 1MB）
            if (imageFile.length() > 1024 * 1024) {
                appendResult("图片文件过大，请选择小于 1MB 的图片");
                return;
            }
            try {
                byte[] imageBytes = new byte[(int) imageFile.length()];
                try (FileInputStream fis = new FileInputStream(imageFile)) {
                    fis.read(imageBytes);
                }
                dialogSelectedImageBase64 = Base64.getEncoder().encodeToString(imageBytes);
                dialogSelectedImageBase64 = dialogSelectedImageBase64.replaceAll("\\s", "");
                String fileInfo = imageFile.getName() + " (" + (imageBytes.length / 1024) + " KB)";
                dialogImagePathLabel.setText(fileInfo);
                dialogImagePathLabel.setForeground(Color.BLACK);
                appendResult("已选择图片: " + fileInfo);
            } catch (Exception e) {
                appendResult("读取图片失败: " + e.getMessage());
                dialogSelectedImageBase64 = null;
                dialogImagePathLabel.setText("未选择图片");
                dialogImagePathLabel.setForeground(Color.GRAY);
            }
        }
    }

    /**
     * 从对话框保存人员
     */
    private void saveOrUpdateUserFromDialog() {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        String cardNumber = dialogUserIdCardField.getText().trim();
        if (cardNumber.isEmpty()) {
            appendResult("编号不能为空");
            return;
        }

        String userId = dialogUserIdField.getText().trim();
        if (userId.isEmpty()) {
            userId = cardNumber;
        }

        String name = dialogUserNameField.getText().trim();
        if (name.isEmpty()) {
            appendResult("姓名不能为空");
            return;
        }

        final String finalUserId = userId;
        final String imageBase64 = dialogSelectedImageBase64;

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("正在保存人员: " + finalUserId);

                JSONObject userInfo = new JSONObject();
                userInfo.put("user_type", dialogUserTypeCombo.getSelectedIndex() + 1);
                userInfo.put("name", name);
                userInfo.put("card_number", cardNumber);
                userInfo.put("phone_number", dialogUserPhoneField.getText().trim());

                String response = client.saveOrUpdateUser(finalUserId, imageBase64, userInfo, null, null);
                JSONObject result = JSONObject.parseObject(response);

                if (result.getIntValue("code") >= 0) {
                    publish("人员 " + finalUserId + " 保存成功");
                    searchUsers();  // 刷新列表
                } else {
                    publish("保存失败: " + result.getString("log"));
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("保存失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * 人员表格操作列渲染器（双按钮）
     */
    class UserActionsRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private JButton updateBtn;
        private JButton deleteBtn;

        public UserActionsRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 8, 5));
            setOpaque(true);

            updateBtn = new JButton("更新");
            updateBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            updateBtn.setPreferredSize(new Dimension(55, 28));

            deleteBtn = new JButton("删除");
            deleteBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            deleteBtn.setPreferredSize(new Dimension(55, 28));

            add(updateBtn);
            add(deleteBtn);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }
            return this;
        }
    }

    /**
     * 人员表格操作列编辑器（双按钮）
     */
    class UserActionsEditor extends AbstractCellEditor implements javax.swing.table.TableCellEditor {
        private JPanel panel;
        private JButton updateBtn;
        private JButton deleteBtn;
        private int clickedRow;

        public UserActionsEditor() {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
            panel.setOpaque(true);

            updateBtn = new JButton("更新");
            updateBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            updateBtn.setPreferredSize(new Dimension(55, 28));
            updateBtn.addActionListener(e -> {
                fireEditingStopped();
                if (clickedRow >= 0) {
                    showUserEditDialog(clickedRow);
                }
            });

            deleteBtn = new JButton("删除");
            deleteBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            deleteBtn.setPreferredSize(new Dimension(55, 28));
            deleteBtn.addActionListener(e -> {
                fireEditingStopped();
                if (clickedRow >= 0) {
                    deleteUser(clickedRow);
                }
            });

            panel.add(updateBtn);
            panel.add(deleteBtn);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            this.clickedRow = row;
            if (isSelected) {
                panel.setBackground(table.getSelectionBackground());
            } else {
                panel.setBackground(table.getBackground());
            }
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }
    }

    private JPanel createRecordPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        int lineHeight = 20;
        // 查询区域
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("识别记录查询"));

        searchPanel.add(new JLabel("人员姓名:"));
        // 识别记录组件
        recordUserNameField = new JTextField(10);
        recordUserNameField.setPreferredSize(new Dimension(150, lineHeight));
        searchPanel.add(recordUserNameField);

        searchPanel.add(new JLabel("通行时间:"));
        // 时间范围显示标签
        JLabel timeRangeLabel = new JLabel("");

        timeRangeLabel.setPreferredSize(new Dimension(265, lineHeight));
        timeRangeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
        timeRangeLabel.setBackground(Color.WHITE);
        timeRangeLabel.setOpaque(true);
        timeRangeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 初始化时间范围为最近一天
        LocalDateTime defaultStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime defaultEnd = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        timeRangeLabel.setText(TimestampFormatter.formatDateTimeRange(defaultStart, defaultEnd));

        // 存储当前选中的时间范围
        final LocalDateTime[] selectedStart = {defaultStart};
        final LocalDateTime[] selectedEnd = {defaultEnd};

        // 点击标签打开选择器
        timeRangeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                DateTimeRangePicker picker = new DateTimeRangePicker(SwingUtilities.getWindowAncestor(panel));
                picker.setSelectedRange(selectedStart[0], selectedEnd[0]);
                picker.setOnDateTimeRangeSelectedListener((startDateTime, endDateTime) -> {
                    selectedStart[0] = startDateTime;
                    selectedEnd[0] = endDateTime;
                    timeRangeLabel.setText(TimestampFormatter.formatDateTimeRange(startDateTime, endDateTime));
                });
                picker.showPicker();
            }
        });

        searchPanel.add(timeRangeLabel);

        JButton searchRecordBtn = new JButton("查询");
        searchRecordBtn.setPreferredSize(new Dimension(60, lineHeight));
        searchRecordBtn.addActionListener(e -> searchRecords(selectedStart[0], selectedEnd[0]));

        searchPanel.add(searchRecordBtn);

        searchPanel.add(new JLabel("时间YYYY-MM-DD:"));
        JTextField timestampField = new JTextField(12);
        timestampField.setText(LocalDate.now().minusDays(1).toString());
        timestampField.setToolTipText("请输入时间，默认取输入时间后的数据");
        searchPanel.add(timestampField);

        JButton oneKeyExportBtn = new JButton("一键识别导出");
        oneKeyExportBtn.addActionListener(e -> oneKeyExport(timestampField.getText().trim()));
        searchPanel.add(oneKeyExportBtn);

        panel.add(searchPanel, BorderLayout.NORTH);

        // 识别记录表格
        String[] columns = {"序号", "时间", "人员ID", "姓名", "核验方式", "通行状态", "操作"};
        recordTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6;  // 只有操作列可编辑
            }
        };

        JTable recordTable = new JTable(recordTableModel) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 6) {
                    return JButton.class;    // 操作列显示按钮
                }
                return String.class;
            }
        };
        recordTable.setRowHeight(45);

        // 设置操作列的自定义渲染器和编辑器
        recordTable.getColumn("操作").setCellRenderer(new RecordButtonRenderer());
        recordTable.getColumn("操作").setCellEditor(new RecordButtonEditor());

        // 设置各列宽度
        recordTable.getColumnModel().getColumn(0).setPreferredWidth(45);   // 序号列
        recordTable.getColumnModel().getColumn(0).setMinWidth(40);
        recordTable.getColumnModel().getColumn(0).setMaxWidth(60);
        recordTable.getColumnModel().getColumn(0).setResizable(false);     // 禁止调整序号列宽度

        recordTable.getColumnModel().getColumn(1).setPreferredWidth(150);  // 时间列
        recordTable.getColumnModel().getColumn(1).setMinWidth(140);

        recordTable.getColumnModel().getColumn(2).setPreferredWidth(80);   // 人员ID列
        recordTable.getColumnModel().getColumn(2).setMinWidth(60);

        recordTable.getColumnModel().getColumn(3).setPreferredWidth(70);   // 姓名列
        recordTable.getColumnModel().getColumn(3).setMinWidth(60);

        recordTable.getColumnModel().getColumn(4).setPreferredWidth(80);   // 核验方式列
        recordTable.getColumnModel().getColumn(4).setMinWidth(70);

        recordTable.getColumnModel().getColumn(5).setPreferredWidth(70);   // 通行状态列
        recordTable.getColumnModel().getColumn(5).setMinWidth(60);

        recordTable.getColumnModel().getColumn(6).setPreferredWidth(70);   // 操作列
        recordTable.getColumnModel().getColumn(6).setMinWidth(65);
        recordTable.getColumnModel().getColumn(6).setMaxWidth(80);

        JScrollPane tableScroll = new JScrollPane(recordTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("识别记录列表"));
        panel.add(tableScroll, BorderLayout.CENTER);

        return panel;
    }

    // 识别记录表格操作列按钮渲染器 - 显示两个按钮
    class RecordButtonRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private JButton syncBtn;

        public RecordButtonRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 8, 5));
            setOpaque(true);

            syncBtn = new JButton("同步");
            syncBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            syncBtn.setPreferredSize(new Dimension(65, 28));
            syncBtn.setFocusPainted(false);

            add(syncBtn);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }
            return this;
        }
    }

    // 识别记录表格操作列按钮编辑器
    class RecordButtonEditor extends AbstractCellEditor implements javax.swing.table.TableCellEditor {
        private JPanel panel;
        private JButton syncBtn;
        private int clickedRow;

        public RecordButtonEditor() {

            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
            panel.setOpaque(true);

            syncBtn = new JButton("同步");
            syncBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            syncBtn.setPreferredSize(new Dimension(65, 28));
            syncBtn.setFocusPainted(false);
            syncBtn.addActionListener(e -> {
                fireEditingStopped();
                if (clickedRow >= 0) {
                    syncRecordToPlatform(clickedRow);
                }
            });

            panel.add(syncBtn);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            this.clickedRow = row;
            if (isSelected) {
                panel.setBackground(table.getSelectionBackground());
            } else {
                panel.setBackground(table.getBackground());
            }
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }
    }

    /**
     * 一键识别导出 - 根据时间戳过滤所有.db文件中的记录，导出到Excel
     */
    private void oneKeyExport(String timestampStr) {
        // 验证时间戳
        if (timestampStr == null || timestampStr.isEmpty()) {
            appendResult("请输入时间");
            return;
        }

        long timestamp;
        try {
            timestamp = TimestampFormatter.parseTimeString(timestampStr);
        } catch (NumberFormatException e) {
            appendResult("时间格式错误，请检查");
            return;
        }

        final long filterTimestamp = timestamp;

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("========== 开始一键识别导出 ==========");
                publish("筛选条件: ts >= " + filterTimestamp + " 且 pass_status = 1");

                // 2. 创建临时目录
                String tempPath = System.getProperty("java.io.tmpdir") + "/dumu_export/";
                File tempDir = new File(tempPath);
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }

                // 3. 查找设备上所有的 .db 文件
                publish("正在查找设备上的数据库文件...");
                File[] dbFiles = RecognitionConfig.getAllDbFile();
                if (dbFiles == null || dbFiles.length == 0) {
                    publish("未找到任何 .db 文件");
                    return null;
                }
                List<String> fileNames = Arrays.stream(dbFiles)
                        .map(File::getName)
                        .collect(Collectors.toList());
                publish("找到 " + dbFiles.length + " 个数据库文件: " + String.join(", ", fileNames));

                // 4. 遍历每个 .db 文件，查询符合条件的记录
                List<RecognitionRecord> allRecords = new ArrayList<>();
                int fileIndex = 0;
                for (File localDbFile : dbFiles) {

                    List<RecognitionRecord> fileRecords = sqliteReader.queryRecognitionRecords(localDbFile.getPath(), "", filterTimestamp, Integer.MAX_VALUE);
                    fileIndex++;
                    publish(String.format("正在处理 [%d/%d]: %s", fileIndex, dbFiles.length, localDbFile.getName()));
                    // 查询该数据库文件
                    publish(String.format("从 %s 中查到 %d 条记录",
                            localDbFile.getName(), fileRecords.size()));
                    allRecords.addAll(fileRecords);
                }

                publish("共查询到 " + allRecords.size() + " 条符合条件的记录");

                if (allRecords.isEmpty()) {
                    publish("没有找到符合条件的记录");
                    return null;
                }

                // 5. 导出到 Excel
                String exportPath = exportToExcel(allRecords, timestampStr, filterTimestamp);
                publish("导出成功！文件保存路径: " + exportPath);

                publish("========== 一键识别导出完成 ==========");
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("一键识别导出失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    /**
     * 导出记录到 Excel 文件
     */
    private String exportToExcel(List<RecognitionRecord> records, String timestampStr, long filterTimestamp) throws Exception {
        // 使用 Apache POI 创建 Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("识别记录");

        // 创建标题行
        Row headerRow = sheet.createRow(0);
        String[] headers = {"序号", "时间戳", "姓名", "识别时间"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // 填充数据
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        int rowNum = 1;
        for (RecognitionRecord record : records) {
            Row row = sheet.createRow(rowNum);
            row.createCell(0).setCellValue(rowNum);
            row.createCell(1).setCellValue(record.getTimestamp());
            row.createCell(2).setCellValue(record.getUserName() != null ? record.getUserName() : "");
            String dateTime = sdf.format(new Date(record.getTimestamp() * 1000));
            row.createCell(3).setCellValue(dateTime);
            rowNum++;
        }

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        // 生成文件名
        String baseFileName = String.format("识别记录导出_%d_%s", filterTimestamp, timestampStr);
        String extension = ".xlsx";

        File exportFile = new File(RecognitionConfig.DB_DIR + baseFileName + extension);
        int counter = 1;

        // 如果文件已存在，添加序号
        while (exportFile.exists()) {
            String newFileName = String.format("%s_%d%s", baseFileName, counter, extension);
            exportFile = new File(RecognitionConfig.DB_DIR + newFileName);
            counter++;
        }
        // 写入文件
        try (FileOutputStream fos = new FileOutputStream(exportFile)) {
            workbook.write(fos);
        }
        workbook.close();

        return exportFile.getAbsolutePath();
    }

    private JPanel createResultPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("操作日志"));

        resultArea = new JTextArea(8, 80);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(resultArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton clearBtn = new JButton("清空日志");
        clearBtn.addActionListener(e -> resultArea.setText(""));
        panel.add(clearBtn, BorderLayout.EAST);

        return panel;
    }

    private void connectDevice() {
        String ip = ipField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());
        String password = new String(passwordField.getPassword());

        client = new DumuClient(ip, port, password);
        statusLabel.setText("已连接: " + ip + ":" + port);
        statusLabel.setForeground(Color.GREEN);
        appendResult("成功连接到设备: " + ip + ":" + port);

        saveConfigToRegistry();
    }

    // 保存配置到注册表（Windows）或 plist 文件（Mac）
    private void saveConfigToRegistry() {
        PREFERENCES.put("device.ip", ipField.getText().trim());
        PREFERENCES.putInt("device.port", Integer.parseInt(portField.getText().trim()));
        PREFERENCES.put("device.password", new String(passwordField.getPassword()));
    }

    // 从注册表加载配置
    private void loadConfigFromRegistry() {
        String ip = PREFERENCES.get("device.ip", "");
        int port = PREFERENCES.getInt("device.port", 8080);
        String password = PREFERENCES.get("device.password", "");
        ipField.setText(ip);
        portField.setText(String.valueOf(port));
        passwordField.setText(password);
    }

    private void appendResult(String text) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        resultArea.append("[" + timestamp + "] " + text + "\n");
        resultArea.setCaretPosition(resultArea.getDocument().getLength());
    }

    // 查询人员
    private void searchUsers() {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        String searchId = searchUserIdField.getText().trim();
        String searchName = searchUserNameField.getText().trim();

        SwingWorker<Void, Object[]> worker = new SwingWorker<Void, Object[]>() {
            private List<Object[]> userList = new ArrayList<>();

            @Override
            protected Void doInBackground() throws Exception {
                publish(new Object[]{"STATUS", "正在查询人员列表..."});

                if (!searchId.isEmpty()) {
                    String response = client.getUserInfo(searchId);
                    JSONObject json = JSONObject.parseObject(response);
                    if (json.getIntValue("code") >= 0) {
                        Object[] userData = parseUserData(searchId, json, 1);
                        if (userData != null) {
                            publish(new Object[]{"ADD_USER", userData});
                        }
                    }
                } else {
                    String response = client.getUserList(0, MAX_PAGE_SIZE);
                    JSONObject json = JSONObject.parseObject(response);
                    if (json.getIntValue("code") >= 0 && json.containsKey("user_id_list")) {
                        JSONArray userIdList = json.getJSONArray("user_id_list");
                        int total = userIdList.size();
                        publish(new Object[]{"STATUS", "共找到 " + total + " 名人员,正在逐个加载人员信息"});

                        for (int i = 0; i < userIdList.size(); i++) {
                            setProgress((i + 1) * 100 / total);
                            String userId = (String) userIdList.get(i);
                            String userInfoResponse = client.getUserInfo(userId);
                            JSONObject userInfoJson = JSONObject.parseObject(userInfoResponse);
                            if (userInfoJson.getIntValue("code") >= 0) {
                                Object[] userData = parseUserData(userId, userInfoJson, i + 1);
                                if (userData != null) {
                                    publish(new Object[]{"ADD_USER", userData});
                                }
                            }
                            Thread.sleep(50);
                        }
                    }
                }

                publish(new Object[]{"FINISH"});
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] chunk : chunks) {
                    String type = (String) chunk[0];
                    switch (type) {
                        case "STATUS":
                            appendResult((String) chunk[1]);
                            break;
                        case "ADD_USER":
                            Object[] userData = (Object[]) chunk[1];
                            userTableModel.addRow(userData);
                            break;
                        case "FINISH":
                            appendResult("人员列表加载完成，共 " + userTableModel.getRowCount() + " 条记录");
                            break;
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("查询失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * 解析用户数据，返回可直接添加到表格的行数据
     */
    private Object[] parseUserData(String userId, JSONObject userInfoJson, int rowIndex) {
        String name = "";
        String cardNumber = "";
        String phoneNumber = "";
        int userType = 1;
        ImageIcon faceImageIcon = null;

        if (userInfoJson == null || !userInfoJson.containsKey("user_info")) {
            return null;
        }

        JSONObject info = userInfoJson.getJSONObject("user_info");
        if (info.containsKey("name")) {
            name = info.getString("name");
        }
        if (info.containsKey("card_number")) {
            cardNumber = info.getString("card_number");
        }
        if (info.containsKey("phone_number")) {
            phoneNumber = info.getString("phone_number");
        }
        if (info.containsKey("user_type")) {
            userType = info.getIntValue("user_type");
        }

        // 尝试获取人脸照片
        if (userInfoJson.containsKey("face_image")) {
            String faceImageBase64 = userInfoJson.getString("face_image");
            if (faceImageBase64 != null && !faceImageBase64.isEmpty()) {
                try {
                    byte[] imageBytes = Base64.getDecoder().decode(faceImageBase64);
                    // 检查图片数据是否有效
                    if (imageBytes.length > 0) {
                        ImageIcon originalIcon = new ImageIcon(imageBytes);
                        // 缩放图片到合适大小
                        Image scaledImage = originalIcon.getImage().getScaledInstance(IMAGE_ICON_SIZE, IMAGE_ICON_SIZE, Image.SCALE_SMOOTH);
                        faceImageIcon = new ImageIcon(scaledImage);
                    }
                } catch (Exception e) {
                    System.err.println("解析人脸照片失败: " + e.getMessage());
                }
            }
        }

        // 如果没有获取到照片，使用默认占位图标
        if (faceImageIcon == null) {
            faceImageIcon = createDefaultImageIcon();
        }

        String userTypeStr = userType == 1 ? "成员" : (userType == 2 ? "访客" : "黑名单");
        return new Object[]{rowIndex, faceImageIcon, userId, name, cardNumber, phoneNumber, userTypeStr, null};
    }

    /**
     * 创建默认图片图标
     */
    private ImageIcon createDefaultImageIcon() {
        BufferedImage defaultImage = new BufferedImage(IMAGE_ICON_SIZE, IMAGE_ICON_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = defaultImage.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, IMAGE_ICON_SIZE, IMAGE_ICON_SIZE);
        g.setColor(Color.GRAY);
        g.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        g.drawString("无照片", 15, 30);
        g.dispose();
        return new ImageIcon(defaultImage);
    }

    private Object[] addUserToTable(String userId, JSONObject userInfoJson, int rowIndex) {
        String name = "";
        String cardNumber = "";
        String phoneNumber = "";
        int userType = 1;
        ImageIcon faceImageIcon = null;
        if (userInfoJson == null || !userInfoJson.containsKey("user_info")) {
            return null;
        }
        JSONObject info = userInfoJson.getJSONObject("user_info");
        if (info.containsKey("name")) {
            name = info.getString("name");
        }
        if (info.containsKey("card_number")) {
            cardNumber = info.getString("card_number");
        }
        if (info.containsKey("phone_number")) {
            phoneNumber = info.getString("phone_number");
        }
        if (info.containsKey("user_type")) {
            userType = info.getIntValue("user_type");
        }
        // 尝试获取人脸照片（如果接口返回了face_image字段）
        if (userInfoJson.containsKey("face_image")) {
            String faceImageBase64 = userInfoJson.getString("face_image");
            if (faceImageBase64 != null && !faceImageBase64.isEmpty()) {
                try {
                    byte[] imageBytes = Base64.getDecoder().decode(faceImageBase64);
                    ImageIcon originalIcon = new ImageIcon(imageBytes);
                    // 缩放图片到合适大小
                    Image scaledImage = originalIcon.getImage().getScaledInstance(IMAGE_ICON_SIZE, IMAGE_ICON_SIZE, Image.SCALE_SMOOTH);
                    faceImageIcon = new ImageIcon(scaledImage);
                } catch (Exception e) {
                    System.err.println("解析人脸照片失败: " + e.getMessage());
                }
            }
        }

        // 如果没有获取到照片，使用默认占位图标
        if (faceImageIcon == null) {
            BufferedImage defaultImage = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = defaultImage.createGraphics();
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(0, 0, 60, 60);
            g.setColor(Color.GRAY);
            g.setFont(new Font("微软雅黑", Font.PLAIN, 10));
            g.drawString("无照片", 15, 30);
            g.dispose();
            faceImageIcon = new ImageIcon(defaultImage);
        }
        String userTypeStr = userType == 1 ? "成员" : (userType == 2 ? "访客" : "黑名单");
        return new Object[]{rowIndex, faceImageIcon, userId, name, cardNumber, phoneNumber, userTypeStr};
    }

    // 从表格删除人员（通过操作列的删除按钮）
    private void deleteUser(int row) {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        String userId = userTableModel.getValueAt(row, 2).toString();
        String userName = userTableModel.getValueAt(row, 3).toString();

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要删除人员 " + userId + " (" + userName + ") 吗？",
                "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            appendResult("删除操作已取消");
            return;
        }

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("正在删除人员: " + userId);

                String response = client.deleteUser(userId);
                JSONObject result = JSONObject.parseObject(response);

                if (result.getIntValue("code") >= 0) {
                    publish("人员 " + userId + " (" + userName + ") 删除成功");
                    searchUsers();  // 刷新列表
                    clearDialogForm();
                } else {
                    publish("删除失败: " + result.get("log"));
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("删除失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    // 查询识别记录
    private void searchRecords(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                publish("正在查询识别记录...");
                // 1. 查找最新的本地数据库文件
                File localDbFile = RecognitionConfig.getLatestDbFile();
                boolean needDownload = true;

                if (localDbFile != null && localDbFile.exists()) {
                    long lastModified = localDbFile.lastModified();
                    long now = System.currentTimeMillis();
                    long age = now - lastModified;

                    publish(String.format("找到本地文件: %s, 距今 %d 分钟",
                            localDbFile.getName(), age / (60 * 1000)));

                    if (age < RecognitionConfig.FILE_VALID_DURATION) {
                        needDownload = false;
                        publish("文件未过期，直接使用本地文件");
                    } else {
                        publish("文件已过期（超过30分钟），开始下载新文件");
                    }
                } else {
                    publish("未找到本地文件，开始下载");
                }

                // 2. 如果需要下载，则从设备获取
                if (needDownload) {
                    // 删除旧文件
                    int i = RecognitionConfig.cleanOldDbFiles();
                    if (i > 0) {
                        publish("已删除 " + i + " 个过期文件");
                    }

                    // 下载新文件
                    String newFileName = System.currentTimeMillis() + ".db";
                    String localPath = RecognitionConfig.DB_DIR + newFileName;

                    publish("正在从设备下载数据库文件...");
                    int success = RecognitionConfig.downloadDbFromDevice(ipField.getText().trim(), localPath);
                    if (success == 0) {
                        localDbFile = new File(localPath);
                        publish("下载成功: " + newFileName);
                    } else {
                        publish("下载失败，请检查设备连接");
                        return null;
                    }
                }
                // 3. 处理用户参数
                String userName = recordUserNameField.getText().trim();
                // 使用传入的时间范围参数
                long startTimestamp = startDateTime != null ?
                        TimestampFormatter.parseLocalDateTimeToTimestamp(startDateTime) : 0;
                long endTimestamp = endDateTime != null ?
                        TimestampFormatter.parseLocalDateTimeToTimestamp(endDateTime) : Long.MAX_VALUE;

                List<RecognitionRecord> records = sqliteReader.queryRecognitionRecords(localDbFile.getPath(), userName, startTimestamp, endTimestamp);
                if (Objects.isNull(records)) {
                    publish("查询完成，共 " + recordTableModel.getRowCount() + " 条记录");
                    return null;
                }
                // 4.渲染表格
                displayRecordsInTable(records);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("查询失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * 显示识别记录到表格
     */
    private void displayRecordsInTable(List<RecognitionRecord> records) {
        SwingUtilities.invokeLater(() -> {
            recordTableModel.setRowCount(0);
            for (int i = 0; i < records.size(); i++) {
                RecognitionRecord record = records.get(i);
                // 通行状态文本
                String passStatusStr = getPassStatusText(record.getPassStatus());

                // 核验方式
                String verifyMethod = getVerifyMethodText(record.getVerificationResult());

                // 渲染表格
                recordTableModel.addRow(new Object[]{i + 1 + "",
                        record.getReadableTime() != null ? record.getReadableTime() : "",
                        record.getUserId() != null ? record.getUserId() : "",
                        record.getUserName() != null ? record.getUserName() : "",
                        verifyMethod,
                        passStatusStr,
                        "同步",
                });
            }

            appendResult("表格已更新，共显示 " + records.size() + " 条记录");
        });
    }

    /**
     * 获取通行状态文本（常用状态精简版）
     */
    private String getPassStatusText(int status) {
        switch (status) {
            case 1:
                return "已通行";
            case -1:
                return "未通行";
            default:
                return status < 0 ? "识别失败" : "未知";
        }
    }

    /**
     * 获取核验方式文本
     */
    private String getVerifyMethodText(Integer verifyResult) {
        if (verifyResult == null) {
            return "";
        }
        switch (verifyResult) {
            case 1:
                return "刷脸";
            case 2:
                return "刷卡";
            case 3:
                return "刷身份证";
            case 4:
                return "刷脸+刷卡";
            case 5:
                return "刷脸+身份证";
            case 6:
                return "二维码";
            default:
                return String.valueOf(verifyResult);
        }
    }

    // 同步记录到管理平台
    private void syncRecordToPlatform(int row) {
        String time = recordTableModel.getValueAt(row, 0).toString();
        String userId = recordTableModel.getValueAt(row, 1).toString();
        String userName = recordTableModel.getValueAt(row, 2).toString();
        String verifyMethod = recordTableModel.getValueAt(row, 3).toString();
        String passStatus = recordTableModel.getValueAt(row, 4).toString();
        String temperature = recordTableModel.getValueAt(row, 5).toString();

        appendResult(String.format("正在同步识别记录到管理平台: [%s] %s(%s) - %s - %s - %s",
                time, userName, userId, verifyMethod, passStatus, temperature));

        // TODO: 实现同步到管理平台的HTTP请求
        // 需要您提供管理平台的API地址和格式
        appendResult("同步功能待实现，请提供管理平台API地址和接口格式");

        // 同步代码示例:
    /*
    String apiUrl = "http://your-platform.com/api/syncRecord";
    JSONObject data = new JSONObject();
    data.put("time", time);
    data.put("userId", userId);
    data.put("userName", userName);
    data.put("verifyMethod", verifyMethod);
    data.put("passStatus", passStatus);
    data.put("temperature", temperature);

    SwingWorker<Void, String> syncWorker = new SwingWorker<Void, String>() {
        @Override
        protected Void doInBackground() throws Exception {
            // 发送HTTP请求
            return null;
        }

        @Override
        protected void done() {
            appendResult("同步完成");
        }
    };
    syncWorker.execute();
    */
    }

    // ==================== CSV导出导入 ====================

    private void exportUsersToCSV() {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("人员信息_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv"));

        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File saveFile = fileChooser.getSelectedFile();
        if (!saveFile.getName().toLowerCase().endsWith(".csv")) {
            saveFile = new File(saveFile.getAbsolutePath() + ".csv");
        }

        final File finalFile = saveFile;

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("开始导出人员信息...");

                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(finalFile), "UTF-8"))) {
                    writer.println("人员ID,姓名,编号,电话号码,人员类型");

                    int rowCount = userTableModel.getRowCount();
                    for (int i = 0; i < rowCount; i++) {
                        String userId = userTableModel.getValueAt(i, 2).toString();
                        String name = userTableModel.getValueAt(i, 3).toString();
                        String cardNum = userTableModel.getValueAt(i, 4).toString();
                        String phone = userTableModel.getValueAt(i, 5).toString();
                        String type = userTableModel.getValueAt(i, 6).toString();

                        writer.printf("%s,%s,%s,%s,%s%n",
                                sqliteReader.escapeCsvField(userId), sqliteReader.escapeCsvField(name),
                                sqliteReader.escapeCsvField(cardNum), sqliteReader.escapeCsvField(phone), sqliteReader.escapeCsvField(type));

                        if ((i + 1) % 100 == 0) {
                            publish(String.format("已导出 %d/%d 条记录", i + 1, rowCount));
                        }
                    }
                }

                publish(String.format("导出完成！共导出 %d 条记录到: %s",
                        userTableModel.getRowCount(), finalFile.getAbsolutePath()));
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("导出失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    // 检测文件编码
    private Charset detectCharset(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[4];
            int read = is.read(buffer);
            if (read >= 3 && buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
                return StandardCharsets.UTF_8; // UTF-8 BOM
            } else if (read >= 2 && buffer[0] == (byte) 0xFE && buffer[1] == (byte) 0xFF) {
                return StandardCharsets.UTF_16BE; // UTF-16 BE
            } else if (read >= 2 && buffer[0] == (byte) 0xFF && buffer[1] == (byte) 0xFE) {
                return StandardCharsets.UTF_16LE; // UTF-16 LE
            }
        }
        // 默认尝试GBK（中文Windows系统常用编码）
        return Charset.forName("GBK");
    }

    private void importUsersFromCSV() {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择人员信息CSV文件");

        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File csvFile = fileChooser.getSelectedFile();

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            private int successCount = 0;
            private int failCount = 0;
            private int totalCount = 0;

            @Override
            protected Void doInBackground() throws Exception {
                publish("开始解析CSV文件: " + csvFile.getName());
                Charset charset = detectCharset(csvFile);
                List<CsvRecord> records = new ArrayList<>();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(Files.newInputStream(csvFile.toPath()), charset))) {
                    String line;
                    boolean isFirstLine = true;
                    int lineNum = 0;

                    while ((line = reader.readLine()) != null) {
                        lineNum++;
                        if (line.trim().isEmpty()) {
                            continue;
                        }

                        List<String> fields = sqliteReader.parseCsvLine(line);

                        if (isFirstLine) {
                            isFirstLine = false;
                            if (fields.size() > 0 && fields.get(0).contains("人员ID")) {
                                publish("跳过表头行");
                                continue;
                            }
                        }

                        if (fields.size() < 2) {
                            publish("第 " + lineNum + " 行格式错误，跳过");
                            continue;
                        }

                        String userId = fields.get(0).trim();
                        String name = fields.get(1).trim();
                        String cardNumber = fields.size() > 2 ? fields.get(2).trim() : "";
                        String phone = fields.size() > 3 ? fields.get(3).trim() : "";

                        if (userId.isEmpty()) {
                            publish("第 " + lineNum + " 行人员ID为空，跳过");
                            continue;
                        }

                        if (!cardNumber.isEmpty() && !cardNumber.equals("\"\"") && !cardNumber.equals("null")) {
                            CsvRecord record = new CsvRecord();
                            record.userId = userId;
                            record.name = name;
                            record.cardNumber = cardNumber;
                            record.phone = phone;
                            records.add(record);
                        }
                    }
                }

                totalCount = records.size();
                publish(String.format("解析完成，共 %d 条记录需要更新编号", totalCount));

                if (totalCount == 0) {
                    return null;
                }

                int confirm = JOptionPane.showConfirmDialog(null,
                        String.format("即将更新 %d 名人员的编号信息，是否继续？", totalCount),
                        "确认导入", JOptionPane.YES_NO_OPTION);

                if (confirm != JOptionPane.YES_OPTION) {
                    publish("导入已取消");
                    return null;
                }

                for (int i = 0; i < records.size(); i++) {
                    CsvRecord record = records.get(i);
                    publish(String.format("更新 [%d/%d] 人员: %s - %s",
                            i + 1, totalCount, record.userId, record.name));

                    try {
                        String response = client.getUserInfo(record.userId);
                        JSONObject json = JSONObject.parseObject(response);

                        if (json.getIntValue("code") < 0) {
                            failCount++;
                            publish("人员 " + record.userId + " 不存在");
                            continue;
                        }

                        JSONObject userInfo = new JSONObject();
                        if (json.containsKey("user_info")) {
                            userInfo = JSONObject.parseObject(json.getString("user_info"));
                        } else {
                            userInfo.put("user_type", 1);
                            userInfo.put("name", record.name.isEmpty() ? "未知" : record.name);
                        }
                        userInfo.put("card_number", record.cardNumber);
                        userInfo.put("phone_number", record.phone);
                        String updateResponse = client.saveOrUpdateUser(record.userId, null, userInfo,
                                null, null);
                        JSONObject result = JSONObject.parseObject(updateResponse);

                        if (result.getIntValue("code") >= 0) {
                            successCount++;
                            publish("✓ " + record.userId + " 编号更新成功");
                        } else {
                            failCount++;
                            publish("✗ " + record.userId + " 更新失败: " + result.getString("log"));
                        }

                    } catch (Exception e) {
                        failCount++;
                        publish("✗ " + record.userId + " 更新异常: " + e.getMessage());
                    }

                    Thread.sleep(100);
                }

                publish(String.format("导入完成！成功: %d, 失败: %d", successCount, failCount));
                searchUsers();

                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    appendResult(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    appendResult("导入失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new DumuSwingApp().setVisible(true);
        });
    }
}