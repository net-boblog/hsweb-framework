package org.hsweb.web.service.impl.form;

import com.alibaba.fastjson.JSON;
import org.hsweb.ezorm.meta.FieldMetaData;
import org.hsweb.ezorm.meta.TableMetaData;
import org.hsweb.ezorm.meta.expand.PropertyWrapper;
import org.hsweb.ezorm.run.*;
import org.hsweb.concurrent.lock.annotation.LockName;
import org.hsweb.concurrent.lock.annotation.ReadLock;
import org.hsweb.concurrent.lock.annotation.WriteLock;
import org.hsweb.web.bean.common.*;
import org.hsweb.web.bean.common.QueryParam;
import org.hsweb.web.bean.common.UpdateParam;
import org.hsweb.web.bean.po.GenericPo;
import org.hsweb.web.bean.po.form.Form;
import org.hsweb.web.bean.po.history.History;
import org.hsweb.web.core.Install;
import org.hsweb.web.core.authorize.ExpressionScopeBean;
import org.hsweb.web.core.exception.BusinessException;
import org.hsweb.web.core.exception.NotFoundException;
import org.hsweb.web.service.form.DynamicFormDataValidator;
import org.hsweb.web.service.form.DynamicFormService;
import org.hsweb.web.service.form.FormParser;
import org.hsweb.web.service.form.FormService;
import org.hsweb.web.service.history.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.webbuilder.office.excel.ExcelIO;
import org.webbuilder.office.excel.config.Header;
import org.webbuilder.utils.common.StringUtils;

import javax.annotation.Resource;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by zhouhao on 16-4-14.
 */
@Service("dynamicFormService")
@Transactional(rollbackFor = Throwable.class)
public class DynamicFormServiceImpl implements DynamicFormService, ExpressionScopeBean {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired(required = false)
    protected FormParser formParser;

    @Autowired
    protected Database database;

    @Resource
    protected FormService formService;

    @Resource
    protected HistoryService historyService;

    @Autowired(required = false)
    protected List<DynamicFormDataValidator> dynamicFormDataValidator;

    @Autowired(required = false)
    protected Map<String, ExpressionScopeBean> expressionScopeBeanMap;

    protected void initDefaultField(TableMetaData metaData) {
        String dataType;
        switch (Install.getDatabaseType()) {
            case "oracle":
                dataType = "varchar2(32)";
                break;
            case "h2":
                dataType = "varchar2(32)";
                break;
            default:
                dataType = "varchar(32)";
        }
        FieldMetaData id = new FieldMetaData("u_id", String.class, dataType, JDBCType.VARCHAR);
        id.setComment("主键");
        id.setProperty("read-only", true);

        metaData.setPrimaryKeys(new HashSet<>(Arrays.asList("u_id")));
        metaData.setProperty("primaryKey", "u_id");
        metaData.addField(id);

    }

    @Override
    public TableMetaData parseMeta(Form form) throws Exception {
        return formParser.parse(form);
    }

    @Override
    @WriteLock
    @LockName(value = "'form.lock.'+#form.name", isExpression = true)
    public void deploy(Form form) throws Exception {
        TableMetaData metaData = formParser.parse(form);
        initDefaultField(metaData);
        History history = historyService.selectLastHistoryByType("form.deploy." + form.getName());
        //首次部署
        if (history == null) {
            try {
                database.createTable(metaData);
            } catch (SQLException e) {
                database.reloadTable(metaData);
            }
        } else {
            Form lastDeploy = JSON.parseObject(history.getChangeAfter(), Form.class);
            TableMetaData lastDeployMetaData = formParser.parse(lastDeploy);
            initDefaultField(lastDeployMetaData);
            //向上发布
            database.reloadTable(lastDeployMetaData);//先放入旧的结构
            //更新结构
            database.alterTable(metaData);
        }
    }

    @Override
    @WriteLock
    @LockName(value = "'form.lock.'+#form.name", isExpression = true)
    public void unDeploy(Form form) throws Exception {
        database.removeTable(form.getName());
    }

