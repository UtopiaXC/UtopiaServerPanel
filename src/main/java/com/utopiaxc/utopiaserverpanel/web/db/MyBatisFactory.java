package com.utopiaxc.utopiaserverpanel.web.db;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.util.function.Consumer;

/**
 * MyBatis integration for the UtopiaServerPanel.
 * <p>
 * Provides a pooled SQLite DataSource and SqlSessionFactory.
 * Use {@link #openSession()} or {@link #doWork(Consumer)} for database operations.
 * </p>
 */
public final class MyBatisFactory {
    private static SqlSessionFactory sqlSessionFactory;
    private static DataSource dataSource;
    private static String dbUrl;

    private MyBatisFactory() {}

    /**
     * Initialize MyBatis with the SQLite database path.
     * Must be called once during server startup.
     */
    public static void initialize(String configDirPath) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        dbUrl = "jdbc:sqlite:" + configDirPath + "/utopia_server_panel.db";

        // Create pooled DataSource
        dataSource = new PooledDataSource(
                "org.sqlite.JDBC",
                dbUrl,
                null,  // username (not needed for SQLite)
                null   // password (not needed for SQLite)
        );
        // SQLite specific pragmas
        ((PooledDataSource) dataSource).setPoolMaximumActiveConnections(5);
        ((PooledDataSource) dataSource).setPoolMaximumIdleConnections(2);

        // Build MyBatis environment
        Environment environment = new Environment.Builder("development")
                .transactionFactory(new JdbcTransactionFactory())
                .dataSource(dataSource)
                .build();

        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);

        // Register mappers
        registerMappers(configuration);

        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        // Apply SQLite pragmas on first connection
        try (SqlSession session = sqlSessionFactory.openSession()) {
            session.getConnection().createStatement().execute("PRAGMA journal_mode=WAL");
            session.getConnection().createStatement().execute("PRAGMA foreign_keys=ON");
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.warn("Failed to set SQLite pragmas", e);
        }

        UtopiaServerPanel.LOGGER.info("MyBatis initialized with SQLite");
    }

    @SuppressWarnings("unchecked")
    private static void registerMappers(Configuration configuration) {
        // Register all mapper interfaces
        try {
            configuration.addMapper((Class) Class.forName(
                    "com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper"));
            configuration.addMapper((Class) Class.forName(
                    "com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper"));
            configuration.addMapper((Class) Class.forName(
                    "com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper"));
            configuration.addMapper((Class) Class.forName(
                    "com.utopiaxc.utopiaserverpanel.web.db.mapper.BindingMapper"));
            configuration.addMapper((Class) Class.forName(
                    "com.utopiaxc.utopiaserverpanel.web.db.mapper.TokenMapper"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to register MyBatis mappers", e);
        }
    }

    /**
     * Open a new SqlSession. Caller is responsible for closing it.
     */
    public static SqlSession openSession() {
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("MyBatisFactory not initialized. Call initialize() first.");
        }
        return sqlSessionFactory.openSession();
    }

    /**
     * Open a new SqlSession with auto-commit enabled.
     */
    public static SqlSession openSessionAutoCommit() {
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("MyBatisFactory not initialized.");
        }
        return sqlSessionFactory.openSession(true);
    }

    /**
     * Execute work within a managed SqlSession (auto-close).
     */
    public static void doWork(Consumer<SqlSession> work) {
        try (SqlSession session = openSessionAutoCommit()) {
            work.accept(session);
        }
    }

    /**
     * Execute work within a managed SqlSession and return a result.
     */
    public static <T> T doWorkWithResult(SqlSessionFunction<T> work) {
        try (SqlSession session = openSessionAutoCommit()) {
            return work.apply(session);
        }
    }

    /**
     * Get a raw JDBC connection (for schema operations that don't use MyBatis mappers).
     */
    public static java.sql.Connection getRawConnection() {
        try {
            return dataSource.getConnection();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get database connection", e);
        }
    }

    /**
     * Get a mapper instance for one-off use.
     */
    public static <T> T getMapper(Class<T> mapperClass) {
        SqlSession session = openSession();
        // Note: session must be closed by caller. For convenience, use doWork instead.
        return session.getMapper(mapperClass);
    }

    @FunctionalInterface
    public interface SqlSessionFunction<T> {
        T apply(SqlSession session);
    }

    /**
     * Shutdown MyBatis and release resources.
     */
    public static void shutdown() {
        if (sqlSessionFactory != null) {
            sqlSessionFactory = null;
        }
        UtopiaServerPanel.LOGGER.info("MyBatis shutdown complete");
    }
}
