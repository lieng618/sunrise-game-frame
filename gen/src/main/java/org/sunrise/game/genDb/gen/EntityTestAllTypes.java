package org.sunrise.game.genDb.gen;

import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 数据库表 test_all_types 的不可变实体类
 * 自动生成，请勿手动修改
 */
@Value
public class EntityTestAllTypes {
    int id;
    int testTinyint;
    int testSmallint;
    int testInt;
    long testBigint;
    float testFloat;
    double testDouble;
    BigDecimal testDecimal;
    boolean testBoolean;
    boolean testBit;
    String testChar;
    String testVarchar;
    String testText;
    LocalDate testDate;
    LocalTime testTime;
    LocalDateTime testDatetime;
    LocalDateTime testTimestamp;
    byte[] testBlob;

    public EntityTestAllTypes(Map<String, Object> dataMap) {
        this.id = EntityConverter.convertToType(dataMap.get("id"), int.class);
        this.testTinyint = EntityConverter.convertToType(dataMap.get("test_tinyint"), int.class);
        this.testSmallint = EntityConverter.convertToType(dataMap.get("test_smallint"), int.class);
        this.testInt = EntityConverter.convertToType(dataMap.get("test_int"), int.class);
        this.testBigint = EntityConverter.convertToType(dataMap.get("test_bigint"), long.class);
        this.testFloat = EntityConverter.convertToType(dataMap.get("test_float"), float.class);
        this.testDouble = EntityConverter.convertToType(dataMap.get("test_double"), double.class);
        this.testDecimal = EntityConverter.convertToType(dataMap.get("test_decimal"), BigDecimal.class);
        this.testBoolean = EntityConverter.convertToType(dataMap.get("test_boolean"), boolean.class);
        this.testBit = EntityConverter.convertToType(dataMap.get("test_bit"), boolean.class);
        this.testChar = EntityConverter.convertToType(dataMap.get("test_char"), String.class);
        this.testVarchar = EntityConverter.convertToType(dataMap.get("test_varchar"), String.class);
        this.testText = EntityConverter.convertToType(dataMap.get("test_text"), String.class);
        this.testDate = EntityConverter.convertToType(dataMap.get("test_date"), LocalDate.class);
        this.testTime = EntityConverter.convertToType(dataMap.get("test_time"), LocalTime.class);
        this.testDatetime = EntityConverter.convertToType(dataMap.get("test_datetime"), LocalDateTime.class);
        this.testTimestamp = EntityConverter.convertToType(dataMap.get("test_timestamp"), LocalDateTime.class);
        this.testBlob = EntityConverter.convertToType(dataMap.get("test_blob"), byte[].class);
    }
}
