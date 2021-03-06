package org.hsweb.web.service.impl.system;

import org.hsweb.ezorm.executor.SqlExecutor;
import org.hsweb.web.bean.common.database.TableField;
import org.hsweb.web.service.system.DataBaseManagerService;
import org.hsweb.web.service.system.SqlExecuteProcess;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 数据库管理服务实现类
 * Created by zhouhao on 16-4-21.
 *
 * @author zhouhao,
 * @version 1.0
 * @see DataBaseManagerService
 */
@Service(value = "dataBaseManagerService")
public class DataBaseManagerServiceImpl implements DataBaseManagerService {
    @Resource
    private SqlExecutor sqlExecutor;

    @Override
    public List<String> getTableNameList() {
        return null;
    }

    @Override
    public List<TableField> getFieldList(String tableName) {
        return null;
    }

    @Override
    public void executeSQL(String sql, SqlExecuteProcess process) throws Exception {

    }
}
