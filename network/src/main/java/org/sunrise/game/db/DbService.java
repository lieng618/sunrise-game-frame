package org.sunrise.game.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sunrise.game.config.ConfigReader;

import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DbService {

    private final HikariDataSource dataSource;
    private final ExecutorService dbExecutor;

    public DbService() {
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            properties = getDefaultDbConfig();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getProperty("jdbc.url"));
        config.setUsername(properties.getProperty("jdbc.user"));
        config.setPassword(properties.getProperty("jdbc.password"));
        config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("jdbc.maximumPoolSize")));
        config.setMinimumIdle(Integer.parseInt(properties.getProperty("jdbc.minimumIdle")));
        config.setConnectionTimeout(Long.parseLong(properties.getProperty("jdbc.connectionTimeout")));
        config.setIdleTimeout(Long.parseLong(properties.getProperty("jdbc.idleTimeout")));
        config.setMaxLifetime(Long.parseLong(properties.getProperty("jdbc.maxLifetime")));

        dataSource = new HikariDataSource(config);
        dbExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    // 获取默认数据库配置（当配置未加载时使用）
    private Properties getDefaultDbConfig() {
        Properties prop = new Properties();
        prop.setProperty("jdbc.url", "jdbc:mysql://127.0.0.1:3306/sunrise");
        prop.setProperty("jdbc.user", "root");
        prop.setProperty("jdbc.password", "123456");
        prop.setProperty("jdbc.maximumPoolSize", "5");
        prop.setProperty("jdbc.minimumIdle", "3");
        prop.setProperty("jdbc.connectionTimeout", "30000");
        prop.setProperty("jdbc.idleTimeout", "600000");
        prop.setProperty("jdbc.maxLifetime", "1800000");
        return prop;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // 同步：根据id查询单条记录
    public Map<String, Object> queryById(String sql, int id) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return extractResultSet(resultSet);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 异步：根据id查询单条记录并使用回调
    public void queryByIdAsync(Consumer<Map<String, Object>> callback, String sql, int id) {
        CompletableFuture.supplyAsync(() -> queryById(sql, id), dbExecutor)
                .thenAccept(callback);
    }

    // 同步：根据参数查询单条记录
    public Map<String, Object> queryGetOneByParams(String sql, Object... params) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return extractResultSet(resultSet);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 异步：根据参数查询单条记录并使用回调
    public void queryGetOneByParamsAsync(Consumer<Map<String, Object>> callback, String sql, Object... params) {
        CompletableFuture.supplyAsync(() -> queryGetOneByParams(sql, params), dbExecutor)
                .thenAccept(callback);
    }

    // 同步：根据参数查询所有记录
    public List<Map<String, Object>> queryGetAllByParams(String sql, Object... params) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    resultList.add(extractResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return resultList;
    }

    // 异步：根据参数查询所有记录并使用回调
    public void queryGetAllByParamsAsync(Consumer<List<Map<String, Object>>> callback, String sql, Object... params) {
        CompletableFuture.supplyAsync(() -> queryGetAllByParams(sql, params), dbExecutor)
                .thenAccept(callback);
    }

    // 同步：查询所有记录
    public List<Map<String, Object>> queryAll(String sql) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                resultList.add(extractResultSet(resultSet));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return resultList;
    }

    // 异步：查询所有记录并使用回调
    public void queryAllAsync(Consumer<List<Map<String, Object>>> callback, String sql) {
        CompletableFuture.supplyAsync(() -> queryAll(sql), dbExecutor)
                .thenAccept(callback);
    }

    // 同步：查询单列
    public List<String> queryAllSingleColumn(String sql) {
        List<String> results = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(resultSet.getString(1)); // 获取第一列的值
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    // 异步：查询单列并使用回调
    public void queryAllSingleColumnAsync(Consumer<List<String>> callback, String sql) {
        CompletableFuture.supplyAsync(() -> queryAllSingleColumn(sql), dbExecutor).thenAccept(callback);
    }

    // 同步：执行更新
    public void execute(String sql, Object... params) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 异步：执行更新并使用回调
    public void executeAsync(Runnable callback, String sql, Object... params) {
        CompletableFuture.runAsync(() -> {
            execute(sql, params);
            callback.run(); // 在执行完更新操作后，调用回调函数
        }, dbExecutor);
    }

    // 异步：执行更新
    public void executeAsync(String sql, Object... params) {
        CompletableFuture.runAsync(() -> execute(sql, params), dbExecutor);
    }

    // 同步：执行更新并返回生成的主键
    public Object executeWithGeneratedKey(String sql, Object... params) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();

            // 获取生成的主键
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getObject(1); // 返回生成的主键，可以是任意类型
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null; // 如果没有生成的主键，则返回null
    }

    // 异步：执行更新并返回生成的主键
    public void executeAsyncWithGeneratedKey(Consumer<Object> callback, String sql, Object... params) {
        CompletableFuture.supplyAsync(() -> executeWithGeneratedKey(sql, params), dbExecutor).thenAccept(generatedKey -> {
            if (generatedKey instanceof BigInteger) {
                callback.accept(((BigInteger) generatedKey).longValue());
            } else {
                callback.accept(generatedKey);
            }
        });
    }

    // 提取结果集
    private Map<String, Object> extractResultSet(ResultSet resultSet) throws SQLException {
        Map<String, Object> rowMap = new HashMap<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            rowMap.put(metaData.getColumnName(i), resultSet.getObject(i));
        }
        return rowMap;
    }
}