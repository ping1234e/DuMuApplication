package com.cscec.dumu.util;

import com.cscec.dumu.entity.RecognitionRecord;
import org.apache.commons.lang.StringUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteReader {

    // 连接 SQLite 数据库
    private Connection connect(String dbPath) {
        String url = "jdbc:sqlite:" + dbPath;
        Connection conn = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(url);
            System.out.println("成功连接到数据库: " + dbPath);
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC 驱动未找到: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("数据库连接失败: " + e.getMessage());
        }
        return conn;
    }

    /**
     * 带条件查询
     *
     * @param dbPath
     * @param userName
     * @param startTimestamp
     * @param endTimestamp
     * @return
     */
    public List<RecognitionRecord> queryRecognitionRecords(String dbPath, String userName, long startTimestamp, long endTimestamp) {
        List<RecognitionRecord> records = new ArrayList<>();
        // 使用 ? 作为参数占位符
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM recog_logging WHERE pass_status = 1 ");
        sql.append("AND ts >= ? AND ts <= ? ");

        if (StringUtils.isNotBlank(userName)) {
            sql.append("AND name LIKE ? ");
        }
        sql.append("ORDER BY ts DESC");
        try (Connection conn = connect(dbPath);
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            // 设置参数
            pstmt.setLong(1, startTimestamp);
            pstmt.setLong(2, endTimestamp);

            if (StringUtils.isNotBlank(userName)) {
                // 模糊查询需要手动添加 % 通配符
                pstmt.setString(3, "%" + userName + "%");
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    RecognitionRecord record = new RecognitionRecord();
                    record.setId(rs.getLong("idx"));
                    record.setUserId(rs.getString("uuid"));
                    record.setUserName(rs.getString("name"));
                    record.setCardNumber(rs.getString("card_num"));
                    record.setPassStatus(rs.getInt("pass_status"));
                    record.setVerificationResult(rs.getInt("verification_result"));
                    record.setRecordType(rs.getInt("record_type"));
                    record.setImage(rs.getBytes("image"));
                    record.setTimestamp(rs.getLong("ts"));

                    if (record.getTimestamp() > 0) {
                        record.setReadableTime(TimestampFormatter.formatAuto(record.getTimestamp()));
                    }

                    records.add(record);
                }
            }
            System.out.println("共读取 " + records.size() + " 条识别记录");

        } catch (SQLException e) {
            System.err.println("查询失败: " + e.getMessage());
            e.printStackTrace();
        }

        return records;
    }

    public List<String> parseCsvLine(String line) {
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

    public String escapeCsvField(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            field = field.replace("\"", "\"\"");
            return "\"" + field + "\"";
        }
        return field;
    }
}