    public Table getTableByName(String name) throws Exception {
        try {
            Table table = database.getTable(name);
            if (table == null) {
                throw new NotFoundException("表单[" + name + "]不存在");
            }
            return table;
        } catch (Exception e) {
            throw new NotFoundException("表单[" + name + "]不存在");
        }
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    @Transactional(readOnly = true)
    public <T> PagerResult<T> selectPager(String name, QueryParam param) throws Exception {
        PagerResult<T> result = new PagerResult<>();
        Table table = getTableByName(name);
        Query query = table.createQuery();
        query.setParam(param);
        int total = query.total();
        result.setTotal(total);
        param.rePaging(total);
        result.setData(query.list(param.getPageIndex(), param.getPageSize()));
        return result;
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    @Transactional(readOnly = true)
    public <T> List<T> select(String name, QueryParam param) throws Exception {
        Table table = getTableByName(name);
        Query query = table.createQuery().setParam(param);
        return query.list();
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    @Transactional(readOnly = true)
    public int total(String name, QueryParam param) throws Exception {
        Table table = getTableByName(name);
        Query query = table.createQuery().setParam(param);
        return query.total();
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    public String insert(String name, Map<String, Object> data) throws Exception {
        Table table = getTableByName(name);
        String primaryKeyName = getPrimaryKeyName(name);
        String pk = GenericPo.createUID();
        data.put(primaryKeyName, pk);
        Insert insert = table.createInsert().value(data);
        insert.exec();
        return pk;
    }

    @Override
    public String saveOrUpdate(String name, Map<String, Object> data) throws Exception {
        String id = (String) data.get(getPrimaryKeyName(name));
        if (id == null)
            id = getRepeatDataId(name, data);
        if (id != null) {
            updateByPk(name, id, UpdateParam.build(data));
        } else {
            id = insert(name, data);
        }
        return id;
    }

    protected String getRepeatDataId(String name, Map<String, Object> data) {
        if (dynamicFormDataValidator != null) {
            for (DynamicFormDataValidator validator : dynamicFormDataValidator) {
                String id = validator.getRepeatDataId(name, data);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    public boolean deleteByPk(String name, String pk) throws Exception {
        String primaryKeyName = getPrimaryKeyName(name);
        Table table = getTableByName(name);
        Delete delete = table.createDelete().where(primaryKeyName, pk);
        return delete.exec() == 1;
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    public int delete(String name, DeleteParam where) throws Exception {
        Table table = getTableByName(name);
        Delete delete = table.createDelete();
        delete.setParam(where);
        return delete.exec();
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    public int updateByPk(String name, String pk, UpdateParam<Map<String, Object>> param) throws Exception {
        Table table = getTableByName(name);
        Update update = table.createUpdate().setParam(param);
        update.where(getPrimaryKeyName(name), pk);
        return update.exec();
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    public int update(String name, UpdateParam<Map<String, Object>> param) throws Exception {
        Table table = getTableByName(name);
        Update update = table.createUpdate().setParam(param);
        return update.exec();
    }

    @ReadLock
    @LockName(value = "'form.lock.'+#tableName", isExpression = true)
    public String getPrimaryKeyName(String tableName) throws Exception {
        Table table = getTableByName(tableName);
        return table.getMeta().getProperty("primaryKey", "u_id").toString();
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    public <T> T selectByPk(String name, Object pk) throws Exception {
        Table<T> table = getTableByName(name);
        Query<T> query = table.createQuery().where(getPrimaryKeyName(name), pk);
        return query.single();
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    @Transactional(readOnly = true)
    public void exportExcel(String name, QueryParam param, OutputStream outputStream) throws Exception {
        List<Object> dataList = select(name, param);
        Table table = getTableByName(name);
        TableMetaData metaData = table.getMeta();
        List<Header> headers = new LinkedList<>();
        Map<String, Object> sample = dataList.isEmpty() ? new HashMap<>() : (Map) dataList.get(0);
        int[] index = new int[1];
        index[0] = 1;
        metaData.getFields().forEach(fieldMetaData -> {
            PropertyWrapper valueWrapper = fieldMetaData.getProperty("export-excel", false);
            if (valueWrapper.isTrue()) {
                String title = fieldMetaData.getProperty("export-header", fieldMetaData.getComment()).toString();
                if (StringUtils.isNullOrEmpty(title)) {
                    title = "字段" + index[0]++;
                }
                String field = fieldMetaData.getName();
                Set<String> includes = param.getIncludes();
                Set<String> excludes = param.getExcludes();
                if (!includes.isEmpty()) {
                    if (!includes.contains(field)) return;
                }
                if (!excludes.isEmpty()) {
                    if (excludes.contains(field)) return;
                }
                if (sample.get(field + "_cn") != null)
                    field = field + "_cn";
                headers.add(new Header(title, field));
            }
        });
        if (metaData.triggerIsSupport("export.excel")) {
            Map<String, Object> var = new HashMap<>();
            if (expressionScopeBeanMap != null)
                var.putAll(expressionScopeBeanMap);
            var.put("database", database);
            var.put("table", table);
            var.put("dataList", dataList);
            var.put("headers", headers);
            metaData.on("export.excel", var);
        }
        ExcelIO.write(outputStream, headers, dataList);
    }

    @Override
    @ReadLock
    @LockName(value = "'form.lock.'+#name", isExpression = true)
    public Map<String, Object> importExcel(String name, InputStream inputStream) throws Exception {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> excelData;
        try {
            excelData = ExcelIO.read2Map(inputStream);
        } catch (Exception e) {
            throw new BusinessException("解析excel失败,请确定文件格式正确!", e, 500);
        }
        List<Map<String, Object>> dataList = new LinkedList<>();
        Map<String, String> headerMapper = new HashMap<>();
        Table table = getTableByName(name);
        TableMetaData metaData = table.getMeta();
        metaData.getFields().forEach(fieldMetaData -> {
            PropertyWrapper valueWrapper = fieldMetaData.getProperty("importExcel", true);
            if (valueWrapper.isTrue()) {
                String title = fieldMetaData.getProperty("excelHeader", fieldMetaData.getComment()).toString();
                String field = fieldMetaData.getName();
                headerMapper.put(title, field);
            }
        });
        if (metaData.triggerIsSupport("export.import.before")) {
            Map<String, Object> var = new HashMap<>();
            var.put("headerMapper", headerMapper);
            var.put("excelData", excelData);
            var.put("dataList", dataList);
            var.put("database", database);
            var.put("table", table);
            if (expressionScopeBeanMap != null)
                var.putAll(expressionScopeBeanMap);
            metaData.on("export.import.before", var);
        }
        excelData.forEach(data -> {
            Map<String, Object> newData = new HashMap<>();
            data.forEach((k, v) -> {
                String field = headerMapper.get(k);
                if (field != null) {
                    newData.put(field, v);
                } else {
                    newData.put(k, v);
                }
            });
            dataList.add(newData);
        });
        List<Map<String, Object>> errorMessage = new LinkedList<>();
        int index = 0, success = 0;
        for (Map<String, Object> map : dataList) {
            index++;
            try {
                if (metaData.triggerIsSupport("export.import.each")) {
                    Map<String, Object> var = new HashMap<>();
                    var.put("headerMapper", headerMapper);
                    var.put("excelData", excelData);
                    var.put("dataList", dataList);
                    var.put("data", map);
                    var.put("database", database);
                    var.put("table", table);
                    if (expressionScopeBeanMap != null)
                        var.putAll(expressionScopeBeanMap);
                    metaData.on("export.import.each", var);
                }
                saveOrUpdate(name, map);
                success++;
            } catch (Exception e) {
                Map<String, Object> errorMsg = new HashMap<>();
                errorMsg.put("index", index);
                errorMsg.put("message", e.getMessage());
                errorMessage.add(errorMsg);
            }
        }
        long endTime = System.currentTimeMillis();
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        result.put("total", dataList.size());
        result.put("success", success);
        result.put("errorMessage", errorMessage);
        return result;
    }
}
