package com.cscec.dumu;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

public class DumuSwingApp extends JFrame {
    private JTextArea resultArea;
    private JTextField ipField;
    private JTextField portField;
    private JPasswordField passwordField;
    private DumuClient client;
    private JLabel statusLabel;

    // 人员管理组件
    private JTextField searchUserIdField;
    private JTextField searchUserNameField;
    private JTable userTable;
    private DefaultTableModel userTableModel;
    private JTextField userIdField;
    private JTextField userNameField;
    private JTextField userIdCardField;
    private JTextField userPhoneField;
    private JComboBox<String> userTypeCombo;
    private String selectedImageBase64 = null;  // 存储选中图片的Base64编码
    private JLabel imagePathLabel;

    private DefaultTableModel recordTableModel;

    // 当前显示的人员列表数据（用于同步）
    private final List<JSONObject> currentUserList = new ArrayList<>();

    public DumuSwingApp() {
        setTitle("度目智能门禁机管理客户端");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 800);
        setLocationRelativeTo(null);
        initUI();
        loadConfig();
    }

    private void initUI() {
        setLayout(new BorderLayout());

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

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("设备连接配置"));

        panel.add(new JLabel("设备IP:"));
        ipField = new JTextField(15);
        panel.add(ipField);

        panel.add(new JLabel("端口:"));
        portField = new JTextField("8080", 6);
        panel.add(portField);

        panel.add(new JLabel("密码:"));
        passwordField = new JPasswordField(10);
        panel.add(passwordField);

        JButton connectBtn = new JButton("连接设备");
        connectBtn.addActionListener(e -> connectDevice());
        panel.add(connectBtn);

        JButton saveConfigBtn = new JButton("保存配置");
        saveConfigBtn.addActionListener(e -> saveConfig());
        panel.add(saveConfigBtn);

        statusLabel = new JLabel("未连接");
        statusLabel.setForeground(Color.RED);
        panel.add(statusLabel);

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

        JButton refreshUserBtn = new JButton("刷新全部");
        refreshUserBtn.addActionListener(e -> searchUsers());
        searchPanel.add(refreshUserBtn);

        JButton exportBtn = new JButton("一键导出人员信息");
        exportBtn.addActionListener(e -> exportUsersToCSV());
        searchPanel.add(exportBtn);

        JButton importBtn = new JButton("一键导入人员编号");
        importBtn.addActionListener(e -> importUsersFromCSV());
        searchPanel.add(importBtn);

        panel.add(searchPanel, BorderLayout.NORTH);

        // 人员表格 - 添加操作列
        String[] columns = {"序号", "人脸照片", "人员ID", "姓名", "编号", "电话号码", "人员类型", "操作"};
        userTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 7;  // 只有操作列可编辑
            }
        };
        userTable = new JTable(userTableModel) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 1) {
                    return ImageIcon.class;  // 人脸照片列显示图片
                }
                if (column == 7) {
                    return JButton.class;    // 操作列显示按钮
                }
                return String.class;
            }
        };
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.getColumnModel().getColumn(0).setMaxWidth(50);   // 序号列宽60
        userTable.getColumnModel().getColumn(1).setMaxWidth(80);   // 人脸照片列宽80
        userTable.getColumnModel().getColumn(1).setMinWidth(80);
        userTable.setRowHeight(60);  // 增加行高以显示照片
        // 设置操作列渲染器和编辑器
        userTable.getColumn("操作").setCellRenderer(new UserButtonRenderer());
        userTable.getColumn("操作").setCellEditor(new UserButtonEditor(new JCheckBox()));

        // 添加鼠标点击事件，用于选中行
        userTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = userTable.rowAtPoint(e.getPoint());
                int col = userTable.columnAtPoint(e.getPoint());
                if (col != 7) {  // 如果不是操作列，则选中该行加载到表单
                    userTable.setRowSelectionInterval(row, row);
                    loadSelectedUserToForm();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(userTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("人员列表"));
        panel.add(tableScroll, BorderLayout.CENTER);

        // 编辑区域
        JPanel editPanel = new JPanel(new GridBagLayout());
        editPanel.setBorder(BorderFactory.createTitledBorder("人员编辑"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 第一行：人员ID、姓名、人员类型
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        editPanel.add(new JLabel("人员ID:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        userIdField = new JTextField();
        userIdField.setEditable(false);
        editPanel.add(userIdField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        editPanel.add(new JLabel("姓名:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 1;
        userNameField = new JTextField();
        editPanel.add(userNameField, gbc);

        gbc.gridx = 4;
        gbc.weightx = 0;
        editPanel.add(new JLabel("人员类型:"), gbc);

        gbc.gridx = 5;
        gbc.weightx = 1;
        userTypeCombo = new JComboBox<>(new String[]{"成员", "访客", "黑名单"});
        editPanel.add(userTypeCombo, gbc);

        // 第二行：编号/卡号、电话号码
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        editPanel.add(new JLabel("编号:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        userIdCardField = new JTextField();
        editPanel.add(userIdCardField, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        editPanel.add(new JLabel("电话号码:"), gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        userPhoneField = new JTextField();
        editPanel.add(userPhoneField, gbc);

        // 第二行后面两个位置留空，保持布局平衡
        gbc.gridx = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        editPanel.add(new JPanel(), gbc);

        // 第三行：人脸图片选择
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        editPanel.add(new JLabel("人脸图片:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1;
        JPanel imagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        imagePanel.setOpaque(false);
        imagePathLabel = new JLabel("未选择图片");
        imagePathLabel.setForeground(Color.GRAY);
        JButton selectImageBtn = new JButton("选择图片");
        selectImageBtn.addActionListener(e -> selectImageFile());
        imagePanel.add(selectImageBtn);
        imagePanel.add(imagePathLabel);
        editPanel.add(imagePanel, gbc);

        // 占位
        gbc.gridx = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        editPanel.add(new JPanel(), gbc);

        // 第四行：操作按钮
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 6;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.CENTER;

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));

        JButton updateBtn = new JButton("新增/编辑人员");
        updateBtn.setPreferredSize(new Dimension(120, 30));
        updateBtn.addActionListener(e -> saveOrUpdateUser());
        btnPanel.add(updateBtn);

        JButton clearBtn = new JButton("清空表单");
        clearBtn.setPreferredSize(new Dimension(100, 30));
        clearBtn.addActionListener(e -> clearUserForm());
        btnPanel.add(clearBtn);

        editPanel.add(btnPanel, gbc);

        panel.add(editPanel, BorderLayout.SOUTH);
        return panel;
    }

    // 选择图片文件并转换为Base64
    private void selectImageFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择人员照片");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "图片文件 (*.jpg)", "jpg"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File imageFile = fileChooser.getSelectedFile();
            try {
                // 读取图片并转换为Base64
                byte[] imageBytes = new byte[(int) imageFile.length()];
                try (FileInputStream fis = new FileInputStream(imageFile)) {
                    fis.read(imageBytes);
                }
                selectedImageBase64 = Base64.getEncoder().encodeToString(imageBytes);
                selectedImageBase64 = selectedImageBase64.replaceAll("\\s", "");
                // 显示文件信息
                String fileInfo = imageFile.getName() + " (" + (imageBytes.length / 1024) + " KB)";
                imagePathLabel.setText(fileInfo);
                imagePathLabel.setForeground(Color.BLACK);
                appendResult("已选择图片: " + fileInfo);
            } catch (Exception e) {
                appendResult("读取图片失败: " + e.getMessage());
                selectedImageBase64 = null;
                imagePathLabel.setText("未选择图片");
                imagePathLabel.setForeground(Color.GRAY);
            }
        }
    }

    private JPanel createRecordPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 查询区域
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("识别记录查询"));

        searchPanel.add(new JLabel("人员姓名:"));
        // 识别记录组件
        JTextField recordUserNameField = new JTextField(10);
        searchPanel.add(recordUserNameField);

        searchPanel.add(new JLabel("开始时间:"));
        JTextField recordStartTimeField = new JTextField(15);
        recordStartTimeField.setToolTipText("格式: 2024-01-01 或 2024-01-01 00:00:00");
        searchPanel.add(recordStartTimeField);

        searchPanel.add(new JLabel("结束时间:"));
        JTextField recordEndTimeField = new JTextField(15);
        recordEndTimeField.setToolTipText("格式: 2024-12-31 或 2024-12-31 23:59:59");
        searchPanel.add(recordEndTimeField);

        JButton searchRecordBtn = new JButton("查询");
        searchRecordBtn.addActionListener(e -> searchRecords());
        searchPanel.add(searchRecordBtn);

        JButton refreshRecordBtn = new JButton("刷新");
        refreshRecordBtn.addActionListener(e -> searchRecords());
        searchPanel.add(refreshRecordBtn);

        panel.add(searchPanel, BorderLayout.NORTH);

        // 识别记录表格
        String[] columns = {"时间", "人员ID", "姓名", "核验方式", "通行状态", "体温", "操作"};
        recordTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6;  // 只有操作列可编辑
            }
        };

        JTable recordTable = new JTable(recordTableModel);
        recordTable.setRowHeight(45);  // 增加行高以容纳两个按钮

        // 设置操作列的自定义渲染器和编辑器
        recordTable.getColumn("操作").setCellRenderer(new RecordButtonRenderer());
        recordTable.getColumn("操作").setCellEditor(new RecordButtonEditor());

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

    // 删除本地识别记录
    private void deleteRecordFromTable(int row) {
        String time = recordTableModel.getValueAt(row, 0).toString();
        String userName = recordTableModel.getValueAt(row, 2).toString();

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要删除识别记录吗？\n时间: " + time + "\n人员: " + userName,
                "确认删除",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            recordTableModel.removeRow(row);
            appendResult("已删除识别记录: " + time + " - " + userName);
        } else {
            appendResult("删除操作已取消");
        }
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

        searchUsers();
    }

    private void loadConfig() {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream("config.properties")) {
            props.load(input);
            ipField.setText(props.getProperty("device.ip", "192.168.20.225"));
            portField.setText(props.getProperty("device.port", "8080"));
            passwordField.setText(props.getProperty("device.password", "Zjbj@123"));
        } catch (IOException e) {
            ipField.setText("192.168.20.225");
            portField.setText("8080");
            passwordField.setText("Zjbj@123");
        }
    }

    private void saveConfig() {
        Properties props = new Properties();
        props.setProperty("device.ip", ipField.getText().trim());
        props.setProperty("device.port", portField.getText().trim());
        props.setProperty("device.password", new String(passwordField.getPassword()));

        try (OutputStream output = new FileOutputStream("config.properties")) {
            props.store(output, "Device Configuration");
            appendResult("配置已保存");
        } catch (IOException e) {
            appendResult("保存配置失败: " + e.getMessage());
        }
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

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("正在查询人员列表...");

                userTableModel.setRowCount(0);
                currentUserList.clear();

                if (!searchId.isEmpty()) {
                    String response = client.getUserInfo(searchId);
                    JSONObject json = JSONObject.parseObject(response);
                    if (json.getIntValue("code") >= 0) {
                        addUserToTable(searchId, json, 1);
                    } else {
                        publish("未找到人员: " + searchId);
                    }
                } else {
                    String response = client.getUserList(0, 9999);
                    JSONObject json = JSONObject.parseObject(response);

                    if (json.getIntValue("code") >= 0 && json.containsKey("user_id_list")) {
                        JSONArray userIdList = json.getJSONArray("user_id_list");
                        int total = userIdList.size();
                        publish("共找到 " + total + " 名人员");
                        for (int i = 0; i < userIdList.size(); i++) {
                            String userId = (String) userIdList.get(i);
                            String userInfoResponse = client.getUserInfo(userId);
                            JSONObject userInfoJson = JSONObject.parseObject(userInfoResponse);

                            if (userInfoJson.getIntValue("code") >= 0) {
                                String name = "";
                                if (userInfoJson.containsKey("user_info") && userInfoJson.getJSONObject("user_info").containsKey("name")) {
                                    name = userInfoJson.getJSONObject("user_info").getString("name");
                                }
                                if (!searchName.isEmpty() && !name.contains(searchName)) {
                                    continue;
                                }
                                addUserToTable(userId, userInfoJson, i + 1);
                            }
                            Thread.sleep(50);
                        }
                    }
                }

                publish("人员列表加载完成，共 " + userTableModel.getRowCount() + " 条记录");
                return null;
            }

            private void addUserToTable(String userId, JSONObject userInfoJson, int rowIndex) {
                String name = "";
                String cardNumber = "";
                String phoneNumber = "";
                int userType = 1;
                ImageIcon faceImageIcon = null;
                if (userInfoJson.containsKey("user_info")) {
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
                }
                // 尝试获取人脸照片（如果接口返回了face_image字段）
                if (userInfoJson.containsKey("face_image")) {
                    String faceImageBase64 = userInfoJson.getString("face_image");
                    if (faceImageBase64 != null && !faceImageBase64.isEmpty()) {
                        try {
                            byte[] imageBytes = Base64.getDecoder().decode(faceImageBase64);
                            ImageIcon originalIcon = new ImageIcon(imageBytes);
                            // 缩放图片到合适大小
                            Image scaledImage = originalIcon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
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
                userTableModel.addRow(new Object[]{rowIndex, faceImageIcon, userId, name, cardNumber, phoneNumber, userTypeStr});
                currentUserList.add(userInfoJson);
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

    private void loadSelectedUserToForm() {
        int row = userTable.getSelectedRow();
        if (row >= 0) {
            userIdField.setText(userTableModel.getValueAt(row, 2).toString());
            userNameField.setText(userTableModel.getValueAt(row, 3).toString());
            userIdCardField.setText(userTableModel.getValueAt(row, 4).toString());
            userPhoneField.setText(userTableModel.getValueAt(row, 5).toString());
            String type = userTableModel.getValueAt(row, 6).toString();
            if ("成员".equals(type)) {
                userTypeCombo.setSelectedIndex(0);
            } else if ("访客".equals(type)) {
                userTypeCombo.setSelectedIndex(1);
            } else {
                userTypeCombo.setSelectedIndex(2);
            }
        }
    }

    private void clearUserForm() {
        userIdField.setText("");
        userNameField.setText("");
        userIdCardField.setText("");
        userPhoneField.setText("");
        userTypeCombo.setSelectedIndex(0);
        userTable.clearSelection();
        // 清空图片选择
        selectedImageBase64 = null;
        if (imagePathLabel != null) {
            imagePathLabel.setText("未选择图片");
            imagePathLabel.setForeground(Color.GRAY);
        }
    }

    private void saveOrUpdateUser() {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        String cardNumber = userIdCardField.getText().trim();
        if (cardNumber.isEmpty()) {
            appendResult("编号不能为空");
            return;
        }
        // 如果人员ID为空（新增时），自动设置为编号的值
        String userId = userIdField.getText().trim();
        if (userId.isEmpty()) {
            userId = cardNumber;
            userIdField.setText(cardNumber);
        }

        String name = userNameField.getText().trim();
        if (name.isEmpty()) {
            appendResult("姓名不能为空");
            return;
        }

        final String imageBase64 = selectedImageBase64;

        String finalUserId = userId;
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("正在新增/更新人员: " + finalUserId);

                JSONObject userInfo = new JSONObject();
                userInfo.put("user_type", userTypeCombo.getSelectedIndex() + 1);
                userInfo.put("name", name);
                userInfo.put("card_number", cardNumber);
                userInfo.put("phone_number", userPhoneField.getText().trim());

                // 如果选择了新图片，则更新图片
                String response = client.saveOrUpdateUser(finalUserId, imageBase64, userInfo, null, null);
                JSONObject result = JSONObject.parseObject(response);

                if (result.getIntValue("code") >= 0) {
                    publish("人员 " + finalUserId + " 新增/更新成功");
                    searchUsers();
                } else {
                    publish("新增/更新失败: " + result.get("log"));
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
                    appendResult("新增/更新失败: " + e.getMessage());
                }
            }
        };
        worker.execute();
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
                    clearUserForm();
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
    private void searchRecords() {
        if (client == null) {
            appendResult("请先连接设备!");
            return;
        }

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("正在查询识别记录...");
                recordTableModel.setRowCount(0);

                publish("注意：识别记录需要通过设备回调接口获取");
                publish("请确保已设置识别记录回调地址，设备会将识别记录推送到您的服务器");

                // 示例数据 - 注意只添加6列数据，第7列(操作列)留空
                String[] sampleRecords = {
                        "2024-01-15 08:30:25,user001,张三,刷脸,已通行,36.5",
                        "2024-01-15 08:35:12,user002,李四,刷卡,已通行,36.3",
                        "2024-01-15 08:40:03,user003,王五,刷脸,体温异常,37.8",
                        "2024-01-15 08:45:30,user004,赵六,二维码,已通行,36.4",
                        "2024-01-15 08:50:15,user005,钱七,刷脸,黄码,36.2",
                        "2024-01-15 08:55:22,user006,孙八,编号,已通行,36.4"
                };

                for (String record : sampleRecords) {
                    String[] parts = record.split(",");
                    // 只添加6列数据，操作列不添加任何值（由Renderer负责显示按钮）
                    recordTableModel.addRow(new Object[]{parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]});
                }

                publish("查询完成，共 " + recordTableModel.getRowCount() + " 条记录");
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
                                escapeCsvField(userId), escapeCsvField(name),
                                escapeCsvField(cardNum), escapeCsvField(phone), escapeCsvField(type));

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

                        List<String> fields = parseCsvLine(line);

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

    // ==================== 内部类 ====================

    private static class CsvRecord {
        String userId;
        String name;
        String cardNumber;
        String phone;
    }

    private String escapeCsvField(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            field = field.replace("\"", "\"\"");
            return "\"" + field + "\"";
        }
        return field;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString());

        for (int i = 0; i < fields.size(); i++) {
            String f = fields.get(i);
            if (f.startsWith("\"") && f.endsWith("\"") && f.length() >= 2) {
                f = f.substring(1, f.length() - 1);
                f = f.replace("\"\"", "\"");
                fields.set(i, f);
            }
            fields.set(i, fields.get(i).trim());
        }

        return fields;
    }

    // 人员表格操作列按钮渲染器
    class UserButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public UserButtonRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                                boolean isSelected, boolean hasFocus, int row, int column) {
            setText("删除");
            return this;
        }
    }

    // 人员表格操作列按钮编辑器
    class UserButtonEditor extends DefaultCellEditor {
        protected JButton button;
        private int clickedRow;

        public UserButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> {
                fireEditingStopped();
                if (clickedRow >= 0) {
                    deleteUser(clickedRow);
                }
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                              boolean isSelected, int row, int column) {
            clickedRow = row;
            button.setText("删除");
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return "删除";
        }
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