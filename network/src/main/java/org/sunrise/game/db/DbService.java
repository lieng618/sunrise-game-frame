package org.sunrise.game.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.log.LogCore;

import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DbService {

    private static final long EXECUTOR_DRAIN_TIMEOUT_SECONDS = 30L;
    private static final long EXECUTOR_FORCE_TIMEOUT_SECONDS = 5L;
    private final HikariDataSource dataSource;
    private final ExecutorService dbExecutor;
    private final AtomicInteger pendingAsyncTasks = new AtomicInteger();
    private final Object pendingAsyncTasksMonitor = new Object();

    public DbService() {
        Properties properties = ConfigReader.getProp();
        if (properties == null) {
            properties = getDefaultDbConfig();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getProperty("jdbc.url"));
        config.setUsername(properties.getProperty("jdbc.user"));
        config.setPassword(properties.getProperty("jdbc.password"));
        config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("jdbc.pool.maximum-size")));
        config.setMinimumIdle(Integer.parseInt(properties.getProperty("jdbc.pool.minimum-idle")));
        config.setConnectionTimeout(Long.parseLong(properties.getProperty("jdbc.pool.connection-timeout")));
        config.setIdleTimeout(Long.parseLong(properties.getProperty("jdbc.pool.idle-timeout")));
        config.setMaxLifetime(Long.parseLong(properties.getProperty("jdbc.pool.max-lifetime")));

        dataSource = new HikariDataSource(config);
        dbExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("DbServiceTaskThreadPool-" + counter.incrementAndGet());
                thread.setDaemon(false);
                return thread;
            }
        });
    }

    // 获取默认数据库配置（当配置未加载时使用）
    private Properties getDefaultDbConfig() {
        Properties prop = new Properties();
        prop.setProperty("jdbc.url", "jdbc:mysql://127.0.0.1:3306/sunrise");
        prop.setProperty("jdbc.user", "root");
        prop.setProperty("jdbc.password", "123456");
        prop.setProperty("jdbc.pool.maximum-size", "5");
        prop.setProperty("jdbc.pool.minimum-idle", "3");
        prop.setProperty("jdbc.pool.connection-timeout", "30000");
        prop.setProperty("jdbc.pool.idle-timeout", "600000");
        prop.setProperty("jdbc.pool.max-lifetime", "1800000");
        return prop;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** 异步链路的异常若无人消费即被静默吞掉，且 thenAccept 的回调不会执行，必须在此记录 */
    private static void logAsyncError(Throwable e) {
        if (e != null) {
            LogCore.DB.error("Async db operation failed: {}", e.getMessage(), e);
        }
    }

    private void runTrackedAsync(Supplier<CompletableFuture<?>> taskStarter) {
        pendingAsyncTasks.incrementAndGet();
        try {
            taskStarter.get().whenComplete((result, error) -> {
                try {
                    logAsyncError(error);
                } finally {
                    completePendingTask();
                }
            });
        } catch (RuntimeException e) {
            completePendingTask();
            throw e;
        }
    }

    private void completePendingTask() {
        if (pendingAsyncTasks.decrementAndGet() == 0) {
            synchronized (pendingAsyncTasksMonitor) {
                pendingAsyncTasksMonitor.notifyAll();
            }
        }
    }

    /**
     * 等待当前已经提交的异步任务及其回调全部结束，但不关闭 executor。
     */
    public boolean awaitAsyncTasks(long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        synchronized (pendingAsyncTasksMonitor) {
            while (pendingAsyncTasks.get() > 0) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(pendingAsyncTasksMonitor, remainingNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for async DB tasks", e);
                }
            }
        }
        return true;
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
        runTrackedAsync(() -> CompletableFuture
                .supplyAsync(() -> queryById(sql, id), dbExecutor)
                .thenAccept(callback));
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
        runTrackedAsync(() -> CompletableFuture
                .supplyAsync(() -> queryGetOneByParams(sql, params), dbExecutor)
                .thenAccept(callback));
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
        runTrackedAsync(() -> CompletableFuture
                .supplyAsync(() -> queryGetAllByParams(sql, params), dbExecutor)
                .thenAccept(callback));
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
        runTrackedAsync(() -> CompletableFuture
                .supplyAsync(() -> queryAll(sql), dbExecutor)
                .thenAccept(callback));
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
        runTrackedAsync(() -> CompletableFuture
                .supplyAsync(() -> queryAllSingleColumn(sql), dbExecutor)
                .thenAccept(callback));
    }

    // 同步：执行更新
    public void execute(String sql, Object... params) {
        executeUpdate(sql, params);
    }

    // 同步：执行更新并返回受影响行数
    public int executeUpdate(String sql, Object... params) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 异步：执行更新并使用回调
    public void executeAsync(Runnable callback, String sql, Object... params) {
        runTrackedAsync(() -> CompletableFuture.runAsync(() -> {
            execute(sql, params);
            callback.run(); // 在执行完更新操作后，调用回调函数
        }, dbExecutor));
    }

    // 异步：执行更新
    public void executeAsync(String sql, Object... params) {
        runTrackedAsync(() -> CompletableFuture.runAsync(() -> execute(sql, params), dbExecutor));
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
        runTrackedAsync(() -> CompletableFuture
                .supplyAsync(() -> executeWithGeneratedKey(sql, params), dbExecutor)
                .thenAccept(generatedKey -> {
            if (generatedKey instanceof BigInteger) {
                callback.accept(((BigInteger) generatedKey).longValue());
            } else {
                callback.accept(generatedKey);
            }
        }));
    }

    /**
     * 先停止接收异步任务并等待队列排空，最后关闭连接池。
     * 若任务无法在超时内结束，则保留连接池，交由停机编排判定失败。
     */
    public void close() {
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            boolean terminated;
            try {
                terminated = dbExecutor.awaitTermination(
                        EXECUTOR_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!terminated) {
                    LogCore.DB.warn("DB executor drain timed out after {}s, interrupting remaining tasks",
                            EXECUTOR_DRAIN_TIMEOUT_SECONDS);
                    dbExecutor.shutdownNow();
                    terminated = dbExecutor.awaitTermination(
                            EXECUTOR_FORCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                dbExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while draining DB executor", e);
            }
            if (!terminated) {
                throw new IllegalStateException(
                        "DB executor did not terminate; data source was left open");
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
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
