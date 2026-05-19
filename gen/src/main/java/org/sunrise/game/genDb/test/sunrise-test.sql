
USE sunrise;

DROP TABLE IF EXISTS test_all_types;
CREATE TABLE `test_all_types`
(
    `id`             INT AUTO_INCREMENT PRIMARY KEY,

    -- 1. 整数类型测试 (映射为 int)
    `test_tinyint`   TINYINT       DEFAULT 0                  NOT NULL COMMENT '测试 tinyint',
    `test_smallint`  SMALLINT      DEFAULT 0                  NOT NULL COMMENT '测试 smallint',
    `test_int`       INT           DEFAULT 0                  NOT NULL COMMENT '测试 int',

    -- 2. 长整数类型测试 (映射为 long)
    `test_bigint`    BIGINT        DEFAULT 0                  NOT NULL COMMENT '测试 bigint',

    -- 3. 浮点与高精度测试 (映射为 float, double, BigDecimal)
    `test_float`     FLOAT         DEFAULT 0.0                NOT NULL COMMENT '测试 float',
    `test_double`    DOUBLE        DEFAULT 0.0                NOT NULL COMMENT '测试 double',
    `test_decimal`   DECIMAL(19,4) DEFAULT 0.0000             NOT NULL COMMENT '测试 decimal (BigDecimal防精度丢失)',

    -- 4. 布尔类型测试 (映射为 boolean)
    `test_boolean`   BOOLEAN       DEFAULT FALSE              NOT NULL COMMENT '测试 boolean (MySQL底层为TINYINT(1))',
    `test_bit`       BIT(1)        DEFAULT b'0'               NOT NULL COMMENT '测试 bit',

    -- 5. 字符串类型测试 (映射为 String)
    `test_char`      CHAR(10)      DEFAULT ''                 NOT NULL COMMENT '测试 char',
    `test_varchar`   VARCHAR(255)  DEFAULT ''                 NOT NULL COMMENT '测试 varchar',
    `test_text`      TEXT                                     NOT NULL COMMENT '测试 text',

    -- 6. 时间与日期测试 (映射为 LocalDate, LocalTime, LocalDateTime)
    `test_date`      DATE          DEFAULT '2000-01-01'       NOT NULL COMMENT '测试 date (LocalDate)',
    `test_time`      TIME          DEFAULT '00:00:00'         NOT NULL COMMENT '测试 time (LocalTime)',
    `test_datetime`  DATETIME      DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '测试 datetime (LocalDateTime)',
    `test_timestamp` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '测试 timestamp (LocalDateTime)',

    -- 7. 二进制类型测试 (映射为 byte[])
    `test_blob`      BLOB                                     NOT NULL COMMENT '测试 blob'
)
    COMMENT ='全数据类型映射测试表';

-- 插入测试数据

-- 记录1：常规值与正数极大值（测试正常转换与溢出边界）
INSERT INTO `test_all_types` (
    `test_tinyint`, `test_smallint`, `test_int`, `test_bigint`,
    `test_float`, `test_double`, `test_decimal`,
    `test_boolean`, `test_bit`,
    `test_char`, `test_varchar`, `test_text`,
    `test_date`, `test_time`, `test_datetime`, `test_timestamp`,
    `test_blob`
) VALUES (
             127, 32767, 2147483647, 9223372036854775807,
             3.14159, 2.718281828459, 999999999999999.9999,
             TRUE, b'1',
             'char_1', 'varchar_normal_test', '这是一段很长的 text 文本，用来测试 String 转换是否完整。',
             '2026-05-19', '11:45:51', '2026-05-19 11:45:51', '2026-05-19 11:45:51',
             0x48656C6C6F -- 'Hello' 的十六进制形式
         );

-- 记录2：负数、零值、空字符串、避开时区边界的最小时间
INSERT INTO `test_all_types` (
    `test_tinyint`, `test_smallint`, `test_int`, `test_bigint`,
    `test_float`, `test_double`, `test_decimal`,
    `test_boolean`, `test_bit`,
    `test_char`, `test_varchar`, `test_text`,
    `test_date`, `test_time`, `test_datetime`, `test_timestamp`,
    `test_blob`
) VALUES (
             -128, -32768, -2147483648, -9223372036854775808,
             -1.5, -2.5, -123.4567,
             FALSE, b'0',
             '', '', '',
             '1971-01-01', '00:00:00', '1971-01-01 00:00:00', '1971-01-01 00:00:00',
             ''
         );