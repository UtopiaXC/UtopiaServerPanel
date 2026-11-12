package com.utopiaxc.utopiaserverpanel.web.db;

import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.*;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * MyBatis integration backed by pooled SQLite connections with per-connection
 * PRAGMA foreign_keys=ON, busy_timeout=5000, and WAL journaling.
 */
public final class MyBatisFactory {
    private static SqlSessionFactory sqlSessionFactory;
    private static PooledDataSource dataSource;

    private MyBatisFactory() {}

    public static void initialize(String configDirPath) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found", e);
        }

        String dbFile = Paths.get(configDirPath, "utopia_server_panel.db").toString().replace("\\", "/");
        String dbUrl = "jdbc:sqlite:" + dbFile;

        Properties driverProps = new Properties();
        driverProps.setProperty("foreign_keys", "true");
        driverProps.setProperty("busy_timeout", "5000");
        driverProps.setProperty("journal_mode", "WAL");
        driverProps.setProperty("synchronous", "NORMAL");

        dataSource = new PooledDataSource("org.sqlite.JDBC", dbUrl, null, null);
        dataSource.setDriverProperties(driverProps);
        dataSource.setPoolMaximumActiveConnections(8);
        dataSource.setPoolMaximumIdleConnections(4);

        Environment environment = new Environment.Builder("production")
                .transactionFactory(new JdbcTransactionFactory())
                .dataSource(dataSource)
                .build();

        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);

        registerMappers(configuration);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
        } catch (Exception e) {
            PanelLog.get().warn("Failed to verify SQLite pragmas", e);
        }

        PanelLog.get().info("MyBatis initialized with 3NF SQLite backend");
    }

    private static void registerMappers(Configuration configuration) {
        configuration.addMapper(PermissionMapper.class);
        configuration.addMapper(RoleMapper.class);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(PlayerBindingMapper.class);
        configuration.addMapper(SessionMapper.class);
        configuration.addMapper(ApiTokenMapper.class);
        configuration.addMapper(SecurityMapper.class);
        configuration.addMapper(SettingsMapper.class);
        configuration.addMapper(MonitorMapper.class);
    }

    public static boolean isInitialized() {
        return sqlSessionFactory != null;
    }

    public static SqlSession openSession() {
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("MyBatisFactory not initialized");
        }
        return sqlSessionFactory.openSession();
    }

    public static SqlSession openSessionAutoCommit() {
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("MyBatisFactory not initialized");
        }
        return sqlSessionFactory.openSession(true);
    }

    public static void doWork(Consumer<SqlSession> work) {
        try (SqlSession session = openSessionAutoCommit()) {
            work.accept(session);
        }
    }

    public static <T> T doWorkWithResult(SqlSessionFunction<T> work) {
        try (SqlSession session = openSessionAutoCommit()) {
            return work.apply(session);
        }
    }

    public static Connection getRawConnection() {
        try {
            Connection conn = dataSource.getConnection();
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
            }
            return conn;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to obtain database connection", e);
        }
    }

    @FunctionalInterface
    public interface SqlSessionFunction<T> {
        T apply(SqlSession session);
    }

    public static void shutdown() {
        if (dataSource != null) {
            dataSource.forceCloseAll();
            dataSource = null;
        }
        sqlSessionFactory = null;
        PanelLog.get().info("MyBatis shutdown complete");
    }
}
