package org.sunrise.game.genDb.test;

import org.sunrise.game.db.DbService;
import org.sunrise.game.genDb.gen.EntityTestAllTypes;

import java.util.List;
import java.util.Map;

public class TestDbTypeMain {
    public static void main(String[] args) {
        String sql = "SELECT * FROM test_all_types";
        List<Map<String, Object>> resultList = new DbService().queryAll(sql);

        System.out.println("共查询到 " + resultList.size() + " 条记录，开始映射测试：\n");

        // 循环遍历并将每一行数据转换为实体对象
        for (Map<String, Object> dataMap : resultList) {
            EntityTestAllTypes entity = new EntityTestAllTypes(dataMap);

            System.out.println("========== 记录 ID: " + entity.getId() + " ==========");
            System.out.println(entity);
            System.out.println();
        }
    }
}
