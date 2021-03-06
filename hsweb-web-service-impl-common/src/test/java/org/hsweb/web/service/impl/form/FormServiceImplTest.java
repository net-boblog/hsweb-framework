package org.hsweb.web.service.impl.form;

import org.hsweb.ezorm.executor.SqlExecutor;
import org.hsweb.ezorm.run.Database;
import org.hsweb.ezorm.run.Table;
import org.hsweb.web.core.authorize.ExpressionScopeBean;
import org.hsweb.web.bean.po.form.Form;
import org.hsweb.web.service.form.DynamicFormService;
import org.hsweb.web.service.form.FormService;
import org.hsweb.web.service.impl.AbstractTestCase;
import org.hsweb.web.core.utils.RandomUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.webbuilder.utils.file.FileUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by zhouhao on 16-4-20.
 */
@Transactional
@Rollback
public class FormServiceImplTest extends AbstractTestCase {

    @Resource
    protected FormService formService;
    @Resource
    private DynamicFormService dynamicFormService;
    @Resource
    protected Database dataBase;

    @Resource
    protected SqlExecutor sqlExecutor;

    @Autowired(required = false)
    private Map<String, ExpressionScopeBean> expressionScopeBeanMap;

    protected Form form;

    private String[] meta = {
            "{" +
                    "\"id1\":[" +
                    "{\"key\":\"name\",\"value\":\"u_id\",\"describe\":\"名称\"}," +
                    "{\"key\":\"comment\",\"value\":\"ID\",\"describe\":\"ID\"}," +
                    "{\"key\":\"javaType\",\"value\":\"string\",\"describe\":\"java类型\"}," +
                    "{\"key\":\"dataType\",\"value\":\"varchar2(32)\",\"describe\":\"数据库类型\"}" +
                    "]" +
                    ",\"id2\":[" +
                    "{\"key\":\"name\",\"value\":\"name\",\"describe\":\"名称\"}," +
                    "{\"key\":\"comment\",\"value\":\"姓名\",\"describe\":\"姓名\"}," +
                    "{\"key\":\"javaType\",\"value\":\"string\",\"describe\":\"java类型\"}," +
                    "{\"key\":\"validator-list\",\"value\":\"[{\\\"validator\\\":\\\"NotNull\\\"}]\",,\"describe\":\"java类型\"}," +
                    "{\"key\":\"dataType\",\"value\":\"varchar2(32)\",\"describe\":\"数据库类型\"}" +
                    "]" +
                    "}",
            "{" +
                    "\"id1\":[" +
                    "{\"key\":\"name\",\"value\":\"u_id\",\"describe\":\"名称\"}," +
                    "{\"key\":\"comment\",\"value\":\"ID\",\"describe\":\"ID\"}," +
                    "{\"key\":\"javaType\",\"value\":\"string\",\"describe\":\"java类型\"}," +
                    "{\"key\":\"dataType\",\"value\":\"varchar2(32)\",\"describe\":\"数据库类型\"}" +
                    "]" +
                    ",\"id2\":[" +
                    "{\"key\":\"name\",\"value\":\"name\",\"describe\":\"名称\"}," +
                    "{\"key\":\"comment\",\"value\":\"姓名\",\"describe\":\"字段描述\"}," +
                    "{\"key\":\"javaType\",\"value\":\"string\",\"describe\":\"java类型\"}," +
                    "{\"key\":\"dataType\",\"value\":\"varchar2(32)\",\"describe\":\"数据库类型\"}" +
                    "]" +
                    ",\"id3\":[" +
                    "{\"key\":\"name\",\"value\":\"sex\",\"describe\":\"名称\"}," +
                    "{\"key\":\"comment\",\"value\":\"性别\",\"describe\":\"性别\"}," +
                    "{\"key\":\"javaType\",\"value\":\"string\",\"describe\":\"java类型\"}," +
                    "{\"key\":\"dataType\",\"value\":\"varchar2(32)\",\"describe\":\"数据库类型\"}" +
                    "]" +
                    "}"
    };

    @Before
    public void setup() throws Exception {
        form = new Form();
        form.setName("test_form");
        form.setCreateDate(new Date());
        form.setHtml("<input fieldId='id1'/><input fieldId='id2'/>");
        form.setMeta(meta[0]);
        form.setId(RandomUtil.randomChar());
        formService.insert(form);
    }

    @Test
    public void testDeploy() throws Exception {
        formService.deploy(form.getId());
        dataBase.getTable("test_form").createInsert().value(new HashMap<String, Object>() {{
            put("u_id", "test");
            put("name", "张三");
        }}).exec();

        dataBase.getTable("test_form").createUpdate().set("u_id", "test2").where("u_id", "test").exec();

        Table<Map<String, Object>> table = dataBase.getTable("test_form");

        Map<String, Object> data = table.createQuery().where("name$LIKE", "张三").single();

        Assert.assertEquals("张三", data.get("name"));
        Assert.assertEquals("test2", data.get("u_id"));
        formService.createDeployHtml(form.getName());
        formService.deploy(form.getId());
        formService.createDeployHtml(form.getName());

        form.setMeta(meta[1]);
        formService.update(form);
        formService.deploy(form.getId());

        formService.selectLatestList(new org.hsweb.web.bean.common.QueryParam());
        dynamicFormService.importExcel("test_form", FileUtils.getResourceAsStream("test.xlsx"));
    }


}